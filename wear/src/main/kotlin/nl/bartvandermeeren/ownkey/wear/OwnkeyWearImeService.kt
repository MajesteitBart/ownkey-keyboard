package nl.bartvandermeeren.ownkey.wear

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OwnkeyWearImeService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client by lazy { VoxtralWearClient() }
    private val settingsStore by lazy { WearSettingsStore(this) }

    private var session: RecordingSession? = null
    private var isTranscribing = false

    private var statusView: TextView? = null
    private var micButton: ImageButton? = null
    private var submitButton: ImageButton? = null
    private var backspaceButton: ImageButton? = null

    private var backspaceHeld = false
    private var backspaceRepeatJob: Job? = null

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }

        val status = TextView(this).apply {
            text = "Enter your input"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(10))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val mic = createRoundActionButton(R.drawable.ic_mic_24, "Mic")
        mic.setOnClickListener { onMicButtonTapped() }

        val submit = createRoundActionButton(R.drawable.ic_send_24, "Send")
        submit.setOnClickListener { onSubmitButtonTapped() }

        val backspace = createRoundActionButton(R.drawable.ic_backspace_24, "Backspace")
        backspace.setOnTouchListener { _, event ->
            handleBackspaceTouch(event)
            true
        }

        row.addView(mic)
        row.addView(submit)
        row.addView(backspace)

        root.addView(status)
        root.addView(row)

        statusView = status
        micButton = mic
        submitButton = submit
        backspaceButton = backspace

        renderUi()
        return root
    }

    override fun onFinishInput() {
        super.onFinishInput()
        stopBackspaceRepeat()
        session?.let {
            runCatching { stopRecording(it) }
        }
        session = null
        isTranscribing = false
        renderUi()
    }

    private fun onMicButtonTapped() {
        if (isTranscribing) return

        if (session == null) {
            if (!hasMicPermission()) {
                setStatus("Geef microfoon-permissie in Ownkey Wear app")
                return
            }
            val apiKey = settingsStore.getApiKey().trim()
            if (apiKey.isBlank()) {
                setStatus("Geen API key. Gebruik Sync vanaf telefoon.")
                return
            }

            val started = startRecording(this)
            if (started == null) {
                setStatus("Opname starten mislukt")
                return
            }
            session = started
            setStatus("Luistert...")
            renderUi()
            return
        }

        val currentSession = session ?: return
        isTranscribing = true
        setStatus("Transcriberen...")
        renderUi()

        scope.launch {
            val recording = stopRecording(currentSession).getOrElse { error ->
                session = null
                isTranscribing = false
                setStatus(error.message ?: "Opname stoppen mislukt")
                renderUi()
                return@launch
            }
            session = null

            val result = withContext(Dispatchers.IO) {
                client.transcribe(
                    apiKey = settingsStore.getApiKey().trim(),
                    endpointUrl = settingsStore.getEndpointUrl().trim().ifBlank { VoxtralWearClient.DefaultEndpointUrl },
                    model = settingsStore.getModel().trim().ifBlank { VoxtralWearClient.DefaultModel },
                    languageHint = settingsStore.getLanguageHint().trim(),
                    recording = recording,
                )
            }

            result
                .onSuccess { transcript ->
                    val committed = currentInputConnection?.commitText(transcript, 1) == true
                    if (committed) {
                        setStatus("Ingevoegd")
                    } else {
                        setStatus("Kon tekst niet invoegen")
                    }
                }
                .onFailure { error ->
                    setStatus(error.message ?: "Transcriptie mislukt")
                }

            isTranscribing = false
            renderUi()
        }
    }

    private fun onSubmitButtonTapped() {
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            setStatus("Geen actief invoerveld")
            return
        }

        val actionSucceeded = inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND) ||
            inputConnection.performEditorAction(EditorInfo.IME_ACTION_DONE) ||
            inputConnection.performEditorAction(EditorInfo.IME_ACTION_GO) ||
            inputConnection.performEditorAction(EditorInfo.IME_ACTION_NEXT)

        if (actionSucceeded) {
            setStatus("Verstuurd")
            return
        }

        val keyEventSent = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)) &&
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))

        if (keyEventSent) {
            setStatus("Enter verstuurd")
            return
        }

        val newlineCommitted = inputConnection.commitText("\n", 1)
        if (newlineCommitted) {
            setStatus("Nieuwe regel toegevoegd")
        } else {
            setStatus("Kon niet verzenden")
        }
    }

    private fun handleBackspaceTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                backspaceHeld = true
                performBackspace()
                startBackspaceRepeat()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                backspaceHeld = false
                stopBackspaceRepeat()
            }
        }
    }

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        backspaceRepeatJob = scope.launch {
            delay(300)
            while (backspaceHeld && !isTranscribing && session == null) {
                performBackspace()
                delay(70)
            }
        }
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeatJob?.cancel()
        backspaceRepeatJob = null
    }

    private fun performBackspace() {
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            setStatus("Geen actief invoerveld")
            return
        }

        val keyEventSent = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)) &&
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))

        if (keyEventSent) {
            setStatus("Backspace")
            return
        }

        val deleted = inputConnection.deleteSurroundingText(1, 0)
        if (deleted) {
            setStatus("Backspace")
        } else {
            setStatus("Kon niet verwijderen")
        }
    }

    private fun createRoundActionButton(iconRes: Int, description: String): ImageButton {
        val size = dp(52)
        val button = ImageButton(this).apply {
            setImageResource(iconRes)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1C1D22"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#C9D6F4"))
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        val lp = LinearLayout.LayoutParams(size, size).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }
        button.layoutParams = lp
        return button
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun setStatus(text: String) {
        statusView?.text = text
    }

    private fun renderUi() {
        val canTap = !isTranscribing && session == null

        micButton?.apply {
            isEnabled = !isTranscribing
            alpha = if (isEnabled) 1f else 0.5f
            setImageResource(if (session != null) android.R.drawable.ic_media_pause else R.drawable.ic_mic_24)
        }

        submitButton?.apply {
            isEnabled = canTap
            alpha = if (isEnabled) 1f else 0.5f
        }

        backspaceButton?.apply {
            isEnabled = canTap
            alpha = if (isEnabled) 1f else 0.5f
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
