/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecorder
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOnlyOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewritePipelineTest : FunSpec({
    test("pause resume elapsed time stop transcription and rewrite happen exactly once") {
        runTest {
            val fixture = pipelineFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING

            advanceTimeBy(500)
            fixture.manager.recordingElapsedMs() shouldBe 500L
            fixture.manager.pauseRecording() shouldBe true
            advanceTimeBy(1_000)
            fixture.manager.recordingElapsedMs() shouldBe 500L
            fixture.manager.resumeRecording() shouldBe true
            advanceTimeBy(500)
            fixture.manager.recordingElapsedMs() shouldBe 1_000L

            fixture.manager.stopRecording()
            fixture.manager.stopRecording()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT
            fixture.manager.state.value.recognizedInstruction shouldBe "make it concise"
            fixture.manager.state.value.resultText shouldBe "Concise result"
            fixture.recorder.stopCount shouldBe 1
            fixture.transcription.calls shouldBe 1
            fixture.rewrite.requests shouldContainExactly listOf("source text" to "make it concise")
        }
    }

    test("thirty-second cap auto-stops once and paused time does not consume the cap") {
        runTest {
            val fixture = pipelineFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                maxRecordingDurationMs = 1_000L,
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            advanceTimeBy(600)
            fixture.manager.pauseRecording()
            advanceTimeBy(2_000)
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.PAUSED
            fixture.manager.resumeRecording()
            advanceTimeBy(399)
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING
            advanceTimeBy(1)
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT
            fixture.recorder.stopCount shouldBe 1
        }
    }

    test("empty and failed transcription never dispatch rewrite") {
        runTest {
            listOf(
                Result.success("   ") to VoiceRewritePipelineFailure.NO_SPEECH,
                Result.failure<String>(IllegalStateException("provider failed")) to
                    VoiceRewritePipelineFailure.TRANSCRIPTION,
            ).forEach { (transcriptionResult, expectedFailure) ->
                val transcription = FakePipelineTranscriptionClient(ArrayDeque(listOf(transcriptionResult)))
                val fixture = pipelineFixture(
                    scope = backgroundScope,
                    transcription = transcription,
                    transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
                )
                fixture.manager.begin()
                runCurrent()
                fixture.manager.stopRecording()
                runCurrent()

                fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.ERROR
                fixture.manager.state.value.pipelineFailure shouldBe expectedFailure
                fixture.rewrite.requests shouldContainExactly emptyList()
            }
        }
    }

    test("rewrite failure retains instruction for retry without reopening audio") {
        runTest {
            val rewrite = FakePipelineRewriteOperation(
                ArrayDeque(
                    listOf(
                        Result.failure(IllegalStateException("rewrite failed")),
                        Result.success("Retried result"),
                    ),
                ),
            )
            val fixture = pipelineFixture(
                scope = backgroundScope,
                rewrite = rewrite,
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.stopRecording()
            runCurrent()
            fixture.manager.state.value.pipelineFailure shouldBe VoiceRewritePipelineFailure.REWRITE
            fixture.manager.state.value.recognizedInstruction shouldBe "make it concise"

            fixture.manager.tryAgain()
            runCurrent()
            fixture.manager.state.value.resultText shouldBe "Retried result"
            fixture.recorder.startCount shouldBe 1
            fixture.transcription.calls shouldBe 1
            rewrite.requests.size shouldBe 2
        }
    }

    test("record again keeps the target but replaces transcript and result") {
        runTest {
            val transcription = FakePipelineTranscriptionClient(
                ArrayDeque(listOf(Result.success("first instruction"), Result.success("second instruction"))),
            )
            val rewrite = FakePipelineRewriteOperation(
                ArrayDeque(listOf(Result.success("first result"), Result.success("second result"))),
            )
            val fixture = pipelineFixture(
                scope = backgroundScope,
                transcription = transcription,
                rewrite = rewrite,
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.stopRecording()
            runCurrent()
            fixture.manager.state.value.resultText shouldBe "first result"

            fixture.manager.recordInstructionAgain()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.STARTING_RECORDING
            fixture.manager.tryAgain()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING
            rewrite.requests shouldContainExactly listOf("source text" to "first instruction")
            fixture.manager.stopRecording()
            runCurrent()
            fixture.manager.state.value.recognizedInstruction shouldBe "second instruction"
            fixture.manager.state.value.resultText shouldBe "second result"
            fixture.recorder.startCount shouldBe 2
            rewrite.requests shouldContainExactly listOf(
                "source text" to "first instruction",
                "source text" to "second instruction",
            )
        }
    }

    test("record again revalidates microphone permission and both provider configurations") {
        runTest {
            listOf(
                Triple("microphone", VoiceRewritePreflightFailure.MICROPHONE_PERMISSION, 0),
                Triple(
                    "transcription provider",
                    VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED,
                    1,
                ),
                Triple("rewrite provider", VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED, 2),
            ).forEach { (_, expectedFailure, revokedInput) ->
                var permissionGranted = true
                var transcriptionConfigured = true
                var rewriteConfigured = true
                val fixture = pipelineFixture(
                    scope = backgroundScope,
                    permissionGranted = { permissionGranted },
                    transcriptionConfigured = { transcriptionConfigured },
                    rewriteConfigured = { rewriteConfigured },
                    transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
                )
                fixture.manager.begin()
                runCurrent()
                fixture.manager.stopRecording()
                runCurrent()
                fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT

                when (revokedInput) {
                    0 -> permissionGranted = false
                    1 -> transcriptionConfigured = false
                    2 -> rewriteConfigured = false
                }
                fixture.manager.recordInstructionAgain()
                runCurrent()

                fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.WARNING
                fixture.manager.state.value.failure shouldBe expectedFailure
                fixture.recorder.startCount shouldBe 1
            }
        }
    }

    test("cancelled transcription leaves the session terminal instead of transcribing forever") {
        runTest {
            val fixture = pipelineFixture(
                scope = backgroundScope,
                transcription = FakePipelineTranscriptionClient(
                    ArrayDeque(listOf(Result.failure(CancellationException("cancelled")))),
                ),
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.stopRecording()
            runCurrent()

            fixture.manager.state.value shouldBe VoiceRewriteSessionState(
                generationId = 1,
                phase = VoiceRewriteSessionPhase.CANCELLED,
            )
        }
    }

    test("lifecycle cancellation prevents a late non-cooperative provider result from publishing") {
        runTest {
            val lateResult = CompletableDeferred<Result<String>>()
            val rewrite = FakePipelineRewriteOperation(ArrayDeque()) { _, _ ->
                withContext(NonCancellable) { lateResult.await() }
            }
            val fixture = pipelineFixture(
                scope = backgroundScope,
                rewrite = rewrite,
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.stopRecording()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.REWRITING

            fixture.manager.invalidate(AudioSessionInvalidation.KEYBOARD_HIDE)
            lateResult.complete(Result.success("must not publish"))
            runCurrent()
            fixture.manager.state.value shouldBe VoiceRewriteSessionState(
                generationId = 1,
                phase = VoiceRewriteSessionPhase.CANCELLED,
            )
        }
    }

    test("fixed policy owns output language and operation has no locale or subtype input") {
        VoiceRewritePolicy.FixedInstruction shouldContain "source text's language"
        VoiceRewritePolicy.FixedInstruction shouldContain "explicitly requests another language"
        VoiceRewritePolicy.FixedInstruction shouldContain "mixed-language source text"

        runTest {
            val fixture = pipelineFixture(
                scope = backgroundScope,
                transcriptionOperation = TranscriptionOnlyOperation(StandardTestDispatcher(testScheduler)),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.stopRecording()
            runCurrent()
            fixture.rewrite.requests.single() shouldBe ("source text" to "make it concise")
            fixture.manager.state.value.toString().contains("source text") shouldBe false
        }
    }
})

private data class PipelineFixture(
    val manager: VoiceRewriteSessionManager,
    val recorder: FakePipelineRecorder,
    val transcription: FakePipelineTranscriptionClient,
    val rewrite: FakePipelineRewriteOperation,
)

private fun pipelineFixture(
    scope: kotlinx.coroutines.CoroutineScope,
    recorder: FakePipelineRecorder = FakePipelineRecorder(),
    transcription: FakePipelineTranscriptionClient = FakePipelineTranscriptionClient(
        ArrayDeque(listOf(Result.success("make it concise"))),
    ),
    rewrite: FakePipelineRewriteOperation = FakePipelineRewriteOperation(
        ArrayDeque(listOf(Result.success("Concise result"))),
    ),
    transcriptionOperation: TranscriptionOnlyOperation = TranscriptionOnlyOperation(),
    maxRecordingDurationMs: Long = 30_000L,
    nowMs: () -> Long = { System.currentTimeMillis() },
    permissionGranted: () -> Boolean = { true },
    transcriptionConfigured: () -> Boolean = { true },
    rewriteConfigured: () -> Boolean = { true },
): PipelineFixture {
    val policy = CloudAiAvailabilityPolicy(
        scope,
        MutableStateFlow(CloudAiEditorSession(7L, isIncognito = false, isSecureField = false)),
    )
    val manager = VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = policy,
        targetSource = VoiceRewriteTargetSource {
            VoiceRewriteTargetResolution.Resolved(pipelineSnapshot())
        },
        audioSessionCoordinator = AudioSessionCoordinator(nowMs),
        audioRecorderProvider = { recorder },
        audioSessionModeProvider = { AudioSessionMode.CONFIGURED_PROVIDER },
        microphonePermission = VoiceRewriteMicrophonePermission { permissionGranted() },
        providerConfiguration = object : VoiceRewriteProviderConfigurationSource {
            override fun transcriptionProvider() =
                VoiceRewriteProviderConfiguration(transcriptionConfigured(), "Mistral")

            override fun rewriteProvider() =
                VoiceRewriteProviderConfiguration(rewriteConfigured(), "OpenAI")
        },
        configurationDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        disclosureStore = object : VoiceRewriteDisclosureStore {
            override fun acknowledgedVersion(): Int = 1
            override fun acknowledge(version: Int) = Unit
        },
        disclosureVersion = 1,
        transcriptionOperation = transcriptionOperation,
        instructionTranscriptionClientProvider = { transcription },
        rewriteOperation = rewrite,
        maxRecordingDurationMs = maxRecordingDurationMs,
        nowMs = nowMs,
    )
    return PipelineFixture(manager, recorder, transcription, rewrite)
}

private class FakePipelineRecorder : AudioRecorder {
    var startCount = 0
    var stopCount = 0
    var cancelCount = 0
    private var recording = false

    override fun start(): Boolean {
        if (recording) return false
        recording = true
        startCount += 1
        return true
    }

    override fun pause(): Boolean = recording
    override fun resume(): Boolean = recording

    override fun stopAndRead(): Result<AudioRecording> {
        if (!recording) return Result.failure(IllegalStateException("not recording"))
        recording = false
        stopCount += 1
        return Result.success(
            AudioRecording(
                bytes = byteArrayOf(1, 2, 3),
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = 1_000,
                mimeType = "audio/test",
                fileName = "temporary.test",
            ),
        )
    }

    override fun cancel() {
        if (recording) cancelCount += 1
        recording = false
    }

    override fun currentAmplitude(): Float = 0f
}

private class FakePipelineTranscriptionClient(
    private val responses: ArrayDeque<Result<String>>,
) : TranscriptionClient {
    var calls = 0

    override suspend fun transcribe(recording: AudioRecording): Result<String> {
        calls += 1
        return responses.removeFirst()
    }
}

private class FakePipelineRewriteOperation(
    private val responses: ArrayDeque<Result<String>>,
    private val handler: (suspend (String, String) -> Result<String>)? = null,
) : VoiceRewriteOperation {
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
        requests += sourceText to instruction
        return handler?.invoke(sourceText, instruction) ?: responses.removeFirst()
    }
}

private fun pipelineSnapshot() = VoiceRewriteTargetSnapshot(
    editorSessionId = 7L,
    hostPackage = "test.editor",
    fieldId = 42,
    scope = VoiceRewriteTargetScope.SELECTION,
    range = EditorRange(0, 11),
    sourceText = "source text",
    characterCount = 11,
    integrityHash = "integrity",
)
