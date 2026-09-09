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
import io.kotest.matchers.shouldBe
import kotlin.math.abs

private const val Baseline = 0.08f

/** The level a given drive in `0..1` corresponds to, mirroring the reducer's range. */
private fun levelFor(drive: Float): Float = Baseline + (1f - Baseline) * drive

private fun profileSlice(count: Int): List<Float> {
    val first = (StationaryLevelBars.BarCount - count) / 2
    return StationaryLevelBars.Profile.subList(first, first + count)
}

class StationaryLevelBarsTest : FunSpec({
    test("the profile and lag tables describe nine slots with the tallest bar in the centre") {
        StationaryLevelBars.Profile.size shouldBe StationaryLevelBars.BarCount
        StationaryLevelBars.SampleLags.size shouldBe StationaryLevelBars.BarCount
        StationaryLevelBars.Profile.indexOf(StationaryLevelBars.Profile.max()) shouldBe 4
        StationaryLevelBars.SampleLags[4] shouldBe 0
        // Every slot reads a different moment, and neighbours never read adjacent moments.
        StationaryLevelBars.SampleLags.distinct().size shouldBe StationaryLevelBars.BarCount
        StationaryLevelBars.SampleLags.zipWithNext { a, b -> abs(a - b) }.all { it > 1 } shouldBe true
    }

    test("silence is a flat row: every bar reads zero") {
        val silence = List(18) { Baseline }
        val heights = StationaryLevelBars.heights(silence, StationaryLevelBars.BarCount, Baseline)

        heights.size shouldBe StationaryLevelBars.BarCount
        heights.distinct() shouldContainExactly listOf(0f)
    }

    test("one loud sample lifts the centre bar first and leaves its neighbours flat") {
        val history = List(17) { Baseline } + 1f
        val heights = StationaryLevelBars.heights(history, StationaryLevelBars.BarCount, Baseline)

        heights[4] shouldBe (StationaryLevelBars.Profile[4] plusOrMinus 0.0001f)
        heights.filterIndexed { index, _ -> index != 4 }.distinct() shouldContainExactly listOf(0f)
    }

    test("each slot reads the sample its lag points at, so bars never move in lockstep") {
        // A history whose value encodes its age: newest = 1.0, one sample older = 0.9, and so on.
        val history = List(18) { index -> levelFor(index / 17f) }
        val heights = StationaryLevelBars.heights(history, StationaryLevelBars.BarCount, Baseline)

        StationaryLevelBars.SampleLags.forEachIndexed { slot, lag ->
            val drive = (17 - lag) / 17f
            heights[slot] shouldBe (StationaryLevelBars.Profile[slot] * drive plusOrMinus 0.0001f)
        }
    }

    test("a uniform reduced-motion history grows the whole mark with the level") {
        val quiet = StationaryLevelBars.heights(List(18) { levelFor(0.3f) }, StationaryLevelBars.BarCount, Baseline)
        val loud = StationaryLevelBars.heights(List(18) { levelFor(0.9f) }, StationaryLevelBars.BarCount, Baseline)

        quiet.indices.forEach { index -> loud[index] shouldBeGreaterThan quiet[index] }
        // Proportions of the silhouette are preserved at every level.
        (quiet[4] / quiet[0]) shouldBe ((loud[4] / loud[0]) plusOrMinus 0.0001f)
        loud[4] shouldBe (0.9f plusOrMinus 0.0001f)
    }

    test("a cramped row keeps the central slots and the peak stays centred") {
        val full = List(18) { 1f }
        val five = StationaryLevelBars.heights(full, 5, Baseline)
        val seven = StationaryLevelBars.heights(full, 7, Baseline)

        five shouldContainExactly profileSlice(5)
        seven shouldContainExactly profileSlice(7)
        five.indexOf(five.max()) shouldBe 2
        seven.indexOf(seven.max()) shouldBe 3
        // Counts outside the supported range clamp instead of failing.
        StationaryLevelBars.heights(full, 1, Baseline).size shouldBe StationaryLevelBars.MinBarCount
        StationaryLevelBars.heights(full, 40, Baseline).size shouldBe StationaryLevelBars.BarCount
    }

    test("a short or empty history never reads past its oldest sample and stays within bounds") {
        val short = StationaryLevelBars.heights(listOf(1f, 1f), StationaryLevelBars.BarCount, Baseline)
        val empty = StationaryLevelBars.heights(emptyList(), StationaryLevelBars.BarCount, Baseline)

        short shouldContainExactly StationaryLevelBars.Profile
        empty.distinct() shouldContainExactly listOf(0f)
        StationaryLevelBars.heights(List(18) { 5f }, StationaryLevelBars.BarCount, Baseline).all { it <= 1f } shouldBe true
    }
})
