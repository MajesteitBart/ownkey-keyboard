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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.florisboard.lib.prediction.ngram.NgramModel
import org.florisboard.lib.prediction.ngram.NgramWordModel

/** [base] with word validity: [invalid] words are not valid, [unlisted] words are valid but not dictionary words. */
private class FlaggedModel(
    private val base: WordLanguageModel,
    private val invalid: Set<String> = emptySet(),
    private val unlisted: Set<String> = emptySet(),
) : WordLanguageModel by base {
    override fun isValidWord(id: Int): Boolean = base.word(id) !in invalid

    override fun isListedWord(id: Int): Boolean = isValidWord(id) && base.word(id) !in unlisted
}

class WordValidityTest : FunSpec({
    val start = PredictionContext.FieldStart

    // "grea" is subtitle debris: it explains "grea" about 3 nats better than "great" with its omitted letter, which is
    // within deltaKnown (4) but beyond deltaInvalid (2).
    val greaCounts = arrayOf("great" to 1_100_000L, "grea" to 1_000L, "the" to 5_000_000L, "green" to 200_000L)
    // Without the context-aware real-word margin, which would also relax a listed "grea" (see below).
    val fixedMargin = EngineConfig(deltaKnownLmSlope = 0.0)

    test("frequency lists and version 1 models count every word as valid and none as listed") {
        val list = TestModels.model("en", *greaCounts)
        list.isValidWord(list.wordId("grea")) shouldBe true
        list.isListedWord(list.wordId("grea")) shouldBe false
        val v1 = NgramWordModel(checkNotNull(WordValidityTest::class.java.getResourceAsStream("/ngram/tiny3v1.bin")).use { NgramModel.load(it) })
        (1 until v1.vocabularySize).none { v1.isListedWord(it) } shouldBe true
        (1 until v1.vocabularySize).all { v1.isValidWord(it) } shouldBe true
    }

    test("on version 1 models and frequency lists the defaults decide like the engine without word validity") {
        // Without a word list the real-word margin must not relax for names and slang (review finding: "np" -> "no",
        // "wss" -> "was" with version 1 assets). Every vocabulary word, and every one-letter slip of it, after a few
        // contexts.
        val v1 = NgramWordModel(checkNotNull(WordValidityTest::class.java.getResourceAsStream("/ngram/tiny3v1.bin")).use { NgramModel.load(it) })
        val list = TestModels.model("en", *greaCounts, "doen" to 5000L, "den" to 50L, "op" to 8000L, "slot" to 300L)
        for (model in listOf<WordLanguageModel>(v1, list)) {
            val aware = PredictionEngine(listOf(model))
            val blind = aware.withConfig(aware.config.copy(validityAware = false, validTargetsOnly = false, deltaKnownLmSlope = 0.0))
            val words = (1 until model.vocabularySize).map { model.word(it) }.filter { w -> w.length >= 2 && w.all { it in 'a'..'z' } }
            val inputs = words.flatMap { w ->
                listOf(w, w.dropLast(1), w.substring(1)) + w.indices.map { i -> w.replaceRange(i, i + 1, if (w[i] == 'e') "r" else "e") }
            }.filter { it.length >= 2 }.distinct()
            for (ctxWords in listOf(emptyList(), listOf("i"), listOf("i", "don't"), listOf("op", "slot"))) {
                val ctx = PredictionContext(ctxWords)
                for (input in inputs) {
                    withClue("${model.language} ${ctxWords.joinToString(" ")} | $input") {
                        aware.suggest(ctx, input).autocorrect?.word shouldBe blind.suggest(ctx, input).autocorrect?.word
                    }
                }
            }
        }
    }

    test("a vocabulary word that is not valid is replaced more readily than a valid one") {
        val valid = PredictionEngine(listOf(TestModels.model("en", *greaCounts)), config = fixedMargin)
        valid.suggest(start, "grea").autocorrect.shouldBeNull()
        val model = FlaggedModel(TestModels.model("en", *greaCounts), invalid = setOf("grea"))
        val engine = PredictionEngine(listOf(model), config = fixedMargin)
        val r = engine.suggest(start, "grea")
        r.isLiteralKnown shouldBe true
        r.autocorrect.shouldNotBeNull().word shouldBe "great"
        // The literal stays available within the visible slots.
        r.displaySlots(3).map { it.word } shouldContain "grea"
        // With the valid-word margin, or without validity, it stays.
        engine.withConfig(engine.config.copy(deltaInvalid = engine.config.deltaKnown)).suggest(start, "grea").autocorrect.shouldBeNull()
        engine.withConfig(engine.config.copy(validityAware = false)).suggest(start, "grea").autocorrect.shouldBeNull()
        // Decided like an unknown word, its own probability still competes.
        engine.withConfig(engine.config.copy(invalidAsUnknown = true)).suggest(start, "grea").autocorrect.shouldNotBeNull()
            .word shouldBe "great"
    }

    test("personal and protected words are valid even when the model marks them invalid") {
        val model = FlaggedModel(TestModels.model("en", *greaCounts), invalid = setOf("grea"))
        val engine = PredictionEngine(listOf(model), config = fixedMargin)
        engine.withPersonalOverlay(PersonalOverlay.of(listOf("grea"))).suggest(start, "grea").autocorrect.shouldBeNull()
        engine.suggest(start, "grea", isProtected = { it == "grea" }).autocorrect.shouldBeNull()
    }

    test("autocorrect never targets a word that is not valid with validTargetsOnly") {
        // "hello" is frequent subtitle debris here; "hrllo" is one near key away from it.
        val counts = arrayOf("hello" to 1_000_000L, "hallo" to 20_000L, "the" to 5_000_000L)
        val engine = PredictionEngine(listOf(FlaggedModel(TestModels.model("en", *counts), invalid = setOf("hello"))))
        engine.withConfig(engine.config.copy(validTargetsOnly = false)).suggest(start, "hrllo").autocorrect
            .shouldNotBeNull().word shouldBe "hello"
        engine.withConfig(engine.config.copy(validTargetsOnly = true)).suggest(start, "hrllo").autocorrect?.word shouldNotBe "hello"
    }

    test("words that are not valid can be left out of completions and next-word predictions") {
        val counts = arrayOf("goin" to 500_000L, "going" to 400_000L, "good" to 300_000L, "the" to 5_000_000L)
        val engine = PredictionEngine(listOf(FlaggedModel(TestModels.model("en", *counts), invalid = setOf("goin"))))
        engine.suggest(start, "goi").candidates.map { it.word } shouldContain "goin"
        val drop = engine.withConfig(engine.config.copy(dropInvalidSuggestions = true))
        drop.suggest(start, "goi").candidates.map { it.word } shouldNotContain "goin"
        drop.suggest(start, "goi").candidates.map { it.word } shouldContain "going"
        drop.suggest(start, "").candidates.map { it.word } shouldNotContain "goin"
        // Typed exactly, it is still the literal.
        drop.suggest(start, "goin").literal.shouldNotBeNull().word shouldBe "goin"
    }

    context("real-word correction is frequency and context aware, for dictionary words only") {
        // "op slot den": the context wants "doen", and "den" is a rare word. With key codes, "den" for "doen" is an
        // omitted letter (4 nats), so the evidence is about 3.4 nats, below deltaKnownShort (5).
        fun model() = TestNgramModel(
            "nl",
            unigrams = mapOf("doen" to 5000L, "den" to 50L, "de" to 20000L, "deur" to 500L, "slot" to 300L, "op" to 8000L),
            bigrams = mapOf(("slot" to "doen") to 200L, ("op" to "slot") to 100L),
        )
        val ctx = PredictionContext(listOf("op", "slot"))
        val relieved = EngineConfig(deltaKnownLmSlope = 1.0, deltaKnownLmOffset = 2.0, deltaKnownFloor = 2.0)

        // Every word of the fixture is a dictionary word, unless named in `unlisted`.
        fun listed(m: WordLanguageModel, unlisted: Set<String> = emptySet()) = FlaggedModel(m, unlisted = unlisted)

        test("a fixed margin keeps the rare word") {
            PredictionEngine(listOf(listed(model())), config = relieved.copy(deltaKnownLmSlope = 0.0)).suggest(ctx, "den").autocorrect.shouldBeNull()
        }

        test("the margin shrinks when the typed word is rarer than the correction in general and in context") {
            PredictionEngine(listOf(listed(model())), config = relieved).suggest(ctx, "den").autocorrect.shouldNotBeNull().word shouldBe "doen"
        }

        test("models without a word list never relax the margin") {
            PredictionEngine(listOf(model()), config = relieved).suggest(ctx, "den").autocorrect.shouldBeNull()
        }

        test("names and chat words (valid, not listed) keep the full margin") {
            PredictionEngine(listOf(listed(model(), unlisted = setOf("den"))), config = relieved)
                .suggest(ctx, "den").autocorrect.shouldBeNull()
        }

        test("no relief for an expensive edit") {
            PredictionEngine(listOf(listed(model())), config = relieved.copy(deltaKnownLmMaxEditCost = 3.0))
                .suggest(ctx, "den").autocorrect.shouldBeNull()
        }

        test("no relief when the typed word is not rarer in general, only in this context") {
            val common = TestNgramModel(
                "nl",
                unigrams = mapOf("doen" to 1000L, "den" to 1000L, "het" to 1_000_000L, "slot" to 300L, "op" to 8000L),
                bigrams = mapOf(("slot" to "doen") to 200L, ("op" to "slot") to 100L),
            )
            PredictionEngine(listOf(listed(common)), config = relieved).suggest(ctx, "den").autocorrect.shouldBeNull()
            PredictionEngine(listOf(listed(common)), config = relieved.copy(deltaKnownUnigramGap = false))
                .suggest(ctx, "den").autocorrect.shouldNotBeNull().word shouldBe "doen"
        }
    }
})
