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

import android.content.Context
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService
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
 * MVP behavior:
 * - Debug builds: internal mock dictation flow (start/stop with microphone key).
 * - Non-debug builds: fallback to legacy "switch to external voice IME" flow.
 *
 * TODO(voxtral):
 * - replace [NoOpAudioRecorder] with real microphone capture
 * - wire [VoxtralRelayTranscriptionClient] to backend relay
 * - expose routing mode as user-visible setting
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
        EXTERNAL_IME_FALLBACK,
    }

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val routingMode = resolveRoutingMode()
    private val audioRecorder: AudioRecorder = when (routingMode) {
        RoutingMode.MOCK_INTERNAL -> NoOpAudioRecorder()
        RoutingMode.EXTERNAL_IME_FALLBACK -> NoOpAudioRecorder()
    }
    private val transcriptionClient: TranscriptionClient = when (routingMode) {
        RoutingMode.MOCK_INTERNAL -> MockTranscriptionClient()
        RoutingMode.EXTERNAL_IME_FALLBACK -> VoxtralRelayTranscriptionClient()
    }

    private val _stateFlow = MutableStateFlow(DictationState.IDLE)
    val stateFlow: StateFlow<DictationState> = _stateFlow

    fun onVoiceInputKeyPressed() {
        when (routingMode) {
            RoutingMode.EXTERNAL_IME_FALLBACK -> {
                FlorisImeService.switchToVoiceInputMethod()
                return
            }
            RoutingMode.MOCK_INTERNAL -> {
                when (_stateFlow.value) {
                    DictationState.IDLE,
                    DictationState.ERROR -> startListening()

                    DictationState.LISTENING -> stopAndInsertTranscript()
                    DictationState.TRANSCRIBING -> {
                        appContext.showShortToastSync("Dictation is already transcribing…")
                    }
                }
            }
        }
    }

    private fun startListening() {
        val hasStarted = audioRecorder.start()
        if (!hasStarted) {
            _stateFlow.value = DictationState.ERROR
            appContext.showShortToastSync("Unable to start dictation")
            return
        }
        _stateFlow.value = DictationState.LISTENING
        appContext.showShortToastSync("Dictation started (mock mode). Tap mic again to insert text.")
    }

    private fun stopAndInsertTranscript() {
        scope.launch {
            _stateFlow.value = DictationState.TRANSCRIBING
            val recording = audioRecorder.stopAndRead().getOrElse { error ->
                setError("Failed to stop dictation", error)
                return@launch
            }

            val transcript = withContext(Dispatchers.IO) {
                transcriptionClient.transcribe(recording)
            }.getOrElse { error ->
                setError("Failed to transcribe dictation", error)
                return@launch
            }.trim()

            if (transcript.isBlank()) {
                _stateFlow.value = DictationState.ERROR
                appContext.showShortToastSync("Dictation returned empty text")
                return@launch
            }

            val wasCommitted = editorInstance.commitText(transcript)
            if (!wasCommitted) {
                _stateFlow.value = DictationState.ERROR
                appContext.showShortToastSync("Could not insert dictated text")
                return@launch
            }

            flogInfo { "Inserted mock dictation transcript (${transcript.length} chars)" }
            _stateFlow.value = DictationState.IDLE
        }
    }

    private fun setError(message: String, error: Throwable) {
        _stateFlow.value = DictationState.ERROR
        flogError { "$message: ${error.message}" }
        appContext.showShortToastSync(message)
    }

    private fun resolveRoutingMode(): RoutingMode {
        return if (BuildConfig.DEBUG) {
            RoutingMode.MOCK_INTERNAL
        } else {
            RoutingMode.EXTERNAL_IME_FALLBACK
        }
    }
}
