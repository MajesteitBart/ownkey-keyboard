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
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate

/**
 * @property range The text to replace with [originalToken].
 * @property cursorAfter Where the cursor goes afterwards, or null to leave it after [originalToken]. Set for a
 *  revision of the previous word, which sits before the last word: the cursor stays at the end of the text.
 */
internal data class AutocorrectUndoReplacement(
    val range: EditorRange,
    val originalToken: String,
    val candidate: SuggestionCandidate,
    /** The subtype the correction was made on; the user may have switched languages since. */
    val subtype: Subtype? = null,
    val cursorAfter: Int? = null,
) {
    val isRevision: Boolean get() = cursorAfter != null
}

/**
 * Remembers the last autocorrection and the last right-context revision of the previous word so backspace or the undo
 * key can restore what was there before.
 *
 * A revision is newer than the autocorrection of the word after it, so it is undone first: right after a revision the
 * first backspace (or undo) restores the previous word and leaves the cursor at the end, the next backspace restores
 * the last word's autocorrection (if any), and further backspaces delete as usual. A revision can only be undone while
 * the cursor is where the revision left it and the text before it still ends with the revised word, the space, the
 * last word and the space; any typing after it ([clearRevision]) ends that chance.
 */
internal class AutocorrectUndoTracker {
    // Ephemeral in-memory state only. No persisted text history. Read from the suggestion thread for the revert chip.
    @Volatile
    private var pendingOperation: PendingAutocorrectOperation? = null
    @Volatile
    private var pendingRevision: PendingRevision? = null

    /**
     * The previous word changed from [originalToken] to [revisedCandidate] by a right-context revision on [subtype];
     * [suffix] is the text from the end of the revised word to the cursor (the space, the last word and its space) and
     * [cursor] the absolute cursor position after the revision.
     */
    fun trackRevision(
        originalToken: String,
        revisedCandidate: SuggestionCandidate,
        suffix: String,
        cursor: Int,
        subtype: Subtype? = null,
    ) {
        val revised = revisedCandidate.text.toString()
        pendingRevision = if (originalToken.isBlank() || revised.isBlank() || originalToken == revised) {
            null
        } else {
            PendingRevision(originalToken, revised, suffix, revisedCandidate, cursor, subtype)
        }
    }

    /** Forgets the pending revision (the user typed on, so backspace no longer means "undo the revision"). */
    fun clearRevision() {
        pendingRevision = null
    }

    /**
     * The text before the last autocorrection moved by [delta] characters (a revision of the word before it changed its
     * length), so the corrected word now ends [delta] later.
     */
    fun shiftPendingAutocorrect(delta: Int) {
        val operation = pendingOperation ?: return
        val end = operation.correctedEnd ?: return
        pendingOperation = operation.copy(correctedEnd = end + delta)
    }

