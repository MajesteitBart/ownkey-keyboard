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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * T-012: the words before the cursor change which word is suggested first.
 */
class ContextScoringTest : FunSpec({
    val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())

    test("AC-010: after 'I would rather', than is suggested first and then is kept on space") {
        val result = benchmark.score(BenchmarkData.enOnly(), "then", "I would rather then")
        result.first().word shouldBe "than"
        result.firstOrNull { it.isAutoCommit }.shouldBeNull()
        // Without the words before it, then stays first.
        benchmark.score(BenchmarkData.enOnly(), "then", "then").first().word shouldBe "then"
    }

    test("Dutch verb endings follow the subject") {
        benchmark.score(BenchmarkData.nlOnly(), "word", "Hij word").first().word shouldBe "wordt"
        benchmark.score(BenchmarkData.nlOnly(), "wordt", "Ik wordt").first().word shouldBe "word"
        benchmark.score(BenchmarkData.nlOnly(), "word", "Hij word").firstOrNull { it.isAutoCommit }.shouldBeNull()
    }

    test("a typo is fixed toward the word that fits the sentence") {
        val result = benchmark.score(BenchmarkData.enOnly(), "stoer", "we went to the stoer")
        result.firstOrNull { it.isAutoCommit }?.word shouldBe "store"
    }

    test("the first word of a sentence gets no word context") {
        val atStart = benchmark.score(BenchmarkData.nlOnly(), "zn", "Ik zag hem. zn")
        val withoutText = benchmark.score(BenchmarkData.nlOnly(), "zn", "zn")
        atStart.map { it.word } shouldBe withoutText.map { it.word }
    }

    test("confusion sets are symmetric, per language, and limited to known words") {
        ConfusionSets.alternatives("en", "then") { true } shouldContain "than"
        ConfusionSets.alternatives("en", "than") { true } shouldContain "then"
        ConfusionSets.alternatives("en", "there") { true } shouldContainExactlyInAnyOrder listOf("their", "they're")
        ConfusionSets.alternatives("nl", "then") { true } shouldNotContain "than"
        ConfusionSets.alternatives("nl", "vind") { true } shouldContain "vindt"
        ConfusionSets.alternatives("nl", "gebeurt") { true } shouldContain "gebeurd"
        ConfusionSets.alternatives("nl", "vind") { it == "vindt" } shouldBe listOf("vindt")
        ConfusionSets.alternatives("de", "then") { true } shouldBe emptyList()
    }
})
