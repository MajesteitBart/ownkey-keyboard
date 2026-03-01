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
    // Ephemeral in-memory state only. No persisted text history.
    private var pendingOperation: PendingAutocorrectOperation? = null

    fun trackAutoCorrect(originalToken: String, correctedCandidate: SuggestionCandidate) {
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
            )
        }
    }

    fun findUndoReplacement(content: EditorContent): AutocorrectUndoReplacement? {
        val operation = pendingOperation ?: return null
        val correctedTokenRange = findCorrectedTokenRange(content, operation.correctedToken) ?: return null
        return AutocorrectUndoReplacement(
            range = correctedTokenRange,
            originalToken = operation.originalToken,
            candidate = operation.candidate,
        )
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

    private fun findCorrectedTokenRange(content: EditorContent, correctedToken: String): EditorRange? {
        if (content.currentWord.isValid && content.currentWordText == correctedToken) {
            return content.currentWord
        }
        val beforeCursor = content.textBeforeSelection
        if (beforeCursor.isEmpty()) return null

        var tokenEnd = beforeCursor.length
        while (tokenEnd > 0 && !beforeCursor[tokenEnd - 1].isUndoTokenChar()) {
            tokenEnd--
        }
        if (tokenEnd == 0) return null

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
    )
}
