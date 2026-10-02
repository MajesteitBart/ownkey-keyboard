package org.ownkey.offline

import android.content.*
import android.os.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume

enum class RuntimeState { UNLOADED, LOADING, READY, TRANSCRIBING, FAILED }

/** Main-process transport. It has no dependency on native classes, recording, routing or editors. */
class InferenceConnection(context: Context) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mutex = Mutex()
    private val _state = MutableStateFlow(RuntimeState.UNLOADED)
    val state: StateFlow<RuntimeState> = _state
    private var remote: Messenger? = null
    private var binding: ServiceConnection? = null
    private var processId: Int? = null
    private var nextRequest = 0L
    private var pending: ((Bundle) -> Unit)? = null
    private var connecting: CompletableDeferred<Unit>? = null
    private var requestStartedAt = 0L
    private var liveId = 0L
    private var liveUpdate: ((LiveUpdate) -> Unit)? = null
    private var liveFailure: ((LocalAsrException) -> Unit)? = null
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        if (data.getLong("request") == nextRequest && binding != null) {
            processId = data.getInt("pid").takeIf { it > 0 }
            when (data.getString("state")) {
                "loading" -> _state.value = RuntimeState.LOADING
                "transcribing" -> _state.value = RuntimeState.TRANSCRIBING
                "live_update" -> {
                    _state.value = RuntimeState.TRANSCRIBING
                    liveUpdate?.invoke(data.liveUpdate())
                }
                else -> {
                    val callback = pending
                    if (callback != null) {
                        callback(data)
                    } else if (liveId != 0L) {
                        // A live session failed between updates, before anyone asked for the final text.
                        val failure = liveFailure
                        endLive()
                        _state.value = RuntimeState.FAILED
                        failure?.invoke(data.failure())
                    }
                }
            }
        }
        true
    })

    suspend fun load(modelId: String) { request(modelId, null, "") }

    /** [hotwords] is an already encoded [HotwordTransport] string; empty keeps the greedy profile. */
    suspend fun transcribe(modelId: String, audio: ParcelFileDescriptor, hotwords: String = ""): String =
        request(modelId, audio, hotwords)

    private suspend fun request(modelId: String, audio: ParcelFileDescriptor?, hotwords: String): String = withContext(Dispatchers.Main.immediate) {
        if (!mutex.tryLock()) throw LocalAsrException(LocalAsrFailure.BUSY)
        requestStartedAt = SystemClock.elapsedRealtime()
        try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                connect()
                val id = ++nextRequest
                val result = suspendCancellableCoroutine<Bundle> { continuation ->
                    pending = { bundle ->
                        pending = null
                        if (continuation.isActive) continuation.resume(bundle)
                    }
                    continuation.invokeOnCancellation { main.post { if (nextRequest == id) disconnect(kill = true) } }
                    try {
                        remote!!.send(Message.obtain(null, InferenceService.COMMAND).apply {
                            replyTo = reply
                            data = Bundle().apply {
                                putLong("request", id); putString("model", modelId)
                                if (audio != null) putParcelable("audio", audio)
                                if (hotwords.isNotEmpty()) putString(InferenceService.KEY_HOTWORDS, hotwords)
                            }
                        })
                    } catch (_: Exception) { died() }
                }
                if (result.getString("state") != "ok") {
                    val failure = runCatching { LocalAsrFailure.valueOf(result.getString("failure")!!) }
                        .getOrDefault(LocalAsrFailure.PROCESS_DIED)
                    _state.value = if (failure == LocalAsrFailure.CANCELLED) RuntimeState.UNLOADED else RuntimeState.FAILED
                    val detail = result.getString(InferenceService.KEY_DETAIL)
                    Log.w(TAG, "Request failed: $failure${detail?.let { " ($it)" }.orEmpty()}")
                    throw LocalAsrException(failure, detail)
                }
                _state.value = RuntimeState.READY
                result.getString("text").orEmpty()
            }
        } catch (_: TimeoutCancellationException) {
            val detail = "no answer from the inference process after ${elapsedMs()} ms"
            Log.w(TAG, "Request timed out: $detail")
            disconnect(kill = true)
            throw LocalAsrException(LocalAsrFailure.TIMEOUT, detail)
        } catch (error: CancellationException) {
            disconnect(kill = true)
            throw error
        } finally { mutex.unlock() }
    }

    /**
     * Starts live dictation over a raw 16 kHz mono 16-bit recording that the caller keeps writing to
     * [audio]. Report progress with [LiveTranscription.append]. [onUpdate] and [onFailure] run on the
     * main thread; after [onFailure] the session is over. The connection stays reserved for this
     * session until it finishes, fails or is cancelled.
     */
    suspend fun startLive(
        modelId: String,
        audio: ParcelFileDescriptor,
        hotwords: String,
        onUpdate: (LiveUpdate) -> Unit,
        onFailure: (LocalAsrException) -> Unit,
    ): LiveTranscription {
        var started = 0L
        try {
            return openLive(modelId, audio, hotwords, onUpdate, onFailure) { started = it }
        } catch (cancel: CancellationException) {
            // withContext can throw after the block finished; the session it started must not stay reserved.
            if (started != 0L) cancelLive(started)
            throw cancel
        }
    }

    private suspend fun openLive(
        modelId: String,
        audio: ParcelFileDescriptor,
        hotwords: String,
        onUpdate: (LiveUpdate) -> Unit,
        onFailure: (LocalAsrException) -> Unit,
        onStarted: (Long) -> Unit,
    ): LiveTranscription = withContext(Dispatchers.Main.immediate) {
        audio.use {
            if (!mutex.tryLock()) throw LocalAsrException(LocalAsrFailure.BUSY)
            requestStartedAt = SystemClock.elapsedRealtime()
            try {
                connect()
            } catch (error: Throwable) {
                mutex.unlock()
                throw error
            }
            val id = ++nextRequest
            liveId = id
            liveUpdate = onUpdate
            liveFailure = onFailure
            onStarted(id)
            try {
                remote!!.send(Message.obtain(null, InferenceService.LIVE_START).apply {
                    replyTo = reply
                    data = Bundle().apply {
                        putLong("request", id); putString("model", modelId); putParcelable("audio", audio)
                        if (hotwords.isNotEmpty()) putString(InferenceService.KEY_HOTWORDS, hotwords)
                    }
                })
            } catch (_: Exception) {
                // The caller hears about it from this exception, not from the failure callback as well.
                liveFailure = null
                died()
                throw LocalAsrException(LocalAsrFailure.PROCESS_DIED)
            }
            LiveTranscription(this@InferenceConnection, id)
        }
    }

    internal fun appendLive(id: Long, samples: Long) {
        main.post {
            if (liveId != id) return@post
            runCatching {
                remote?.send(Message.obtain(null, InferenceService.LIVE_APPEND).apply {
                    data = Bundle().apply { putLong("request", id); putLong(InferenceService.KEY_SAMPLES, samples) }
                })
            }
        }
    }

    internal suspend fun finishLive(id: Long, samples: Long): LiveUpdate = withContext(Dispatchers.Main.immediate) {
        if (liveId != id) throw LocalAsrException(LocalAsrFailure.CANCELLED)
        try {
            val result = withTimeout(FINISH_TIMEOUT_MS) {
                suspendCancellableCoroutine<Bundle> { continuation ->
                    pending = { bundle ->
                        pending = null
                        if (continuation.isActive) continuation.resume(bundle)
                    }
                    try {
                        remote!!.send(Message.obtain(null, InferenceService.LIVE_FINISH).apply {
                            data = Bundle().apply { putLong("request", id); putLong(InferenceService.KEY_SAMPLES, samples) }
                        })
                    } catch (_: Exception) { died() }
                }
            }
            if (result.getString("state") != "ok") {
                val failure = result.failure()
                _state.value = if (failure.reason == LocalAsrFailure.CANCELLED) RuntimeState.READY else RuntimeState.FAILED
                throw failure
            }
            _state.value = RuntimeState.READY
            result.liveUpdate()
        } catch (_: TimeoutCancellationException) {
            val detail = "no final text from the inference process after ${elapsedMs()} ms"
            Log.w(TAG, "Live finish timed out: $detail")
            // Only while this session still holds the connection; a newer one must not end with it.
            if (liveId == id) disconnect(kill = true)
            throw LocalAsrException(LocalAsrFailure.TIMEOUT, detail)
        } catch (cancel: CancellationException) {
            // The native decode can't be interrupted; ending the process is the bounded way to stop it.
            if (liveId == id) disconnect(kill = true)
            throw cancel
        } finally {
            if (liveId == id) endLive()
        }
    }

    /** Ends a live session without a result. A decode that is already running finishes and is discarded. */
    internal fun cancelLive(id: Long) {
        onMain {
            if (liveId != id) return@onMain
            runCatching {
                remote?.send(Message.obtain(null, InferenceService.LIVE_CANCEL).apply {
                    data = Bundle().apply { putLong("request", id) }
                })
            }
            // A finish that was waiting belongs to this session. It ends now, cancelled, instead of waiting
            // for its timeout.
            val waiting = pending
            pending = null
            endLive()
            waiting?.invoke(Bundle().apply { putString("state", "error"); putString("failure", LocalAsrFailure.CANCELLED.name) })
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    private fun endLive() {
        if (liveId == 0L) return
        liveId = 0L
        liveUpdate = null
        liveFailure = null
        mutex.unlock()
    }

    private fun Bundle.liveUpdate() = LiveUpdate(
        getStringArrayList(InferenceService.KEY_FROZEN).orEmpty(),
        getStringArrayList(InferenceService.KEY_LIVE).orEmpty(),
    )

    private fun Bundle.failure(): LocalAsrException {
        val failure = runCatching { LocalAsrFailure.valueOf(getString("failure")!!) }.getOrDefault(LocalAsrFailure.PROCESS_DIED)
        val detail = getString(InferenceService.KEY_DETAIL)
        Log.w(TAG, "Request failed: $failure${detail?.let { " ($it)" }.orEmpty()}")
        return LocalAsrException(failure, detail)
    }

    private suspend fun connect() {
        if (remote != null) return
        val ready = CompletableDeferred<Unit>()
        connecting = ready
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (binding !== this) return
                remote = Messenger(binder); ready.complete(Unit)
            }
            override fun onServiceDisconnected(name: ComponentName) { if (binding === this) died() }
            override fun onBindingDied(name: ComponentName) { if (binding === this) died() }
            override fun onNullBinding(name: ComponentName) { if (binding === this) died() }
        }
        binding = connection
        try {
            if (!context.bindService(Intent(context, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)) {
                throw LocalAsrException(LocalAsrFailure.PROCESS_DIED)
            }
            withTimeout(10_000) { ready.await() }
        } catch (error: Exception) { disconnect(kill = true); throw error
        } finally { if (connecting === ready) connecting = null }
    }

    private fun died() {
        val callback = pending
        val liveCallback = liveFailure.takeIf { callback == null }
        val detail = "inference process ended after ${elapsedMs()} ms"
        if (callback != null || liveCallback != null) Log.w(TAG, "Request lost: $detail")
        disconnect(kill = false)
        callback?.invoke(Bundle().apply {
            putString("state", "error"); putString("failure", LocalAsrFailure.PROCESS_DIED.name)
            putString(InferenceService.KEY_DETAIL, detail)
        })
        liveCallback?.invoke(LocalAsrException(LocalAsrFailure.PROCESS_DIED, detail))
    }

    private fun elapsedMs(): Long = SystemClock.elapsedRealtime() - requestStartedAt

    private fun disconnect(kill: Boolean) {
        nextRequest++
        val old = binding
        binding = null
        if (kill) {
            runCatching { remote?.send(Message.obtain(null, InferenceService.CANCEL)) }
            processId?.takeIf { it != Process.myPid() }?.let { Process.killProcess(it) }
        }
        remote = null; processId = null; pending = null
        endLive()
        connecting?.completeExceptionally(LocalAsrException(LocalAsrFailure.PROCESS_DIED))
        connecting = null
        if (old != null) runCatching { context.unbindService(old) }
        _state.value = RuntimeState.UNLOADED
    }

    /** Stops an active request before the caller removes files or changes versions. */
    suspend fun unload() = withContext(Dispatchers.Main.immediate) {
        val callback = pending
        disconnect(kill = true)
        callback?.invoke(Bundle().apply { putString("state", "error"); putString("failure", LocalAsrFailure.CANCELLED.name) })
    }

    fun trimMemory() { main.post { if (!mutex.isLocked) disconnect(kill = true) } }

    private companion object {
        const val TAG = "OwnkeyAsr"
        const val REQUEST_TIMEOUT_MS = 90_000L
        const val FINISH_TIMEOUT_MS = 30_000L
    }
}

/** One live dictation on an [InferenceConnection]. */
class LiveTranscription internal constructor(private val connection: InferenceConnection, private val id: Long) {
    /** The recording now holds [samples] samples. Any thread. */
    fun append(samples: Long) { connection.appendLive(id, samples) }

    /** Decodes what is still live once more and returns it as final. The recording must hold [samples] samples. */
    suspend fun finish(samples: Long): LiveUpdate = connection.finishLive(id, samples)

    /** Ends the session without a final decode. Any thread. */
    fun cancel() { connection.cancelLive(id) }
}
