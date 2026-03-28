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

package dev.patrickgold.florisboard.ime.editor

/**
 * Adapts editor content for suggestion refresh when the keyboard is carrying a pending separator
 * (phantom space) after accepting a suggestion or gesture.
 *
 * In that state the visual editor still has the cursor directly after the committed word, but the
 * next tap of a letter or punctuation symbol will behave as if a word boundary exists. For next-word
 * prediction we therefore want the NLP layer to see a virtual boundary immediately.
 */
object PendingSeparatorSuggestionContent {
    fun forRefresh(content: EditorContent, hasPendingSeparator: Boolean): EditorContent {
        val selection = content.localSelection
        if (!hasPendingSeparator || selection.isNotValid || !selection.isCursorMode) {
            return content
        }
        val existingBoundaryChar = content.textBeforeSelection.lastOrNull()
        if (existingBoundaryChar != null && (
                existingBoundaryChar.isWhitespace() ||
                    existingBoundaryChar in setOf('.', ',', ';', ':', '!', '?')
                )
        ) {
            return content
        }
        if (content.textAfterSelection.firstOrNull()?.isWhitespace() == true) {
            return content
        }

        val separator = " "
        val newCursor = selection.start + separator.length
        return EditorContent(
            text = buildString(content.text.length + separator.length) {
                append(content.textBeforeSelection)
                append(separator)
                append(content.textAfterSelection)
            },
            offset = content.offset,
            localSelection = EditorRange.cursor(newCursor),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange.Unspecified,
        )
    }
}
