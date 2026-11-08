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

package org.florisboard.lib.prediction

import java.text.Normalizer
import java.util.Locale

/**
 * Text normalisation shared by the lexicon, the engine and the simulator.
 *
 * The *key form* of a word is the letter sequence a user types for it on a plain QWERTY layout. It mirrors
 * `fold()` in `tools/kbbench/bench.py` exactly: straight and curly apostrophes removed, lower case, NFKD with
 * combining marks dropped, and `ĳ` spelled `ij`. So `"Één"`, `"don’t"` and `"ĳs"` become `"een"`, `"dont"` and
 * `"ijs"`.
 */
object TextFolding {
    /** Returns the key form of [word]; see the object documentation. */
    fun keyForm(word: String): String {
        if (isKeyLetters(word)) return word
        var s = word.replace('’', '\'').replace("'", "").lowercase(Locale.ROOT)
        if (!isAscii(s)) {
            s = Normalizer.normalize(s, Normalizer.Form.NFKD)
            val sb = StringBuilder(s.length)
            for (ch in s) {
                if (!isCombiningMark(ch)) sb.append(ch)
            }
            s = sb.toString().replace("ĳ", "ij")
        }
        return s
    }

    /**
     * Normalises a word as the language models expect it: lower case ([Locale.ROOT]) and curly apostrophes
     * replaced by straight ones. Diacritics and apostrophes are kept.
     */
    fun normalizeWord(word: String): String {
        if (isKeyLetters(word)) return word
        return word.replace('’', '\'').lowercase(Locale.ROOT)
    }

    /** True when [s] is non-empty and consists only of the letters `a` to `z`. */
    fun isKeyLetters(s: CharSequence): Boolean {
        if (s.isEmpty()) return false
        for (ch in s) {
            if (ch < 'a' || ch > 'z') return false
        }
        return true
    }

    /** True when [word] contains only letters and apostrophes and at least one letter. */
    fun isWordLike(word: CharSequence): Boolean {
        var letters = 0
        for (ch in word) {
            when {
                Character.isLetter(ch) -> letters++
                ch == '\'' || ch == '’' -> Unit
                isCombiningMark(ch) -> Unit
                else -> return false
            }
        }
        return letters > 0
    }

    private fun isAscii(s: String): Boolean {
        for (ch in s) {
            if (ch.code > 0x7F) return false
        }
        return true
    }

    private fun isCombiningMark(ch: Char): Boolean {
        return when (Character.getType(ch).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }
    }
}
