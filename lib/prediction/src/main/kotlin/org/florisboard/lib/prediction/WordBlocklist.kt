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

/**
 * Words that [PredictionEngine.suggest] must not offer (its `isBlocked` predicate), for example possibly offensive
 * words: exact words plus prefix stems ("fuck*" also blocks "fucking" and "fuckers").
 *
 * Text format ([parse]): one entry per line, `#` starts a comment, blank lines are ignored, a trailing `*` makes the
 * entry a prefix stem. Entries and queries are compared in the normalised form of [TextFolding.normalizeWord]
 * (lower case, straight apostrophes, diacritics kept). Immutable and thread-safe.
 */
class WordBlocklist private constructor(
    private val words: Set<String>,
    private val stems: List<String>,
) {
    /** Number of exact words plus stems. */
    val size: Int get() = words.size + stems.size

    /** True when [word] (any case) is listed or starts with a listed stem. */
    operator fun contains(word: String): Boolean {
        val norm = TextFolding.normalizeWord(word)
        if (norm in words) return true
        for (stem in stems) {
            if (norm.startsWith(stem)) return true
        }
        return false
    }

    /** The entries of both lists. */
    operator fun plus(other: WordBlocklist): WordBlocklist = when {
        other.size == 0 -> this
        size == 0 -> other
        else -> WordBlocklist(words + other.words, (stems + other.stems).distinct())
    }

    companion object {
        val Empty = WordBlocklist(emptySet(), emptyList())

        /** Parses the text format described in the class documentation. */
        fun parse(text: String): WordBlocklist {
            val words = HashSet<String>()
            val stems = ArrayList<String>()
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                if (line.endsWith('*')) {
                    val stem = TextFolding.normalizeWord(line.dropLast(1).trim())
                    if (stem.isNotEmpty()) stems.add(stem)
                } else {
                    words.add(TextFolding.normalizeWord(line))
                }
            }
            return WordBlocklist(words, stems.distinct())
        }
    }
}
