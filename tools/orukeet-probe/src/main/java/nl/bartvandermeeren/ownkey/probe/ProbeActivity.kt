package nl.bartvandermeeren.ownkey.probe

import android.app.Activity
import android.content.*
import android.os.*
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Test controls and aggregate evidence only; does not collect microphone or editor content. */
class ProbeActivity : Activity() {
    private lateinit var output: TextView
    private val main = Handler(Looper.getMainLooper())
    private val preparation = Executors.newSingleThreadExecutor()
    private var connection: ServiceConnection? = null
    private var remote: Messenger? = null
    private var busy = false
    private var generation = 0
    private var runId = ""
    private var mode = ""
    private var failure = ""
    private var cancelAt = 0L
    private var starting = 0L
    private var completed = false
    private var evidence = JSONObject()
    private val events = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 48, 28, 24) }
        output = TextView(this).apply { textSize = 16f; text = "Ownkey ASR probe\nPrepare verified model files and the public sample first." }
        column.addView(output)
        listOf(
            "Run normal WAV" to { start("normal", "wav", "") },
            "Run isolated WAV" to { start("isolated", "wav", "") },
            "Run normal M4A" to { start("normal", "aac", "") },
            "Run isolated M4A" to { start("isolated", "aac", "") },
            "Kill normal during load" to { start("normal", "wav", "load") },
            "Kill normal during inference" to { start("normal", "wav", "inference") },
            "Cancel normal inference" to { start("normal", "wav", "cancel") },
            "Inject allocation failure" to { start("isolated", "wav", "oom") },
        ).forEach { (label, action) ->
            column.addView(Button(this).apply { text = label; setOnClickListener { if (!busy) action() } })
        }
        setContentView(ScrollView(this).apply { addView(column, ViewGroup.LayoutParams(-1, -2)) })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "ownkey-probe" && uri.host == "run" && !busy) {
            start(uri.getQueryParameter("mode") ?: "isolated",
                uri.getQueryParameter("format") ?: "wav", uri.getQueryParameter("failure") ?: "")
        }
    }

    private fun start(selectedMode: String, format: String, fault: String) {
        require(selectedMode in setOf("normal", "isolated") && format in setOf("wav", "aac"))
        mode = selectedMode; failure = fault
        runId = "$mode-$format-${fault.ifEmpty { "success" }}-${System.currentTimeMillis()}"
        busy = true; completed = false; generation++; events.clear(); cancelAt = 0
        evidence = JSONObject()
        val currentGeneration = generation
        starting = SystemClock.elapsedRealtime()
        output.text = "RUNNING $runId\nPreparing fixture"
        preparation.execute {
            try {
                val root = File(filesDir, "probe-data")
                val wave = File(root, "sample.wav")
                require(wave.isFile)
                val audio = if (format == "aac") File(root, "sample.m4a").also { ProbeAudio.encodeAac(wave, it) } else wave
                main.post { if (generation == currentGeneration) bind(currentGeneration, root, audio, format) }
            } catch (error: Throwable) {
                main.post { finish(JSONObject().put("status", "error").put("error_type", error.javaClass.simpleName).put("phase", "prepare")) }
            }
        }
    }

    private fun bind(currentGeneration: Int, root: File, audio: File, format: String) {
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (currentGeneration != generation || completed) return@Handler true
            val event = JSONObject(message.data.getString("json") ?: "{}")
            when (event.optString("event")) {
                "evidence" -> event.keys().forEach { key -> evidence.put(key, event.get(key)) }
                "phase" -> {
                    evidence.put("service_pid", event.getInt("service_pid"))
                    val phase = event.getString("phase")
                    events.add(phase)
                    output.text = "RUNNING $runId\n$phase\nMain process responsive"
                    if (phase == "inference" && failure == "cancel") {
                        main.postDelayed({
                            if (!completed && currentGeneration == generation) {
                                cancelAt = SystemClock.elapsedRealtime()
                                runCatching { remote?.send(Message.obtain(null, ProbeService.CANCEL)) }
                            }
                        }, 50)
                    }
                }
                "cancel_ack" -> {
                    evidence.put("cancel_ack_ms", SystemClock.elapsedRealtime() - cancelAt)
                }
                "result" -> finish(event)
            }
            true
        })
        val serviceClass = if (mode == "isolated") IsolatedAsrService::class.java else RegularAsrService::class.java
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (completed || currentGeneration != generation) return
                remote = Messenger(binder)
                val paths = arrayListOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")
                    .mapTo(arrayListOf()) { File(root, "model/$it").path }
                val fds = arrayListOf<ParcelFileDescriptor>()
                try {
                    (paths.map(::File) + audio).forEach { fds.add(ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)) }
                    remote!!.send(Message.obtain(null, ProbeService.RUN).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putParcelableArrayList("files", fds)
                            putStringArrayList("paths", paths)
                            putString("loader", if (mode == "isolated") "fd" else "path")
                            putString("format", format)
                            putString("kill_phase", failure.takeIf { it in setOf("load", "inference") })
                            putBoolean("inject_oom", failure == "oom")
                            putBoolean("long_audio", failure in setOf("cancel", "inference"))
                            putInt("main_uid", Process.myUid())
                        }
                    })
                } catch (error: Throwable) {
                    finish(JSONObject().put("status", "error").put("error_type", error.javaClass.simpleName).put("phase", "handoff"))
                } finally { fds.forEach { runCatching { it.close() } } }
            }
            override fun onServiceDisconnected(name: ComponentName) = died()
            override fun onBindingDied(name: ComponentName) = died()
            override fun onNullBinding(name: ComponentName) = died()
            private fun died() {
                if (!completed && currentGeneration == generation) {
                    val result = JSONObject().put("status", if (cancelAt > 0) "cancelled" else "service_died")
                        .put("last_phase", events.lastOrNull()).put("service_death_observed", true)
                    if (cancelAt > 0) result.put("cancel_to_death_ms", SystemClock.elapsedRealtime() - cancelAt)
                    finish(result)
                }
            }
        }
        connection = conn
        if (!bindService(Intent(this, serviceClass), conn, Context.BIND_AUTO_CREATE)) {
            finish(JSONObject().put("status", "bind_failed"))
        }
        main.postDelayed({
            if (!completed && currentGeneration == generation) {
                runCatching { remote?.send(Message.obtain(null, ProbeService.CANCEL)) }
                finish(JSONObject().put("status", "timeout"))
            }
        }, 120_000)
    }

    private fun finish(result: JSONObject) {
        if (completed) return
        evidence.keys().forEach { key -> if (!result.has(key)) result.put(key, evidence.get(key)) }
        completed = true; busy = false
        result.put("run_id", runId).put("mode", mode).put("requested_failure", failure)
            .put("main_pid", Process.myPid()).put("main_alive", true)
            .put("elapsed_ms", SystemClock.elapsedRealtime() - starting)
            .put("phases", events.joinToString(","))
        File(filesDir, "results.jsonl").appendText(result.toString() + "\n")
        output.text = "COMPLETE $runId\nStatus: ${result.optString("status")}\n" +
            "Load: ${result.optLong("load_ms", -1)} ms; inference: ${result.optLong("inference_ms", -1)} ms\n" +
            "Transcript chars: ${result.optInt("transcript_chars", 0)}\nMain process responsive"
        connection?.let { runCatching { unbindService(it) } }
        connection = null; remote = null
    }

    override fun onDestroy() {
        if (busy) runCatching { remote?.send(Message.obtain(null, ProbeService.CANCEL)) }
        connection?.let { runCatching { unbindService(it) } }
        generation++
        preparation.shutdown()
        super.onDestroy()
    }
}
