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

package org.florisboard.lib.prediction.ngram

import java.text.Normalizer
import java.util.Locale

/**
 * Token rules shared with the offline pipeline (tools/lm/lmtool/textnorm.py): a word is a run of letters with
 * internal apostrophes, lower-cased, with curly apostrophes normalised to `'` and edge apostrophes removed.
 */
object NgramText {
    private val APOSTROPHES = charArrayOf('’', '‘', 'ʼ', '`', '´', '′')

    fun isApostrophe(c: Char): Boolean = c == '\'' || c in APOSTROPHES

    /** Characters that belong to a word run before validation (letters, digits, underscore, apostrophes). */
    fun isWordChar(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_' || isApostrophe(c)

    fun normalizeToken(word: String): String {
        val nfkc = Normalizer.normalize(word, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length)
        for (c in nfkc) sb.append(if (c in APOSTROPHES) '\'' else c)
        return sb.toString().lowercase(Locale.ROOT).trim('\'')
    }

    fun fold(token: String): String {
        val nfd = Normalizer.normalize(token, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (c in nfd) {
            if (c == '\'' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(c)
        }
        return sb.toString()
    }

    /**
     * The last valid word of a raw run (letters, digits, underscore, apostrophes), or null when the run is dropped
     * (it contains a digit or underscore, or no letters).
     */
    fun wordOfRun(run: String): String? = wordsOfRun(run).lastOrNull()

    /**
     * All valid words of a raw run in order (usually one; a doubled apostrophe separates words), or an empty list
     * when the run is dropped (it contains a digit or underscore, or no letters). Words keep their case.
     */
    fun wordsOfRun(run: String): List<String> {
        val s = run.map { if (it in APOSTROPHES) '\'' else it }.joinToString("").trim('\'')
        if (s.isEmpty()) return emptyList()
        for (c in s) if (Character.isDigit(c) || c == '_') return emptyList()
        return s.split("''").map { it.trim('\'') }.filter { it.isNotEmpty() && isWord(it) }
    }

    private fun isWord(p: String): Boolean {
        if (p.isEmpty() || p.first() == '\'' || p.last() == '\'') return false
        var prevApostrophe = false
        for (c in p) {
            if (c == '\'') {
                if (prevApostrophe) return false
                prevApostrophe = true
            } else {
                if (!Character.isLetter(c)) return false
                prevApostrophe = false
            }
        }
        return true
    }
}
