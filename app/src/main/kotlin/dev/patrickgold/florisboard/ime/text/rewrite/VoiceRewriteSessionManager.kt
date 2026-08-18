/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.text.dictation.AudioRecorder
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionLease
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionOwner
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionStartResult
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionFailureReason
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOnlyOperation
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOutcome
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionErrorReason
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoiceRewriteSessionPhase {
    READY,
    TARGETING,
    DISCLOSURE,
    STARTING_RECORDING,
    RECORDING,
    PAUSED,
    TRANSCRIBING,
    REWRITING,
    RESULT,
    WARNING,
    ERROR,
    SUCCESS,
    CANCELLED,
}

enum class VoiceRewritePreflightFailure {
    NO_ACTIVE_EDITOR,
    SECURE_FIELD,
    INCOGNITO,
    TARGET_REJECTED,
    AUDIO_SESSION_BUSY,
    MICROPHONE_PERMISSION,
    DICTATION_PROVIDER_NOT_CONFIGURED,
    REWRITE_PROVIDER_NOT_CONFIGURED,
    RECORDER_UNAVAILABLE,
}

enum class VoiceRewritePipelineFailure {
    NO_SPEECH,
    RECORDING,
    TRANSCRIPTION,
    REWRITE,
    EMPTY_RESULT,
}

data class VoiceRewriteProviderConfiguration(
    val isConfigured: Boolean,
    val displayName: String,
)

data class VoiceRewriteProviderDisclosure(
    val version: Int,
    val audioProviderName: String,
    val rewriteProviderName: String,
)

data class VoiceRewriteSessionState(
    val generationId: Long = 0L,
    val phase: VoiceRewriteSessionPhase = VoiceRewriteSessionPhase.READY,
    val targetScope: VoiceRewriteTargetScope? = null,
    val targetCharacterCount: Int? = null,
    val disclosure: VoiceRewriteProviderDisclosure? = null,
    val audioSessionId: Long? = null,
    val failure: VoiceRewritePreflightFailure? = null,
    val targetFailure: VoiceRewriteTargetFailure? = null,
    val pipelineFailure: VoiceRewritePipelineFailure? = null,
    val recognizedInstruction: String? = null,
    val resultText: String? = null,
    val canReplace: Boolean = false,
    val canCopyResult: Boolean = false,
    val replacementFailure: VoiceRewriteTargetVerificationFailure? = null,
)

fun interface VoiceRewriteMicrophonePermission {
    fun isGranted(): Boolean
}

interface VoiceRewriteProviderConfigurationSource {
    fun transcriptionProvider(): VoiceRewriteProviderConfiguration
    fun rewriteProvider(): VoiceRewriteProviderConfiguration
}

interface VoiceRewriteDisclosureStore {
    fun acknowledgedVersion(): Int
    fun acknowledge(version: Int)
}

/**
 * Headless voice-rewrite session boundary. This first slice owns deterministic preflight and the
 * single-recorder lease; transcription/rewrite operations extend the same generation-scoped state.
 */
