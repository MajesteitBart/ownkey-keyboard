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
})
