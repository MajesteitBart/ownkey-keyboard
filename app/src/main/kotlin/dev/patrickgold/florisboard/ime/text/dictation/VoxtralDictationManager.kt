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

import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyHints
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyMode
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationCleanupResult
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationInsertion
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationInsertionListener
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.OrdinaryDictationCleanup
import dev.patrickgold.florisboard.ime.text.rewrite.AiAvailability
import dev.patrickgold.florisboard.ime.text.rewrite.AiAvailabilityPolicy
import dev.patrickgold.florisboard.speechDictionary
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

/** Dictation entrypoint with an immutable backend/client/model snapshot for each recording. */
class VoxtralDictationManager(
    context: Context,
    private val audioSessionCoordinator: AudioSessionCoordinator,
    private val feedbackController: VoiceActionFeedbackController,
    private val aiAvailabilityPolicy: AiAvailabilityPolicy,
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

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val subtypeManager by context.subtypeManager()
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voxtralSecretsStore = VoxtralSecretsStore(appContext)

    private val mockAudioRecorder: AudioRecorder = NoOpAudioRecorder()
    private val mockTranscriptionClient: TranscriptionClient = MockTranscriptionClient()
    private val transcriptionOnlyOperation = TranscriptionOnlyOperation()
    private val speechDictionary by lazy { appContext.speechDictionary().value }

    /** Keyboard-side fix flow; it learns about ordinary insertions only, never about rewrite instructions. */
    @Volatile
    var insertionListener: DictationInsertionListener? = null
    private var lastCommit: DictationInsertion? = null

    private val ordinaryDictationCommitOperation = OrdinaryDictationCommitOperation { transcript ->
        // Dictation supplies its own word boundary against the text around the cursor, so a phrase
        // dictated after `keyboard.` is inserted as ` When I…` rather than fused to the full stop.
        val content = editorInstance.activeContent
        val joined = DictationInsertionSpacing.join(
            transcript = transcript,
            textBefore = content.textBeforeSelection,
            textAfter = content.textAfterSelection,
        )
        val committed = editorInstance.commitText(joined)
        if (committed) {
            val info = editorInstance.activeInfo
            lastCommit = DictationInsertion(
                rawTranscript = "",
                committedText = joined,
                editorSessionId = editorInstance.activeInputSessionId,
                hostPackage = info.packageName,
                fieldId = info.base.fieldId,
                committedAtMs = System.currentTimeMillis(),
            )
        }
        committed
    }

    private var activeSession: TranscriptionSession? = null
    private var sessionGeneration = 0L
    private var autoStopJob: Job? = null
    private var activeLease: AudioSessionLease? = null
    private var operationJob: Job? = null

    init {
        migrateLegacyApiKeyIfNeeded()
        scope.launch {
            aiAvailabilityPolicy.state.collectLatest { availability ->
                if (availability is AiAvailability.Unavailable && activeLease != null) {
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
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            activeLease?.let { cancelDictation() }
            return
        }
        val currentMode = activeSession?.backend ?: resolveTranscriptionBackend()

        when (currentMode) {
            TranscriptionBackend.EXTERNAL_IME -> {
                FlorisImeService.switchToVoiceInputMethod()
                return
            }

            TranscriptionBackend.MOCK,
            TranscriptionBackend.UNAVAILABLE,
            TranscriptionBackend.ORUKEET,
            TranscriptionBackend.CLOUD -> {
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

    private fun startListening(mode: TranscriptionBackend) {
        if (operationJob?.isActive == true) return
        val generation = ++sessionGeneration
        operationJob = scope.launch {
            if (aiAvailabilityPolicy.current() !is AiAvailability.Available) return@launch
            if (mode != TranscriptionBackend.MOCK && !hasRecordAudioPermission()) {
                _stateFlow.value = DictationState.ERROR
                feedbackController.standaloneError(VoiceActionErrorReason.MICROPHONE_PERMISSION)
                appContext.showShortToastSync("Microphone permission missing. Grant it in Settings → AI.")
                return@launch
            }
            var snapshot: TranscriptionSession? = null
            var acquired: AudioSessionLease? = null
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    // Assign before crossing the dispatcher boundary so cancellation cannot lose the lease.
                    snapshot = snapshotSession(TranscriptionPurpose.DICTATION, mode)
                }
                val session = requireNotNull(snapshot)
                val started = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    audioSessionCoordinator.tryStart(AudioSessionOwner.DICTATION, session.mode, session.recorder, session::close)
                        .also { acquired = (it as? AudioSessionStartResult.Started)?.lease }
                }
                if (generation != sessionGeneration || aiAvailabilityPolicy.current() !is AiAvailability.Available) {
                    acquired?.cancel(); snapshot?.close(); return@launch
                }
                val lease = acquired
                if (lease == null) {
                    snapshot?.close()
                    feedbackController.standaloneError(if (started is AudioSessionStartResult.Busy) VoiceActionErrorReason.AUDIO_SESSION_BUSY else VoiceActionErrorReason.RECORDER_UNAVAILABLE)
                    _stateFlow.value = DictationState.ERROR
                    return@launch
                }
                activeSession = snapshot
                activeLease = lease
                feedbackController.begin(lease.sessionId)
                syncRecordingSession(lease)
                _stateFlow.value = DictationState.LISTENING
                insertionListener?.onDictationStarted()
                if (session.backend == TranscriptionBackend.ORUKEET) {
                    autoStopJob = scope.launch {
                        while (lease.isCurrent && _stateFlow.value in setOf(DictationState.LISTENING, DictationState.PAUSED)) {
                            if ((lease.state?.elapsedMs(System.currentTimeMillis()) ?: 0) >= org.ownkey.offline.ModelCatalog.RECORDING_CAP_MS) {
                                stopAndInsertTranscript(); break
                            }
                            delay(100)
                        }
                    }
                }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                acquired?.cancel(); snapshot?.close(); throw cancel
            } catch (_: Exception) {
                acquired?.cancel(); snapshot?.close()
                if (generation == sessionGeneration) {
                    _stateFlow.value = DictationState.ERROR
                    feedbackController.standaloneError(VoiceActionErrorReason.TRANSCRIPTION)
                    appContext.showShortToastSync(if (mode == TranscriptionBackend.ORUKEET) appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__not_ready) else "Unable to start dictation")
                }
            } finally { if (generation == sessionGeneration) operationJob = null }
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
        val lease = activeLease ?: run { invalidateSession(AudioSessionInvalidation.OWNER_CANCELLED); return }
        sessionGeneration++
        autoStopJob?.cancel(); autoStopJob = null
        operationJob?.cancel()
        operationJob = null
        lease.cancel()
        feedbackController.cancel(lease.sessionId)
        activeLease = null
        activeSession = null
        _recordingSessionFlow.value = null
        _stateFlow.value = DictationState.IDLE
        appContext.showShortToastSync("Dictation cancelled")
    }

    fun stopAndInsertTranscript() {
        if (_stateFlow.value != DictationState.LISTENING && _stateFlow.value != DictationState.PAUSED) {
            return
        }
        val lease = activeLease ?: return
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            cancelDictation()
            return
        }
        if (operationJob?.isActive == true) return
        operationJob = scope.launch {
            _stateFlow.value = DictationState.TRANSCRIBING
            feedbackController.processing(lease.sessionId)
            autoStopJob?.cancel(); autoStopJob = null
            val session = activeSession ?: return@launch
            val transcriptionClient = session.client ?: return@launch
            val rawOutcome = transcriptionOnlyOperation.stopAndTranscribe(lease, transcriptionClient)
            // Ordinary dictation is the only path that is cleaned. It uses the snapshot taken at
            // recording start, runs off the main thread, and the lease is rechecked right before commit.
            val cleaned = kotlinx.coroutines.withContext(Dispatchers.Default) {
                OrdinaryDictationCleanup.apply(rawOutcome, session.dictionary?.cleaner)
            }

            if (!lease.isCurrent) {
                feedbackController.cancel(lease.sessionId)
                if (activeLease?.sessionId == lease.sessionId) {
                    activeLease = null
                    activeSession = null
                    _recordingSessionFlow.value = null
                    operationJob = null
                    _stateFlow.value = DictationState.IDLE
                }
                return@launch
            }

            if (cleaned is DictationCleanupResult.OnlyFillers) {
                // Neutral result: the recognizer worked, there was just nothing left to insert.
                feedbackController.cancel(lease.sessionId)
                finishSession(lease)
                _stateFlow.value = DictationState.IDLE
                appContext.showShortToastSync(appContext.getString(dev.patrickgold.florisboard.R.string.speech_dictionary__only_fillers))
                return@launch
            }
            val outcome = (cleaned as DictationCleanupResult.Ready).outcome

            when (val commitResult = ordinaryDictationCommitOperation.commit(outcome)) {
                OrdinaryDictationCommitResult.Committed -> {
                    feedbackController.success(lease.sessionId)
                    finishSession(lease)
                    _stateFlow.value = DictationState.IDLE
                    val insertion = lastCommit
                    lastCommit = null
                    if (insertion != null) {
                        insertionListener?.onDictationInserted(insertion.copy(rawTranscript = cleaned.rawTranscript.orEmpty()))
                    }
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
                                TranscriptionFailureReason.PROVIDER,
                                TranscriptionFailureReason.LOCAL_MODEL,
                                TranscriptionFailureReason.LOCAL_RUNTIME -> VoiceActionErrorReason.TRANSCRIPTION
                            }
                            feedbackController.error(lease.sessionId, reason)
                            finishSession(lease)
                            setError(
                                if (commitResult.outcome.reason in setOf(TranscriptionFailureReason.LOCAL_MODEL, TranscriptionFailureReason.LOCAL_RUNTIME)) appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__transcription_failed) else "Failed to transcribe dictation",
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
        sessionGeneration++
        autoStopJob?.cancel(); autoStopJob = null
        val sessionId = audioSessionCoordinator.state.value?.sessionId
        operationJob?.cancel()
        operationJob = null
        audioSessionCoordinator.invalidate(reason)
        sessionId?.let(feedbackController::cancel)
        activeLease = null
        activeSession = null
        _recordingSessionFlow.value = null
        _stateFlow.value = DictationState.IDLE
    }

    private fun resolveTranscriptionBackend(): TranscriptionBackend = TranscriptionBackend.resolve(
        prefs.voxtral.dictationBackend.get(), apiKey().isNotBlank(), BuildConfig.DEBUG,
    )

    /**
     * Vocabulary hints reach both ordinary dictation and spoken rewrite instructions; the attached
     * dictionary snapshot is only consulted for cleanup by [stopAndInsertTranscript].
     */
    suspend fun snapshotSession(purpose: TranscriptionPurpose, backend: TranscriptionBackend = resolveTranscriptionBackend()): TranscriptionSession {
        val dictionary = speechDictionary.snapshot()
        return when (backend) {
            TranscriptionBackend.UNAVAILABLE -> throw org.ownkey.offline.LocalAsrException(org.ownkey.offline.LocalAsrFailure.UNSUPPORTED)
            TranscriptionBackend.ORUKEET -> appContext.offlineDictation().session(
                vocabulary = if (prefs.voxtral.localVocabularyHints.get()) dictionary.vocabulary else emptyList(),
                dictionary = dictionary,
            )
            TranscriptionBackend.MOCK -> TranscriptionSession(backend, NoOpAudioRecorder(), mockTranscriptionClient, dictionary)
            TranscriptionBackend.EXTERNAL_IME -> TranscriptionSession(backend, mockAudioRecorder, null)
            TranscriptionBackend.CLOUD -> {
                val key = apiKey()
                val endpoint = prefs.voxtral.endpointUrl.get()
                val model = prefs.voxtral.model.get()
                val language = TranscriptionLanguageHints.resolve(purpose, prefs.voxtral.languageHint.get(), subtypeManager.activeSubtype.primaryLocale.languageTag())
                val vocabularyField = CloudVocabularyHints.field(
                    endpoint,
                    CloudVocabularyMode.fromPreference(prefs.voxtral.cloudVocabularyMode.get()),
                )
                val vocabulary = dictionary.vocabulary
                TranscriptionSession(backend, MediaRecorderAudioRecorder(appContext), VoxtralRelayTranscriptionClient(
                    apiKeyProvider = { key }, endpointUrlProvider = { endpoint }, modelProvider = { model }, languageHintProvider = { language },
                    vocabularyProvider = { vocabulary }, vocabularyFieldProvider = { vocabularyField },
                ), dictionary)
            }
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

    fun isLocalSelected(): Boolean = resolveTranscriptionBackend() == TranscriptionBackend.ORUKEET

    fun isTranscriptionConfigured(): Boolean = when (resolveTranscriptionBackend()) {
        TranscriptionBackend.ORUKEET -> appContext.offlineDictation().ready
        TranscriptionBackend.EXTERNAL_IME, TranscriptionBackend.UNAVAILABLE -> false
        TranscriptionBackend.CLOUD -> apiKey().isNotBlank()
        TranscriptionBackend.MOCK -> true
    }

    fun transcriptionProviderKnownLabel(): String? = when (resolveTranscriptionBackend()) {
        TranscriptionBackend.ORUKEET -> appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__title)
        TranscriptionBackend.MOCK, TranscriptionBackend.EXTERNAL_IME, TranscriptionBackend.UNAVAILABLE -> null
        TranscriptionBackend.CLOUD -> TranscriptionProviderNaming.knownLabel(prefs.voxtral.endpointUrl.get())
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
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
            activeSession = null
            _recordingSessionFlow.value = null
            operationJob = null
        }
    }
}
