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

import dev.patrickgold.florisboard.ime.text.dictation.offline.LiveOrukeetSession
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
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.showShortToastSync
import org.ownkey.offline.LiveTranscription
import org.ownkey.offline.LiveUpdate
import org.ownkey.offline.LocalAsrException

/** Live dictation has no practical length limit; this only ends a session left running by accident. */
private const val LIVE_SAFETY_LIMIT_MS = 10 * 60_000L

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

    /**
     * Orukeet dictation types while the user talks. Words that froze are already in the field; the live
     * words after them are composing text that each preview replaces. Main thread only.
     */
    private class LiveDictation(
        val sessionId: Long,
        val source: LiveOrukeetSession,
        val transcription: LiveTranscription,
        val text: LiveDictationText,
        /**
         * The text goes into the field while the user talks. False for editors that can't hold composing
         * text and with TalkBack on; the text then goes in at Stop.
         */
        val draft: Boolean,
        /** The editor reports the user taking over the field: through the draft, or a watch when the text waits for Stop. */
        val watched: Boolean,
    )
    private var live: LiveDictation? = null
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
                if (FlorisImeService.switchToVoiceInputMethod()) insertionListener?.onDictationStarted()
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
            // Reading the key touches the Keystore-backed store, so it stays off the main thread.
            if (mode.lacksApiKey(hasCloudKey = kotlinx.coroutines.withContext(Dispatchers.IO) { apiKey().isNotBlank() })) {
                _stateFlow.value = DictationState.ERROR
                feedbackController.standaloneError(VoiceActionErrorReason.PROVIDER_CONFIGURATION)
                appContext.showShortToast("Add a transcription API key in Settings → AI")
                return@launch
            }
            if (mode != TranscriptionBackend.MOCK && !hasRecordAudioPermission()) {
                _stateFlow.value = DictationState.ERROR
                feedbackController.standaloneError(VoiceActionErrorReason.MICROPHONE_PERMISSION)
                appContext.showShortToastSync("Microphone permission missing. Grant it in Settings → AI.")
                return@launch
            }
            var snapshot: TranscriptionSession? = null
            var acquired: AudioSessionLease? = null
            var liveSource: LiveOrukeetSession? = null
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    // Assign before crossing the dispatcher boundary so cancellation cannot lose the lease.
                    if (mode == TranscriptionBackend.ORUKEET) {
                        liveSource = snapshotLiveSession().also { snapshot = it.session }
                    } else {
                        snapshot = snapshotSession(TranscriptionPurpose.DICTATION, mode)
                    }
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
                liveSource?.let { source -> startLive(source, lease, session) }
                activeSession = snapshot
                activeLease = lease
                feedbackController.begin(lease.sessionId)
                syncRecordingSession(lease)
                _stateFlow.value = DictationState.LISTENING
                insertionListener?.onDictationStarted()
                if (session.backend == TranscriptionBackend.ORUKEET) {
                    // Live dictation decodes a few seconds at a time, so only a safety limit remains.
                    autoStopJob = scope.launch {
                        while (lease.isCurrent && _stateFlow.value in setOf(DictationState.LISTENING, DictationState.PAUSED)) {
                            if ((lease.state?.elapsedMs(System.currentTimeMillis()) ?: 0) >= LIVE_SAFETY_LIMIT_MS) {
                                appContext.showShortToastSync(appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__live_limit))
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
        endLive(keepText = false)
        val lease = activeLease ?: run {
            sessionGeneration++
            operationJob?.cancel(); operationJob = null
            audioSessionCoordinator.invalidate(AudioSessionInvalidation.OWNER_CANCELLED, AudioSessionOwner.DICTATION)
            _stateFlow.value = DictationState.IDLE
            return
        }
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
        live?.takeIf { it.sessionId == lease.sessionId }?.let { current ->
            stopLive(lease, current)
            return
        }
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
        endLive(keepText = true)
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

    private suspend fun snapshotLiveSession(): LiveOrukeetSession {
        val dictionary = speechDictionary.snapshot()
        return appContext.offlineDictation().liveSession(
            vocabulary = if (prefs.voxtral.localVocabularyHints.get()) dictionary.vocabulary else emptyList(),
            dictionary = dictionary,
        )
    }

    /** The recorder already runs; this connects it to the inference process and starts the draft. */
    private suspend fun startLive(source: LiveOrukeetSession, lease: AudioSessionLease, session: TranscriptionSession) {
        val content = editorInstance.activeContent
        val cleaner = session.dictionary?.cleaner
        val text = LiveDictationText(content.textBeforeSelection, content.textAfterSelection) { cleaner?.clean(it) ?: it }
        val sessionId = lease.sessionId
        val transcription = source.start(
            onUpdate = { update -> onLiveUpdate(sessionId, update) },
            onFailure = { error -> failLive(sessionId, VoiceActionErrorReason.TRANSCRIPTION, error) },
        )
        val draft = !isTouchExplorationEnabled() && editorInstance.beginDictationDraft()
        // Without a draft (TalkBack, raw editors) a watch still ends the session when the user takes over.
        val watched = draft || editorInstance.beginDictationWatch()
        if (watched) editorInstance.onDictationDraftInterrupted = { onLiveDraftInterrupted(sessionId) }
        // Dispatched, never immediate: a failure replayed right here must wait until the session is set up.
        source.recorder.onFailure = {
            scope.launch(Dispatchers.Main) {
                failLive(sessionId, VoiceActionErrorReason.RECORDING, IllegalStateException("Microphone capture failed"))
            }
        }
        live = LiveDictation(sessionId, source, transcription, text, draft, watched)
    }

    private fun onLiveUpdate(sessionId: Long, update: LiveUpdate) {
        val current = live?.takeIf { it.sessionId == sessionId } ?: return
        // Updates keep arriving in order after Stop; words that froze in them are not in the final text.
        val (commit, shown) = current.text.update(update.frozen, update.live)
        if (current.draft) editorInstance.updateDictationDraft(commit, shown)
    }

    /** Recording or inference failed while the user was talking. What they already see stays. */
    private fun failLive(sessionId: Long, reason: VoiceActionErrorReason, cause: Throwable) {
        // After Stop, the stop path reports the failure itself.
        if (live?.sessionId != sessionId || _stateFlow.value == DictationState.TRANSCRIBING) return
        flogError { "Live dictation failed: ${(cause as? LocalAsrException)?.reason ?: cause.javaClass.simpleName}" }
        endLive(keepText = true)
        val lease = activeLease ?: return
        abortSession(lease)
        feedbackController.error(lease.sessionId, reason)
        setError(appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__live_stopped), cause)
    }

    /**
     * The user typed or moved the cursor. Text already in the field stays where it is; text waiting for
     * Stop is dropped, because the place it was meant for has changed. Dictation ends.
     */
    private fun onLiveDraftInterrupted(sessionId: Long) {
        if (live?.sessionId != sessionId) return
        endLive(keepText = true)
        val lease = activeLease ?: return
        abortSession(lease)
        feedbackController.cancel(lease.sessionId)
        _stateFlow.value = DictationState.IDLE
    }

    private fun abortSession(lease: AudioSessionLease) {
        sessionGeneration++
        autoStopJob?.cancel(); autoStopJob = null
        operationJob?.cancel(); operationJob = null
        lease.cancel()
        activeLease = null
        activeSession = null
        _recordingSessionFlow.value = null
    }

    /** Ends the live session without a final decode. [keepText] leaves the visible text; otherwise it is removed. */
    private fun endLive(keepText: Boolean) {
        val current = detachLive() ?: return
        if (current.draft && !keepText) editorInstance.removeDictationDraft() else editorInstance.abandonDictationDraft()
    }

    /**
     * Clears the live session and releases its reservation of the inference process. Every way a session
     * ends goes through here; cancelling after a finished or failed finish is a no-op.
     */
    private fun detachLive(): LiveDictation? {
        val current = live ?: return null
        live = null
        editorInstance.onDictationDraftInterrupted = null
        current.source.recorder.onFailure = null
        current.transcription.cancel()
        return current
    }

    private fun stopLive(lease: AudioSessionLease, current: LiveDictation) {
        operationJob = scope.launch {
            _stateFlow.value = DictationState.TRANSCRIBING
            feedbackController.processing(lease.sessionId)
            autoStopJob?.cancel(); autoStopJob = null
            val stopped = kotlinx.coroutines.withContext(Dispatchers.IO) { lease.stop() }
            if (stopped is AudioSessionStopResult.AlreadyStopped || stopped is AudioSessionStopResult.Stale) {
                if (live === current) endLive(keepText = true)
                feedbackController.cancel(lease.sessionId)
                return@launch
            }
            val recording = (stopped as? AudioSessionStopResult.Stopped)?.recording
            val final = try {
                if (recording == null) null else current.transcription.finish(current.source.recorder.samplesWritten)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                recording?.close()
                throw cancel
            } catch (error: Exception) {
                flogError { "Live dictation could not finish: ${(error as? LocalAsrException)?.reason ?: error.javaClass.simpleName}" }
                null
            }
            recording?.close()
            if (live !== current || !lease.isCurrent) {
                if (live === current) endLive(keepText = true)
                feedbackController.cancel(lease.sessionId)
                return@launch
            }
            // Releases the inference reservation too when the recording failed and finish never ran.
            detachLive()
            if (final == null) {
                // Keep what the user saw rather than throw it away.
                editorInstance.abandonDictationDraft()
                feedbackController.error(lease.sessionId, if (recording == null) VoiceActionErrorReason.RECORDING else VoiceActionErrorReason.TRANSCRIPTION)
                finishSession(lease)
                setError(appContext.getString(dev.patrickgold.florisboard.R.string.orukeet__live_stopped), IllegalStateException("Live dictation failed"))
                return@launch
            }
            val commit = current.text.finish(final.frozen)
            val inserted = current.text.committedText
            if (inserted.isBlank()) {
                // Nothing to insert: take the draft back and restore any text it replaced.
                editorInstance.removeDictationDraft()
                finishSession(lease)
                if (current.text.rawTranscript.isNotBlank()) {
                    // Neutral result: the recognizer worked, there was just nothing left to insert.
                    feedbackController.cancel(lease.sessionId)
                    _stateFlow.value = DictationState.IDLE
                    appContext.showShortToastSync(appContext.getString(dev.patrickgold.florisboard.R.string.speech_dictionary__only_fillers))
                } else {
                    feedbackController.error(lease.sessionId, VoiceActionErrorReason.EMPTY_AUDIO)
                    setError("Dictation returned empty text", IllegalStateException("Empty transcription"))
                }
                return@launch
            }
            val committed = if (current.draft) {
                editorInstance.commitDictationDraft(commit)
            } else {
                // The watch ends before the commit moves the cursor.
                editorInstance.abandonDictationDraft()
                editorInstance.commitText(inserted)
            }
            if (!committed) {
                feedbackController.error(lease.sessionId, VoiceActionErrorReason.EDITOR_COMMIT)
                finishSession(lease)
                setError("Could not insert dictated text", IllegalStateException("Editor commit failed"))
                return@launch
            }
            feedbackController.success(lease.sessionId)
            finishSession(lease)
            _stateFlow.value = DictationState.IDLE
            val info = editorInstance.activeInfo
            insertionListener?.onDictationInserted(
                DictationInsertion(
                    rawTranscript = current.text.rawTranscript,
                    committedText = inserted,
                    editorSessionId = editorInstance.activeInputSessionId,
                    hostPackage = info.packageName,
                    fieldId = info.base.fieldId,
                    committedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun isTouchExplorationEnabled(): Boolean =
        appContext.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true

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
                // The client falls back to the default endpoint for a blank preference; the hint
                // field must be resolved from the same URL the request will actually use.
                val vocabularyField = CloudVocabularyHints.field(
                    endpoint.trim().ifBlank { VoxtralRelayTranscriptionClient.DefaultEndpointUrl },
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
