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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

/**
 * T-013: predictions after a space come from the word-pair counts.
 */
class NextWordPredictionTest : FunSpec({
    val scorer = NoisyChannelLatinScorer()

    suspend fun predict(languages: List<LatinScoringLanguage>, text: String, count: Int = 3) =
        scorer.predictNextWords(languages, languages.first().locale, text, count)

    test("AC-012: after 'ik' the Dutch predictions are verbs that follow ik") {
        val words = predict(BenchmarkData.nlOnly(), "ik ").map { it.word }
        println("ik -> $words")
        words.size shouldBe 3
        listOf("ben", "heb", "wil", "kan", "weet", "denk", "ga", "zal", "moet", "hou") shouldContainAll words
    }

    test("after 'I' the English predictions are verbs, and I keeps its capital") {
        val words = predict(BenchmarkData.enOnly(), "Yesterday I ").map { it.word }
        println("I -> $words")
        listOf("am", "have", "think", "don't", "was", "can", "want", "know", "didn't", "can't", "will") shouldContainAll words
        predict(BenchmarkData.enOnly(), "and ", 8).map { it.text } shouldContain "I"
    }

    test("a sentence end switches to sentence-start predictions") {
        val afterPeriod = predict(BenchmarkData.nlOnly(), "Dat was leuk. ", 5).map { it.word }
        val atStart = predict(BenchmarkData.nlOnly(), "", 5).map { it.word }
        afterPeriod shouldBe atStart
    }

    test("the Dutch-English keyboard follows the language of the sentence") {
        val dutch = predict(BenchmarkData.nlEn(), "ik ").map { it.word }
        println("NL+EN ik -> $dutch")
        dutch.all { BenchmarkData.nl().model.isKnown(it) } shouldBe true
        predict(BenchmarkData.nlEn(), "I think that it ", 3).map { it.locale.language }.distinct() shouldBe listOf("en")
        // English knows "de" almost only from "de Janeiro"; that must not beat the Dutch successors.
        predict(BenchmarkData.nlEn(), "Morgen ga ik naar de ", 5).map { it.locale.language }.distinct() shouldBe listOf("nl")
    }

    test("languages without word pairs predict nothing") {
        val bare = LatinScoringLanguage("de", java.util.Locale.GERMAN, LatinWordModel.build(mapOf("ich" to 10, "bin" to 5)), isPrimary = true)
        scorer.predictNextWords(listOf(bare), java.util.Locale.GERMAN, "ich ", 3).shouldBeEmpty()
    }
})
