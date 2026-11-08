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
 * Compares text read back from the editor with the text the keyboard expects there. Pure; no Android APIs.
 */
internal object EditorTextMatch {
    private const val NoBreakSpace = ' '

    /**
     * True when [actual] (from the editor) is [expected], except that a no-break space may stand for a space: rich
     * text editors such as WebView `contenteditable` fields store a typed trailing space as U+00A0.
     */
    fun sameText(actual: CharSequence, expected: CharSequence): Boolean {
        if (actual.length != expected.length) return false
        for (i in actual.indices) {
            val a = actual[i]
            val e = expected[i]
            if (a == e) continue
            if ((a == NoBreakSpace && e == ' ') || (a == ' ' && e == NoBreakSpace)) continue
            return false
        }
        return true
    }
}
