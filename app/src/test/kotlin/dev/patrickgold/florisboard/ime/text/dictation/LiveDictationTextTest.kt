/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CleanupSettings
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CorrectionRule
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.TranscriptCleaner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiveDictationTextTest : FunSpec({
    test("frozen words are committed once and live words follow them with a space") {
        val text = LiveDictationText(textBefore = "", textAfter = "")
        text.update(emptyList(), listOf("Hello", "world")) shouldBe ("" to "Hello world")
        text.update(listOf("Hello", "world."), listOf("This", "is")) shouldBe ("Hello world." to " This is")
        text.finish(listOf("This", "is", "fine.")) shouldBe " This is fine."
        text.committedText shouldBe "Hello world. This is fine."
        text.rawTranscript shouldBe "Hello world. This is fine."
    }

    test("the first piece gets a space after existing text and the last one before following text") {
        val text = LiveDictationText(textBefore = "Ownkey is a keyboard.", textAfter = "Thanks")
        text.update(listOf("It", "types."), emptyList()) shouldBe (" It types." to "")
        text.finish(listOf("Really.")) shouldBe " Really. "
    }

    test("no space after an opening bracket or before a closing one") {
        val text = LiveDictationText(textBefore = "(", textAfter = ")")
        text.update(emptyList(), listOf("aside")) shouldBe ("" to "aside")
        text.finish(listOf("aside")) shouldBe "aside"
    }

    test("a piece that continues a sentence keeps its casing when a filler at its start is removed") {
        val cleaner = TranscriptCleaner(CleanupSettings(removeFillers = true, fillerWords = listOf("uh"), corrections = emptyList()))
        val text = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        text.update(listOf("Hij", "dineerde", "met"), emptyList())
        text.update(listOf("uh", "Frank", "iedere", "dag"), emptyList()).first shouldBe " Frank iedere dag"
        text.update(listOf("uh", "in", "zijn", "club."), emptyList()).first shouldBe " in zijn club."
        text.finish(listOf("uh", "hij", "rookte.")) shouldBe " Hij rookte."
    }

    test("a new field or sentence starts with a capital; mid-sentence the decoded casing stays") {
        LiveDictationText(textBefore = "", textAfter = "").update(emptyList(), listOf("beiden", "bedolven")).second shouldBe "Beiden bedolven"
        LiveDictationText(textBefore = "Hallo daar.", textAfter = "").update(listOf("beiden"), emptyList()).first shouldBe " Beiden"
        LiveDictationText(textBefore = "Ik zei", textAfter = "").update(listOf("beiden"), emptyList()).first shouldBe " beiden"
        LiveDictationText(textBefore = "Ik zei", textAfter = "").update(listOf("Frank"), emptyList()).first shouldBe " Frank"
    }

    test("a mark decided after a pause attaches to the word before it") {
        val text = LiveDictationText(textBefore = "", textAfter = "")
        text.update(listOf("Ik", "wil", "graag"), emptyList()) shouldBe ("Ik wil graag" to "")
        text.update(listOf("."), listOf("Daarna")) shouldBe ("." to " Daarna")
        text.committedText shouldBe "Ik wil graag."
        text.rawTranscript shouldBe "Ik wil graag."
    }

    test("a mark whose words were cleaned away as fillers is dropped too") {
        val cleaner = TranscriptCleaner(CleanupSettings(removeFillers = true, fillerWords = listOf("uh"), corrections = emptyList()))
        val after = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        after.update(listOf("Hello."), emptyList())
        after.update(listOf("Uh"), emptyList())
        after.finish(listOf("."))
        after.committedText shouldBe "Hello."
        val alone = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        alone.update(listOf("Uh"), emptyList())
        alone.finish(listOf(".")) shouldBe ""
        alone.committedText shouldBe ""
        alone.rawTranscript shouldBe "Uh."
        // The same, when the mark arrives together with the next words.
        val joined = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        joined.update(listOf("Hello."), emptyList())
        joined.update(listOf("Uh"), emptyList())
        joined.update(listOf(".", "World"), emptyList())
        joined.committedText shouldBe "Hello. World"
        joined.rawTranscript shouldBe "Hello. Uh. World"
    }

    test("dictionary corrections apply to every piece") {
        val cleaner = TranscriptCleaner(CleanupSettings(removeFillers = false, fillerWords = emptyList(), corrections = listOf(CorrectionRule("own key", "Ownkey"))))
        val text = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        text.update(listOf("I", "use"), listOf("own", "key")) shouldBe ("I use" to " Ownkey")
    }

    test("a piece of only fillers adds nothing") {
        val cleaner = TranscriptCleaner(CleanupSettings(removeFillers = true, fillerWords = listOf("uh", "um"), corrections = emptyList()))
        val text = LiveDictationText(textBefore = "", textAfter = "", clean = cleaner::clean)
        text.update(listOf("uh", "um"), emptyList()) shouldBe ("" to "")
        text.finish(emptyList()) shouldBe ""
        text.committedText shouldBe ""
        text.rawTranscript shouldBe "uh um"
    }
})
