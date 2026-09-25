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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * T-016: words two edits away become candidates, without making autocorrect less sure of one-edit fixes.
 */
class TwoEditCandidateTest : FunSpec({
    val geometry = KeyGeometry.QwertyPhone

    test("keys near a key and near a tap") {
        val nearG = geometry.neighbors('g', 1.3)
        nearG shouldContain 'f'
        nearG shouldContain 'h'
        nearG shouldNotContain 'g'
        nearG shouldNotContain 'p'
        val (x, y) = geometry.center('r')!!
        geometry.nearestKeys(x + 0.45, y, 2) shouldBe listOf('r', 't')
    }

    test("two deletions and two substitutions are found") {
        val model = BenchmarkData.en().model
        // Two extra letters.
        model.lookupTwoEditCandidates("thhee", { emptyList() }, 12) shouldContain "the"
        // Two wrong letters, with the keys next to each typed letter as alternatives.
        model.lookupTwoEditCandidates("deturm", { index -> geometry.neighbors("deturm"[index], 1.3) }, 12) shouldContain "return"
        model.lookupTwoEditCandidates("ab", { emptyList() }, 12) shouldBe emptyList()
    }

    test("a two-edit typo gets the right word first when the taps point to it") {
        val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())
        val typed = "deturm"
        val taps = typed.mapIndexed { index, ch ->
            // The d was tapped toward r, the m toward n.
            val intended = "return"[index]
            val (x, y) = geometry.center(ch)!!
            val (ix, iy) = geometry.center(intended)!!
            Tap(ch, x + (ix - x) * 0.45, y + (iy - y) * 0.45)
        }
        benchmark.score(BenchmarkData.enOnly(), typed, "I will deturm", taps).first().word shouldBe "return"
    }

    test("two-edit readings do not change one-edit decisions") {
        val with = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())
        val without = AutocorrectBenchmark(NoisyChannelLatinScorer(NoisyChannelParams(twoEditCandidates = false)), BenchmarkPolicies.default())
        for (typed in listOf("teh", "becuase", "wrld", "mischien")) {
            val languages = if (typed == "mischien") BenchmarkData.nlOnly() else BenchmarkData.enOnly()
            val a = with.score(languages, typed, "so $typed").firstOrNull { it.isAutoCommit }?.word
            val b = without.score(languages, typed, "so $typed").firstOrNull { it.isAutoCommit }?.word
            a shouldBe b
        }
    }
})
