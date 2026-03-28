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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AcceptedSuggestionSpacingPolicyTest : FunSpec({
    test("keeps phantom separator when accepted suggestion ends at the cursor") {
        AcceptedSuggestionSpacingPolicy.forComposingReplacement(
            contentAtCursorWithFollowingText("")
        ) shouldBe AcceptedSuggestionSpacingDecision(
            shouldActivatePhantomSpace = true,
            cursorAdvanceAfterCommit = 0,
        )
    }

    test("reuses an existing trailing space after correcting a committed word") {
        AcceptedSuggestionSpacingPolicy.forComposingReplacement(
            contentAtCursorWithFollowingText(" world")
        ) shouldBe AcceptedSuggestionSpacingDecision(
            shouldActivatePhantomSpace = false,
            cursorAdvanceAfterCommit = 1,
        )
    }

    test("does not arm phantom spacing when punctuation already follows the corrected word") {
        AcceptedSuggestionSpacingPolicy.forComposingReplacement(
            contentAtCursorWithFollowingText(", world")
        ) shouldBe AcceptedSuggestionSpacingDecision(
            shouldActivatePhantomSpace = false,
            cursorAdvanceAfterCommit = 0,
        )
    }
})

private fun contentAtCursorWithFollowingText(textAfterSelection: String): EditorContent {
    val textBeforeSelection = "helo"
    val fullText = textBeforeSelection + textAfterSelection
    return EditorContent(
        text = fullText,
        offset = 0,
        localSelection = EditorRange.cursor(textBeforeSelection.length),
        localComposing = EditorRange(0, textBeforeSelection.length),
        localCurrentWord = EditorRange(0, textBeforeSelection.length),
    )
}
