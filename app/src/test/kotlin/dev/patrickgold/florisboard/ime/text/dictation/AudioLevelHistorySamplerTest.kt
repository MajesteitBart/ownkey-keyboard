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

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val Baseline = AudioLevelHistoryConfig().baseline

/**
 * The sampler owns an unbounded collector and sampling loop, so every case drives a dedicated
 * [TestScope] on the shared scheduler and cancels it before the test body ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private inline fun withSampler(
    scheduler: TestCoroutineScheduler,
    block: (AudioSessionCoordinator, AudioLevelHistorySampler) -> Unit,
) {
    val samplerScope = TestScope(scheduler)
    val coordinator = AudioSessionCoordinator()
    val sampler = AudioLevelHistorySampler(coordinator = coordinator, scope = samplerScope)
    try {
        block(coordinator, sampler)
    } finally {
        samplerScope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AudioLevelHistorySamplerTest : FunSpec({
    test("only measured recorder amplitude reaches the waveform, for either mode") {
        AudioSessionOwner.entries.forEach { owner ->
            runTest {
                withSampler(testScheduler) { coordinator, sampler ->
                    runCurrent()
                    sampler.state.value.levels.distinct() shouldContainExactly listOf(Baseline)

                    val recorder = FakeLevelRecorder(amplitude = 0.6f)
                    coordinator.tryStart(owner, AudioSessionMode.CONFIGURED_PROVIDER, recorder)
                    runCurrent()
                    advanceTimeBy(500L)
                    runCurrent()

                    sampler.state.value.smoothedLevel shouldBeGreaterThan Baseline
                    (recorder.sampleCount > 0) shouldBe true
                }
            }
        }
    }

    test("a mock recorder with no measured input never fakes activity") {
        runTest {
            withSampler(testScheduler) { coordinator, sampler ->
                coordinator.tryStart(
                    AudioSessionOwner.DICTATION,
                    AudioSessionMode.MOCK,
                    FakeLevelRecorder(amplitude = 0f),
                )
                runCurrent()
                advanceTimeBy(1_000L)
                runCurrent()

                sampler.state.value.levels.distinct() shouldContainExactly listOf(Baseline)
            }
        }
    }

    test("measured-level publications do not restart the twenty hertz poller") {
        runTest {
            withSampler(testScheduler) { coordinator, _ ->
                val recorder = FakeLevelRecorder(amplitude = 0.6f)
                coordinator.tryStart(
                    AudioSessionOwner.DICTATION,
                    AudioSessionMode.CONFIGURED_PROVIDER,
                    recorder,
                )
                runCurrent()
                advanceTimeBy(500L)
                runCurrent()

                (recorder.sampleCount in 10..11) shouldBe true
            }
        }
    }

    test("pausing stops sampling and settles the bars to the dim baseline") {
        runTest {
            withSampler(testScheduler) { coordinator, sampler ->
                val recorder = FakeLevelRecorder(amplitude = 0.8f)
                val lease = (
                    coordinator.tryStart(
                        AudioSessionOwner.VOICE_REWRITE,
                        AudioSessionMode.CONFIGURED_PROVIDER,
                        recorder,
                    ) as AudioSessionStartResult.Started
                    ).lease
                runCurrent()
                advanceTimeBy(500L)
                runCurrent()
                sampler.state.value.smoothedLevel shouldBeGreaterThan Baseline

                lease.pause() shouldBe true
                runCurrent()
                sampler.state.value.isPaused shouldBe true
                sampler.state.value.levels.distinct() shouldContainExactly listOf(Baseline)

                val pausedSampleCount = recorder.sampleCount
                advanceTimeBy(1_000L)
                runCurrent()
                recorder.sampleCount shouldBe pausedSampleCount
            }
        }
    }

    test("processing and session teardown reset the history so no level survives the recorder") {
        runTest {
            withSampler(testScheduler) { coordinator, sampler ->
                val lease = (
                    coordinator.tryStart(
                        AudioSessionOwner.DICTATION,
                        AudioSessionMode.CONFIGURED_PROVIDER,
                        FakeLevelRecorder(amplitude = 0.9f),
                    ) as AudioSessionStartResult.Started
                    ).lease
                runCurrent()
                advanceTimeBy(500L)
                runCurrent()

                lease.stop()
                runCurrent()
                sampler.state.value.levels.distinct() shouldContainExactly listOf(Baseline)

                lease.complete() shouldBe true
                runCurrent()
                sampler.state.value.levels.distinct() shouldContainExactly listOf(Baseline)
            }
        }
    }

    test("a cancelled session stops its sampler before a newer session starts") {
        runTest {
            withSampler(testScheduler) { coordinator, sampler ->
                val staleRecorder = FakeLevelRecorder(amplitude = 0.7f)
                val stale = (
                    coordinator.tryStart(
                        AudioSessionOwner.DICTATION,
                        AudioSessionMode.CONFIGURED_PROVIDER,
                        staleRecorder,
                    ) as AudioSessionStartResult.Started
                    ).lease
                runCurrent()
                advanceTimeBy(300L)
                runCurrent()
                stale.cancel()
                runCurrent()
                val staleSampleCount = staleRecorder.sampleCount

                coordinator.tryStart(
                    AudioSessionOwner.VOICE_REWRITE,
                    AudioSessionMode.CONFIGURED_PROVIDER,
                    FakeLevelRecorder(amplitude = 0.5f),
                )
                runCurrent()
                advanceTimeBy(500L)
                runCurrent()

                staleRecorder.sampleCount shouldBe staleSampleCount
                sampler.state.value.smoothedLevel shouldBeGreaterThan Baseline
            }
        }
    }
})

private class FakeLevelRecorder(private val amplitude: Float) : AudioRecorder {
    private var active = false
    private var paused = false
    var sampleCount = 0
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
        active = false
        return Result.success(
            AudioRecording(
                bytes = byteArrayOf(1),
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = 100,
                mimeType = "audio/test",
                fileName = "recording.test",
            ),
        )
    }

    override fun cancel() {
        active = false
        paused = false
    }

    override fun currentAmplitude(): Float {
        sampleCount += 1
        return amplitude
    }
}
