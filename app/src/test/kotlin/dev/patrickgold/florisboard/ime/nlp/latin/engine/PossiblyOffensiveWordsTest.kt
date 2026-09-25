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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class PossiblyOffensiveWordsTest : FunSpec({
    val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())

    test("the lists only name words of the shipped dictionaries") {
        for (code in listOf("en", "nl")) {
            val language = BenchmarkData.language(code)
            val listed = BenchmarkData.offensiveWords(code)
            listed.shouldNotBeEmpty()
            withClue("$code words not in the shipped model") {
                listed.filter { !language.model.isKnown(it) }.shouldBeEmpty()
            }
        }
    }

    test("a typo is never corrected into a blocked word") {
        val blocked = BenchmarkData.offensiveWords("en") + BenchmarkData.offensiveWords("nl")
        val typos = listOf(
            "fuxk" to BenchmarkData.enOnly(), "shjt" to BenchmarkData.enOnly(), "bitcj" to BenchmarkData.enOnly(),
            "fuckimg" to BenchmarkData.enOnly(), "kloyzak" to BenchmarkData.nlEn(), "godvedomme" to BenchmarkData.nlEn(),
        )
        val corrections = typos.map { (typed, languages) ->
            val scored = benchmark.score(languages, typed, typed)
            val unblocked = scored.firstOrNull { it.isAutoCommit }?.word
            val filtered = PossiblyOffensiveWords.filter(scored, blocked, typed) { it.text }.firstOrNull { it.isAutoCommit }?.word
            withClue("$typed became $filtered") { (filtered == null || filtered !in blocked) shouldBe true }
            unblocked
        }
        // Without the block these typos do become offensive words, so the check above means something.
        corrections.filter { it in blocked }.shouldNotBeEmpty()
    }

    test("the typed word stays, and nothing is filtered when the user allows these words") {
        val blocked = setOf("shit", "fuck")
        PossiblyOffensiveWords.filter(listOf("shit", "shot", "fuck"), blocked, "Shit") { it } shouldBe listOf("shit", "shot")
        PossiblyOffensiveWords.filter(listOf("fuck", "duck"), emptySet(), "fuxk") { it } shouldBe listOf("fuck", "duck")
        PossiblyOffensiveWords.filter(listOf("Fuck", "Duck"), blocked, "Fuxk") { it } shouldBe listOf("Duck")
    }

    test("a missed-space split is checked word by word") {
        val blocked = setOf("fuck")
        PossiblyOffensiveWords.filter(listOf("fuck you", "thank you", "fuckyou"), blocked, "fuckyou") { it } shouldBe
            listOf("thank you", "fuckyou")
    }
})
