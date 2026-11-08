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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AutocorrectUndoTrackerTest : FunSpec({
    test("returns undo replacement for corrected token before cursor") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "teh", correctedCandidate = correctedCandidate)

        val content = contentAtCursor(text = "I like the ")
        val replacement = tracker.findUndoReplacement(content)

        replacement shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 7, end = 10),
            originalToken = "teh",
            candidate = correctedCandidate,
        )
    }

    test("returns undo replacement when corrected token is current word") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "hello", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "helo", correctedCandidate = correctedCandidate)

        val text = "hello"
        val content = EditorContent(
            text = text,
            offset = 0,
            localSelection = EditorRange.cursor(text.length),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange(0, text.length),
        )

        tracker.findUndoReplacement(content) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(0, text.length),
            originalToken = "helo",
            candidate = correctedCandidate,
        )
    }

    test("undoes a missed-space correction that spans two words") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "this is", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "thisis", correctedCandidate = correctedCandidate)

        tracker.findBackspaceRestoreReplacement(contentAtCursor("I think this is ")) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 8, end = 15),
            originalToken = "thisis",
            candidate = correctedCandidate,
        )
        // Only the whole pair counts: "xthis is" does not end with the corrected words as separate words.
        tracker.findBackspaceRestoreReplacement(contentAtCursor("I think xthis is ")).shouldBeNull()
    }

    test("undo only applies right after the correction") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true)
        // "I like teh" became "I like the"; the corrected word ends at 10.
        tracker.trackAutoCorrect(originalToken = "teh", correctedCandidate = correctedCandidate, correctedEnd = 10)

        tracker.findUndoReplacement(contentAtCursor("I like the ")) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 7, end = 10),
            originalToken = "teh",
            candidate = correctedCandidate,
        )
        // Typed on, and ended with the same word again: that "the" was typed correctly and must stay.
        tracker.findUndoReplacement(contentAtCursor("I like the cat sat on the ")).shouldBeNull()
        tracker.findBackspaceRestoreReplacement(contentAtCursor("I like the cat sat on the ")).shouldBeNull()
    }

    test("undo does not reach across several separators or a selection") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "teh",
            correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true),
        )
        tracker.findBackspaceRestoreReplacement(contentAtCursor("I saw the.\n\n")).shouldBeNull()

        val text = "I saw the cat"
        val selected = EditorContent(
            text = text,
            offset = 0,
            localSelection = EditorRange(10, 13),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange.Unspecified,
        )
        tracker.findUndoReplacement(selected).shouldBeNull()
    }

    test("does not return undo replacement when token near cursor does not match") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "teh",
            correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true),
        )

        tracker.findUndoReplacement(contentAtCursor("I like tea ")).shouldBeNull()
    }

    test("returns backspace restore replacement for corrected token before trailing space") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "teh", correctedCandidate = correctedCandidate)

        tracker.findBackspaceRestoreReplacement(contentAtCursor("I like the ")) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 7, end = 10),
            originalToken = "teh",
            candidate = correctedCandidate,
        )
    }

    test("returns backspace restore replacement when cursor is at end of corrected current word") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "hello", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "helo", correctedCandidate = correctedCandidate)

        tracker.findBackspaceRestoreReplacement(wordContent(text = "hello", cursor = 5)) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(0, 5),
            originalToken = "helo",
            candidate = correctedCandidate,
        )
    }

    test("does not return backspace restore replacement when cursor moved inside corrected word") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "helo",
            correctedCandidate = WordSuggestionCandidate(text = "hello", isEligibleForAutoCommit = true),
        )

        tracker.findBackspaceRestoreReplacement(wordContent(text = "hello", cursor = 3)).shouldBeNull()
    }

    test("undo does not apply with the cursor moved inside the corrected word") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "helo",
            correctedCandidate = WordSuggestionCandidate(text = "hello", isEligibleForAutoCommit = true),
        )

        tracker.findUndoReplacement(wordContent(text = "hello", cursor = 3)).shouldBeNull()
        tracker.findUndoReplacement(wordContent(text = "hello", cursor = 5)).shouldNotBeNull()
    }

    test("does not return backspace restore replacement for active text selection") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "helo",
            correctedCandidate = WordSuggestionCandidate(text = "hello", isEligibleForAutoCommit = true),
        )

        val content = EditorContent(
            text = "hello world",
            offset = 0,
            localSelection = EditorRange(0, 5),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange(0, 5),
        )

        tracker.findBackspaceRestoreReplacement(content).shouldBeNull()
    }

    test("undo reports the subtype the correction was made on") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true)
        // Not the default subtype, so a fallback to a default or the active subtype would fail.
        val subtype = Subtype.DEFAULT.copy(id = 42L)
        tracker.trackAutoCorrect(originalToken = "teh", correctedCandidate = correctedCandidate, subtype = subtype)

        tracker.findUndoReplacement(contentAtCursor("I like the "))?.subtype shouldBe subtype
        tracker.findBackspaceRestoreReplacement(contentAtCursor("I like the "))?.subtype shouldBe subtype
        tracker.subtypeForCandidate(correctedCandidate) shouldBe subtype
        (subtype == Subtype.DEFAULT) shouldBe false
        tracker.subtypeForCandidate(WordSuggestionCandidate(text = "tea", isEligibleForAutoCommit = true)).shouldBeNull()
    }

    test("returns original token for matching candidate") {
        val tracker = AutocorrectUndoTracker()
        val correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "teh", correctedCandidate = correctedCandidate)

        tracker.originalTokenForCandidate(correctedCandidate) shouldBe "teh"
        tracker.originalTokenForCandidate(WordSuggestionCandidate(text = "tea", isEligibleForAutoCommit = true)).shouldBeNull()
    }

    test("a revision of the previous word is undone first, keeping the cursor at the end") {
        val tracker = AutocorrectUndoTracker()
        // "ok gaa": "gaa" was autocorrected to "ga", then "ok" was revised to "ik".
        val autocorrected = WordSuggestionCandidate(text = "ga", isEligibleForAutoCommit = true)
        tracker.trackAutoCorrect(originalToken = "gaa", correctedCandidate = autocorrected)
        val revised = WordSuggestionCandidate(text = "ik", isEligibleForAutoCommit = true)
        tracker.trackRevision(originalToken = "ok", revisedCandidate = revised, suffix = " ga ", cursor = 10)

        val replacement = tracker.findBackspaceRestoreReplacement(contentAtCursor("Dus ik ga ")).shouldNotBeNull()
        replacement shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 4, end = 6),
            originalToken = "ok",
            candidate = revised,
            cursorAfter = 10,
        )
        replacement.isRevision shouldBe true
        tracker.findUndoReplacement(contentAtCursor("Dus ik ga ")) shouldBe replacement

        // Once the revision is undone, the next backspace restores the autocorrection.
        tracker.clearRevision()
        tracker.findBackspaceRestoreReplacement(contentAtCursor("Dus ok ga ")) shouldBe AutocorrectUndoReplacement(
            range = EditorRange(start = 7, end = 9),
            originalToken = "gaa",
            candidate = autocorrected,
        )
    }

    test("a revision is only undone while the text still ends with it") {
        val tracker = AutocorrectUndoTracker()
        val revised = WordSuggestionCandidate(text = "ik", isEligibleForAutoCommit = true)
        tracker.trackRevision(originalToken = "ok", revisedCandidate = revised, suffix = " ga ", cursor = 6)
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga n")).shouldBeNull()
        tracker.findBackspaceRestoreReplacement(contentAtCursor("dik ga ")).shouldBeNull()
        tracker.findBackspaceRestoreReplacement(
            EditorContent(
                text = "ik ga ",
                offset = 0,
                localSelection = EditorRange(0, 2),
                localComposing = EditorRange.Unspecified,
                localCurrentWord = EditorRange.Unspecified,
            ),
        ).shouldBeNull()
        // A longer original moves the cursor back by the difference.
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga ")).shouldNotBeNull().cursorAfter shouldBe 6
        tracker.trackRevision(originalToken = "okee", revisedCandidate = revised, suffix = " ga ", cursor = 6)
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga ")).shouldNotBeNull().cursorAfter shouldBe 8
        // Clearing everything forgets the revision too.
        tracker.clearPending()
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga ")).shouldBeNull()
        // Identical or blank tokens are not tracked.
        tracker.trackRevision(originalToken = "ik", revisedCandidate = revised, suffix = " ga ", cursor = 6)
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga ")).shouldBeNull()
    }

    test("a revision is only undone with the cursor where the revision left it") {
        val tracker = AutocorrectUndoTracker()
        val revised = WordSuggestionCandidate(text = "ik", isEligibleForAutoCommit = true)
        tracker.trackRevision(originalToken = "ok", revisedCandidate = revised, suffix = " ga ", cursor = 6)
        // The same text before the cursor, but the cursor is elsewhere: text was inserted before it, or the user
        // moved the cursor to an identical phrase. Backspace deletes there.
        tracker.findBackspaceRestoreReplacement(contentAtCursor("xx ik ga ")).shouldBeNull()
        tracker.findUndoReplacement(contentAtCursor("xx ik ga ")).shouldBeNull()
        tracker.findBackspaceRestoreReplacement(contentAtCursor("ik ga ")).shouldNotBeNull()
    }

    test("ignores invalid autocorrect history with blank or identical tokens") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "same",
            correctedCandidate = WordSuggestionCandidate(text = "same", isEligibleForAutoCommit = true),
        )
        tracker.findUndoReplacement(contentAtCursor("same ")).shouldBeNull()

        tracker.trackAutoCorrect(
            originalToken = "   ",
            correctedCandidate = WordSuggestionCandidate(text = "word", isEligibleForAutoCommit = true),
        )
        tracker.findUndoReplacement(contentAtCursor("word ")).shouldBeNull()
    }
})

private fun contentAtCursor(text: String): EditorContent {
    return EditorContent(
        text = text,
        offset = 0,
        localSelection = EditorRange.cursor(text.length),
        localComposing = EditorRange.Unspecified,
        localCurrentWord = EditorRange.Unspecified,
    )
}

private fun wordContent(text: String, cursor: Int): EditorContent {
    return EditorContent(
        text = text,
        offset = 0,
        localSelection = EditorRange.cursor(cursor),
        localComposing = EditorRange.Unspecified,
        localCurrentWord = EditorRange(0, text.length),
    )
}
