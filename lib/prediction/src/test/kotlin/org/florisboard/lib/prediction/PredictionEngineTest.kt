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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class PredictionEngineTest : FunSpec({
    fun ctx(text: String) = PredictionContext(text.split(' ').filter { it.isNotBlank() })

    context("autocorrects typical typos") {
        withData(
            nameFn = { "${it.second} -> ${it.third}" },
            Triple(TestModels.enOnly, "teh", "the"),
            Triple(TestModels.enOnly, "becuase", "because"),
            Triple(TestModels.enOnly, "helo", "hello"),
            Triple(TestModels.enOnly, "wprld", "world"),
            Triple(TestModels.nlOnly, "gistren", "gisteren"),
            Triple(TestModels.nlEn, "gistren", "gisteren"),
            Triple(TestModels.nlEn, "becuase", "because"),
            Triple(TestModels.nlEn, "morgne", "morgen"),
        ) { (engine, input, target) ->
            val r = engine.suggest(PredictionContext.FieldStart, input)
            r.autocorrect.shouldNotBeNull().word shouldBe target
            r.displaySlots(8).first().word shouldBe target
        }
    }

    test("switches to the more probable surface of the same key form") {
        val engine = PredictionEngine(listOf(TestModels.model("en", "don't" to 1000, "dont" to 10, "done" to 300)))
        val r = engine.suggest(PredictionContext.FieldStart, "dont")
        r.isLiteralKnown shouldBe true
        r.autocorrect.shouldNotBeNull().word shouldBe "don't"
        r.autocorrect!!.kind shouldBe CandidateKind.EXACT
        // The literal stays available within the three visible slots.
        r.displaySlots(3).map { it.word } shouldContain "dont"
    }

    test("keeps a real word whose other spelling is only somewhat more probable") {
        val engine = PredictionEngine(listOf(TestModels.model("en", "we'd" to 400, "wed" to 100, "the" to 5000)))
        // "wed" has 20% of its key form's mass: log-odds 1.4 < surfaceSwitchMargin.
        engine.config.surfaceSwitchMargin shouldBe (2.0 plusOrMinus 1e-9)
        engine.suggest(PredictionContext.FieldStart, "wed").autocorrect.shouldBeNull()
        engine.withConfig(engine.config.copy(surfaceSwitchMargin = 1.0))
            .suggest(PredictionContext.FieldStart, "wed").autocorrect.shouldNotBeNull().word shouldBe "we'd"
    }

    context("never changes correctly typed common words") {
        withData(
            "ik denk dat het morgen gaat regenen",
            "we gaan vanavond uit eten met de kinderen",
            "heb je de sleutels van de auto gezien",
            "i think we should leave a bit earlier tomorrow",
            "can you send me the slides before the meeting",
            "ik heb de meeting verplaatst naar donderdag",
            "the deadline is next friday so we have time",
        ) { sentence ->
            val words = sentence.split(' ')
            for (i in words.indices) {
                val r = TestModels.nlEn.suggest(PredictionContext(words.take(i)), words[i])
                val ac = r.autocorrect
                if (ac != null) TextFolding.keyForm(ac.word) shouldBe words[i]
            }
        }
    }

    test("language weights follow the context") {
        val nl = TestModels.nlEn.suggest(ctx("ik denk dat we"), "")
        nl.languageWeights["nl"]!! shouldBeGreaterThan 0.9
        val en = TestModels.nlEn.suggest(ctx("i think that we"), "")
        en.languageWeights["en"]!! shouldBeGreaterThan 0.9
        val start = TestModels.nlEn.suggest(PredictionContext.FieldStart, "")
        start.languageWeights["nl"]!! shouldBeGreaterThan start.languageWeights["en"]!!
        start.languageWeights.values.sum() shouldBe (1.0 plusOrMinus 1e-9)
    }

    test("mixed Dutch-English: English words inside a Dutch sentence are corrected and completed") {
        val r = TestModels.nlEn.suggest(ctx("ik heb de"), "meetimg")
        r.autocorrect.shouldNotBeNull().word shouldBe "meeting"
        val c = TestModels.nlEn.suggest(ctx("we hebben een"), "deadl")
        c.displaySlots(3).map { it.word } shouldContain "deadline"
        val e = TestModels.nlEn.suggest(ctx("i will"), "becuase")
        e.autocorrect.shouldNotBeNull().language shouldBe "en"
    }

    test("completions rank by probability, not by length") {
        val r = TestModels.enOnly.suggest(PredictionContext.FieldStart, "consi")
        r.autocorrect.shouldBeNull()
        val slots = r.displaySlots(8).map { it.word }
        slots.take(3) shouldContainAll listOf("consider", "considered", "considering")
        r.candidates.first().kind shouldBe CandidateKind.COMPLETION
    }

    test("a known literal moves behind three useful alternatives; posteriors sum to at most one") {
        val r = TestModels.enOnly.suggest(PredictionContext.FieldStart, "the")
        r.isLiteralKnown shouldBe true
        r.autocorrect.shouldBeNull()
        val slots = r.displaySlots(8)
        slots.indexOfFirst { it.word == "the" } shouldBe 3
        slots.map { it.keyForm }.toSet().size shouldBe slots.size
        (r.candidates.sumOf { it.prob } <= 1.0 + 1e-9) shouldBe true
    }

    context("the typed literal is visible within three slots while an autocorrect is pending") {
        withData(
            nameFn = { "${it.second} -> ${it.third}" },
            Triple(TestModels.enOnly, "becuase", "because"),
            Triple(TestModels.enOnly, "teh", "the"),
            Triple(TestModels.enOnly, "wprld", "world"),
            Triple(TestModels.nlEn, "gistren", "gisteren"),
            Triple(TestModels.nlEn, "teh", "the"),
        ) { (engine, input, target) ->
            val r = engine.suggest(PredictionContext.FieldStart, input)
            r.autocorrect.shouldNotBeNull().word shouldBe target
            val slots = r.displaySlots(3)
            slots[0].word shouldBe target
            slots.indexOfFirst { it.word == input } shouldBe engine.config.pendingLiteralSlot
            slots.first { it.word == input }.kind shouldBe CandidateKind.LITERAL
        }
    }

    test("an engine needs at least one language model") {
        shouldThrow<IllegalArgumentException> { PredictionEngine(emptyList()) }
    }

    test("unknown literals below minUnknownAutocorrectLength are kept") {
        val engine = PredictionEngine(listOf(TestModels.model("en", "no" to 1000, "on" to 500, "nope" to 50)))
        val lenient = engine.withConfig(engine.config.copy(minUnknownAutocorrectLength = 2))
        lenient.suggest(PredictionContext.FieldStart, "np").autocorrect.shouldNotBeNull().word shouldBe "no"
        val strict = engine.withConfig(engine.config.copy(minUnknownAutocorrectLength = 3))
        strict.suggest(PredictionContext.FieldStart, "np").autocorrect.shouldBeNull()
        strict.suggest(PredictionContext.FieldStart, "npe").autocorrect.shouldNotBeNull().word shouldBe "nope"
    }

    test("input longer than maxDecodeLength is only matched exactly") {
        val engine = TestModels.enOnly.withConfig(EngineConfig(maxDecodeLength = 4))
        val exact = engine.suggest(PredictionContext.FieldStart, "hello")
        exact.isLiteralKnown shouldBe true
        exact.candidates.isNotEmpty() shouldBe true
        exact.candidates.all { it.kind == CandidateKind.EXACT && it.keyForm == "hello" } shouldBe true
        val typo = engine.suggest(PredictionContext.FieldStart, "hellp")
        typo.isLiteralKnown shouldBe false
        typo.candidates.isEmpty() shouldBe true
        typo.autocorrect.shouldBeNull()
        val huge = TestModels.enOnly.suggest(PredictionContext.FieldStart, "ab".repeat(1500))
        huge.autocorrect.shouldBeNull()
        huge.literal.shouldNotBeNull().kind shouldBe CandidateKind.LITERAL
    }

    test("a context-free model does not predict the previous word again") {
        val first = TestModels.nlOnly.suggest(PredictionContext.FieldStart, "").displaySlots(3).first().word
        val after = TestModels.nlOnly.suggest(ctx(first), "")
        after.displaySlots(3).size shouldBe 3
        after.candidates.map { it.word } shouldNotContain first
    }

    test("inputs with non-letters are never autocorrected") {
        TestModels.enOnly.suggest(PredictionContext.FieldStart, "teh1").autocorrect.shouldBeNull()
        TestModels.enOnly.suggest(PredictionContext.FieldStart, "wo'rld").autocorrect.shouldBeNull()
    }

    test("next-word prediction works at field start and after words") {
        val start = TestModels.nlEn.suggest(PredictionContext.FieldStart, "")
        start.candidates.isNotEmpty() shouldBe true
        start.displaySlots(3).size shouldBe 3
        start.candidates.all { it.kind == CandidateKind.NEXT_WORD } shouldBe true
        start.autocorrect.shouldBeNull()
        val after = TestModels.nlEn.suggest(ctx("ik"), "")
        after.displaySlots(8).size shouldBeLessThanOrEqual 8
    }

    test("config overrides by name") {
        val c = EngineConfig().with(mapOf("alpha" to "1.5", "maxEdits" to "1", "autocorrectEnabled" to "off"))
        c.alpha shouldBe 1.5
        c.maxEdits shouldBe 1
        c.autocorrectEnabled shouldBe false
        c.toMap().keys shouldBe EngineConfig.parameterNames
        TestModels.enOnly.withConfig(c).suggest(PredictionContext.FieldStart, "teh").autocorrect.shouldBeNull()
    }
})
