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

package org.florisboard.lib.prediction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.TestNgramModel.Companion.BOS

/** Context handling with a context-aware model, the personal overlay and protected literals. */
class EngineContextTest : FunSpec({
    val nl = TestNgramModel(
        "nl",
        unigrams = mapOf("de" to 500, "het" to 400, "ik" to 300, "heb" to 200, "ga" to 150, "hoi" to 20, "naar" to 100),
        bigrams = mapOf((BOS to "hoi") to 50, (BOS to "ik") to 30, ("ik" to "heb") to 10, ("ik" to "ga") to 5),
        trigrams = mapOf(Triple(BOS, "ik", "ga") to 20),
    )
    val en = TestNgramModel(
        "en",
        unigrams = mapOf(
            "the" to 500, "you" to 1000, "your" to 500, "young" to 300, "york" to 2, "i" to 400, "have" to 200,
            "hello" to 30, "end" to 40, "new" to 80,
        ),
        bigrams = mapOf((BOS to "hello") to 50, (BOS to "i") to 20, ("the" to "end") to 100, ("new" to "york") to 50),
    )
    val nlEngine = PredictionEngine(listOf(nl))
    val mixed = PredictionEngine(listOf(nl, en))

    fun firstSlot(engine: PredictionEngine, context: PredictionContext) = engine.suggest(context, "").displaySlots(3).first().word

    test("the sentence-start token conditions predictions at a sentence start only") {
        firstSlot(nlEngine, PredictionContext.FieldStart) shouldBe "hoi"
        firstSlot(nlEngine, PredictionContext(startsSentence = false)) shouldBe "de"
    }

    test("the first word of a sentence has <s> as its second context word") {
        // Trigram "<s> ik ga" beats bigram "ik heb" only when <s> precedes "ik".
        firstSlot(nlEngine, PredictionContext(listOf("ik"), startsSentence = true)) shouldBe "ga"
        firstSlot(nlEngine, PredictionContext(listOf("ik"), startsSentence = false)) shouldBe "heb"
    }

    test("language hint words move the language mix but are never n-gram context") {
        val hinted = PredictionContext(languageHintWords = listOf("i", "have", "the"))
        val r = mixed.suggest(hinted, "")
        r.languageWeights["en"]!! shouldBeGreaterThan 0.9
        // Prediction follows <s> ("hello"), not the last hint word ("the" -> "end").
        r.displaySlots(3).first().word shouldBe "hello"
        val notStart = mixed.suggest(hinted.copy(startsSentence = false), "")
        notStart.displaySlots(3).map { it.word } shouldNotContain "end"
        notStart.languageWeights["en"]!! shouldBeGreaterThan 0.9
    }

    test("context successors add completions that the per-subtree unigram top-k misses") {
        // One unigram word per subtree ("you" for "yo") and the two best successors of the context.
        val engine = PredictionEngine(listOf(en), config = EngineConfig(completionsPerSubtree = 1, contextSuccessors = 2))
        engine.suggest(PredictionContext(listOf("new")), "yo").displaySlots(3).map { it.word } shouldContain "york"
        engine.suggest(PredictionContext.FieldStart, "yo").candidates.map { it.word } shouldNotContain "york"
    }

    context("personal overlay") {
        val overlay = PersonalOverlay.build(listOf("sanne" to 5L, "kheb" to 3L, "Jordaan" to 1L, "e-mail" to 9L))
        val engine = TestModels.nlEn.withPersonalOverlay(overlay)

        test("normalises words and skips entries that are not words") {
            overlay.size shouldBe 3
            overlay.contains("Sanne") shouldBe true
            overlay.contains("e-mail") shouldBe false
        }

        test("a personal word typed exactly is a known literal and stays") {
            val r = engine.suggest(PredictionContext(listOf("ik", "zie")), "kheb")
            r.isLiteralKnown shouldBe true
            r.autocorrect.shouldBeNull()
            r.literal.shouldNotBeNull().isPersonal shouldBe true
        }

        test("personal words are completed and keep their capitalisation") {
            val r = engine.suggest(PredictionContext(listOf("ik", "zie")), "sann")
            val sanne = r.displaySlots(3).firstOrNull { it.word == "sanne" }.shouldNotBeNull()
            sanne.isPersonal shouldBe true
            engine.suggest(PredictionContext(listOf("in", "de")), "jordaa").candidates
                .first { it.word == "jordaan" }.isCapitalized shouldBe true
        }

        test("does not change the language weights") {
            val ctx = PredictionContext(listOf("sanne", "kheb", "zin"))
            engine.suggest(ctx, "").languageWeights shouldBe TestModels.nlEn.suggest(ctx, "").languageWeights
        }

        test("an empty overlay changes nothing") {
            val plain = TestModels.nlEn.suggest(PredictionContext.FieldStart, "gistren")
            val empty = TestModels.nlEn.withPersonalOverlay(PersonalOverlay.Empty).suggest(PredictionContext.FieldStart, "gistren")
            empty.candidates shouldBe plain.candidates
            empty.autocorrect shouldBe plain.autocorrect
        }
    }

    context("protected literals") {
        test("an unknown protected literal counts as known and is never replaced") {
            val plain = TestModels.enOnly.suggest(PredictionContext.FieldStart, "teh")
            plain.autocorrect.shouldNotBeNull()
            val r = TestModels.enOnly.suggest(PredictionContext.FieldStart, "teh", isProtected = { it == "teh" })
            r.autocorrect.shouldBeNull()
            r.isLiteralKnown shouldBe true
            val lit = r.literal.shouldNotBeNull()
            lit.word shouldBe "teh"
            lit.kind shouldBe CandidateKind.EXACT
            lit.isPersonal shouldBe true
            r.displaySlots(8).map { it.word } shouldContain "teh"
        }

        test("a protected spelling is not switched to another surface") {
            val engine = PredictionEngine(listOf(TestModels.model("en", "don't" to 1000, "dont" to 10)))
            engine.suggest(PredictionContext.FieldStart, "dont").autocorrect.shouldNotBeNull()
            val r = engine.suggest(PredictionContext.FieldStart, "dont", isProtected = { it == "dont" })
            r.autocorrect.shouldBeNull()
            r.literal.shouldNotBeNull().word shouldBe "dont"
        }

        test("the predicate gets the normalised input") {
            val seen = ArrayList<String>()
            TestModels.enOnly.suggest(PredictionContext.FieldStart, " Teh ", isProtected = { seen.add(it); false })
            seen shouldBe listOf("teh")
        }
    }
})
