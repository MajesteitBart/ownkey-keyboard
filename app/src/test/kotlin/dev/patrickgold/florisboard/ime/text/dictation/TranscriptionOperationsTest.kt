/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionOperationsTest : FunSpec({
    test("transcription-only returns trimmed text without any editor mutation") {
        runTest {
            val recorder = OperationRecorder()
            val coordinator = AudioSessionCoordinator()
            val lease = (coordinator.tryStart(
                AudioSessionOwner.VOICE_REWRITE,
                AudioSessionMode.CONFIGURED_PROVIDER,
                recorder,
            ) as AudioSessionStartResult.Started).lease
            var editorCommitCount = 0
            val ordinaryCommit = OrdinaryDictationCommitOperation {
                editorCommitCount += 1
                true
            }
            val operation = TranscriptionOnlyOperation(UnconfinedTestDispatcher(testScheduler))

            val outcome = operation.stopAndTranscribe(
                lease,
                StaticTranscriptionClient(Result.success("  make this shorter  ")),
            )

            outcome shouldBe TranscriptionOutcome.Transcript("make this shorter")
            editorCommitCount shouldBe 0
            ordinaryCommit.commit(outcome) shouldBe OrdinaryDictationCommitResult.Committed
            editorCommitCount shouldBe 1
        }
    }

    test("empty failure and cancellation are typed and never committed") {
        runTest {
            val cases = listOf(
                Result.success("   ") to TranscriptionOutcome.Empty,
                Result.failure<String>(IllegalStateException("provider unavailable")) to
                    TranscriptionOutcome.Failure(TranscriptionFailureReason.PROVIDER),
                Result.failure<String>(CancellationException("cancelled")) to TranscriptionOutcome.Cancelled,
            )
            cases.forEach { (clientResult, expected) ->
                val coordinator = AudioSessionCoordinator()
                val lease = (coordinator.tryStart(
                    AudioSessionOwner.VOICE_REWRITE,
                    AudioSessionMode.CONFIGURED_PROVIDER,
                    OperationRecorder(),
                ) as AudioSessionStartResult.Started).lease
                var commits = 0
                val operation = TranscriptionOnlyOperation(UnconfinedTestDispatcher(testScheduler))
                val outcome = operation.stopAndTranscribe(lease, StaticTranscriptionClient(clientResult))

                outcome shouldBe expected
                OrdinaryDictationCommitOperation {
                    commits += 1
                    true
                }.commit(outcome) shouldBe OrdinaryDictationCommitResult.NotCommitted(outcome)
                commits shouldBe 0
            }
        }
    }

    test("cancellation during stop cleans the active lease") {
        runTest {
            val coordinator = AudioSessionCoordinator()
            val lease = (coordinator.tryStart(
                AudioSessionOwner.VOICE_REWRITE,
                AudioSessionMode.CONFIGURED_PROVIDER,
                OperationRecorder(stopResult = Result.failure(CancellationException("stop cancelled"))),
            ) as AudioSessionStartResult.Started).lease

            TranscriptionOnlyOperation(UnconfinedTestDispatcher(testScheduler)).stopAndTranscribe(
                lease,
                StaticTranscriptionClient(Result.success("unused")),
            ) shouldBe TranscriptionOutcome.Cancelled
            lease.isCurrent shouldBe false
            coordinator.state.value shouldBe null
        }
    }

    test("ordinary commit failure is explicit and still invokes the editor only once") {
        var commitCount = 0
        val result = OrdinaryDictationCommitOperation {
            commitCount += 1
            false
        }.commit(TranscriptionOutcome.Transcript("dictated text"))

        result shouldBe OrdinaryDictationCommitResult.CommitFailed
        commitCount shouldBe 1
    }
})

private class StaticTranscriptionClient(
    private val result: Result<String>,
) : TranscriptionClient {
    override suspend fun transcribe(recording: AudioRecording): Result<String> = result
}

private class OperationRecorder(
    private val stopResult: Result<AudioRecording> = Result.success(testRecording()),
) : AudioRecorder {
    private var active = false

    override fun start(): Boolean {
        active = true
        return true
    }

    override fun pause(): Boolean = active
    override fun resume(): Boolean = active

    override fun stopAndRead(): Result<AudioRecording> {
        active = false
        return stopResult
    }

    override fun cancel() {
        active = false
    }

    override fun currentAmplitude(): Float = 0f
}

private fun testRecording() = AudioRecording(
    bytes = byteArrayOf(1, 2, 3),
    sampleRateHz = 16_000,
    channelCount = 1,
    durationMs = 500,
    mimeType = "audio/test",
    fileName = "recording.test",
)
