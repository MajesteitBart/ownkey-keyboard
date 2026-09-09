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

/**
 * Maps the measured level history onto the stationary bars of the Ownkey waveform mark.
 *
 * Bars never move sideways. Each bar owns a fixed slot, a full height traced from the mark (the
 * centre-peaked, asymmetric silhouette the Windows overlay also uses), and a fixed lag into the
 * level history. A bar's height is its share of the mark scaled by the level measured that many
 * samples ago, so every bar is a real recent measurement rather than one value spread over a
 * profile, and because no two neighbouring bars share a lag they neither move in lockstep nor
 * scroll. Silence maps to zero on every bar, so a quiet room shows a flat row and the silhouette
 * exists only while someone is speaking.
 *
 * Nothing here needs a clock. The meter redraws only when the recorder's sampler publishes a
 * sample, which happens at 20 Hz while recording and never otherwise, so the earlier drains (a
 * looping sine animation on the mic key and an amplitude poll that ran while idle) cannot return
 * through this path. Reduced motion feeds a uniform history at 5 Hz, so the bars simply breathe
 * with the stationary level.
 */
object StationaryLevelBars {
    const val BarCount = 9
    const val MinBarCount = 5

    /** Full heights of the mark, tallest bar in the centre slot. */
    val Profile: List<Float> = listOf(0.30f, 0.52f, 0.42f, 0.74f, 1.00f, 0.46f, 0.66f, 0.56f, 0.32f)

    /**
     * How many samples back each slot reads. The centre reacts to the newest sample; the others
     * spread over the last 0.4 s in an order where neighbouring slots are always at least two
     * samples apart, so no two adjacent bars rise together.
     */
    val SampleLags: List<Int> = listOf(6, 2, 8, 4, 0, 3, 7, 1, 5)

    /**
     * Bar heights in `0..1` for [barCount] centred slots, from a history ordered oldest to newest
     * whose values sit in `baseline..1`. Silence (every sample at `baseline`) yields all zeros. A
     * history shorter than a slot's lag reads its oldest sample.
     */
    fun heights(levels: List<Float>, barCount: Int, baseline: Float): List<Float> {
        val count = barCount.coerceIn(MinBarCount, BarCount)
        val firstSlot = (BarCount - count) / 2
        val newest = levels.lastIndex
        val range = (1f - baseline).coerceAtLeast(0.001f)
        return List(count) { index ->
            val slot = firstSlot + index
            val level = when {
                levels.isEmpty() -> baseline
                else -> levels[(newest - SampleLags[slot]).coerceAtLeast(0)]
            }
            val drive = ((level - baseline) / range).coerceIn(0f, 1f)
            (Profile[slot] * drive).coerceIn(0f, 1f)
        }
    }
}