class VoiceRewriteSessionManager(
    private val scope: CoroutineScope,
    private val availabilityPolicy: CloudAiAvailabilityPolicy,
    private val targetSource: VoiceRewriteTargetSource,
    private val audioSessionCoordinator: AudioSessionCoordinator,
    private val feedbackController: VoiceActionFeedbackController,
    private val audioRecorderProvider: () -> AudioRecorder,
    private val audioSessionModeProvider: () -> AudioSessionMode,
    private val microphonePermission: VoiceRewriteMicrophonePermission,
    private val providerConfiguration: VoiceRewriteProviderConfigurationSource,
    private val configurationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val disclosureStore: VoiceRewriteDisclosureStore,
    private val disclosureVersion: Int,
    private val transcriptionOperation: TranscriptionOnlyOperation = TranscriptionOnlyOperation(),
    private val instructionTranscriptionClientProvider: () -> TranscriptionClient? = { null },
    private val rewriteOperation: VoiceRewriteOperation = VoiceRewriteOperation { _, _ ->
        Result.failure(IllegalStateException("Voice rewrite operation is not configured"))
    },
    private val maxRecordingDurationMs: Long = 30_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(VoiceRewriteSessionState())
    val state: StateFlow<VoiceRewriteSessionState> = _state

    private var nextGenerationId = 0L
    private var preflightJob: Job? = null
    private var startRecordingJob: Job? = null
    private var activeTarget: VoiceRewriteTargetSnapshot? = null
    private var activeAudioLease: AudioSessionLease? = null
    private var feedbackSessionId: Long? = null
    private var operationJob: Job? = null
    private var autoStopJob: Job? = null
    private var recognizedInstruction: String? = null
    private var resultText: String? = null

    init {
        scope.launch {
            availabilityPolicy.state.collectLatest { availability ->
                if (availability is CloudAiAvailability.Unavailable && _state.value.phase.isActiveSessionPhase()) {
                    cancelResources()
                    publishAvailabilityFailure(_state.value.generationId, availability)
                }
            }
        }
    }

    fun begin() {
        cancelResources()
        val generationId = ++nextGenerationId
        _state.value = VoiceRewriteSessionState(generationId = generationId)
        val availability = availabilityPolicy.current()
        if (availability is CloudAiAvailability.Unavailable) {
            publishAvailabilityFailure(generationId, availability)
            return
        }
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.TARGETING,
        )
        preflightJob = scope.launch { runPreflight(generationId) }
    }

    fun acknowledgeDisclosure() {
        val current = _state.value
        if (current.phase != VoiceRewriteSessionPhase.DISCLOSURE || current.disclosure == null) return
        disclosureStore.acknowledge(current.disclosure.version)
        launchStartRecording(current.generationId)
    }

    fun cancel() {
        val generationId = _state.value.generationId
        cancelResources()
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.CANCELLED,
        )
    }

    fun pauseRecording(): Boolean {
        val current = _state.value
        val lease = activeAudioLease ?: return false
        if (current.phase != VoiceRewriteSessionPhase.RECORDING || !lease.pause()) return false
        autoStopJob?.cancel()
        autoStopJob = null
        feedbackController.pause(lease.sessionId)
        publishRecordingState(current.generationId, VoiceRewriteSessionPhase.PAUSED, lease)
        return true
    }

    fun resumeRecording(): Boolean {
        val current = _state.value
        val lease = activeAudioLease ?: return false
        if (current.phase != VoiceRewriteSessionPhase.PAUSED || !lease.resume()) return false
        feedbackController.resume(lease.sessionId)
        publishRecordingState(current.generationId, VoiceRewriteSessionPhase.RECORDING, lease)
        scheduleAutoStop(current.generationId, lease)
        return true
    }

    fun recordingElapsedMs(): Long {
        val lease = activeAudioLease ?: return 0L
        return lease.state?.elapsedMs(nowMs()) ?: 0L
    }

    fun stopRecording() {
        val current = _state.value
        if (current.phase !in setOf(VoiceRewriteSessionPhase.RECORDING, VoiceRewriteSessionPhase.PAUSED)) return
        val lease = activeAudioLease ?: return
        if (operationJob?.isActive == true) return
        autoStopJob?.cancel()
        autoStopJob = null
        _state.value = current.copy(phase = VoiceRewriteSessionPhase.TRANSCRIBING)
        feedbackController.processing(lease.sessionId)
        operationJob = scope.launch { transcribeAndRewrite(current.generationId, lease) }
    }

    fun tryAgain() {
        val current = _state.value
        val instruction = recognizedInstruction ?: return
        if (
            activeTarget == null ||
            operationJob?.isActive == true ||
            startRecordingJob?.isActive == true
        ) return
        if (current.phase !in setOf(VoiceRewriteSessionPhase.RESULT, VoiceRewriteSessionPhase.ERROR)) return
        if (launchRewrite(current.generationId, instruction) && current.phase == VoiceRewriteSessionPhase.ERROR) {
            feedbackSessionId = feedbackController.beginStandaloneProcessing().takeUnless { it == 0L }
        }
    }

    fun recordInstructionAgain() {
        val current = _state.value
        if (
            activeTarget == null ||
            operationJob?.isActive == true ||
            startRecordingJob?.isActive == true
        ) return
        if (current.phase !in setOf(VoiceRewriteSessionPhase.RESULT, VoiceRewriteSessionPhase.ERROR)) return
        cancelVoiceFeedback()
        launchStartRecording(current.generationId)
    }

    fun replaceResult(gateway: VoiceRewriteReplacementGateway): VoiceRewriteReplacementOutcome {
        val current = _state.value
        val target = activeTarget
        val reviewedResult = resultText
        if (
            current.phase != VoiceRewriteSessionPhase.RESULT ||
            target == null ||
            reviewedResult.isNullOrEmpty() ||
            !current.canReplace ||
            startRecordingJob?.isActive == true
        ) {
            return VoiceRewriteReplacementOutcome.Unavailable
        }
        target.verify(gateway.currentFrame())?.let { failure ->
            publishCopyFallback(current.generationId, failure)
            return VoiceRewriteReplacementOutcome.CopyFallback(failure)
        }
        return when (gateway.replace(target.range, reviewedResult)) {
            VoiceRewriteEditorReplaceResult.Replaced -> {
                val generationId = current.generationId
                val completedFeedbackSessionId = feedbackSessionId
                cancelResources(cancelFeedback = false)
                completedFeedbackSessionId?.let(feedbackController::success)
                feedbackSessionId = null
                _state.value = VoiceRewriteSessionState(
                    generationId = generationId,
                    phase = VoiceRewriteSessionPhase.SUCCESS,
                )
                VoiceRewriteReplacementOutcome.Replaced
            }
            VoiceRewriteEditorReplaceResult.SelectionFailed -> {
                publishCopyFallback(current.generationId, VoiceRewriteTargetVerificationFailure.SELECTION_FAILED)
                VoiceRewriteReplacementOutcome.CopyFallback(VoiceRewriteTargetVerificationFailure.SELECTION_FAILED)
            }
            VoiceRewriteEditorReplaceResult.CommitFailed -> {
                publishCopyFallback(current.generationId, VoiceRewriteTargetVerificationFailure.COMMIT_FAILED)
                VoiceRewriteReplacementOutcome.CopyFallback(VoiceRewriteTargetVerificationFailure.COMMIT_FAILED)
            }
        }
    }

    fun copyResult(gateway: VoiceRewriteReplacementGateway): Boolean {
        val current = _state.value
        val reviewedResult = resultText ?: return false
        if (
            current.phase != VoiceRewriteSessionPhase.RESULT ||
            !current.canCopyResult ||
            startRecordingJob?.isActive == true
        ) return false
        return gateway.copy(reviewedResult)
    }

    fun invalidate(reason: AudioSessionInvalidation) {
        val generationId = _state.value.generationId
        cancelResources(reason)
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.CANCELLED,
        )
    }

    fun reset() {
        cancelResources()
        _state.value = VoiceRewriteSessionState(generationId = _state.value.generationId)
    }

    private suspend fun runPreflight(generationId: Long) {
        val availability = availabilityPolicy.current()
        if (availability is CloudAiAvailability.Unavailable) {
            publishAvailabilityFailure(generationId, availability)
            return
        }
        val targetResolution = targetSource.resolve()
        if (!isCurrentPhase(generationId, VoiceRewriteSessionPhase.TARGETING)) return
        val target = when (targetResolution) {
            is VoiceRewriteTargetResolution.Resolved -> targetResolution.snapshot
            is VoiceRewriteTargetResolution.Rejected -> {
                publishFailure(
                    generationId = generationId,
                    failure = VoiceRewritePreflightFailure.TARGET_REJECTED,
                    targetFailure = targetResolution.reason,
                )
                return
            }
        }
        activeTarget = target
        val availabilityAfterTarget = availabilityPolicy.current()
        if (availabilityAfterTarget is CloudAiAvailability.Unavailable) {
            cancelResources()
            publishAvailabilityFailure(generationId, availabilityAfterTarget)
            return
        }
        if (audioSessionCoordinator.state.value != null) {
            publishFailure(generationId, VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY)
            return
        }
        if (!microphonePermission.isGranted()) {
            publishFailure(generationId, VoiceRewritePreflightFailure.MICROPHONE_PERMISSION)
            return
        }
        val transcriptionProvider = withContext(configurationDispatcher) {
            providerConfiguration.transcriptionProvider()
        }
        if (!isCurrentPhase(generationId, VoiceRewriteSessionPhase.TARGETING)) return
        if (!transcriptionProvider.isConfigured) {
            publishFailure(generationId, VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED)
            return
        }
        val rewriteProvider = withContext(configurationDispatcher) {
            providerConfiguration.rewriteProvider()
        }
        if (!isCurrentPhase(generationId, VoiceRewriteSessionPhase.TARGETING)) return
        if (!rewriteProvider.isConfigured) {
            publishFailure(generationId, VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED)
            return
        }

        if (disclosureStore.acknowledgedVersion() != disclosureVersion) {
            _state.value = VoiceRewriteSessionState(
                generationId = generationId,
                phase = VoiceRewriteSessionPhase.DISCLOSURE,
                targetScope = target.scope,
                targetCharacterCount = target.characterCount,
                disclosure = VoiceRewriteProviderDisclosure(
                    version = disclosureVersion,
                    audioProviderName = transcriptionProvider.displayName,
                    rewriteProviderName = rewriteProvider.displayName,
                ),
            )
            return
        }
        launchStartRecording(generationId)
    }

    private fun launchStartRecording(generationId: Long) {
        if (startRecordingJob?.isActive == true) return
        val current = _state.value
        if (current.generationId != generationId || activeTarget == null) return
        _state.value = current.copy(
            phase = VoiceRewriteSessionPhase.STARTING_RECORDING,
            disclosure = null,
            failure = null,
            targetFailure = null,
            pipelineFailure = null,
            canReplace = false,
            canCopyResult = false,
        )
        startRecordingJob = scope.launch { startRecording(generationId) }
    }

    private suspend fun startRecording(generationId: Long) {
        if (!validateRecordingStart(generationId)) return
        val transcriptionProvider = withContext(configurationDispatcher) {
            providerConfiguration.transcriptionProvider()
        }
        if (!validateRecordingStart(generationId)) return
        if (!transcriptionProvider.isConfigured) {
            publishFailure(generationId, VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED)
            return
        }
        val rewriteProvider = withContext(configurationDispatcher) {
            providerConfiguration.rewriteProvider()
        }
        if (!validateRecordingStart(generationId)) return
        if (!rewriteProvider.isConfigured) {
            publishFailure(generationId, VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED)
            return
        }
        val (audioSessionMode, audioRecorder) = withContext(configurationDispatcher) {
            audioSessionModeProvider() to audioRecorderProvider()
        }
        if (!validateRecordingStart(generationId)) return
        val startResult = startAudioSession(audioSessionMode, audioRecorder)
        when (startResult) {
            is AudioSessionStartResult.Started -> {
                if (!validateAcquiredRecordingStart(generationId, startResult.lease)) return
                recognizedInstruction = null
                resultText = null
                activeAudioLease = startResult.lease
                feedbackSessionId = startResult.lease.sessionId
                feedbackController.begin(startResult.lease.sessionId)
                publishRecordingState(generationId, VoiceRewriteSessionPhase.RECORDING, startResult.lease)
                scheduleAutoStop(generationId, startResult.lease)
            }
            is AudioSessionStartResult.Busy -> {
                publishFailure(generationId, VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY)
            }
            AudioSessionStartResult.RecorderUnavailable -> {
                publishFailure(generationId, VoiceRewritePreflightFailure.RECORDER_UNAVAILABLE)
            }
        }
    }

    /** Recorder startup can block in MediaRecorder.prepare/start, so acquisition never runs on the IME thread. */
    private suspend fun startAudioSession(
        audioSessionMode: AudioSessionMode,
        audioRecorder: AudioRecorder,
    ): AudioSessionStartResult {
        var acquiredLease: AudioSessionLease? = null
        return try {
            withContext(configurationDispatcher) {
                audioSessionCoordinator.tryStart(
                    owner = AudioSessionOwner.VOICE_REWRITE,
                    mode = audioSessionMode,
                    recorder = audioRecorder,
                ).also { result ->
                    acquiredLease = (result as? AudioSessionStartResult.Started)?.lease
                }
            }
        } catch (error: CancellationException) {
            acquiredLease?.cancel()
            throw error
        } catch (_: Exception) {
            acquiredLease?.cancel()
            AudioSessionStartResult.RecorderUnavailable
        }
    }

    private fun validateRecordingStart(generationId: Long): Boolean {
        if (
            !isCurrentPhase(generationId, VoiceRewriteSessionPhase.STARTING_RECORDING) ||
            activeAudioLease != null ||
            activeTarget == null
        ) return false
        val availability = availabilityPolicy.current()
        if (availability is CloudAiAvailability.Unavailable) {
            publishAvailabilityFailure(generationId, availability)
            return false
        }
        if (audioSessionCoordinator.state.value != null) {
            publishFailure(generationId, VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY)
            return false
        }
        if (!microphonePermission.isGranted()) {
            publishFailure(generationId, VoiceRewritePreflightFailure.MICROPHONE_PERMISSION)
            return false
        }
        return true
    }

    /** Revalidates mutable gates after the blocking recorder acquisition and disposes stale leases. */
    private fun validateAcquiredRecordingStart(generationId: Long, lease: AudioSessionLease): Boolean {
        if (
            !isCurrentPhase(generationId, VoiceRewriteSessionPhase.STARTING_RECORDING) ||
            activeTarget == null ||
            !lease.isCurrent
        ) {
            lease.cancel()
            return false
        }
        val availability = availabilityPolicy.current()
        if (availability is CloudAiAvailability.Unavailable) {
            lease.cancel()
            publishAvailabilityFailure(generationId, availability)
            return false
        }
        if (!microphonePermission.isGranted()) {
            lease.cancel()
            publishFailure(generationId, VoiceRewritePreflightFailure.MICROPHONE_PERMISSION)
            return false
        }
        return true
    }

    private fun publishAvailabilityFailure(
        generationId: Long,
        availability: CloudAiAvailability.Unavailable,
    ) {
        val failure = when (availability.reason) {
            CloudAiUnavailableReason.NO_ACTIVE_EDITOR -> VoiceRewritePreflightFailure.NO_ACTIVE_EDITOR
            CloudAiUnavailableReason.SECURE_FIELD -> VoiceRewritePreflightFailure.SECURE_FIELD
            CloudAiUnavailableReason.INCOGNITO -> VoiceRewritePreflightFailure.INCOGNITO
        }
        publishFailure(generationId, failure)
    }

    private fun publishFailure(
        generationId: Long,
        failure: VoiceRewritePreflightFailure,
        targetFailure: VoiceRewriteTargetFailure? = null,
    ) {
        if (!isCurrent(generationId)) return
        val target = activeTarget
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.WARNING,
            targetScope = target?.scope,
            targetCharacterCount = target?.characterCount,
            failure = failure,
            targetFailure = targetFailure,
        )
        publishVoiceError(failure.toVoiceActionErrorReason())
    }

    private fun cancelResources(
        reason: AudioSessionInvalidation = AudioSessionInvalidation.OWNER_CANCELLED,
        cancelFeedback: Boolean = true,
    ) {
        preflightJob?.cancel()
        preflightJob = null
        startRecordingJob?.cancel()
        startRecordingJob = null
        operationJob?.cancel()
        operationJob = null
        autoStopJob?.cancel()
        autoStopJob = null
        activeAudioLease?.cancel(reason)
        activeAudioLease = null
        if (cancelFeedback) cancelVoiceFeedback()
        activeTarget = null
        recognizedInstruction = null
        resultText = null
    }

    private suspend fun transcribeAndRewrite(generationId: Long, lease: AudioSessionLease) {
        val client = try {
            withContext(configurationDispatcher) { instructionTranscriptionClientProvider() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (client == null) {
            finishAudioLease(lease)
            publishPipelineError(generationId, VoiceRewritePipelineFailure.TRANSCRIPTION)
            return
        }
        val outcome = transcriptionOperation.stopAndTranscribe(lease, client)
        if (!isCurrentPhase(generationId, VoiceRewriteSessionPhase.TRANSCRIBING)) return
        finishAudioLease(lease)
        when (outcome) {
            is TranscriptionOutcome.Transcript -> {
                val instruction = outcome.text.trim()
                if (instruction.isEmpty()) {
                    publishPipelineError(generationId, VoiceRewritePipelineFailure.NO_SPEECH)
                    return
                }
                recognizedInstruction = instruction
                operationJob = null
                launchRewrite(generationId, instruction)
            }
            TranscriptionOutcome.Empty -> publishPipelineError(generationId, VoiceRewritePipelineFailure.NO_SPEECH)
            is TranscriptionOutcome.Failure -> publishPipelineError(
                generationId,
                when (outcome.reason) {
                    TranscriptionFailureReason.RECORDING -> VoiceRewritePipelineFailure.RECORDING
                    TranscriptionFailureReason.PROVIDER -> VoiceRewritePipelineFailure.TRANSCRIPTION
                },
            )
            TranscriptionOutcome.Cancelled -> {
                operationJob = null
                cancelVoiceFeedback()
                activeTarget = null
                recognizedInstruction = null
                resultText = null
                _state.value = VoiceRewriteSessionState(
                    generationId = generationId,
                    phase = VoiceRewriteSessionPhase.CANCELLED,
                )
            }
        }
    }

    private fun launchRewrite(generationId: Long, instruction: String): Boolean {
        val target = activeTarget ?: return false
        val availability = availabilityPolicy.current()
        if (availability is CloudAiAvailability.Unavailable) {
            cancelResources()
            publishAvailabilityFailure(generationId, availability)
            return false
        }
        operationJob?.cancel()
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.REWRITING,
            targetScope = target.scope,
            targetCharacterCount = target.characterCount,
            recognizedInstruction = instruction,
        )
        operationJob = scope.launch {
            val rewritten = try {
                withContext(configurationDispatcher) {
                    rewriteOperation.rewrite(target.sourceText, instruction)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (!isCurrentPhase(generationId, VoiceRewriteSessionPhase.REWRITING)) return@launch
            rewritten.fold(
                onSuccess = { rawResult ->
                    val trimmedResult = rawResult.trim()
                    if (trimmedResult.isEmpty()) {
                        publishPipelineError(generationId, VoiceRewritePipelineFailure.EMPTY_RESULT)
                    } else {
                        resultText = trimmedResult
                        _state.value = VoiceRewriteSessionState(
                            generationId = generationId,
                            phase = VoiceRewriteSessionPhase.RESULT,
                            targetScope = target.scope,
                            targetCharacterCount = target.characterCount,
                            recognizedInstruction = instruction,
                            resultText = trimmedResult,
                            canReplace = true,
                        )
                    }
                },
                onFailure = {
                    publishPipelineError(generationId, VoiceRewritePipelineFailure.REWRITE)
                },
            )
        }
        return true
    }

    private fun publishPipelineError(generationId: Long, failure: VoiceRewritePipelineFailure) {
        if (!isCurrent(generationId)) return
        val target = activeTarget
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.ERROR,
            targetScope = target?.scope,
            targetCharacterCount = target?.characterCount,
            pipelineFailure = failure,
            recognizedInstruction = recognizedInstruction,
        )
        publishVoiceError(failure.toVoiceActionErrorReason())
    }

    private fun publishRecordingState(
        generationId: Long,
        phase: VoiceRewriteSessionPhase,
        lease: AudioSessionLease,
    ) {
        val target = activeTarget ?: return
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = phase,
            targetScope = target.scope,
            targetCharacterCount = target.characterCount,
            audioSessionId = lease.sessionId,
        )
    }

    private fun publishCopyFallback(
        generationId: Long,
        failure: VoiceRewriteTargetVerificationFailure,
    ) {
        val target = activeTarget ?: return
        _state.value = VoiceRewriteSessionState(
            generationId = generationId,
            phase = VoiceRewriteSessionPhase.RESULT,
            targetScope = target.scope,
            targetCharacterCount = target.characterCount,
            recognizedInstruction = recognizedInstruction,
            resultText = resultText,
            canReplace = false,
            canCopyResult = true,
            replacementFailure = failure,
        )
        publishVoiceError(VoiceActionErrorReason.TARGET)
    }

    private fun publishVoiceError(reason: VoiceActionErrorReason) {
        val sessionId = feedbackSessionId
        val published = if (sessionId != null) {
            feedbackController.error(sessionId, reason)
        } else {
            false
        }
        if (!published) {
            feedbackSessionId = feedbackController.standaloneError(reason).takeUnless { it == 0L }
        }
    }

    private fun cancelVoiceFeedback() {
        feedbackSessionId?.let(feedbackController::cancel)
        feedbackSessionId = null
    }

    private fun VoiceRewritePreflightFailure.toVoiceActionErrorReason(): VoiceActionErrorReason = when (this) {
        VoiceRewritePreflightFailure.NO_ACTIVE_EDITOR,
        VoiceRewritePreflightFailure.SECURE_FIELD,
        VoiceRewritePreflightFailure.INCOGNITO,
        -> VoiceActionErrorReason.AI_UNAVAILABLE

        VoiceRewritePreflightFailure.TARGET_REJECTED -> VoiceActionErrorReason.TARGET
        VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY -> VoiceActionErrorReason.AUDIO_SESSION_BUSY
        VoiceRewritePreflightFailure.MICROPHONE_PERMISSION -> VoiceActionErrorReason.MICROPHONE_PERMISSION
        VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED,
        VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED,
        -> VoiceActionErrorReason.PROVIDER_CONFIGURATION

        VoiceRewritePreflightFailure.RECORDER_UNAVAILABLE -> VoiceActionErrorReason.RECORDER_UNAVAILABLE
    }

    private fun VoiceRewritePipelineFailure.toVoiceActionErrorReason(): VoiceActionErrorReason = when (this) {
        VoiceRewritePipelineFailure.NO_SPEECH -> VoiceActionErrorReason.EMPTY_AUDIO
        VoiceRewritePipelineFailure.RECORDING -> VoiceActionErrorReason.RECORDING
        VoiceRewritePipelineFailure.TRANSCRIPTION -> VoiceActionErrorReason.TRANSCRIPTION
        VoiceRewritePipelineFailure.REWRITE,
        VoiceRewritePipelineFailure.EMPTY_RESULT,
        -> VoiceActionErrorReason.REWRITE
    }

    private fun scheduleAutoStop(generationId: Long, lease: AudioSessionLease) {
        autoStopJob?.cancel()
        val remainingMs = (maxRecordingDurationMs - (lease.state?.elapsedMs(nowMs()) ?: 0L)).coerceAtLeast(0L)
        autoStopJob = scope.launch {
            delay(remainingMs)
            if (isCurrent(generationId) && activeAudioLease?.sessionId == lease.sessionId) {
                stopRecording()
            }
        }
    }

    private fun finishAudioLease(lease: AudioSessionLease) {
        if (!lease.complete()) lease.cancel()
        if (activeAudioLease?.sessionId == lease.sessionId) activeAudioLease = null
    }

    private fun isCurrentPhase(generationId: Long, phase: VoiceRewriteSessionPhase): Boolean =
        isCurrent(generationId) && _state.value.phase == phase

    private fun isCurrent(generationId: Long): Boolean = _state.value.generationId == generationId

    private fun VoiceRewriteSessionPhase.isActiveSessionPhase(): Boolean = when (this) {
        VoiceRewriteSessionPhase.TARGETING,
        VoiceRewriteSessionPhase.DISCLOSURE,
        VoiceRewriteSessionPhase.STARTING_RECORDING,
        VoiceRewriteSessionPhase.RECORDING,
        VoiceRewriteSessionPhase.PAUSED,
        VoiceRewriteSessionPhase.TRANSCRIBING,
        VoiceRewriteSessionPhase.REWRITING,
        VoiceRewriteSessionPhase.RESULT,
        -> true
        else -> false
    }
}
