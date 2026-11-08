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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.TestNgramModel.Companion.BOS

class WordBlocklistTest : FunSpec({
    context("list format") {
        val list = WordBlocklist.parse(
            """
            # comment line
            darn
            heck*   # a stem
              Gosh
            don’t

            """.trimIndent()
        )

        test("exact words, stems, comments and blank lines") {
            list.size shouldBe 4
            ("darn" in list) shouldBe true
            ("darned" in list) shouldBe false
            ("heck" in list) shouldBe true
            ("heckin" in list) shouldBe true
            ("check" in list) shouldBe false
        }

        test("entries and queries are normalised (case, curly apostrophes)") {
            ("gosh" in list) shouldBe true
            ("GOSH" in list) shouldBe true
            ("don't" in list) shouldBe true
        }

        test("lists combine") {
            val both = list + WordBlocklist.parse("drat")
            ("drat" in both) shouldBe true
            ("heckin" in both) shouldBe true
            (WordBlocklist.Empty + list).size shouldBe list.size
            WordBlocklist.Empty.size shouldBe 0
        }
    }

    context("engine") {
        val en = TestNgramModel(
            "en",
            unigrams = mapOf(
                "what" to 500, "the" to 1000, "heck" to 300, "hell" to 200, "help" to 150, "hello" to 100,
                "darn" to 400, "dark" to 50, "barn" to 20,
            ),
            bigrams = mapOf(("the" to "heck") to 50, ("the" to "hell") to 40, ("the" to "help") to 5, (BOS to "what") to 10),
        )
        val engine = PredictionEngine(listOf(en))
        val blocked: (String) -> Boolean = { it == "heck" || it == "darn" }
        val ctx = PredictionContext(listOf("what", "the"))

        test("blocked words are not predicted as next word") {
            engine.suggest(ctx, "").candidates.map { it.word } shouldContain "heck"
            val r = engine.suggest(ctx, "", isBlocked = blocked)
            r.candidates.map { it.word } shouldNotContain "heck"
            r.candidates.first().word shouldBe "hell"
        }

        test("blocked words are not completed") {
            engine.suggest(ctx, "he").candidates.map { it.word } shouldContain "heck"
            val r = engine.suggest(ctx, "he", isBlocked = blocked)
            r.candidates.map { it.word } shouldNotContain "heck"
            r.displaySlots(3).map { it.word } shouldNotContain "heck"
        }

        test("a typo whose best explanation is blocked stays as typed instead of taking the next best word") {
            engine.suggest(PredictionContext.FieldStart, "dran").autocorrect.shouldNotBeNull().word shouldBe "darn"
            val r = engine.suggest(PredictionContext.FieldStart, "dran", isBlocked = blocked)
            r.autocorrect.shouldBeNull()
            r.candidates.map { it.word } shouldNotContain "darn"
        }

        test("a blocked word typed exactly is kept and offered") {
            val r = engine.suggest(ctx, "heck", isBlocked = blocked)
            r.autocorrect.shouldBeNull()
            r.literal.shouldNotBeNull().word shouldBe "heck"
            r.isLiteralKnown shouldBe true
        }

        test("without a predicate nothing changes") {
            val a = engine.suggest(ctx, "he")
            val b = engine.suggest(ctx, "he", isBlocked = { false })
            b.candidates shouldBe a.candidates
            b.autocorrect shouldBe a.autocorrect
        }
    }
})
