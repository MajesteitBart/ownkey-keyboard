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

class EditorTextMatchTest : FunSpec({
    test("the editor text must be exactly the expected text") {
        EditorTextMatch.sameText("ok ga ", "ok ga ") shouldBe true
        EditorTextMatch.sameText("", "") shouldBe true
        // The app changed the text (an input filter dropped a letter, a formatter replaced a word).
        EditorTextMatch.sameText("o ga ", "ok ga ") shouldBe false
        EditorTextMatch.sameText("OK ga ", "ok ga ") shouldBe false
        EditorTextMatch.sameText("ok gaa", "ok ga ") shouldBe false
    }

    test("a no-break space stands for a space") {
        // WebView contenteditable keeps a typed trailing space as U+00A0.
        EditorTextMatch.sameText("ok ga ", "ok ga ") shouldBe true
        EditorTextMatch.sameText("ok ga ", "ok ga ") shouldBe true
        EditorTextMatch.sameText("ok ga", "ok ga ") shouldBe false
        EditorTextMatch.sameText("ok ga ", "ok ga ") shouldBe false
    }
})
