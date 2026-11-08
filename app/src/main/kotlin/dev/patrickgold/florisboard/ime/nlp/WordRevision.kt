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

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.latin.engine.KeyGeometry
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap

/**
 * A request to revise the word before the last one (right-context revision): the user typed `previous`, space,
 * `next`, space, letter by letter, and the text before the cursor is exactly `committed + " " + next + " "` as the
 * keyboard left it. In memory only; never stored or logged.
 *
 * @property sessionId The input session the words were typed in.
 * @property textBeforeWord The editor text before the previous word (left context; the editor's window before the
 *  cursor, minus [tail]).
 * @property textStartsAtFieldStart [textBeforeWord] starts at the start of the field.
 * @property typed The letters typed for the previous word, with their case ("Ok").
 * @property committed The previous word as it is in the text (the typed letters or their autocorrection).
 * @property next The last word as it is in the text.
 * @property taps The taps of [typed] in key widths ([dev.patrickgold.florisboard.ime.nlp.latin.TapTrail]), measured on
 *  [geometry].
 * @property wordStart Absolute position of [committed] in the editor.
 * @property cursor Absolute cursor position after [tail].
 */
data class WordRevisionRequest(
    val sessionId: Long,
    val textBeforeWord: String,
    val textStartsAtFieldStart: Boolean,
    val typed: String,
    val committed: String,
    val next: String,
    val taps: List<LatinTap>?,
    val geometry: KeyGeometry?,
    val wordStart: Int,
    val cursor: Int,
) {
    /** The text between [wordStart] and [cursor] when the request was made. */
    val tail: String get() = "$committed$SEPARATOR$next$SEPARATOR"

    /** The text from the end of the previous word to the cursor. */
    val suffix: String get() = "$SEPARATOR$next$SEPARATOR"

    /**
     * True while [content] is still the state the request was made in: the same cursor, no selection and no composing
     * word, the text before the cursor ends with [tail], and [committed] still starts a token.
     */
    fun matches(content: EditorContent): Boolean {
        val selection = content.selection
        if (!selection.isValid || !selection.isCursorMode || selection.end != cursor) return false
        if (content.composing.isValid) return false
        val before = content.textBeforeSelection
        if (!before.endsWith(tail)) return false
        val start = before.length - tail.length
        return start == 0 || !isTokenChar(before[start - 1])
    }

    companion object {
        /** Letters, digits and apostrophes continue a word. */
        fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’'

        const val SEPARATOR = " "
    }
}

/**
 * A suggestion provider that can revise the word before the last one once the next word is committed. Called off the
 * main thread; must return quickly (the keyboard drops results that arrive after
 * [WordRevisionTracker.ResultBudgetMillis]).
 */
interface WordRevisionProvider {
    /**
     * The replacement for [WordRevisionRequest.committed] (its text with the case to commit), or null to keep it. The
     * candidate is used for undo and revert feedback ([SuggestionProvider.notifySuggestionReverted]); it is never
     * reported as accepted, so revisions do not feed personal learning.
     */
    suspend fun revisePreviousWord(
        subtype: Subtype,
        request: WordRevisionRequest,
        allowPossiblyOffensive: Boolean,
    ): SuggestionCandidate?
}

/**
 * Remembers the last word the user typed letter by letter and ended with the space key, so that the next such word
 * can trigger a right-context revision of it ([WordRevisionRequest]). Pure; no Android APIs; main thread only.
 *
 * A revision is only requested when, in the same input session,
 * - both words were typed letter by letter on the keyboard (the caller passes their taps; tapped suggestions, glide
 *   words, pasted text and hardware keys have none),
 * - the last word starts exactly where the previous word's space ended, and the text before the cursor is exactly
 *   `previous + " " + last + " "` (one space each, so both are in the same sentence and nothing was edited between),
 * - the previous word starts a token (no letter, digit or apostrophe before it).
 * Any other editing event ([clear]) forgets the previous word.
 */
class WordRevisionTracker {
    private data class TypedWord(
        val sessionId: Long,
        val typed: String,
        val committed: String,
        val taps: List<LatinTap>,
        val geometry: KeyGeometry?,
        val start: Int,
    )

    private var last: TypedWord? = null

    /**
     * Records the word that the space key just ended and returns a revision request for the word before it, if one
     * is allowed (see the class documentation).
     *
     * @param typed The letters typed for the word (the composing text before space), with their case.
     * @param committed The word as the keyboard committed it (the autocorrection or [typed]).
     * @param taps The word's taps, or null when it was not typed letter by letter.
     * @param geometry The key geometry the taps were measured on.
     * @param content The editor content after the space was committed.
     */
    fun onWordEndedWithSpace(
        sessionId: Long,
        typed: String,
        committed: String,
        taps: List<LatinTap>?,
        geometry: KeyGeometry?,
        content: EditorContent,
    ): WordRevisionRequest? {
        val previous = last
        last = null
        if (taps == null || typed.isBlank() || committed.isBlank() || sessionId == 0L) return null
        val selection = content.selection
        if (!selection.isValid || !selection.isCursorMode) return null
        val before = content.textBeforeSelection
        val ownTail = committed + WordRevisionRequest.SEPARATOR
        if (!before.endsWith(ownTail)) return null
        val start = selection.end - ownTail.length
        last = TypedWord(sessionId, typed, committed, taps, geometry, start)
        if (previous == null || previous.sessionId != sessionId) return null
        if (previous.start + previous.committed.length + WordRevisionRequest.SEPARATOR.length != start) return null
        val request = WordRevisionRequest(
            sessionId = sessionId,
            textBeforeWord = "",
            textStartsAtFieldStart = false,
            typed = previous.typed,
            committed = previous.committed,
            next = committed,
            taps = previous.taps,
            geometry = previous.geometry,
            wordStart = previous.start,
            cursor = selection.end,
        )
        val tail = request.tail
        if (!before.endsWith(tail)) return null
        val wordStartLocal = before.length - tail.length
        if (wordStartLocal > 0 && WordRevisionRequest.isTokenChar(before[wordStartLocal - 1])) return null
        val textBeforeWord = before.substring(0, wordStartLocal)
        return request.copy(textBeforeWord = textBeforeWord, textStartsAtFieldStart = content.offset == 0)
    }

    /**
     * The previous word of [request] was replaced by [replacement]: the last word moved by the length difference.
     */
    fun onRevisionApplied(request: WordRevisionRequest, replacement: String) {
        val word = last ?: return
        if (word.sessionId != request.sessionId || word.committed != request.next) return
        last = word.copy(start = word.start + replacement.length - request.committed.length)
    }

    /** Forgets the last word: any edit other than typing a word and space. */
    fun clear() {
        last = null
    }

    companion object {
        /** Results that take longer are dropped; the user is probably typing the next word by then. */
        const val ResultBudgetMillis: Long = 250L
    }
}
