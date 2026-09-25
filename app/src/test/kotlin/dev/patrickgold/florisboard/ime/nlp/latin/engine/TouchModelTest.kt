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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.AutocorrectBenchmark
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkPolicies
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.Tap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * T-015: a tap between two keys makes that substitution cheap; a tap in the middle of a key does not.
 */
class TouchModelTest : FunSpec({
    val scorer = NoisyChannelLatinScorer()
    val geometry = KeyGeometry.QwertyPhone

    fun tapAt(ch: Char, dx: Double = 0.0, dy: Double = 0.0): LatinTap {
        val (x, y) = geometry.center(ch)!!
        return LatinTap(x + dx, y + dy)
    }

    test("a tap on the border between r and t is cheap to read as t") {
        val withoutTaps = scorer.channelCost("thr", "tht", geometry)
        val border = scorer.channelCost("thr", "tht", geometry, listOf(tapAt('t'), tapAt('h'), tapAt('r', dx = 0.48)))
        val center = scorer.channelCost("thr", "tht", geometry, listOf(tapAt('t'), tapAt('h'), tapAt('r')))
        border shouldBeLessThan withoutTaps
        border shouldBeLessThan center
        center shouldBe withoutTaps
    }

    test("taps without a position, or not one per letter, change nothing") {
        val withoutTaps = scorer.channelCost("thr", "the", geometry)
        scorer.channelCost("thr", "the", geometry, listOf(tapAt('t'), tapAt('h'), LatinTap(Double.NaN, Double.NaN))) shouldBe withoutTaps
        val benchmark = AutocorrectBenchmark(scorer, BenchmarkPolicies.default())
        val plain = benchmark.score(BenchmarkData.enOnly(), "thr", "so thr")
        val shortTaps = benchmark.score(BenchmarkData.enOnly(), "thr", "so thr", listOf(Tap('t', 0.0, 0.0)))
        shortTaps.map { it.word to it.confidence } shouldBe plain.map { it.word to it.confidence }
    }

    test("a word that gets longer when lowercased keeps working with taps") {
        // Outside Turkish, "İ" lowercases to two characters, so the taps no longer line up and are ignored.
        val benchmark = AutocorrectBenchmark(scorer, BenchmarkPolicies.default())
        val typed = "İstanbul"
        val taps = typed.map { Tap(it, 1.0, 1.0) }
        for (languages in listOf(BenchmarkData.enOnly(), BenchmarkData.nlEn())) {
            val withTaps = benchmark.score(languages, typed, "in $typed", taps)
            val plain = benchmark.score(languages, typed, "in $typed")
            withTaps.map { it.word to it.confidence } shouldBe plain.map { it.word to it.confidence }
        }
    }

    test("where the typo was tapped decides the correction") {
        val benchmark = AutocorrectBenchmark(scorer, BenchmarkPolicies.default())
        fun taps(dx: Double): List<Tap> = listOf('t', 'h', 'r').mapIndexed { index, ch ->
            val tap = tapAt(ch, dx = if (index == 2) dx else 0.0)
            Tap(ch, tap.x, tap.y)
        }
        // "thr" with the r tapped toward e: the reading "the" gets more likely than with a tap in the middle of r.
        val towardE = benchmark.score(BenchmarkData.enOnly(), "thr", "so thr", taps(-0.48))
        val middle = benchmark.score(BenchmarkData.enOnly(), "thr", "so thr", taps(0.0))
        val confidenceTowardE = towardE.first { it.word == "the" }.confidence
        val confidenceMiddle = middle.first { it.word == "the" }.confidence
        confidenceMiddle shouldBeLessThan confidenceTowardE
    }
})
