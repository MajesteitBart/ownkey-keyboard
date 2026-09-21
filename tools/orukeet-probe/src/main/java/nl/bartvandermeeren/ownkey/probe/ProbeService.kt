package nl.bartvandermeeren.ownkey.probe

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import com.k2fsa.sherpa.onnx.*
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RegularAsrService : ProbeService()
class IsolatedAsrService : ProbeService()

/** Disposable experiment process. It terminates after each run, including cancellation. */
open class ProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var reply: Messenger? = null
    private var started = false
    private val cancelled = AtomicBoolean(false)
    @Volatile private var phase = "bind"
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            RUN -> if (!started) {
                started = true
                reply = message.replyTo
                val data = Bundle(message.data)
                worker.execute { runProbe(data) }
            }
            CANCEL -> {
                cancelled.set(true)
                emit(JSONObject().put("event", "cancel_ack").put("service_pid", Process.myPid()))
                // Native decode is synchronous; never release its recognizer from this thread.
                main.postDelayed({ Process.killProcess(Process.myPid()) }, 100)
            }
        }
        true
    })

    override fun onBind(intent: Intent) = messenger.binder

    private fun emit(data: JSONObject) {
        runCatching {
            reply?.send(Message.obtain(null, EVENT).apply {
                this.data = Bundle().apply { putString("json", data.toString()) }
            })
        }
    }

    private fun enter(name: String, data: Bundle) {
        phase = name
        emit(JSONObject().put("event", "phase").put("phase", name).put("service_pid", Process.myPid()))
        if (data.getString("kill_phase") == name) {
            main.postDelayed({ Process.killProcess(Process.myPid()) }, 50)
        }
    }

    @Suppress("DEPRECATION")
    private fun runProbe(data: Bundle) {
        val descriptors = data.getParcelableArrayList<ParcelFileDescriptor>("files") ?: arrayListOf()
        val measure = AtomicBoolean(true)
        val peaks = ConcurrentHashMap<String, Long>()
        val sampler = Thread {
            while (measure.get()) {
                val rss = statusKb("VmRSS")
                peaks.merge(phase, rss, ::maxOf)
                Thread.sleep(20)
            }
        }.apply { isDaemon = true; start() }
        val result = JSONObject().put("event", "result")
            .put("service_pid", Process.myPid()).put("service_uid", Process.myUid())
            .put("main_uid", data.getInt("main_uid"))
            .put("page_bytes", android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE))
            .put("internet_permission", checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED)
            .put("loader", data.getString("loader"))
            .put("sample_format", data.getString("format"))
            .put("start_rss_kb", statusKb("VmRSS"))
        var recognizer: OfflineRecognizer? = null
        try {
            require(descriptors.size == 5)
            val modelPaths = if (data.getString("loader") == "fd") {
                descriptors.take(4).map { "/proc/self/fd/${it.fd}" }
            } else {
                data.getStringArrayList("paths") ?: error("Missing paths")
            }
            // Only a loopback permission check. Audio and model data are never sent to a socket.
            val networkResult = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", 9), 300) }
                "connected"
            }.exceptionOrNull()?.let {
                if (it.message.orEmpty().contains(Regex("EPERM|EACCES|Permission denied|Operation not permitted", RegexOption.IGNORE_CASE)))
                    "permission_denied" else "connection_failed"
            } ?: "connected"
            result.put("loopback_socket", networkResult)
            result.put("original_paths_readable", data.getStringArrayList("paths")?.all { File(it).canRead() } == true)
            result.put("model_descriptors_readable", descriptors.take(4).all {
                runCatching { android.system.Os.pread(it.fileDescriptor, ByteArray(1), 0, 1, 0) == 1 }.getOrDefault(false)
            })
            result.put("model_fd_paths_reopenable", descriptors.take(4).all {
                runCatching { File("/proc/self/fd/${it.fd}").inputStream().use { stream -> stream.read() >= 0 } }.getOrDefault(false)
            })
            emit(JSONObject(result.toString()).put("event", "evidence"))
            if (data.getBoolean("inject_oom")) throw OutOfMemoryError("Injected allocation failure")

            enter("audio", data)
            val audioStart = SystemClock.elapsedRealtime()
            val samples = if (data.getString("format") == "aac") {
                ProbeAudio.decodeAac(descriptors[4].fileDescriptor)
            } else {
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptors[4].fileDescriptor)).use {
                    ProbeAudio.readWav(it.readBytes())
                }
            }
            result.put("audio_decode_ms", SystemClock.elapsedRealtime() - audioStart)
                .put("audio_samples", samples.size)
            emit(JSONObject(result.toString()).put("event", "evidence"))

            enter("load", data)
            val loadStart = SystemClock.elapsedRealtime()
            recognizer = OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 128),
                    modelConfig = OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = modelPaths[0], decoder = modelPaths[1], joiner = modelPaths[2],
                        ),
                        tokens = modelPaths[3], modelType = "nemo_transducer",
                        numThreads = 2, provider = "cpu", debug = false,
                    ),
                ),
            )
            result.put("load_ms", SystemClock.elapsedRealtime() - loadStart)
                .put("after_load_rss_kb", statusKb("VmRSS"))
                .put("activation_hwm_kb", statusKb("VmHWM"))
                .put("after_load_pss_kb", Debug.getPss())
            emit(JSONObject(result.toString()).put("event", "evidence"))
            val boundedSamples = if (data.getBoolean("long_audio")) {
                FloatArray(16000 * 30) { samples[it % samples.size] }
            } else samples
            val inferStart = SystemClock.elapsedRealtime()
            val stream = recognizer.createStream()
            val transcript = try {
                stream.acceptWaveform(boundedSamples, 16000)
                enter("inference", data)
                recognizer.decode(stream)
                recognizer.getResult(stream).text
            } finally { stream.release() }
            val normalized = transcript.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
                .trim().replace(Regex("\\s+"), " ")
            result.put("inference_ms", SystemClock.elapsedRealtime() - inferStart)
                .put("transcript_chars", transcript.length)
                .put("normalized_sha256", MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) })
                .put("nonempty", normalized.isNotEmpty())
                .put("after_inference_rss_kb", statusKb("VmRSS"))
                .put("inference_hwm_kb", statusKb("VmHWM"))
                .put("status", "ok")
        } catch (error: Throwable) {
            result.put("status", "error").put("error_type", error.javaClass.simpleName).put("error_phase", phase)
        } finally {
            phase = "unload"
            runCatching { recognizer?.release() }
            descriptors.forEach { runCatching { it.close() } }
            result.put("after_unload_rss_kb", statusKb("VmRSS"))
            measure.set(false)
            sampler.join(200)
            result.put("sampled_peak_rss_kb", JSONObject(peaks.toMap()))
            // Serialize result delivery with the main-thread cancellation acknowledgment.
            main.post { if (!cancelled.get()) emit(result) }
            // Guarantee a fresh process for the next activation measurement.
            main.postDelayed({ Process.killProcess(Process.myPid()) }, 150)
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Process.killProcess(Process.myPid())
        return false
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val RUN = 1
        const val CANCEL = 2
        const val EVENT = 3
        fun statusKb(key: String): Long = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("$key:") }?.split(Regex("\\s+"))?.get(1)?.toLong() ?: 0L
            }
        }.getOrDefault(0)
    }
}
