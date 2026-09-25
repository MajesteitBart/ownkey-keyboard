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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * T-019: two words typed without the space between them.
 */
class MissedSpaceTest : FunSpec({
    val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())

    suspend fun auto(languages: List<LatinScoringLanguage>, typed: String, before: String) =
        benchmark.score(languages, typed, before + typed).firstOrNull { it.isAutoCommit }?.text

    test("common run-together words are split") {
        auto(BenchmarkData.enOnly(), "thisis", "I think ") shouldBe "this is"
        auto(BenchmarkData.enOnly(), "ofthe", "the end ") shouldBe "of the"
        auto(BenchmarkData.enOnly(), "Thisis", "") shouldBe "This is"
        auto(BenchmarkData.nlOnly(), "ikben", "ja ") shouldBe "ik ben"
    }

    test("an English I inside a split keeps its capital") {
        auto(BenchmarkData.enOnly(), "iknow", "yes ") shouldBe "I know"
    }

    test("very long tokens skip the expensive lookups") {
        val long = "thisis".repeat(10)
        benchmark.score(BenchmarkData.enOnly(), long, "so $long").none { ' ' in it.word } shouldBe true
    }

    test("two uncommon words are only suggested: they may be a compound missing from the word list") {
        val result = benchmark.score(BenchmarkData.enOnly(), "treehouse", "we built a treehouse")
        result.firstOrNull { it.isAutoCommit }.shouldBeNull()
        result.first().word shouldBe "tree house"
    }

    test("known words and Dutch pairs never seen together are not split") {
        benchmark.score(BenchmarkData.enOnly(), "into", "go into").none { ' ' in it.word } shouldBe true
        // A Dutch compound that is not in the word list stays as typed.
        auto(BenchmarkData.nlOnly(), "fietsenstalling", "de ") shouldNotBe "fietsen stalling"
    }
})
