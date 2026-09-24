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

package dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark

import java.util.Random
import kotlin.math.floor

/**
 * Simulates touch typing on a phone QWERTY layout: each intended letter becomes a tap drawn from a 2D Gaussian
 * around its key center, and the typed letter is whichever key rectangle the tap lands in. On top of that come
 * missed taps, double taps and swapped letters at fixed per-letter rates.
 *
 * The geometry follows the standard Android layout (row offsets of 0, 0.5 and 1.5 key widths, keys 1.4 times as
 * tall as wide). It deliberately differs from any scorer's cost model, so benchmark gates are not graded by the
 * scorer's own assumptions. All coordinates are in key widths.
 */
internal class TapNoiseTypoGenerator(
    seed: Long,
    private val sigmaX: Double = 0.20,
    private val sigmaY: Double = 0.25,
    private val biasY: Double = 0.08,
    private val omissionRate: Double = 0.008,
    private val doubleTapRate: Double = 0.005,
    private val transpositionRate: Double = 0.008,
) {
    private val random = Random(seed)

    data class Generated(val pairs: List<TypoPair>, val realWordCount: Int, val total: Int) {
        val realWordRate: Double get() = if (total == 0) 0.0 else realWordCount.toDouble() / total
    }

    /**
     * Types [word] once. Returns the typed text and one tap per typed character.
     */
    fun type(word: String): Pair<String, List<Tap>> {
        val taps = mutableListOf<Tap>()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            val roll = random.nextDouble()
            when {
                roll < omissionRate -> Unit
                roll < omissionRate + transpositionRate && i + 1 < word.length && word[i + 1] != ch -> {
                    tap(word[i + 1])?.let { taps.add(it) }
                    tap(ch)?.let { taps.add(it) }
                    i++
                }
                roll < omissionRate + transpositionRate + doubleTapRate -> {
                    tap(ch)?.let { taps.add(it) }
                    tap(ch)?.let { taps.add(it) }
                }
                else -> tap(ch)?.let { taps.add(it) }
            }
            i++
        }
        return taps.joinToString("") { it.char.toString() } to taps
    }

    private fun tap(intended: Char): Tap? {
        val center = KeyCenters[intended] ?: return Tap(intended, Double.NaN, Double.NaN)
        val x = center.first + random.nextGaussian() * sigmaX
        val y = center.second + biasY + random.nextGaussian() * sigmaY
        val hit = keyAt(x, y) ?: return null
        return Tap(hit, x, y)
    }

    fun buildTypos(words: Map<String, Int>, sample: List<String>, wanted: Int): Generated {
        val pairs = mutableListOf<TypoPair>()
        var realWord = 0
        var total = 0
        for (word in sample) {
            if (pairs.size >= wanted) break
            val (typed, taps) = type(word)
            if (typed == word || typed.isEmpty()) continue
            total++
            if (words.containsKey(typed)) {
                realWord++
                continue
            }
            pairs.add(TypoPair(typed, word, taps))
        }
        return Generated(pairs, realWord, total)
    }

    /** Usage-weighted (by frequency) or vocabulary-uniform (ranks 100 to 10,000) word sample. */
    fun sampleWords(words: Map<String, Int>, n: Int, tokenWeighted: Boolean, minLen: Int = 3): List<String> {
        val ranked = HarnessTypoGenerator.rankedWords(words, 10000, minLen)
        val out = ArrayList<String>(n)
        if (tokenWeighted) {
            val cumulative = DoubleArray(ranked.size)
            var sum = 0.0
            ranked.forEachIndexed { index, (_, f) ->
                sum += f
                cumulative[index] = sum
            }
            repeat(n) {
                val x = random.nextDouble() * sum
                var index = cumulative.binarySearch(x)
                if (index < 0) index = -index - 1
                out.add(ranked[index.coerceAtMost(ranked.lastIndex)].first)
            }
        } else {
            repeat(n) {
                out.add(ranked[100 + random.nextInt(ranked.size - 100)].first)
            }
        }
        return out
    }

    companion object {
        const val RowHeight = 1.4
        private val Rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val RowOffsets = listOf(0.0, 0.5, 1.5)

        val KeyCenters: Map<Char, Pair<Double, Double>> = buildMap {
            Rows.forEachIndexed { row, letters ->
                letters.forEachIndexed { index, ch ->
                    put(ch, (RowOffsets[row] + index + 0.5) to (row + 0.5) * RowHeight)
                }
            }
        }

        /** The letter key under a tap, or null when the tap misses every letter key. */
        fun keyAt(x: Double, y: Double): Char? {
            val row = floor(y / RowHeight).toInt().coerceIn(0, Rows.lastIndex)
            val letters = Rows[row]
            val index = floor(x - RowOffsets[row]).toInt()
            return when {
                index in letters.indices -> letters[index]
                // The top two rows span the full width, so edge taps still hit the outer key.
                row < 2 -> letters[index.coerceIn(0, letters.lastIndex)]
                // Bottom row: shift and backspace sit left and right of the letters.
                else -> null
            }
        }
    }
}
