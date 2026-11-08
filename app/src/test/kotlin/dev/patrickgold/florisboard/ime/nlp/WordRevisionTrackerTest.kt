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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private fun taps(word: String) = word.map { LatinTap(1.0, 1.0) }

/** The editor after typing [text], cursor at the end; [offset] characters of the field precede it. */
private fun content(text: String, offset: Int = 0, selection: EditorRange = EditorRange.cursor(text.length)) = EditorContent(
    text = text,
    offset = offset,
    localSelection = selection,
    localComposing = EditorRange.Unspecified,
    localCurrentWord = EditorRange.Unspecified,
)

class WordRevisionTrackerTest : FunSpec({
    val session = 7L

    /** Types the words of [text] one by one, each ended with space, and returns the last request. */
    fun WordRevisionTracker.typeAll(text: String, committedAs: Map<String, String> = emptyMap()): WordRevisionRequest? {
        var request: WordRevisionRequest? = null
        var editor = ""
        for (typed in text.trim().split(' ')) {
            val committed = committedAs[typed] ?: typed
            editor += "$committed "
            request = onWordEndedWithSpace(session, typed, committed, taps(typed), null, content(editor))
        }
        return request
    }

    test("two words typed letter by letter and ended with space ask to revise the first") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "Ik", "Ik", taps("Ik"), null, content("Dag. Ik ")).shouldBeNull()
        val request = tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("Dag. Ik ok ")).shouldNotBeNull()
        // The request is about "Ik", the word before the last one.
        request.typed shouldBe "Ik"
        request.committed shouldBe "Ik"
        request.next shouldBe "ok"
        request.wordStart shouldBe 5
        request.cursor shouldBe 11
        request.textBeforeWord shouldBe "Dag. "
        request.textStartsAtFieldStart shouldBe true
        request.tail shouldBe "Ik ok "
        request.matches(content("Dag. Ik ok ")) shouldBe true
    }

    test("the committed text of an autocorrected word is what the request expects in the editor") {
        val tracker = WordRevisionTracker()
        val request = tracker.typeAll("teh ok ga", committedAs = mapOf("teh" to "the")).shouldNotBeNull()
        request.typed shouldBe "ok"
        request.textBeforeWord shouldBe "the "
        // The previous word committed as "the" while "teh" was typed.
        val t2 = WordRevisionTracker()
        t2.onWordEndedWithSpace(session, "teh", "the", taps("teh"), null, content("the ")).shouldBeNull()
        val r2 = t2.onWordEndedWithSpace(session, "cat", "cat", taps("cat"), null, content("the cat ")).shouldNotBeNull()
        r2.typed shouldBe "teh"
        r2.committed shouldBe "the"
        r2.wordStart shouldBe 0
    }

    test("words not typed letter by letter are never revised and break the chain") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", null, null, content("ok ")).shouldBeNull()
        tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldBeNull()
        // A tapped suggestion (no taps) as the last word: nothing, and the next word has no typed predecessor.
        tracker.onWordEndedWithSpace(session, "naar", "naar", null, null, content("ok ga naar ")).shouldBeNull()
        tracker.onWordEndedWithSpace(session, "huis", "huis", taps("huis"), null, content("ok ga naar huis ")).shouldBeNull()
    }

    test("only directly adjacent words in the same session and sentence") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        // Punctuation between the words (a comma, a sentence end) or a second space: the text no longer matches.
        tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("ok, ga ")).shouldBeNull()
        tracker.onWordEndedWithSpace(session, "naar", "naar", taps("naar"), null, content("ok, ga. naar ")).shouldBeNull()
        tracker.onWordEndedWithSpace(session, "huis", "huis", taps("huis"), null, content("ok, ga. naar  huis ")).shouldBeNull()
        // Another input session.
        val other = WordRevisionTracker()
        other.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        other.onWordEndedWithSpace(session + 1, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldBeNull()
        // No input session.
        val none = WordRevisionTracker()
        none.onWordEndedWithSpace(0L, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        none.onWordEndedWithSpace(0L, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldBeNull()
    }

    test("the previous word must start a token") {
        val tracker = WordRevisionTracker()
        // The keyboard committed "ok" right after "l'" (an elision typed before): "l'ok" is one token.
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("l'ok ")).shouldBeNull()
        tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("l'ok ga ")).shouldBeNull()
    }

    test("clear() forgets the last word") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        tracker.clear()
        tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldBeNull()
    }

    test("after a revision the last word can be revised by the next one at its new position") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        val first = tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldNotBeNull()
        // "ok" became "oké" (one letter longer): "ga" moved by one.
        tracker.onRevisionApplied(first, "oké")
        val second = tracker.onWordEndedWithSpace(session, "naar", "naar", taps("naar"), null, content("oké ga naar ")).shouldNotBeNull()
        second.committed shouldBe "ga"
        second.wordStart shouldBe 4
        second.textBeforeWord shouldBe "oké "
    }

    test("a window that does not start at the field start is reported as such") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("x ok ", offset = 300)).shouldBeNull()
        val request = tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("x ok ga ", offset = 300))
            .shouldNotBeNull()
        request.textStartsAtFieldStart shouldBe false
        request.wordStart shouldBe 302
        request.cursor shouldBe 308
    }

    test("a request only matches the editor it was made for") {
        val tracker = WordRevisionTracker()
        tracker.onWordEndedWithSpace(session, "ok", "ok", taps("ok"), null, content("ok ")).shouldBeNull()
        val request = tracker.onWordEndedWithSpace(session, "ga", "ga", taps("ga"), null, content("ok ga ")).shouldNotBeNull()
        request.matches(content("ok ga ")) shouldBe true
        // The user typed on, moved the cursor or selected text.
        request.matches(content("ok ga n")) shouldBe false
        request.matches(content("ok ga ", selection = EditorRange.cursor(3))) shouldBe false
        request.matches(content("ok ga ", selection = EditorRange(0, 6))) shouldBe false
        // The same text at another position (text was inserted before it).
        request.matches(content("xx ok ga ")) shouldBe false
    }
})
