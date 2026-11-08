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
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class RevisionTest : FunSpec({
    // A Dutch-like model in which "ok" (a real word) is a common slip for "ik" before a verb: "ik ga", "ik ben". No word
    // list, so the real-word relief of autocorrect (deltaKnownLmSlope) stays off and only the revision can fix it.
    fun model() = TestNgramModel(
        "nl",
        unigrams = mapOf(
            "ik" to 100_000L, "ok" to 300L, "ga" to 8_000L, "ben" to 9_000L, "naar" to 6_000L, "huis" to 3_000L,
            "dat" to 20_000L, "is" to 30_000L, "goed" to 5_000L, "oké" to 200L,
        ),
        bigrams = mapOf(
            (TestNgramModel.BOS to "ik") to 6_000L, (TestNgramModel.BOS to "dat") to 3_000L,
            ("ik" to "ga") to 3_000L, ("ik" to "ben") to 3_000L,
            ("ga" to "naar") to 1_000L, ("naar" to "huis") to 500L,
            ("dat" to "is") to 4_000L, ("is" to "goed") to 800L, ("ok" to "dat") to 50L,
        ),
    )
    val engine = PredictionEngine(listOf(model()))
    val start = PredictionContext.FieldStart

    test("a real-word slip is revised once the next word shows what was meant") {
        // At the space after "ok" the left context alone is not enough to replace a real word.
        engine.suggest(start, "ok").autocorrect.shouldBeNull()
        val revised = engine.revise(start, typed = "ok", committed = "ok", next = "ga").shouldNotBeNull()
        revised.word shouldBe "ik"
        revised.kind shouldBe CandidateKind.CORRECTION
        val p = engine.proposeRevision(start, "ok", "ok", "ga").shouldNotBeNull()
        p.passes shouldBe true
        p.margin shouldBeGreaterThanOrEqual engine.config.reviseMarginShort
        p.rightGain shouldBeGreaterThanOrEqual engine.config.reviseMinRightGain
        p.committedClass shouldBe RevisionProposal.CommittedClass.VALID
    }

    test("the next word must argue for the change") {
        // "ok dat ..." is fine: the right context prefers the typed word.
        engine.revise(start, "ok", "ok", "dat").shouldBeNull()
        // A next word that neither reading predicts gives no right-context gain.
        engine.revise(start, "ok", "ok", "huis").shouldBeNull()
        // A next word no model knows says nothing.
        engine.revise(start, "ok", "ok", "xqzw").shouldBeNull()
    }

    test("correctly typed words stay") {
        engine.revise(start, "ik", "ik", "ga").shouldBeNull()
        engine.revise(PredictionContext(listOf("ik")), "ga", "ga", "naar").shouldBeNull()
        engine.revise(PredictionContext(listOf("dat")), "is", "is", "goed").shouldBeNull()
    }

    test("protected, blocked and disabled") {
        engine.revise(start, "ok", "ok", "ga", isProtected = { it == "ok" }).shouldBeNull()
        engine.revise(start, "ok", "ok", "ga", isBlocked = { it == "ik" }).shouldBeNull()
        engine.withConfig(engine.config.copy(reviseEnabled = false)).revise(start, "ok", "ok", "ga").shouldBeNull()
        engine.withConfig(engine.config.copy(autocorrectEnabled = false)).revise(start, "ok", "ok", "ga").shouldBeNull()
    }

    test("thresholds: margin, right-context gain and the slip-of-the-hand cost cap") {
        engine.withConfig(engine.config.copy(reviseMarginShort = 99.0)).revise(start, "ok", "ok", "ga").shouldBeNull()
        engine.withConfig(engine.config.copy(reviseMinRightGain = 99.0)).revise(start, "ok", "ok", "ga").shouldBeNull()
        // "i" for "o" is a near key; a cap below its channel cost rules it out.
        engine.withConfig(engine.config.copy(reviseMaxExtraCost = 0.5)).revise(start, "ok", "ok", "ga").shouldBeNull()
        val p = engine.withConfig(engine.config.copy(reviseMarginShort = 99.0)).proposeRevision(start, "ok", "ok", "ga")
            .shouldNotBeNull()
        p.passes shouldBe false
        p.candidate.word shouldBe "ik"
        (p.extraCost > 0.5) shouldBe true
    }

    test("the committed word must be an interpretation of the typed letters") {
        engine.revise(start, "ok", "huis", "ga").shouldBeNull()
        engine.revise(start, "", "", "ga").shouldBeNull()
        engine.revise(start, "ok", "ok", "").shouldBeNull()
        // Letters outside a-z are never revised.
        engine.revise(start, "o'k", "o'k", "ga").shouldBeNull()
    }

    test("an autocorrection is a committed word like any other") {
        // Suppose "ok" had been autocorrected to "oké" when it was typed; "ga" shows that "ik" was meant.
        engine.revise(start, "ok", "oké", "ga").shouldNotBeNull().word shouldBe "ik"
    }

    test("with touches, a valid word typed squarely on its keys is not respelled with neighbouring keys") {
        val qwerty = SpatialModel.qwerty()
        val o = qwerty.centre('o')
        val i = qwerty.centre('i')
        val k = qwerty.centre('k')
        fun towardsI(fraction: Double) = TouchPoint(o.x + fraction * (i.x - o.x), o.y + fraction * (i.y - o.y))
        // "ok" tapped on the key centres: the user hit what they meant ("ok, ga maar"), whatever the next word says.
        val centred = engine.proposeRevision(start, "ok", "ok", "ga", touches = listOf(o, k)).shouldNotBeNull()
        centred.candidate.word shouldBe "ik"
        (centred.extraCost > engine.config.reviseMaxTouchSubstitutionCost) shouldBe true
        centred.passes shouldBe false
        engine.revise(start, "ok", "ok", "ga", touches = listOf(o, k)).shouldBeNull()
        // A slip: the tap meant for "i" landed just inside "o".
        val slipped = engine.revise(start, "ok", "ok", "ga", touches = listOf(towardsI(0.4), k)).shouldNotBeNull()
        slipped.word shouldBe "ik"
        // Key codes carry no tap positions: the margin decides alone.
        engine.revise(start, "ok", "ok", "ga").shouldNotBeNull().word shouldBe "ik"
        // Off when the cap is raised past the centre cost.
        engine.withConfig(engine.config.copy(reviseMaxTouchSubstitutionCost = 99.0))
            .revise(start, "ok", "ok", "ga", touches = listOf(o, k)).shouldNotBeNull()
    }

    test("a capital that a sentence start does not explain is never revised") {
        // Auto-capitalised at the start of the field or of a sentence: revised like the lower-case word.
        engine.revise(start, "Ok", "Ok", "ga").shouldNotBeNull().word shouldBe "ik"
        engine.revise(PredictionContext(emptyList(), startsSentence = true, languageHintWords = listOf("dat")), "Ok", "Ok", "ga")
            .shouldNotBeNull()
        // Capitalised by hand in the middle of a sentence (a name), or in capitals (an acronym): not even considered,
        // while the same letters in lower case are.
        val midSentence = PredictionContext(listOf("dat"))
        engine.proposeRevision(midSentence, "ok", "ok", "ga").shouldNotBeNull()
        engine.proposeRevision(midSentence, "Ok", "Ok", "ga").shouldBeNull()
        val unknownStart = PredictionContext(emptyList(), startsSentence = false)
        engine.proposeRevision(unknownStart, "ok", "ok", "ga").shouldNotBeNull()
        engine.proposeRevision(unknownStart, "Ok", "Ok", "ga").shouldBeNull()
        engine.proposeRevision(start, "OK", "OK", "ga").shouldBeNull()
        engine.proposeRevision(start, "oK", "oK", "ga").shouldBeNull()
    }

    test("a short word that is in the vocabulary but not valid is kept, whatever the next word says") {
        // "wss" (chat for "waarschijnlijk") is rare and has no successors; "was niet" is a frequent bigram, so "niet"
        // argues for "was" through the backed-off right-context factor of "wss" alone.
        val base = TestNgramModel(
            "nl",
            unigrams = mapOf("dat" to 20_000L, "is" to 30_000L, "was" to 30_000L, "wss" to 50L, "niet" to 10_000L, "zo" to 5_000L),
            bigrams = mapOf(("dat" to "is") to 4_000L, ("was" to "niet") to 2_000L, ("niet" to "zo") to 500L),
        )
        fun flagged(invalid: Set<String>) = object : WordLanguageModel by base {
            override fun isValidWord(id: Int): Boolean = base.word(id) !in invalid
            override fun isListedWord(id: Int): Boolean = false
        }
        val ctx = PredictionContext(listOf("dat", "is"))
        val notValid = PredictionEngine(listOf(flagged(setOf("wss"))))
        notValid.suggest(ctx, "wss").autocorrect.shouldBeNull()
        val p = notValid.proposeRevision(ctx, "wss", "wss", "niet").shouldNotBeNull()
        p.candidate.word shouldBe "was"
        p.committedClass shouldBe RevisionProposal.CommittedClass.NOT_VALID
        p.margin shouldBeGreaterThanOrEqual notValid.config.reviseMarginShort
        p.rightGain shouldBeGreaterThanOrEqual notValid.config.reviseMinRightGain
        p.passes shouldBe false
        notValid.revise(ctx, "wss", "wss", "niet").shouldBeNull()
        // The rule is about that class: opted in, or with "wss" as a valid word (a name, say), the same margins revise.
        notValid.withConfig(notValid.config.copy(reviseInvalidShort = true)).revise(ctx, "wss", "wss", "niet")
            .shouldNotBeNull().word shouldBe "was"
        val valid = PredictionEngine(listOf(flagged(emptySet())))
        valid.proposeRevision(ctx, "wss", "wss", "niet").shouldNotBeNull().committedClass shouldBe RevisionProposal.CommittedClass.VALID
        valid.revise(ctx, "wss", "wss", "niet").shouldNotBeNull().word shouldBe "was"
    }

    test("the explanation lists every scored reading and the decision") {
        val lines = engine.explainRevision(start, "ok", "ok", "ga")
        lines.any { it.trimStart().startsWith("ik ") } shouldBe true
        lines.last().startsWith("revision: -> ik") shouldBe true
    }
})
