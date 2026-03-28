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
 * Spacing contract for accepted suggestions/corrections.
 *
 * Mainstream mobile keyboards usually distinguish between two cases:
 * 1) accepting the current in-flight word at the cursor -> keep a pending separator so punctuation can follow cleanly
 * 2) correcting a word that already has a literal separator after it -> reuse that separator instead of arming another one
 */
data class AcceptedSuggestionSpacingDecision(
    val shouldActivatePhantomSpace: Boolean,
    val cursorAdvanceAfterCommit: Int,
)

object AcceptedSuggestionSpacingPolicy {
    fun forComposingReplacement(content: EditorContent): AcceptedSuggestionSpacingDecision {
        return when (content.textAfterSelection.firstOrNull()) {
            null -> AcceptedSuggestionSpacingDecision(
                shouldActivatePhantomSpace = true,
                cursorAdvanceAfterCommit = 0,
            )
            ' ' -> AcceptedSuggestionSpacingDecision(
                shouldActivatePhantomSpace = false,
                cursorAdvanceAfterCommit = 1,
            )
            else -> AcceptedSuggestionSpacingDecision(
                shouldActivatePhantomSpace = false,
                cursorAdvanceAfterCommit = 0,
            )
        }
    }
}
