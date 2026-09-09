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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class AudioLevelHistoryReducerTest : FunSpec({
    test("silence quiet speech and ordinary speech remain distinguishable in bounded history") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(24) { state = reducer.sample(state, 0.005f) }
        val silence = state.levels.average()
        repeat(24) { state = reducer.sample(state, 0.08f) }
        val quiet = state.levels.average()
        repeat(24) { state = reducer.sample(state, 0.45f) }
        val speech = state.levels.average()

        state.levels.size shouldBe 18
        quiet shouldBeGreaterThan silence
        speech shouldBeGreaterThan quiet
        state.levels.max() shouldBeLessThanOrEqual 1f
    }

    test("step pulse release and saturation are deterministic and clamped") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        state = reducer.sample(state, 1f)
        val attacked = state.smoothedLevel
        attacked shouldBeGreaterThan reducer.config.baseline
        repeat(8) { state = reducer.sample(state, 0f) }
        val released = state.smoothedLevel
        (released < attacked) shouldBe true
        (released >= reducer.config.baseline) shouldBe true
        repeat(40) { state = reducer.sample(state, 4f) }
        state.levels.all { it in reducer.config.baseline..1f } shouldBe true
    }

    test("pause settles to dim baseline and resume does not replay stale peaks") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(12) { state = reducer.sample(state, 0.8f) }
        state = reducer.pause(state)
        state.isPaused shouldBe true
        state.levels.distinct() shouldContainExactly listOf(reducer.config.baseline)
        reducer.sample(state, 1f) shouldBe state

        state = reducer.resume(state)
        state.isPaused shouldBe false
        state.levels.distinct() shouldContainExactly listOf(reducer.config.baseline)
    }

    test("reduced motion uses measured samples at five hertz without travelling history") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(3) { state = reducer.sample(state, 0.8f, reducedMotion = true) }
        state.levels.distinct() shouldContainExactly listOf(reducer.config.baseline)
        state = reducer.sample(state, 0.8f, reducedMotion = true)
        state.levels.first() shouldBe state.levels.last()
        state.levels.last() shouldBeGreaterThan reducer.config.baseline
    }

    test("mock or missing input never creates activity") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(40) { state = reducer.sample(state, 0f) }
        state.levels.distinct() shouldContainExactly listOf(reducer.config.baseline)
    }

    test("adjacent bars keep the contrast of the syllables they measured") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        // Alternating loud and quiet windows, the coarse shape of speech at the sampling rate.
        repeat(reducer.config.historySize) { index ->
            state = reducer.sample(state, if (index % 2 == 0) 0.7f else 0.02f)
        }

        val steps = state.levels.zipWithNext { previous, next -> kotlin.math.abs(next - previous) }
        // Every neighbouring pair differs clearly: the history is a waveform, not a smeared hump.
        steps.all { it > 0.3f } shouldBe true
        state.levels.max() shouldBeLessThanOrEqual 1f
    }

    test("the loudest recent input fills the meter whatever gain the microphone chain applies") {
        val reducer = AudioLevelHistoryReducer()
        // A phone whose gain control hands a shout over at a fifth of full scale.
        var quietDevice = reducer.initialState()
        repeat(12) { quietDevice = reducer.sample(quietDevice, 0.2f) }
        // A phone that reports the same shout at full scale.
        var loudDevice = reducer.initialState()
        repeat(12) { loudDevice = reducer.sample(loudDevice, 1f) }

        val target = reducer.config.baseline + reducer.config.peakTarget * (1f - reducer.config.baseline)
        quietDevice.smoothedLevel shouldBe (target plusOrMinus 0.01f)
        loudDevice.smoothedLevel shouldBe (target plusOrMinus 0.01f)
    }

    test("after a shout, ordinary speech keeps its proportion below it and silence goes flat") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(12) { state = reducer.sample(state, 1f) }
        val shout = state.smoothedLevel
        repeat(12) { state = reducer.sample(state, 0.3f) }
        val speech = state.smoothedLevel

        speech shouldBeGreaterThan reducer.config.baseline
        (speech < shout * 0.75f) shouldBe true
        // Silence is gated before scaling, so the rolling peak cannot lift room noise or nothing.
        repeat(40) { state = reducer.sample(state, 0.01f) }
        state.levels.all { it < reducer.config.baseline + 0.001f } shouldBe true
    }

    test("a whisper is not amplified into a shout: the peak never drops below its floor") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        repeat(24) { state = reducer.sample(state, 0.04f) }

        state.peak shouldBe reducer.config.peakFloor
        (state.smoothedLevel < 0.4f) shouldBe true
    }

    test("a loud syllable decays within a couple of bars instead of trailing across the meter") {
        val reducer = AudioLevelHistoryReducer()
        var state = reducer.initialState()
        state = reducer.sample(state, 0.9f)
        val peak = state.smoothedLevel
        repeat(2) { state = reducer.sample(state, 0.02f) }

        // After two quiet windows less than a quarter of the peak's rise above baseline remains.
        val remaining = state.smoothedLevel - reducer.config.baseline
        (remaining < (peak - reducer.config.baseline) * 0.25f) shouldBe true
    }
})
