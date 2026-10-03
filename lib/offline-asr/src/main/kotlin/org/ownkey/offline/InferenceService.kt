package org.ownkey.offline

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/** Crash boundary, not a network permission sandbox. The host application must skip normal startup. */
class InferenceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() }, "ownkey-asr")
    }
    private var active = false
    private var engine: OrukeetEngine? = null
    private var version: String? = null
    private var live: LiveRun? = null
    private val idle = Runnable { terminate() }
    private val endpoint = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        val id = data.getLong("request")
        when (message.what) {
            COMMAND -> {
                val reply = message.replyTo
                @Suppress("DEPRECATION")
                val audio = data.getParcelable<ParcelFileDescriptor>("audio")
                if (active) {
                    audio?.close()
                    respond(reply, id, "error", failure = LocalAsrFailure.BUSY)
                } else {
                    active = true
                    main.removeCallbacks(idle)
                    respond(reply, id, "loading")
                    worker.execute { execute(data, audio, reply, id) }
                }
            }
            LIVE_START -> startLive(message.replyTo, id, data)
            LIVE_APPEND -> live?.takeIf { it.id == id }?.let { run ->
                run.available = maxOf(run.available, data.getLong(KEY_SAMPLES))
                schedule(run)
            }
            LIVE_FINISH -> live?.takeIf { it.id == id }?.let { run ->
                run.available = maxOf(run.available, data.getLong(KEY_SAMPLES))
                run.finishing = true
                schedule(run)
            }
            LIVE_CANCEL -> live?.takeIf { it.id == id }?.let(::closeLive)
            CANCEL, UNLOAD -> terminate()
        }
        true
    })

    override fun onBind(intent: Intent) = endpoint.binder
    override fun onUnbind(intent: Intent): Boolean { terminate(); return false }

    private fun execute(data: Bundle, audio: ParcelFileDescriptor?, reply: Messenger, id: Long) {
        try {
            val hotwords = data.getString(KEY_HOTWORDS).orEmpty()
            val engine = loadEngine(data.getString("model"), hotwords)
            if (audio == null) {
                respond(reply, id, "ok")
            } else {
                respond(reply, id, "transcribing")
                val samples = AudioDecoder.decode(audio.fileDescriptor)
                val transcript = engine.transcribe(samples, hotwords)
                if (transcript.length > 16_000) throw LocalAsrException(LocalAsrFailure.RUNTIME)
                respond(reply, id, "ok", text = transcript)
            }
        } catch (error: Throwable) {
            fail(reply, id, error)
        } finally {
            runCatching { audio?.close() }
        }
    }

    /** Worker thread only. One resident engine, keyed by model and decoder profile; a profile change recreates it. */
    private fun loadEngine(modelId: String?, hotwords: String): OrukeetEngine {
        if (modelId == null) throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
        val release = ModelCatalog.trusted.firstOrNull { it.id == modelId }
            ?: throw LocalAsrException(LocalAsrFailure.MODEL_DAMAGED)
        val directory = File(noBackupFilesDir, "ai-models/models/${release.id}")
        if (release.files.any { File(directory, it.name).length() != it.bytes }) {
            throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
        }
        if (hotwords.toByteArray(Charsets.UTF_8).size > HotwordTransport.MAX_BYTES) {
            throw LocalAsrException(LocalAsrFailure.RUNTIME)
        }
        val profile = if (hotwords.isEmpty()) DecoderProfile.GREEDY else DecoderProfile.BEAM_HOTWORDS
        engine?.takeIf { version == modelId && it.profile == profile }?.let { return it }
        engine?.close(); engine = null; version = null
        val started = SystemClock.elapsedRealtime()
        Log.i(TAG, "Loading $modelId with the $profile decoder")
        return OrukeetEngine(directory, profile).also {
            engine = it
            version = modelId
            Log.i(TAG, "Loaded $modelId in ${SystemClock.elapsedRealtime() - started} ms")
        }
    }

    /**
     * Live dictation. The main process records raw 16 kHz mono PCM into a file and reports how many
     * samples it has written; this side decodes the live part of the transcript whenever the decoder is
     * free and enough new audio has arrived, and sends back what froze and what is still live.
     */
    private class LiveRun(val id: Long, val reply: Messenger, val audio: ParcelFileDescriptor, val hotwords: String) {
        val window = LiveWindow()

        /** Worker thread only. */
        val gate = QuietGate()
        var engine: OrukeetEngine? = null
        var available = 0L
        var decodedTo = 0L
        var decoding = false
        var finishing = false
        var closed = false
    }

    private fun startLive(reply: Messenger, id: Long, data: Bundle) {
        @Suppress("DEPRECATION")
        val audio = data.getParcelable<ParcelFileDescriptor>("audio")
        if (active || audio == null) {
            audio?.close()
            respond(reply, id, "error", failure = if (audio == null) LocalAsrFailure.AUDIO else LocalAsrFailure.BUSY)
            return
        }
        active = true
        main.removeCallbacks(idle)
        val run = LiveRun(id, reply, audio, data.getString(KEY_HOTWORDS).orEmpty())
        live = run
        respond(reply, id, "loading")
        val modelId = data.getString("model")
        // Load while the user starts talking, so the first preview doesn't wait for the model.
        worker.execute {
            val loaded = runCatching { loadEngine(modelId, run.hotwords) }
            main.post {
                loaded.fold(
                    onSuccess = { run.engine = it; schedule(run) },
                    // A run cancelled while its model loaded is already closed; whatever runs now isn't its business.
                    onFailure = { if (!run.closed) failLive(run, it) },
                )
            }
        }
    }

    /** Main thread. Starts the next decode when the decoder is free and there is something to decode. */
    private fun schedule(run: LiveRun) {
        val engine = run.engine
        if (run.closed || run.decoding || engine == null || live !== run) return
        if (!run.finishing && run.available - run.decodedTo < PREVIEW_SAMPLES) return
        val start = (run.window.decodeStart() * SAMPLE_RATE).toLong().coerceIn(0L, run.available)
        // A decode covers at most 30 s. When decoding falls further behind, the backlog is worked through
        // in bounded ranges as previews, and the final decode waits until the rest fits.
        val end = minOf(run.available, start + MAX_DECODE_SAMPLES)
        val final = run.finishing && end == run.available
        val fresh = run.decodedTo
        val settled = run.window.isSettled
        run.decoding = true
        worker.execute {
            // Nobody types during dictation, so live decodes needn't yield to the keyboard.
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
            val result = try {
                // Nothing but room noise for a while: nothing new was said, so don't decode it. Stop still
                // decodes unless everything is already final.
                if (run.gate.isQuiet(AudioDecoder.readPcm16(run.audio.fileDescriptor, fresh, end)) && (!final || settled)) {
                    main.post { onQuiet(run, end, final) }
                    return@execute
                }
                val samples = AudioDecoder.readPcm16(run.audio.fileDescriptor, start, end)
                val started = SystemClock.elapsedRealtime()
                val words = engine.transcribeWords(samples, run.hotwords)
                // Timing only, never text: how long each live decode takes on this phone.
                Log.d(TAG, "Live ${if (final) "final" else "preview"}: ${samples.size * 1000L / SAMPLE_RATE} ms of audio in ${SystemClock.elapsedRealtime() - started} ms")
                Result.success(words)
            } catch (error: Throwable) {
                Result.failure(error)
            } finally {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            }
            main.post { onDecoded(run, result, start, end, final) }
        }
    }

    private fun onDecoded(run: LiveRun, result: Result<List<TimedWord>>, start: Long, end: Long, final: Boolean) {
        run.decoding = false
        run.decodedTo = end
        if (run.closed) {
            runCatching { run.audio.close() }
            return
        }
        val words = result.getOrElse { return failLive(run, it) }
            .map { TimedWord(it.text, it.start + start.toDouble() / SAMPLE_RATE) }
        if (final) {
            val update = run.window.finish(words)
            closeLive(run)
            respond(run.reply, run.id, "ok", frozen = update.frozen, liveWords = update.live)
            return
        }
        run.window.accept(words, end.toDouble() / SAMPLE_RATE)?.let { update ->
            respond(run.reply, run.id, "live_update", frozen = update.frozen, liveWords = update.live)
        }
        schedule(run)
    }

    private fun onQuiet(run: LiveRun, end: Long, final: Boolean) {
        run.decoding = false
        run.decodedTo = end
        if (run.closed) {
            runCatching { run.audio.close() }
            return
        }
        if (final) {
            val update = run.window.finish(emptyList())
            closeLive(run)
            respond(run.reply, run.id, "ok", frozen = update.frozen, liveWords = update.live)
            return
        }
        run.window.quiet(end.toDouble() / SAMPLE_RATE)?.let { update ->
            respond(run.reply, run.id, "live_update", frozen = update.frozen, liveWords = update.live)
        }
        schedule(run)
    }

    private fun closeLive(run: LiveRun) {
        if (run.closed) return
        run.closed = true
        if (live === run) live = null
        if (run.decoding) {
            watchCancelledDecode(run)
        } else {
            runCatching { run.audio.close() }
        }
        active = false
        main.removeCallbacks(idle)
        main.postDelayed(idle, ModelCatalog.IDLE_RETENTION_MS)
    }

    /**
     * Native decoding can't be interrupted. A short preview may finish, but a cancelled decode that runs past
     * the deadline ends the process. A request that came in meanwhile waits for it on the same worker thread
     * and would end with the process, so the decode gets until [STALE_DECODE_LIMIT_MS] then; a decode that
     * takes longer is stuck, and the new request's client sees the process end and reports it.
     */
    private fun watchCancelledDecode(run: LiveRun, since: Long = SystemClock.elapsedRealtime()) {
        main.postDelayed({
            if (!run.decoding) return@postDelayed
            if (active && SystemClock.elapsedRealtime() - since < STALE_DECODE_LIMIT_MS) watchCancelledDecode(run, since) else terminate()
        }, CANCEL_DEADLINE_MS)
    }

    private fun failLive(run: LiveRun, error: Throwable) {
        closeLive(run)
        fail(run.reply, run.id, error)
    }

    private fun fail(reply: Messenger, id: Long, error: Throwable) {
        if (error is LocalAsrException) {
            Log.w(TAG, "Request $id failed: ${error.reason}")
            respond(reply, id, "error", failure = error.reason)
        } else {
            val code = InferenceDiagnostics.failureCode(error)
            Log.e(TAG, "Request $id failed: $code")
            respond(reply, id, "error", failure = LocalAsrFailure.RUNTIME, detail = code)
            main.post { terminate() }
        }
    }

    private fun respond(
        reply: Messenger, id: Long, state: String, text: String? = null,
        failure: LocalAsrFailure? = null, detail: String? = null,
        frozen: List<String>? = null, liveWords: List<String>? = null,
    ) {
        main.post {
        if (state == "ok" || (state == "error" && failure != LocalAsrFailure.BUSY)) {
            active = live != null
            main.removeCallbacks(idle)
            if (!active) main.postDelayed(idle, ModelCatalog.IDLE_RETENTION_MS)
        }
        runCatching {
            reply.send(Message.obtain(null, EVENT).apply {
                data = Bundle().apply {
                    putLong("request", id); putString("state", state); putInt("pid", Process.myPid())
                    if (text != null) putString("text", text)
                    if (failure != null) putString("failure", failure.name)
                    if (detail != null) putString(KEY_DETAIL, detail)
                    if (frozen != null) putStringArrayList(KEY_FROZEN, ArrayList(frozen))
                    if (liveWords != null) putStringArrayList(KEY_LIVE, ArrayList(liveWords))
                }
            })
        }.onFailure { terminate() }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // UI_HIDDEN starts the explicit deadline; memory pressure can evict model state immediately.
        if (level != TRIM_MEMORY_UI_HIDDEN && level >= TRIM_MEMORY_RUNNING_LOW) terminate()
    }
    override fun onLowMemory() { super.onLowMemory(); terminate() }
    private fun terminate() {
        main.removeCallbacks(idle)
        // Process termination is the bounded interruption path for synchronous native code.
        Process.killProcess(Process.myPid())
    }
    companion object {
        const val PROCESS_SUFFIX = ":offline_asr"
        const val COMMAND = 1
        const val CANCEL = 2
        const val UNLOAD = 3
        const val EVENT = 4
        const val LIVE_START = 5
        const val LIVE_APPEND = 6
        const val LIVE_FINISH = 7
        const val LIVE_CANCEL = 8
        const val KEY_HOTWORDS = "hotwords"
        const val KEY_DETAIL = "detail"
        const val KEY_SAMPLES = "samples"
        const val KEY_FROZEN = "frozen"
        const val KEY_LIVE = "live"
        const val SAMPLE_RATE = 16_000
        /** A preview runs once at least this much new audio has arrived and the decoder is free. */
        private const val PREVIEW_SAMPLES = SAMPLE_RATE * 8 / 10
        /** The engine accepts at most 31 s per decode. */
        private const val MAX_DECODE_SAMPLES = SAMPLE_RATE * 30L
        private const val CANCEL_DEADLINE_MS = 2_000L
        /** A 30 s decode takes a few seconds; one still running after this is stuck. */
        private const val STALE_DECODE_LIMIT_MS = 10_000L
        private const val TAG = "OwnkeyAsr"
    }
}
