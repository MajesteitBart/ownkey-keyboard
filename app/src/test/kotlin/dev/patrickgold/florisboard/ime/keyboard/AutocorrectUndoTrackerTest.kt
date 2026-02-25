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

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
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

    test("does not return undo replacement when token near cursor does not match") {
        val tracker = AutocorrectUndoTracker()
        tracker.trackAutoCorrect(
            originalToken = "teh",
            correctedCandidate = WordSuggestionCandidate(text = "the", isEligibleForAutoCommit = true),
        )

        tracker.findUndoReplacement(contentAtCursor("I like tea ")).shouldBeNull()
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