    private fun findRevisionReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        val revision = pendingRevision ?: return null
        val selection = content.selection
        // The user moved the cursor since (and maybe came back): backspace there means delete.
        if (!selection.isValid || !selection.isCursorMode || selection.end != revision.cursor) return null
        val before = content.textBeforeSelection
        val tail = revision.revisedToken + revision.suffix
        if (!before.endsWith(tail)) return null
        val startLocal = before.length - tail.length
        if (startLocal > 0 && before[startLocal - 1].isUndoTokenChar()) return null
        val start = selection.end - before.length + startLocal
        return AutocorrectUndoReplacement(
            range = EditorRange(start, start + revision.revisedToken.length),
            originalToken = revision.originalToken,
            candidate = revision.candidate,
            subtype = revision.subtype,
            cursorAfter = selection.end + revision.originalToken.length - revision.revisedToken.length,
        )
    }

    /**
     * Remembers an autocorrection made on [subtype]. [correctedEnd] is where the corrected word ends in the editor,
     * when known; undo then only applies with the cursor right there or one separator (the space that triggered it)
     * after it.
     */
    fun trackAutoCorrect(
        originalToken: String,
        correctedCandidate: SuggestionCandidate,
        correctedEnd: Int? = null,
        subtype: Subtype? = null,
    ) {
        val normalizedOriginalToken = originalToken.trim()
        val correctedToken = correctedCandidate.text.toString().trim()
        pendingOperation = if (
            normalizedOriginalToken.isBlank() ||
            correctedToken.isBlank() ||
            normalizedOriginalToken == correctedToken
        ) {
            null
        } else {
            PendingAutocorrectOperation(
                originalToken = normalizedOriginalToken,
                correctedToken = correctedToken,
                candidate = correctedCandidate,
                correctedEnd = correctedEnd,
                subtype = subtype,
            )
        }
    }

    fun findUndoReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        findRevisionReplacement(content)?.let { return it }
        val operation = pendingOperation ?: return null
        if (!isRightAfterCorrection(content, operation)) return null
        val correctedTokenRange = findCorrectedTokenRange(content, operation.correctedToken) ?: return null
        return AutocorrectUndoReplacement(
            range = correctedTokenRange,
            originalToken = operation.originalToken,
            candidate = operation.candidate,
            subtype = operation.subtype,
        )
    }

    fun findBackspaceRestoreReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        if (content.selection.isSelectionMode) return null
        findRevisionReplacement(content)?.let { return it }
        val operation = pendingOperation ?: return null
        if (!isRightAfterCorrection(content, operation)) return null
        val correctedTokenRange = findBackspaceRestoreRange(content, operation.correctedToken) ?: return null
        return AutocorrectUndoReplacement(
            range = correctedTokenRange,
            originalToken = operation.originalToken,
            candidate = operation.candidate,
            subtype = operation.subtype,
        )
    }

    fun originalTokenForCandidate(candidate: SuggestionCandidate?): String? {
        val pending = pendingOperation ?: return null
        return if (candidate == pending.candidate) pending.originalToken else null
    }

    fun subtypeForCandidate(candidate: SuggestionCandidate?): Subtype? {
        val pending = pendingOperation ?: return null
        return if (candidate == pending.candidate) pending.subtype else null
    }

    fun clearPending() {
        pendingOperation = null
        pendingRevision = null
    }

    fun clearIfCandidateMatches(candidate: SuggestionCandidate?) {
        val pending = pendingOperation ?: return
        if (candidate == pending.candidate) {
            pendingOperation = null
        }
    }

    /**
     * Undo only applies right after the correction: no selection, and the cursor at the end of the corrected word or
     * one separator after it. Anything typed or moved since makes the correction final.
     */
    private fun isRightAfterCorrection(content: EditorContent, operation: PendingAutocorrectOperation): Boolean {
        if (content.selection.isSelectionMode) return false
        val cursor = content.selection.end
        val end = operation.correctedEnd
        if (end != null) {
            if (cursor != end && cursor != end + 1) return false
        }
        // Without a known end, at most one character may separate the corrected word from the cursor.
        val before = content.textBeforeSelection
        var tokenEnd = before.length
        while (tokenEnd > 0 && !before[tokenEnd - 1].isUndoTokenChar()) tokenEnd--
        val gap = before.length - tokenEnd
        if (gap > 1) return false
        // Right at a word, the cursor must be at its end, not moved inside it.
        return end != null || gap == 1 || content.textAfterSelection.firstOrNull()?.isUndoTokenChar() != true
    }

    private fun findCorrectedTokenRange(content: EditorContent, correctedToken: String): EditorRange? {
        if (content.currentWord.isValid && content.currentWordText == correctedToken) {
            return content.currentWord
        }
        return findTokenRangeBeforeCursor(content, correctedToken)
    }

    private fun findBackspaceRestoreRange(content: EditorContent, correctedToken: String): EditorRange? {
        if (
            content.currentWord.isValid &&
            content.selection.end == content.currentWord.end &&
            content.currentWordText == correctedToken
        ) {
            return content.currentWord
        }
        return findTokenRangeBeforeCursor(content, correctedToken)
    }

    private fun findTokenRangeBeforeCursor(content: EditorContent, correctedToken: String): EditorRange? {
        val beforeCursor = content.textBeforeSelection
        if (beforeCursor.isEmpty()) return null

        var tokenEnd = beforeCursor.length
        while (tokenEnd > 0 && !beforeCursor[tokenEnd - 1].isUndoTokenChar()) {
            tokenEnd--
        }
        if (tokenEnd == 0) return null

        // A correction that added a space ("thisis" became "this is") spans two tokens.
        if (correctedToken.contains(' ')) {
            val start = tokenEnd - correctedToken.length
            if (start < 0 || beforeCursor.substring(start, tokenEnd) != correctedToken) return null
            if (start > 0 && beforeCursor[start - 1].isUndoTokenChar()) return null
            val offset = content.selection.end - beforeCursor.length
            return EditorRange(offset + start, offset + tokenEnd)
        }

        var tokenStart = tokenEnd
        while (tokenStart > 0 && beforeCursor[tokenStart - 1].isUndoTokenChar()) {
            tokenStart--
        }

        val detectedToken = beforeCursor.substring(tokenStart, tokenEnd)
        if (detectedToken != correctedToken) return null

        val absoluteTokenStart = content.selection.end - beforeCursor.length + tokenStart
        val absoluteTokenEnd = content.selection.end - beforeCursor.length + tokenEnd
        return EditorRange(absoluteTokenStart, absoluteTokenEnd)
    }

    private fun Char.isUndoTokenChar(): Boolean {
        return isLetterOrDigit() || this == '\'' || this == 0x2019.toChar()
    }

    private data class PendingRevision(
        val originalToken: String,
        val revisedToken: String,
        val suffix: String,
        val candidate: SuggestionCandidate,
        val cursor: Int,
        val subtype: Subtype?,
    )

    private data class PendingAutocorrectOperation(
        val originalToken: String,
        val correctedToken: String,
        val candidate: SuggestionCandidate,
        val correctedEnd: Int?,
        val subtype: Subtype?,
    )
}
