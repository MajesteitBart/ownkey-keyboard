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

class PendingSeparatorSuggestionContentTest : FunSpec({
    test("treats a pending separator after suggestion acceptance as an immediate next-word boundary") {
        PendingSeparatorSuggestionContent.forRefresh(
            content = acceptedWordContent(),
            hasPendingSeparator = true,
        ) shouldBe EditorContent(
            text = "hello ",
            offset = 0,
            localSelection = EditorRange.cursor(6),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange.Unspecified,
        )
    }

    test("leaves the literal editor content untouched when no separator is pending") {
        val content = acceptedWordContent()
        PendingSeparatorSuggestionContent.forRefresh(
            content = content,
            hasPendingSeparator = false,
        ) shouldBe content
    }

    test("does not synthesize an extra separator when the cursor already sits at a real boundary") {
        val content = EditorContent(
            text = "hello world",
            offset = 0,
            localSelection = EditorRange.cursor(6),
            localComposing = EditorRange.Unspecified,
            localCurrentWord = EditorRange.Unspecified,
        )
        PendingSeparatorSuggestionContent.forRefresh(
            content = content,
            hasPendingSeparator = true,
        ) shouldBe content
    }
})

private fun acceptedWordContent(): EditorContent {
    return EditorContent(
        text = "hello",
        offset = 0,
        localSelection = EditorRange.cursor(5),
        localComposing = EditorRange.Unspecified,
        localCurrentWord = EditorRange(0, 5),
    )
}
