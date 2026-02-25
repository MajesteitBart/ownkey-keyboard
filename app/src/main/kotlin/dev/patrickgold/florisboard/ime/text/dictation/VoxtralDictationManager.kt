/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToastSync

/**
 * Dictation entrypoint for the keyboard voice key.
 *
 * Debug behavior:
 * - API key empty: internal mock dictation flow.
 * - API key set: internal Voxtral API flow.
 *
 * Non-debug behavior remains legacy fallback to external voice IME.
 */
class VoxtralDictationManager(
    context: Context,
) {
    enum class DictationState {
        IDLE,
        LISTENING,
        TRANSCRIBING,
        ERROR,
    }

    private enum class RoutingMode {
        MOCK_INTERNAL,
        INTERNAL_VOXTRAL,
        EXTERNAL_IME_FALLBACK,
    }

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voxtralSecretsStore = VoxtralSecretsStore(appContext)

    private val mockAudioRecorder: AudioRecorder = NoOpAudioRecorder()
    private val mediaAudioRecorder: AudioRecorder = MediaRecorderAudioRecorder(context = appContext)
    private val mockTranscriptionClient: TranscriptionClient = MockTranscriptionClient()
    private val voxtralTranscriptionClient: TranscriptionClient = VoxtralRelayTranscriptionClient(
        apiKeyProvider = { apiKey() },
        endpointUrlProvider = { prefs.voxtral.endpointUrl.get() },
        modelProvider = { prefs.voxtral.model.get() },
        languageHintProvider = { prefs.voxtral.languageHint.get() },
    )

    private var activeSessionMode: RoutingMode? = null

    init {
        migrateLegacyApiKeyIfNeeded()
    }

    private val _stateFlow = MutableStateFlow(DictationState.IDLE)
    val stateFlow: StateFlow<DictationState> = _stateFlow

    fun onVoiceInputKeyPressed() {
        val currentMode = activeSessionMode ?: resolveRoutingMode()

        when (currentMode) {
            RoutingMode.EXTERNAL_IME_FALLBACK -> {
                FlorisImeService.switchToVoiceInputMethod()
                return
            }

            RoutingMode.MOCK_INTERNAL,
            RoutingMode.INTERNAL_VOXTRAL -> {
                when (_stateFlow.value) {
                    DictationState.IDLE,
                    DictationState.ERROR -> startListening(currentMode)

                    DictationState.LISTENING -> stopAndInsertTranscript()
                    DictationState.TRANSCRIBING -> {
                        appContext.showShortToastSync("Dictation is already transcribing…")
                    }
                }
            }
        }
    }

    private fun startListening(mode: RoutingMode) {
        if (mode == RoutingMode.INTERNAL_VOXTRAL && !hasRecordAudioPermission()) {
            _stateFlow.value = DictationState.ERROR
            appContext.showShortToastSync("Microphone permission missing. Grant it in Settings → Voxtral.")
            return
        }

        val hasStarted = recorderFor(mode).start()
        if (!hasStarted) {
            _stateFlow.value = DictationState.ERROR
            if (mode == RoutingMode.INTERNAL_VOXTRAL) {
                appContext.showShortToastSync("Unable to start microphone recording")
            } else {
                appContext.showShortToastSync("Unable to start dictation")
            }
            return
        }

        activeSessionMode = mode
        _stateFlow.value = DictationState.LISTENING

        if (mode == RoutingMode.MOCK_INTERNAL) {
            appContext.showShortToastSync("Dictation started (mock mode). Tap mic again to insert text.")
        } else {
            appContext.showShortToastSync("Dictation started. Tap mic again to transcribe.")
        }
    }

    private fun stopAndInsertTranscript() {
        scope.launch {
            _stateFlow.value = DictationState.TRANSCRIBING
            val sessionMode = activeSessionMode ?: resolveRoutingMode()

            val recorder = recorderFor(sessionMode)
            val transcriptionClient = transcriptionClientFor(sessionMode)

            val recording = recorder.stopAndRead().getOrElse { error ->
                activeSessionMode = null
                setError(error.message ?: "Failed to stop dictation", error)
                return@launch
            }

            val transcript = withContext(Dispatchers.IO) {
                transcriptionClient.transcribe(recording)
            }.getOrElse { error ->
                activeSessionMode = null
                setError(error.message ?: "Failed to transcribe dictation", error)
                return@launch
            }.trim()

            if (transcript.isBlank()) {
                activeSessionMode = null
                _stateFlow.value = DictationState.ERROR
                appContext.showShortToastSync("Dictation returned empty text")
                return@launch
            }

            val wasCommitted = editorInstance.commitText(transcript)
            if (!wasCommitted) {
                activeSessionMode = null
                _stateFlow.value = DictationState.ERROR
                appContext.showShortToastSync("Could not insert dictated text")
                return@launch
            }

            if (sessionMode == RoutingMode.MOCK_INTERNAL) {
                flogInfo { "Inserted mock dictation transcript (${transcript.length} chars)" }
            } else {
                flogInfo { "Inserted Voxtral dictation transcript (${transcript.length} chars)" }
            }
            activeSessionMode = null
            _stateFlow.value = DictationState.IDLE
        }
    }

    private fun setError(message: String, error: Throwable) {
        _stateFlow.value = DictationState.ERROR
        flogError { "$message: ${error.message}" }
        appContext.showShortToastSync(message)
    }

    private fun resolveRoutingMode(): RoutingMode {
        val apiKey = apiKey().trim()
        return when {
            apiKey.isNotEmpty() -> RoutingMode.INTERNAL_VOXTRAL
            BuildConfig.DEBUG -> RoutingMode.MOCK_INTERNAL
            else -> RoutingMode.EXTERNAL_IME_FALLBACK
        }
    }

    private fun apiKey(): String {
        val secureApiKey = voxtralSecretsStore.getApiKey().trim()
        if (secureApiKey.isNotEmpty()) {
            return secureApiKey
        }

        val legacyApiKey = prefs.voxtral.apiKey.get().trim()
        if (legacyApiKey.isNotEmpty()) {
            voxtralSecretsStore.setApiKey(legacyApiKey)
            scope.launch {
                prefs.voxtral.apiKey.set("")
            }
            return legacyApiKey
        }

        return ""
    }

    private fun migrateLegacyApiKeyIfNeeded() {
        apiKey()
    }

    private fun recorderFor(mode: RoutingMode): AudioRecorder {
        return when (mode) {
            RoutingMode.MOCK_INTERNAL -> mockAudioRecorder
            RoutingMode.INTERNAL_VOXTRAL -> mediaAudioRecorder
            RoutingMode.EXTERNAL_IME_FALLBACK -> mockAudioRecorder
        }
    }

    private fun transcriptionClientFor(mode: RoutingMode): TranscriptionClient {
        return when (mode) {
            RoutingMode.MOCK_INTERNAL -> mockTranscriptionClient
            RoutingMode.INTERNAL_VOXTRAL -> voxtralTranscriptionClient
            RoutingMode.EXTERNAL_IME_FALLBACK -> mockTranscriptionClient
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
}
