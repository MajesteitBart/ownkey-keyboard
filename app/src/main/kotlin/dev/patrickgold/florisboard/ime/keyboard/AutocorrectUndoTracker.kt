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
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate

internal data class AutocorrectUndoReplacement(
    val range: EditorRange,
    val originalToken: String,
    val candidate: SuggestionCandidate,
)

internal class AutocorrectUndoTracker {
    // Ephemeral in-memory state only. No persisted text history. Read from the suggestion thread for the revert chip.
    @Volatile
    private var pendingOperation: PendingAutocorrectOperation? = null

    /**
     * Remembers an autocorrection. [correctedEnd] is where the corrected word ends in the editor, when known; undo
     * then only applies with the cursor right there or one separator (the space that triggered it) after it.
     */
    fun trackAutoCorrect(originalToken: String, correctedCandidate: SuggestionCandidate, correctedEnd: Int? = null) {
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
            )
        }
    }

    fun findUndoReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        val operation = pendingOperation ?: return null
        if (!isRightAfterCorrection(content, operation)) return null
        val correctedTokenRange = findCorrectedTokenRange(content, operation.correctedToken) ?: return null
        return AutocorrectUndoReplacement(
            range = correctedTokenRange,
            originalToken = operation.originalToken,
            candidate = operation.candidate,
        )
    }

    fun findBackspaceRestoreReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        if (content.selection.isSelectionMode) return null
        val operation = pendingOperation ?: return null
        if (!isRightAfterCorrection(content, operation)) return null
        val correctedTokenRange = findBackspaceRestoreRange(content, operation.correctedToken) ?: return null
        return AutocorrectUndoReplacement(
            range = correctedTokenRange,
            originalToken = operation.originalToken,
            candidate = operation.candidate,
        )
    }

    fun originalTokenForCandidate(candidate: SuggestionCandidate?): String? {
        val pending = pendingOperation ?: return null
        return if (candidate == pending.candidate) pending.originalToken else null
    }

    fun clearPending() {
        pendingOperation = null
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
        return before.length - tokenEnd <= 1
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

    private data class PendingAutocorrectOperation(
        val originalToken: String,
        val correctedToken: String,
        val candidate: SuggestionCandidate,
        val correctedEnd: Int?,
    )
}
