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

import app.cash.turbine.test
import dev.patrickgold.florisboard.lib.util.BatteryTraceSink
import dev.patrickgold.florisboard.lib.util.OwnkeyBatteryTraceLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AudioSessionCoordinatorTest : FunSpec({
    test("one owner holds the recorder and state exposes only content-free session metadata") {
        var nowMs = 100L
        val coordinator = AudioSessionCoordinator(nowMs = { nowMs })
        val recorder = FakeAudioRecorder(amplitude = 0.4f)

        coordinator.state.test {
            awaitItem() shouldBe null
            val started = coordinator.tryStart(
                AudioSessionOwner.DICTATION,
                AudioSessionMode.CONFIGURED_PROVIDER,
                recorder,
            ) as AudioSessionStartResult.Started
            awaitItem().let { state ->
                state?.owner shouldBe AudioSessionOwner.DICTATION
                state?.mode shouldBe AudioSessionMode.CONFIGURED_PROVIDER
                state?.phase shouldBe AudioSessionPhase.RECORDING
                state?.measuredLevel shouldBe 0f
            }

            coordinator.tryStart(
                AudioSessionOwner.VOICE_REWRITE,
                AudioSessionMode.CONFIGURED_PROVIDER,
                FakeAudioRecorder(),
            ) shouldBe AudioSessionStartResult.Busy(
                owner = AudioSessionOwner.DICTATION,
                phase = AudioSessionPhase.RECORDING,
            )

            started.lease.sampleLevel() shouldBe 0.4f
            awaitItem()?.measuredLevel shouldBe 0.4f
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("pause resume elapsed time and stop are deterministic and idempotent") {
        var nowMs = 1_000L
        val recorder = FakeAudioRecorder()
        val coordinator = AudioSessionCoordinator(nowMs = { nowMs })
        val lease = (coordinator.tryStart(
            AudioSessionOwner.DICTATION,
            AudioSessionMode.MOCK,
            recorder,
        ) as AudioSessionStartResult.Started).lease

        nowMs = 1_400L
        lease.pause() shouldBe true
        lease.pause() shouldBe false
        nowMs = 1_900L
        lease.state?.elapsedMs(nowMs) shouldBe 400L
        lease.resume() shouldBe true
        lease.resume() shouldBe false
        nowMs = 2_200L
        lease.state?.elapsedMs(nowMs) shouldBe 700L

        (lease.stop() is AudioSessionStopResult.Stopped) shouldBe true
        lease.stop() shouldBe AudioSessionStopResult.AlreadyStopped
        recorder.stopCount shouldBe 1
        lease.complete() shouldBe true
        lease.complete() shouldBe false
        coordinator.state.value shouldBe null
    }

    test("cancel and every lifecycle invalidation release and clean the recorder once") {
        AudioSessionInvalidation.entries.forEach { reason ->
            val recorder = FakeAudioRecorder()
            val coordinator = AudioSessionCoordinator()
            val lease = (coordinator.tryStart(
                AudioSessionOwner.VOICE_REWRITE,
                AudioSessionMode.CONFIGURED_PROVIDER,
                recorder,
            ) as AudioSessionStartResult.Started).lease

            if (reason == AudioSessionInvalidation.OWNER_CANCELLED) {
                lease.cancel(reason) shouldBe true
                lease.cancel(reason) shouldBe false
            } else {
                coordinator.invalidate(reason) shouldBe true
                coordinator.invalidate(reason) shouldBe false
            }
            recorder.cancelCount shouldBe 1
            coordinator.state.value shouldBe null
        }
    }

    test("stale lease callbacks cannot change a newer generation") {
        val coordinator = AudioSessionCoordinator()
        val firstRecorder = FakeAudioRecorder()
        val first = (coordinator.tryStart(
            AudioSessionOwner.DICTATION,
            AudioSessionMode.MOCK,
            firstRecorder,
        ) as AudioSessionStartResult.Started).lease
        first.cancel() shouldBe true

        val secondRecorder = FakeAudioRecorder()
        val second = (coordinator.tryStart(
            AudioSessionOwner.VOICE_REWRITE,
            AudioSessionMode.CONFIGURED_PROVIDER,
            secondRecorder,
        ) as AudioSessionStartResult.Started).lease

        first.pause() shouldBe false
        first.complete() shouldBe false
        first.cancel() shouldBe false
        coordinator.state.value?.sessionId shouldBe second.sessionId
        coordinator.state.value?.owner shouldBe AudioSessionOwner.VOICE_REWRITE
        secondRecorder.cancelCount shouldBe 0
    }

    test("recording processing completion and cancellation emit balanced content-free spans") {
        val trace = RecordingAudioTraceSink()
        val coordinator = AudioSessionCoordinator(traceSink = trace)
        val completed = (coordinator.tryStart(
            AudioSessionOwner.DICTATION,
            AudioSessionMode.CONFIGURED_PROVIDER,
            FakeAudioRecorder(),
        ) as AudioSessionStartResult.Started).lease

        completed.stop()
        completed.complete() shouldBe true

        val cancelled = (coordinator.tryStart(
            AudioSessionOwner.VOICE_REWRITE,
            AudioSessionMode.CONFIGURED_PROVIDER,
            FakeAudioRecorder(),
        ) as AudioSessionStartResult.Started).lease
        cancelled.cancel() shouldBe true

        trace.events shouldContainExactly listOf(
            "begin:${OwnkeyBatteryTraceLabels.VoiceDictationRecording}:1",
            "end:${OwnkeyBatteryTraceLabels.VoiceDictationRecording}:1",
            "begin:${OwnkeyBatteryTraceLabels.VoiceDictationProcessing}:1",
            "end:${OwnkeyBatteryTraceLabels.VoiceDictationProcessing}:1",
            "begin:${OwnkeyBatteryTraceLabels.VoiceRewriteRecording}:2",
            "end:${OwnkeyBatteryTraceLabels.VoiceRewriteRecording}:2",
        )
    }
})

private class RecordingAudioTraceSink : BatteryTraceSink {
    val events = mutableListOf<String>()

    override fun beginSection(label: String) = Unit
    override fun endSection() = Unit

    override fun beginAsyncSection(label: String, cookie: Int) {
        events += "begin:$label:$cookie"
    }

    override fun endAsyncSection(label: String, cookie: Int) {
        events += "end:$label:$cookie"
    }
}

private class FakeAudioRecorder(
    private val amplitude: Float = 0f,
) : AudioRecorder {
    private var active = false
    private var paused = false
    var stopCount = 0
        private set
    var cancelCount = 0
        private set

    override fun start(): Boolean {
        if (active) return false
        active = true
        return true
    }

    override fun pause(): Boolean {
        if (!active || paused) return false
        paused = true
        return true
    }

    override fun resume(): Boolean {
        if (!active || !paused) return false
        paused = false
        return true
    }

    override fun stopAndRead(): Result<AudioRecording> {
        if (!active) return Result.failure(IllegalStateException("not active"))
        active = false
        paused = false
        stopCount += 1
        return Result.success(
            AudioRecording(
                bytes = byteArrayOf(1),
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = 500,
                mimeType = "audio/test",
                fileName = "recording.test",
            ),
        )
    }

    override fun cancel() {
        if (!active) return
        active = false
        paused = false
        cancelCount += 1
    }

    override fun currentAmplitude(): Float = amplitude
}
