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

/**
 * Spec acceptance scenarios that the scorer alone decides, on the shipped dictionaries.
 */
class NoisyChannelAcceptanceTest : FunSpec({
    val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())

    suspend fun autoCorrection(languages: List<LatinScoringLanguage>, typed: String, before: String = ""): String? {
        return benchmark.score(languages, typed, before + typed).firstOrNull { it.isAutoCommit }?.text
    }

    test("AC-001: becuase becomes because") {
        autoCorrection(BenchmarkData.enOnly(), "becuase") shouldBe "because"
    }

    test("AC-009: a dropped final letter is fixed") {
        autoCorrection(BenchmarkData.enOnly(), "becaus") shouldBe "because"
    }

    test("common English slips") {
        autoCorrection(BenchmarkData.enOnly(), "teh") shouldBe "the"
        autoCorrection(BenchmarkData.enOnly(), "wrld") shouldBe "world"
        autoCorrection(BenchmarkData.enOnly(), "thsi") shouldBe "this"
        autoCorrection(BenchmarkData.enOnly(), "thnaks") shouldBe "thanks"
    }

    test("common Dutch slips, also on the NL+EN subtype") {
        autoCorrection(BenchmarkData.nlOnly(), "bedakt") shouldBe "bedankt"
        autoCorrection(BenchmarkData.nlEn(), "vergaderign") shouldBe "vergadering"
        autoCorrection(BenchmarkData.nlEn(), "natuurlik") shouldBe "natuurlijk"
    }

    test("AC-003: product names and jargon stay as typed") {
        autoCorrection(BenchmarkData.nlEn(), "Voxtral", "ik gebruik ").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "webhook", "check de ").shouldBeNull()
    }

    test("capitalized names in mid-sentence stay as typed") {
        autoCorrection(BenchmarkData.nlEn(), "Lieke", "ik zag ").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "Woerden", "we wonen in ").shouldBeNull()
    }

    test("chat shorthand, acronyms and apostrophe words stay as typed") {
        autoCorrection(BenchmarkData.nlEn(), "idd").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "wss").shouldBeNull()
        autoCorrection(BenchmarkData.enOnly(), "API", "the ").shouldBeNull()
        autoCorrection(BenchmarkData.enOnly(), "didn't").shouldBeNull()
    }

    test("known words are never replaced") {
        autoCorrection(BenchmarkData.enOnly(), "form").shouldBeNull()
        autoCorrection(BenchmarkData.nlOnly(), "moet").shouldBeNull()
    }

    test("the most likely finished word is suggested first") {
        benchmark.score(BenchmarkData.enOnly(), "thos", "thos").first().word shouldBe "this"
    }
})
