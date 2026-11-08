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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.nlp.personal.PersonalPrediction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.Candidate
import org.florisboard.lib.prediction.CandidateKind
import kotlin.math.ln

private fun next(word: String, prob: Double, display: String = word) = Candidate(
    word = word, keyForm = word, language = "nl", kind = CandidateKind.NEXT_WORD, logPosterior = ln(prob), prob = prob,
    editCost = 0.0, edits = 0, isCapitalized = display != word, displayForm = display,
)

class PersonalNextWordBlendTest : FunSpec({
    val engine = listOf(next("de", 0.30), next("het", 0.20), next("een", 0.15), next("amsterdam", 0.05, "Amsterdam"))

    test("without personal predictions the engine order is kept") {
        PersonalNextWordBlend.blend(engine, emptyList(), limit = 8).map { it.word } shouldBe listOf("de", "het", "een", "amsterdam")
        PersonalNextWordBlend.blend(engine, emptyList(), limit = 2).map { it.word } shouldBe listOf("de", "het")
    }

    test("a phrase learned once reaches the visible slots") {
        val blended = PersonalNextWordBlend.blend(engine, listOf(PersonalPrediction("sportschool", 1)), limit = 8) { it }
        // beta = 1 / (1 + 3) = 0.25: "sportschool" gets 0.25, "de" 0.75 * 0.30 = 0.225.
        blended.map { it.word }.take(3) shouldBe listOf("sportschool", "de", "het")
        blended.first().prob shouldBe (0.25 plusOrMinus 1e-9)
        blended.first().candidate shouldBe null
    }

    test("personal evidence for a word the engine predicts adds to its probability") {
        val blended = PersonalNextWordBlend.blend(engine, listOf(PersonalPrediction("een", 3)), limit = 8)
        // beta = 3 / 6 = 0.5: "een" = 0.5 * 0.15 + 0.5 = 0.575.
        blended.first().word shouldBe "een"
        blended.first().prob shouldBe (0.575 plusOrMinus 1e-9)
        blended.first().candidate shouldBe engine[2]
    }

    test("the personal share is capped so the model keeps a share") {
        val blended = PersonalNextWordBlend.blend(engine, listOf(PersonalPrediction("wij", 100)), limit = 8)
        blended.first().prob shouldBe (PersonalNextWordBlend.MaxPersonalWeight plusOrMinus 1e-9)
        blended[1].word shouldBe "de"
        blended[1].prob shouldBe ((1 - PersonalNextWordBlend.MaxPersonalWeight) * 0.30 plusOrMinus 1e-9)
    }

    test("personal-only words get their written form; non-words are ignored") {
        val blended = PersonalNextWordBlend.blend(
            engine,
            listOf(PersonalPrediction("utrecht", 2), PersonalPrediction("e-mail", 5), PersonalPrediction("", 4)),
            limit = 8,
        ) { if (it == "utrecht") "Utrecht" else it }
        blended.first().displayForm shouldBe "Utrecht"
        blended.none { it.word == "e-mail" } shouldBe true
        blended.first { it.word == "amsterdam" }.displayForm shouldBe "Amsterdam"
    }
})
