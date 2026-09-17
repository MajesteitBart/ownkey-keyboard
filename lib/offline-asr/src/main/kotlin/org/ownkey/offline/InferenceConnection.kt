package org.ownkey.offline

import android.content.*
import android.os.*
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
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        if (data.getLong("request") == nextRequest && binding != null) {
            processId = data.getInt("pid").takeIf { it > 0 }
            when (data.getString("state")) {
                "loading" -> _state.value = RuntimeState.LOADING
                "transcribing" -> _state.value = RuntimeState.TRANSCRIBING
                else -> pending?.invoke(data)
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
        try {
            withTimeout(90_000) {
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
                    _state.value = RuntimeState.FAILED
                    throw LocalAsrException(runCatching { LocalAsrFailure.valueOf(result.getString("failure")!!) }
                        .getOrDefault(LocalAsrFailure.PROCESS_DIED))
                }
                _state.value = RuntimeState.READY
                result.getString("text").orEmpty()
            }
        } catch (_: TimeoutCancellationException) {
            disconnect(kill = true)
            throw LocalAsrException(LocalAsrFailure.TIMEOUT)
        } catch (error: CancellationException) {
            disconnect(kill = true)
            throw error
        } finally { mutex.unlock() }
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
        disconnect(kill = false)
        callback?.invoke(Bundle().apply { putString("state", "error"); putString("failure", LocalAsrFailure.PROCESS_DIED.name) })
    }

    private fun disconnect(kill: Boolean) {
        nextRequest++
        val old = binding
        binding = null
        if (kill) {
            runCatching { remote?.send(Message.obtain(null, InferenceService.CANCEL)) }
            processId?.takeIf { it != Process.myPid() }?.let { Process.killProcess(it) }
        }
        remote = null; processId = null; pending = null
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
}
