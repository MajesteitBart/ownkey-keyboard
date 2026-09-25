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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Locale

/**
 * T-008: apostrophe-less forms and the English pronoun "I".
 */
class ApostropheAndCasingTest : FunSpec({
    val benchmark = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default())

    suspend fun auto(languages: List<LatinScoringLanguage>, typed: String, before: String = ""): String? {
        return benchmark.score(languages, typed, before + typed).firstOrNull { it.isAutoCommit }?.text
    }

    test("AC-006: unambiguous English forms autocorrect") {
        val expected = mapOf(
            "dont" to "don't", "im" to "I'm", "youre" to "you're", "thats" to "that's", "didnt" to "didn't",
            "doesnt" to "doesn't", "isnt" to "isn't", "ive" to "I've", "wasnt" to "wasn't", "couldnt" to "couldn't",
            "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't", "arent" to "aren't",
        )
        expected.forEach { (typed, want) ->
            withClue(typed) { auto(BenchmarkData.enOnly(), typed, "well ") shouldBe want }
        }
    }

    test("ambiguous English forms only suggest the apostrophe form") {
        val expected = mapOf(
            "ill" to "i'll", "id" to "i'd", "wont" to "won't", "cant" to "can't", "were" to "we're", "its" to "it's",
            "well" to "we'll", "hell" to "he'll", "shell" to "she'll", "lets" to "let's",
        )
        expected.forEach { (typed, form) ->
            val result = benchmark.score(BenchmarkData.enOnly(), typed, "so $typed")
            withClue("$typed is not replaced") { result.firstOrNull { it.isAutoCommit }.shouldBeNull() }
            withClue("$typed suggests $form") { result.take(3).map { it.word } shouldContain form }
        }
    }

    test("Dutch elisions, also with English as second language") {
        for (languages in listOf(BenchmarkData.nlOnly(), BenchmarkData.nlEn())) {
            auto(languages, "zn", "ik zag ") shouldBe "z'n"
            auto(languages, "mn", "hij zag ") shouldBe "m'n"
        }
    }

    test("standalone English i becomes I") {
        auto(BenchmarkData.enOnly(), "i", "and ") shouldBe "I"
        auto(BenchmarkData.enOnly(), "i'm", "and ") shouldBe "I'm"
        // English context on the NL+EN subtype
        auto(BenchmarkData.nlEn(), "i", "and then ") shouldBe "I"
    }

    test("i is not capitalized on a Dutch-first keyboard without English context, nor inside words") {
        auto(BenchmarkData.nlEn(), "i", "ik zag ").shouldBeNull()
        auto(BenchmarkData.nlOnly(), "i", "ik zag ").shouldBeNull()
        auto(BenchmarkData.enOnly(), "in", "and ").shouldBeNull()
    }

    test("listed forms are not words themselves, and their apostrophe forms are") {
        for (code in listOf("en", "nl")) {
            val model = BenchmarkData.language(code).model
            ApostropheForms.all(code).forEach { (typed, form) ->
                withClue("$code:$typed") {
                    model.isKnown(typed).shouldBeFalse()
                    model.isKnown(form).shouldBeTrue()
                }
            }
        }
    }

    test("a listed form is not used on a keyboard without its language") {
        auto(BenchmarkData.nlOnly(), "dont", "ik ") shouldNotBe "don't"
    }

    test("all-caps input and capitals in mid-sentence are not listed forms") {
        auto(BenchmarkData.enOnly(), "IM", "so ").shouldBeNull()
        auto(BenchmarkData.nlEn(), "MN", "I live in ").shouldBeNull()
        auto(BenchmarkData.nlOnly(), "ZN", "ik zag ").shouldBeNull()
        auto(BenchmarkData.nlOnly(), "Zn", "ik zag ").shouldBeNull()
        auto(BenchmarkData.nlOnly(), "Zn", "Ik zag hem. ") shouldBe "Z'n"
    }

    test("a listed form needs its language to fit the words before it") {
        auto(BenchmarkData.enNl(), "mn", "the ").shouldBeNull()
        auto(BenchmarkData.enNl(), "zn", "ik zag ") shouldBe "z'n"
    }

    test("English first with Dutch second: a tie goes to English") {
        auto(BenchmarkData.enNl(), "i", "hi bart, ") shouldBe "I"
        auto(BenchmarkData.enNl(), "i", "lol ") shouldBe "I"
        auto(BenchmarkData.enNl(), "i", "ik zag ").shouldBeNull()
        auto(BenchmarkData.nlEn(), "i", "het was ").shouldBeNull()
        auto(BenchmarkData.nlEn(), "i", "ok ").shouldBeNull()
    }

    test("only the sentence being written decides the language") {
        auto(BenchmarkData.nlEn(), "i", "Ik zag z'n fiets\nand then ") shouldBe "I"
        auto(BenchmarkData.nlEn(), "i", "And then I left. Ik zag het en ").shouldBeNull()
    }

    test("the typed apostrophe is kept") {
        auto(BenchmarkData.enOnly(), "i’m", "and ") shouldBe "I’m"
    }

    test("no I next to a language that borrows the English word list") {
        // Polish has no dictionary of its own; in Polish "i" means "and".
        val polish = LatinScoringLanguage("pl", Locale.forLanguageTag("pl"), BenchmarkData.en().model, isPrimary = true, hasOwnDictionary = false)
        auto(listOf(polish, BenchmarkData.en().slot(isPrimary = false)), "i", "mama ").shouldBeNull()
        auto(listOf(BenchmarkData.en().slot(isPrimary = true), polish.copy(isPrimary = false)), "i", "and then ").shouldBeNull()
    }

    test("suggestions for pronoun forms show a capital I") {
        benchmark.score(BenchmarkData.enOnly(), "im", "so im").first().text shouldBe "I'm"
    }
})
