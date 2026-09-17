package org.ownkey.offline

import android.app.Service
import android.content.Intent
import android.os.*
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
    private val idle = Runnable { terminate() }
    private val endpoint = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            COMMAND -> {
                val reply = message.replyTo
                val data = message.data
                val id = data.getLong("request")
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
            CANCEL, UNLOAD -> terminate()
        }
        true
    })

    override fun onBind(intent: Intent) = endpoint.binder
    override fun onUnbind(intent: Intent): Boolean { terminate(); return false }

    private fun execute(data: Bundle, audio: ParcelFileDescriptor?, reply: Messenger, id: Long) {
        try {
            val modelId = data.getString("model") ?: throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
            val release = ModelCatalog.trusted.firstOrNull { it.id == modelId }
                ?: throw LocalAsrException(LocalAsrFailure.MODEL_DAMAGED)
            val directory = File(noBackupFilesDir, "ai-models/models/${release.id}")
            if (release.files.any { File(directory, it.name).length() != it.bytes }) {
                throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
            }
            if (engine == null || version != modelId) {
                engine?.close(); engine = null; version = null
                engine = OrukeetEngine(directory)
                version = modelId
            }
            if (audio == null) {
                respond(reply, id, "ok")
            } else {
                respond(reply, id, "transcribing")
                val samples = AudioDecoder.decode(audio.fileDescriptor)
                val transcript = engine!!.transcribe(samples)
                if (transcript.length > 16_000) throw LocalAsrException(LocalAsrFailure.RUNTIME)
                respond(reply, id, "ok", text = transcript)
            }
        } catch (error: LocalAsrException) {
            respond(reply, id, "error", failure = error.reason)
        } catch (_: Throwable) {
            respond(reply, id, "error", failure = LocalAsrFailure.RUNTIME)
            main.post { terminate() }
        } finally {
            runCatching { audio?.close() }
        }
    }

    private fun respond(reply: Messenger, id: Long, state: String, text: String? = null, failure: LocalAsrFailure? = null) {
        main.post {
        if (state == "ok" || (state == "error" && failure != LocalAsrFailure.BUSY)) {
            active = false
            main.removeCallbacks(idle)
            main.postDelayed(idle, ModelCatalog.IDLE_RETENTION_MS)
        }
        runCatching {
            reply.send(Message.obtain(null, EVENT).apply {
                data = Bundle().apply {
                    putLong("request", id); putString("state", state); putInt("pid", Process.myPid())
                    if (text != null) putString("text", text)
                    if (failure != null) putString("failure", failure.name)
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
    }
}
