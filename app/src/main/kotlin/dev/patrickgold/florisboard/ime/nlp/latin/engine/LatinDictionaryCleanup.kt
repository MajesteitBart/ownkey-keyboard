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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

/**
 * Removes known misspellings and tokenizer artifacts from a frequency list before it becomes a word model. The
 * FrequencyWords lists come from subtitles, so they contain common misspellings, contractions broken up with
 * backticks and OCR noise such as a lone "l" for "I".
 */
internal object LatinDictionaryCleanup {
    const val RemovalAssetDir = "ime/dict/removals"

    /**
     * Single-letter words that are real; other single letters in these languages are OCR noise or halves of an
     * elision ("z'n"). Matches tools/dictionary-build/build.py.
     */
    private val SingleLetterWords = mapOf(
        "en" to setOf("a", "i", "k", "u", "x"),
        "nl" to setOf("u"),
    )

    fun parseRemovalList(lines: Sequence<String>): Set<String> {
        return lines
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { LatinText.normalizeDictionaryWord(it) }
            .toSet()
    }

    fun apply(words: Map<String, Int>, language: String, removals: Set<String>): Map<String, Int> {
        val singleLetterWords = SingleLetterWords[language]
        return words.filterTo(LinkedHashMap()) { (word, _) ->
            word !in removals &&
                '`' !in word &&
                '�' !in word &&
                (singleLetterWords == null || word.length != 1 || word in singleLetterWords)
        }
    }
}
