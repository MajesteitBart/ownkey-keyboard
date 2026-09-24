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

import kotlin.math.floor
import kotlin.math.hypot

/**
 * Verbatim port of the typo generator in `.project/projects/autocorrect-engine/research/baseline-harness/bench.js`
 * and `maxslider.js`, including its random number sequence, so the Kotlin benchmark reproduces the 2026-09-24
 * baseline. Do not change the order of random draws.
 *
 * The typos use QWERTY key adjacency, the same model the reference noisy-channel scorer used, so results on these
 * sets are optimistic for any scorer with an adjacency-based cost model. Use [TapNoiseTypoGenerator] for gates.
 */
internal class HarnessTypoGenerator(seed: Long) {
    private var state = seed

    fun rnd(): Double {
        state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return state.toDouble() / 4294967296.0
    }

    data class Generated(val pairs: List<TypoPair>, val realWordCount: Int, val total: Int) {
        val realWordRate: Double get() = if (total == 0) 0.0 else realWordCount.toDouble() / total
    }

    fun sampleWords(words: Map<String, Int>, n: Int, tokenWeighted: Boolean, minLen: Int = 3, maxRank: Int = 10000): List<String> {
        val ranked = rankedWords(words, maxRank, minLen)
        val out = mutableListOf<String>()
        if (tokenWeighted) {
            val total = ranked.fold(0.0) { acc, (_, f) -> acc + f }
            while (out.size < n) {
                var x = rnd() * total
                for ((w, f) in ranked) {
                    x -= f
                    if (x <= 0) {
                        out.add(w)
                        break
                    }
                }
            }
        } else {
            while (out.size < n) {
                out.add(ranked[100 + floor(rnd() * (ranked.size - 100)).toInt()].first)
            }
        }
        return out
    }

    fun makeTypo(w: String): String? {
        val r = rnd()
        val i = floor(rnd() * w.length).toInt()
        if (r < 0.55) {
            val nb = Neighbors[w[i]]
            if (nb.isNullOrEmpty()) return null
            return w.substring(0, i) + nb[floor(rnd() * nb.size).toInt()] + w.substring(i + 1)
        }
        if (r < 0.75) return if (w.length > 2) w.substring(0, i) + w.substring(i + 1) else null
        if (r < 0.9) {
            val nb = Neighbors[w[i]].orEmpty()
            val ch = if (rnd() < 0.4) {
                w[i]
            } else {
                nb.getOrNull(floor(rnd() * nb.size).toInt()) ?: w[i]
            }
            return w.substring(0, i) + ch + w.substring(i)
        }
        if (w.length < 2 || i == w.length - 1 || w[i] == w[i + 1]) return null
        return w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)
    }

    fun buildTypos(words: Map<String, Int>, sample: List<String>): Generated {
        val pairs = mutableListOf<TypoPair>()
        var realWord = 0
        var total = 0
        for (w in sample) {
            var t: String? = null
            var k = 0
            while (k < 5 && (t == null || t == w)) {
                t = makeTypo(w)
                k++
            }
            if (t == null || t == w) continue
            total++
            if (words.containsKey(t)) {
                realWord++
                continue
            }
            pairs.add(TypoPair(t, w))
        }
        return Generated(pairs, realWord, total)
    }

    /** `maxslider.js`: adjacent-key substitutions only, usage-weighted. */
    fun maxSliderSubstitutions(words: Map<String, Int>, n: Int): List<TypoPair> {
        val ranked = rankedWords(words, 10000, 3)
        val total = ranked.fold(0.0) { acc, (_, f) -> acc + f }
        val out = mutableListOf<TypoPair>()
        while (out.size < n) {
            var x = rnd() * total
            var w: String? = null
            for ((ww, f) in ranked) {
                x -= f
                if (x <= 0) {
                    w = ww
                    break
                }
            }
            checkNotNull(w)
            val i = floor(rnd() * w.length).toInt()
            val nb = Neighbors[w[i]].orEmpty()
            if (nb.isEmpty()) continue
            val t = w.substring(0, i) + nb[floor(rnd() * nb.size).toInt()] + w.substring(i + 1)
            if (words.containsKey(t)) continue
            out.add(TypoPair(t, w))
        }
        return out
    }

    companion object {
        private val Rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val Offsets = listOf(0.0, 0.25, 0.75)
        private val Positions: Map<Char, Pair<Double, Double>> = buildMap {
            Rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch -> put(ch, (x + Offsets[y]) to y.toDouble()) }
            }
        }

        fun isAdjacent(a: Char, b: Char): Boolean {
            if (a == b) return false
            val pa = Positions[a] ?: return false
            val pb = Positions[b] ?: return false
            return hypot(pa.first - pb.first, pa.second - pb.second) <= 1.3
        }

        val Neighbors: Map<Char, List<Char>> = ('a'..'z').associateWith { a -> ('a'..'z').filter { b -> isAdjacent(a, b) } }

        /** Sorted by frequency (stable, so file order breaks ties), cut at [maxRank], then filtered. */
        fun rankedWords(words: Map<String, Int>, maxRank: Int, minLen: Int): List<Pair<String, Int>> {
            return words.entries
                .map { it.key to it.value }
                .sortedByDescending { it.second }
                .take(maxRank)
                .filter { (w, _) -> w.length >= minLen && w.all { it in 'a'..'z' } }
        }
    }
}
