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

package org.florisboard.lib.prediction.ngram

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.Lexicon
import org.florisboard.lib.prediction.PersonalOverlay
import org.florisboard.lib.prediction.PredictionContext
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.UnigramListModel

class NgramWordModelTest : FunSpec({
    val ngram = checkNotNull(NgramWordModelTest::class.java.getResourceAsStream("/ngram/tiny3.bin")).use { NgramModel.load(it) }
    val model = NgramWordModel(ngram)

    test("adapts ids, probabilities and successors without changing them") {
        model.language shouldBe "en"
        NgramWordModel(ngram, language = "xx").language shouldBe "xx"
        model.bosId shouldBe NgramModel.BOS_ID
        model.usesContext shouldBe true
        model.vocabularySize shouldBe ngram.vocabularySize
        val know = ngram.wordId("know")
        val dont = ngram.wordId("don't")
        model.wordId("know") shouldBe know
        model.word(know) shouldBe "know"
        model.logProb(know, dont, NgramModel.BOS_ID) shouldBe ngram.logProb(know, dont, NgramModel.BOS_ID).toDouble()
        model.unigramLogProb(know) shouldBe ngram.unigramLogProb(know).toDouble()
        model.topNext(dont, NgramModel.BOS_ID, 5).map { it.id to it.logProb } shouldBe
            ngram.topNext(dont, NgramModel.BOS_ID, 5).map { it.wordId to it.logProb.toDouble() }
    }

    test("isValidWord is the model's validity flag; frequency lists count every word as valid") {
        model.isValidWord(ngram.wordId("know")) shouldBe true
        model.isValidWord(ngram.wordId("straks")) shouldBe false
        model.isValidWord(NgramModel.BOS_ID) shouldBe false
        val v1 = NgramWordModel(checkNotNull(NgramWordModelTest::class.java.getResourceAsStream("/ngram/tiny3v1.bin")).use { NgramModel.load(it) })
        v1.isValidWord(v1.wordId("straks")) shouldBe true
        val list = UnigramListModel.fromCounts("user", listOf("fiets" to 5L))
        list.isValidWord(list.wordId("fiets")) shouldBe true
    }

    test("displayForm is the cased form") {
        model.displayForm(ngram.wordId("iphone")) shouldBe "iPhone"
        model.displayForm(ngram.wordId("amsterdam")) shouldBe "Amsterdam"
        model.displayForm(ngram.wordId("i")) shouldBe "I"
        model.displayForm(ngram.wordId("know")) shouldBe "know"
        model.isCapitalized(ngram.wordId("fbi")) shouldBe true
    }

    test("engine candidates carry the display form") {
        val engine = PredictionEngine(listOf(model))
        val c = engine.suggest(PredictionContext(listOf("you", "in")), "amster").candidates
            .firstOrNull { it.word == "amsterdam" }.shouldNotBeNull()
        c.isCapitalized shouldBe true
        c.displayForm shouldBe "Amsterdam"
        val lower = engine.suggest(PredictionContext(listOf("i", "don't")), "kno").candidates
            .first { it.word == "know" }
        lower.displayForm shouldBe "know"
    }

    test("fromLexicons reuses lexicons and gives the same results") {
        val built = PredictionEngine(listOf(model))
        val shared = PredictionEngine.fromLexicons(listOf(Lexicon.build(model)))
        val ctx = PredictionContext(listOf("i"))
        shared.suggest(ctx, "dont").candidates shouldBe built.suggest(ctx, "dont").candidates
        shared.suggest(ctx, "").candidates shouldBe built.suggest(ctx, "").candidates
    }

    test("frequency lists and overlays keep the written form of their words") {
        val list = UnigramListModel.fromCounts("user", listOf("Sanne" to 3L, "iPhone" to 2L, "fiets" to 5L, "FBI" to 1L))
        list.displayForm(list.wordId("sanne")) shouldBe "Sanne"
        list.displayForm(list.wordId("iphone")) shouldBe "iPhone"
        list.displayForm(list.wordId("fbi")) shouldBe "FBI"
        list.displayForm(list.wordId("fiets")) shouldBe "fiets"
        list.isCapitalized(list.wordId("fiets")) shouldBe false
        // The more frequent spelling wins.
        val mixed = UnigramListModel.fromCounts("user", listOf("rose" to 5L, "Rose" to 2L, "Bob" to 3L, "bob" to 1L))
        mixed.isCapitalized(mixed.wordId("rose")) shouldBe false
        mixed.displayForm(mixed.wordId("bob")) shouldBe "Bob"

        val overlay = PersonalOverlay.build(listOf("Sanne" to 1L))
        val engine = PredictionEngine(listOf(model)).withPersonalOverlay(overlay)
        engine.suggest(PredictionContext(listOf("i", "know")), "sann").candidates
            .first { it.word == "sanne" }.displayForm shouldBe "Sanne"
    }
})
