/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Every assertion of the Windows reference suite (tests/test_text_cleanup.py at ba94c11b) is ported
 * here verbatim, followed by Android-specific cases for JVM Unicode boundaries and literal
 * replacements.
 */
class TranscriptCleanupTest : FunSpec({
    test("sentence fillers preserve capitalization and Latin dotted names") {
        TranscriptCleanup.removeFillers("Uh. Um. hello", listOf("uh", "um")) shouldBe "Hello"
        TranscriptCleanup.removeFillers("foo.um next", listOf("um")) shouldBe "foo.um next"
    }

    test("stored whitespace cannot bypass duplicate validation") {
        val document = SpeechDictionaryDocument(
            words = listOf(VocabularyEntry(1, "  Ownkey  ")),
            corrections = listOf(CorrectionEntry(2, " own   key ", "Ownkey")),
        )
        SpeechDictionaryValidation.validateWord(document, "Ownkey") shouldBe EntryError.DUPLICATE_WORD
        SpeechDictionaryValidation.validateCorrection(document, "own key", "Ownkey") shouldBe EntryError.DUPLICATE_CORRECTION
    }
    val words = FillerRules.fillerWords(listOf("en", "nl"))
    fun clean(text: String) = TranscriptCleanup.removeFillers(text, words)
    fun cleaner(
        removeFillers: Boolean = true,
        languages: List<String> = listOf("en", "nl"),
        custom: List<String> = emptyList(),
        corrections: List<CorrectionRule> = emptyList(),
    ) = TranscriptCleaner(CleanupSettings(removeFillers, FillerRules.fillerWords(languages, custom), corrections))

    context("Windows parity: filler removal") {
        test("mid sentence fillers and pause commas disappear") {
            clean("I think, uh, we should ship it.") shouldBe "I think, we should ship it."
            clean("I think uh, we should ship it.") shouldBe "I think we should ship it."
            clean("I think uh we should ship it.") shouldBe "I think we should ship it."
        }
        test("sentence start filler recapitalizes the next word") {
            clean("Um, hello there.") shouldBe "Hello there."
            clean("Yes. Uh, then we left.") shouldBe "Yes. Then we left."
            clean("Um. Hello.") shouldBe "Hello."
        }
        test("sentence end punctuation survives") {
            clean("We should go, uh.") shouldBe "We should go."
            clean("I think, uh. We should go.") shouldBe "I think. We should go."
        }
        test("repeated and Dutch fillers") {
            clean("Dat is uh uhm, ehm goed.") shouldBe "Dat is goed."
            clean("Dat is, eh, gewoon dus goed.") shouldBe "Dat is, gewoon dus goed."
        }
        test("meaningful words stay") {
            clean("Uh-huh, that is like, well, fine.") shouldBe "Uh-huh, that is like, well, fine."
            clean("Er is een umbrella.") shouldBe "Er is een umbrella."
            clean("Hmm, not sure.") shouldBe "Hmm, not sure."
        }
        test("language awareness protects ordinary words") {
            FillerRules.fillerWords(listOf("en")) shouldContain "er"
            FillerRules.fillerWords(listOf("en", "nl")) shouldNotContain "er"
            FillerRules.fillerWords(listOf("en")) shouldContain "um"
            FillerRules.fillerWords(listOf("en", "de")) shouldNotContain "um"
            TranscriptCleanup.removeFillers("Wir gehen um acht.", FillerRules.fillerWords(listOf("en", "de"))) shouldBe "Wir gehen um acht."
        }
        test("explicit empty language list keeps only custom words") {
            FillerRules.normalizeLanguages(emptyList()) shouldBe emptyList()
            FillerRules.normalizeLanguages(listOf("NL", "xx", "nl")) shouldBe listOf("nl")
            FillerRules.fillerWords(emptyList()) shouldBe emptyList()
            FillerRules.fillerWords(emptyList(), listOf("basically")) shouldBe listOf("basically")
            cleaner(languages = emptyList(), custom = listOf("basically")).clean("Um, we basically left.") shouldBe "Um, we left."
        }
        test("custom fillers and empty lists") {
            TranscriptCleanup.removeFillers("So basically, we, basically, left.", FillerRules.fillerWords(listOf("en"), listOf("basically"))) shouldBe
                "So we, left."
            TranscriptCleanup.removeFillers("uh uh", emptyList()) shouldBe "uh uh"
            clean("") shouldBe ""
        }
        test("newlines are preserved") {
            clean("First line, uh.\num second line") shouldBe "First line.\nSecond line"
            clean("First line\num second line") shouldBe "First line\nSecond line"
            clean("First line,\num second line") shouldBe "First line,\nSecond line"
            clean("Eerste alinea.\n\nUhm tweede alinea.") shouldBe "Eerste alinea.\n\nTweede alinea."
        }
        test("intentional casing survives a removed leading filler") {
            clean("Um, iPhone works.") shouldBe "iPhone works."
            clean("Yes. Um, eBay works.") shouldBe "Yes. eBay works."
            clean("Um, NASA works.") shouldBe "NASA works."
            clean("Um, hello there.") shouldBe "Hello there."
        }
    }

    context("Windows parity: corrections") {
        test("whole word case insensitive replacement") {
            val rules = listOf(CorrectionRule("own key", "Ownkey"), CorrectionRule("bart", "Bart"))
            TranscriptCleanup.applyCorrections("Own key is by bart, not bartender.", rules) shouldBe "Ownkey is by Bart, not bartender."
        }
        test("normalization drops incomplete and duplicate rules") {
            val rules = TranscriptCleanup.normalizeCorrections(
                listOf(
                    CorrectionRule(" a ", "b"),
                    CorrectionRule("a", "c"),
                    CorrectionRule("x", ""),
                    CorrectionRule("same", "Same"),
                    CorrectionRule("p", "q"),
                ),
            )
            rules shouldBe listOf(CorrectionRule("a", "b"), CorrectionRule("same", "Same"), CorrectionRule("p", "q"))
        }
        test("vocabulary normalization") {
            TranscriptCleanup.normalizeVocabulary(listOf("Ownkey", " ownkey ", "", "Orukeet\tv1")) shouldBe listOf("Ownkey", "Orukeet v1")
            TranscriptCleanup.normalizeVocabulary(emptyList()) shouldBe emptyList()
        }
    }

    context("Windows parity: pipeline") {
        test("clean transcript applies fillers then corrections") {
            cleaner(corrections = listOf(CorrectionRule("own key", "Ownkey"))).clean("Um, own key uh works.") shouldBe "Ownkey works."
        }
        test("filler removal can be disabled while corrections still apply") {
            cleaner(removeFillers = false).clean("Um, hello.") shouldBe "Um, hello."
            cleaner(removeFillers = false, corrections = listOf(CorrectionRule("hello", "hi"))).clean("Um, hello.") shouldBe "Um, hi."
        }
    }

    context("Android additions") {
        test("accented German fillers and Unicode word boundaries") {
            val german = FillerRules.fillerWords(listOf("de"))
            TranscriptCleanup.removeFillers("Äh, das ist, ähm, gut.", german) shouldBe "Das ist, gut."
            // `äh` inside a longer word keeps its letters because the boundary is Unicode aware.
            TranscriptCleanup.removeFillers("Die Zähne sind sauber.", german) shouldBe "Die Zähne sind sauber."
        }
        test("apostrophes and hyphens keep neighbouring words intact") {
            clean("Uh-oh, it's, um, Bart's turn.") shouldBe "Uh-oh, it's, Bart's turn."
            clean("The e-mail, uh, arrived.") shouldBe "The e-mail, arrived."
        }
        test("corrections keep punctuation bearing names and treat replacements literally") {
            TranscriptCleanup.applyCorrections("Ask bart's team.", listOf(CorrectionRule("bart", "Bart"))) shouldBe "Ask Bart's team."
            TranscriptCleanup.applyCorrections("Price is dollar.", listOf(CorrectionRule("dollar", "$5 \\ more"))) shouldBe "Price is $5 \\ more."
            TranscriptCleanup.applyCorrections("Send an e mail.", listOf(CorrectionRule("e mail", "e-mail"))) shouldBe "Send an e-mail."
            TranscriptCleanup.applyCorrections("Call zoe now.", listOf(CorrectionRule("zoe", "Zoë"))) shouldBe "Call Zoë now."
            TranscriptCleanup.applyCorrections("Where is zoë?", listOf(CorrectionRule("Zoë", "Zoe"))) shouldBe "Where is Zoe?"
        }
        test("corrections run in saved order and can cascade") {
            val rules = listOf(CorrectionRule("a", "b"), CorrectionRule("b", "c"))
            TranscriptCleanup.applyCorrections("a b", rules) shouldBe "c c"
            val reversed = listOf(CorrectionRule("b", "c"), CorrectionRule("a", "b"))
            TranscriptCleanup.applyCorrections("a b", reversed) shouldBe "b c"
        }
        test("case only corrections apply and paragraphs survive the whole pipeline") {
            val compiled = cleaner(corrections = listOf(CorrectionRule("ownkey", "Ownkey")))
            compiled.clean("Uhm, ownkey is here.\n\nUm, ownkey works.") shouldBe "Ownkey is here.\n\nOwnkey works."
        }
        test("custom removals deliberately override language protection") {
            cleaner(languages = listOf("nl"), custom = listOf("er")).clean("Er is er, uh, niets.") shouldBe "Is niets."
        }
        test("filler only speech leaves at most punctuation and identity cleaners are recognised") {
            // Same as the reference: the sentence-ending full stop survives; the dictation path
            // treats a result without letters or digits as nothing to insert.
            cleaner().clean("Uh, um. Ehm.") shouldBe "."
            cleaner().clean("Uh, um") shouldBe ""
            cleaner().isIdentity shouldBe false
            cleaner(removeFillers = false).isIdentity shouldBe true
            cleaner(languages = emptyList()).isIdentity shouldBe true
        }
        test("compiled cleaner matches the reference functions") {
            val compiled = cleaner(corrections = listOf(CorrectionRule("own key", "Ownkey")))
            val text = "Um, own key uh works.\nAnd, uh, own key again."
            compiled.clean(text) shouldBe TranscriptCleanup.applyCorrections(
                TranscriptCleanup.removeFillers(text, words),
                listOf(CorrectionRule("own key", "Ownkey")),
            )
        }
        test("large inputs stay fast enough for the dictation path") {
            val corrections = (1..100).map { CorrectionRule("word$it", "Word$it") }
            val compiled = cleaner(corrections = corrections)
            val text = buildString { repeat(700) { append("Um, word${it % 120} is, uh, here. ") } }.take(10_000)
            compiled.clean(text) // warm up
            val start = System.nanoTime()
            repeat(20) { compiled.clean(text) }
            val averageMs = (System.nanoTime() - start) / 20 / 1_000_000.0
            // Best-effort sanity bound, not the device budget: it only catches catastrophic
            // backtracking, so it is generous enough for a loaded CI runner.
            (averageMs < 5_000.0) shouldBe true
        }
    }
})
