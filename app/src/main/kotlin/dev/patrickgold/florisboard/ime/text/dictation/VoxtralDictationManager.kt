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
import dev.patrickgold.florisboard.ime.text.rewrite.CloudAiAvailability
import dev.patrickgold.florisboard.ime.text.rewrite.CloudAiAvailabilityPolicy
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val audioSessionCoordinator: AudioSessionCoordinator,
    private val feedbackController: VoiceActionFeedbackController,
    private val cloudAiAvailabilityPolicy: CloudAiAvailabilityPolicy,
) {
    enum class DictationState {
        IDLE,
        LISTENING,
        PAUSED,
        TRANSCRIBING,
        ERROR,
    }

    data class RecordingSessionState(
        val startedAtMs: Long,
        val pausedAtMs: Long? = null,
        val pausedDurationMs: Long = 0L,
    ) {
        fun elapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
            val activePauseMs = pausedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
            return (nowMs - startedAtMs - pausedDurationMs - activePauseMs).coerceAtLeast(0L)
        }
    }

    private enum class RoutingMode {
        MOCK_INTERNAL,
        INTERNAL_VOXTRAL,
        EXTERNAL_IME_FALLBACK,
    }

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val subtypeManager by context.subtypeManager()
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
        languageHintProvider = {
            TranscriptionLanguageHints.resolve(
                purpose = TranscriptionPurpose.DICTATION,
                storedLanguageHint = prefs.voxtral.languageHint.get(),
                activeSubtypeLanguageTag = subtypeManager.activeSubtype.primaryLocale.languageTag(),
            )
        },
    )
    private val voxtralInstructionTranscriptionClient: TranscriptionClient = VoxtralRelayTranscriptionClient(
        apiKeyProvider = { apiKey() },
        endpointUrlProvider = { prefs.voxtral.endpointUrl.get() },
        modelProvider = { prefs.voxtral.model.get() },
        languageHintProvider = { null },
    )
    private val transcriptionOnlyOperation = TranscriptionOnlyOperation()
    private val ordinaryDictationCommitOperation = OrdinaryDictationCommitOperation { transcript ->
        // Dictation supplies its own word boundary against the text around the cursor, so a phrase
        // dictated after `keyboard.` is inserted as ` When I…` rather than fused to the full stop.
        val content = editorInstance.activeContent
        editorInstance.commitText(
            DictationInsertionSpacing.join(
                transcript = transcript,
                textBefore = content.textBeforeSelection,
                textAfter = content.textAfterSelection,
            ),
        )
    }

    private var activeSessionMode: RoutingMode? = null
    private var activeLease: AudioSessionLease? = null
    private var operationJob: Job? = null

    init {
        migrateLegacyApiKeyIfNeeded()
        scope.launch {
            cloudAiAvailabilityPolicy.state.collectLatest { availability ->
                if (availability is CloudAiAvailability.Unavailable && activeLease != null) {
                    cancelDictation()
                }
            }
        }
    }

    private val _stateFlow = MutableStateFlow(DictationState.IDLE)
    val stateFlow: StateFlow<DictationState> = _stateFlow

    private val _recordingSessionFlow = MutableStateFlow<RecordingSessionState?>(null)
    val recordingSessionFlow: StateFlow<RecordingSessionState?> = _recordingSessionFlow

    val feedbackStateFlow: StateFlow<VoiceActionFeedbackState> = feedbackController.state

    fun onVoiceInputKeyPressed() {
        if (cloudAiAvailabilityPolicy.current() !is CloudAiAvailability.Available) {
            activeLease?.let { cancelDictation() }
            return
        }
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
                    DictationState.PAUSED -> stopAndInsertTranscript()
                    DictationState.TRANSCRIBING -> {
                        appContext.showShortToastSync("Dictation is already transcribing…")
                    }
                }
            }
        }
    }

    private fun startListening(mode: RoutingMode) {
        if (cloudAiAvailabilityPolicy.current() !is CloudAiAvailability.Available) {
            return
        }
        if (mode == RoutingMode.INTERNAL_VOXTRAL && !hasRecordAudioPermission()) {
            _stateFlow.value = DictationState.ERROR
            feedbackController.standaloneError(VoiceActionErrorReason.MICROPHONE_PERMISSION)
            appContext.showShortToastSync("Microphone permission missing. Grant it in Settings → AI.")
            return
        }

        val startResult = audioSessionCoordinator.tryStart(
            owner = AudioSessionOwner.DICTATION,
            mode = mode.toAudioSessionMode(),
            recorder = recorderFor(mode),
        )
        val lease = when (startResult) {
            is AudioSessionStartResult.Started -> startResult.lease
            is AudioSessionStartResult.Busy -> {
                feedbackController.standaloneError(VoiceActionErrorReason.AUDIO_SESSION_BUSY)
                appContext.showShortToastSync("Finish the active voice session first")
                return
            }
            AudioSessionStartResult.RecorderUnavailable -> {
                _stateFlow.value = DictationState.ERROR
                feedbackController.standaloneError(VoiceActionErrorReason.RECORDER_UNAVAILABLE)
                if (mode == RoutingMode.INTERNAL_VOXTRAL) {
                    appContext.showShortToastSync("Unable to start microphone recording")
                } else {
                    appContext.showShortToastSync("Unable to start dictation")
                }
                return
            }
        }

        activeSessionMode = mode
        activeLease = lease
        feedbackController.begin(lease.sessionId)
        syncRecordingSession(lease)
        _stateFlow.value = DictationState.LISTENING

        // The smartbar recording row owns the visible `Listening` state, its timer, and the stop
        // and cancel controls. A start toast would only duplicate it, so it remains solely as the
        // fallback for keyboards whose smartbar is switched off and therefore have no row.
        if (!prefs.smartbar.enabled.get()) {
            if (mode == RoutingMode.MOCK_INTERNAL) {
                appContext.showShortToastSync("Dictation started (mock mode). Tap mic again to insert text.")
            } else {
                appContext.showShortToastSync("Dictation started. Tap mic again to transcribe.")
            }
        }
    }

    fun togglePauseResume() {
        when (_stateFlow.value) {
            DictationState.LISTENING -> pauseListening()
            DictationState.PAUSED -> resumeListening()
            else -> Unit
        }
    }

    fun cancelDictation() {
        val lease = activeLease ?: return
        operationJob?.cancel()
        operationJob = null
        lease.cancel()
        feedbackController.cancel(lease.sessionId)
        activeLease = null
        activeSessionMode = null
        _recordingSessionFlow.value = null
        _stateFlow.value = DictationState.IDLE
        appContext.showShortToastSync("Dictation cancelled")
    }

    fun stopAndInsertTranscript() {
        if (_stateFlow.value != DictationState.LISTENING && _stateFlow.value != DictationState.PAUSED) {
            return
        }
        val lease = activeLease ?: return
        if (cloudAiAvailabilityPolicy.current() !is CloudAiAvailability.Available) {
            cancelDictation()
            return
        }
        if (operationJob?.isActive == true) return
        operationJob = scope.launch {
            _stateFlow.value = DictationState.TRANSCRIBING
            feedbackController.processing(lease.sessionId)
            val sessionMode = activeSessionMode ?: resolveRoutingMode()
            val transcriptionClient = transcriptionClientFor(sessionMode, TranscriptionPurpose.DICTATION)
                ?: return@launch
            val outcome = transcriptionOnlyOperation.stopAndTranscribe(lease, transcriptionClient)

            if (!lease.isCurrent) {
                feedbackController.cancel(lease.sessionId)
                if (activeLease?.sessionId == lease.sessionId) {
                    activeLease = null
                    activeSessionMode = null
                    _recordingSessionFlow.value = null
                    operationJob = null
                    _stateFlow.value = DictationState.IDLE
                }
                return@launch
            }

            when (val commitResult = ordinaryDictationCommitOperation.commit(outcome)) {
                OrdinaryDictationCommitResult.Committed -> {
                    feedbackController.success(lease.sessionId)
                    finishSession(lease)
                    _stateFlow.value = DictationState.IDLE
                }
                OrdinaryDictationCommitResult.CommitFailed -> {
                    feedbackController.error(lease.sessionId, VoiceActionErrorReason.EDITOR_COMMIT)
                    finishSession(lease)
                    setError("Could not insert dictated text", IllegalStateException("Editor commit failed"))
                }
                is OrdinaryDictationCommitResult.NotCommitted -> {
                    when (commitResult.outcome) {
                        TranscriptionOutcome.Empty -> {
                            feedbackController.error(lease.sessionId, VoiceActionErrorReason.EMPTY_AUDIO)
                            finishSession(lease)
                            setError("Dictation returned empty text", IllegalStateException("Empty transcription"))
                        }
                        is TranscriptionOutcome.Failure -> {
                            val reason = when (commitResult.outcome.reason) {
                                TranscriptionFailureReason.RECORDING -> VoiceActionErrorReason.RECORDING
                                TranscriptionFailureReason.PROVIDER -> VoiceActionErrorReason.TRANSCRIPTION
                            }
                            feedbackController.error(lease.sessionId, reason)
                            finishSession(lease)
                            setError(
                                "Failed to transcribe dictation",
                                IllegalStateException("Transcription failed: ${commitResult.outcome.reason}"),
                            )
                        }
                        TranscriptionOutcome.Cancelled -> {
                            feedbackController.cancel(lease.sessionId)
                            finishSession(lease)
                            _stateFlow.value = DictationState.IDLE
                        }
                        is TranscriptionOutcome.Transcript -> Unit
                    }
                }
            }
        }
    }

    private fun pauseListening() {
        val lease = activeLease ?: return
        if (lease.pause()) {
            feedbackController.pause(lease.sessionId)
            syncRecordingSession(lease)
            _stateFlow.value = DictationState.PAUSED
        } else {
            appContext.showShortToastSync("Unable to pause dictation")
        }
    }

    private fun resumeListening() {
        val lease = activeLease ?: return
        if (lease.resume()) {
            feedbackController.resume(lease.sessionId)
            syncRecordingSession(lease)
            _stateFlow.value = DictationState.LISTENING
        } else {
            appContext.showShortToastSync("Unable to resume dictation")
        }
    }

    private fun setError(message: String, error: Throwable) {
        _recordingSessionFlow.value = null
        _stateFlow.value = DictationState.ERROR
        flogError { "$message: ${error.message}" }
        appContext.showShortToastSync(message)
    }

    fun invalidateSession(reason: AudioSessionInvalidation) {
        val sessionId = audioSessionCoordinator.state.value?.sessionId
        operationJob?.cancel()
        operationJob = null
        audioSessionCoordinator.invalidate(reason)
        sessionId?.let(feedbackController::cancel)
        activeLease = null
        activeSessionMode = null
        _recordingSessionFlow.value = null
        _stateFlow.value = DictationState.IDLE
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

    private fun transcriptionClientFor(
        mode: RoutingMode,
        purpose: TranscriptionPurpose,
    ): TranscriptionClient? {
        return when (mode) {
            RoutingMode.MOCK_INTERNAL -> mockTranscriptionClient
            RoutingMode.INTERNAL_VOXTRAL -> when (purpose) {
                TranscriptionPurpose.DICTATION -> voxtralTranscriptionClient
                TranscriptionPurpose.VOICE_REWRITE_INSTRUCTION -> voxtralInstructionTranscriptionClient
            }
            RoutingMode.EXTERNAL_IME_FALLBACK -> null
        }
    }

    fun instructionTranscriptionClient(): TranscriptionClient? =
        transcriptionClientFor(resolveRoutingMode(), TranscriptionPurpose.VOICE_REWRITE_INSTRUCTION)

    /**
     * Recorder and session mode for a voice-rewrite instruction. Voice rewrite must reuse the exact
     * dictation routing so both modes contend for one recorder through [AudioSessionCoordinator]
     * instead of opening a second microphone.
     */
    fun voiceRewriteRecorder(): AudioRecorder = recorderFor(resolveRoutingMode())

    fun voiceRewriteAudioSessionMode(): AudioSessionMode = resolveRoutingMode().toAudioSessionMode()

    /**
     * True when the in-process transcription path is available. The external voice-IME fallback
     * cannot return a transcript to the rewrite pipeline, so it counts as not configured.
     */
    fun isTranscriptionConfigured(): Boolean =
        resolveRoutingMode() != RoutingMode.EXTERNAL_IME_FALLBACK

    /**
     * Known provider label for the configured transcription endpoint, or `null` for a custom or
     * mock endpoint. The full endpoint URL never leaves the settings screen.
     */
    fun transcriptionProviderKnownLabel(): String? {
        if (resolveRoutingMode() == RoutingMode.MOCK_INTERNAL) return null
        return TranscriptionProviderNaming.knownLabel(prefs.voxtral.endpointUrl.get())
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun RoutingMode.toAudioSessionMode(): AudioSessionMode = when (this) {
        RoutingMode.MOCK_INTERNAL -> AudioSessionMode.MOCK
        RoutingMode.INTERNAL_VOXTRAL -> AudioSessionMode.CONFIGURED_PROVIDER
        RoutingMode.EXTERNAL_IME_FALLBACK -> AudioSessionMode.MOCK
    }

    private fun syncRecordingSession(lease: AudioSessionLease) {
        _recordingSessionFlow.value = lease.state?.let { state ->
            RecordingSessionState(
                startedAtMs = state.startedAtMs,
                pausedAtMs = state.pausedAtMs,
                pausedDurationMs = state.pausedDurationMs,
            )
        }
    }

    private fun finishSession(lease: AudioSessionLease) {
        lease.complete()
        if (activeLease?.sessionId == lease.sessionId) {
            activeLease = null
            activeSessionMode = null
            _recordingSessionFlow.value = null
            operationJob = null
        }
    }
}
