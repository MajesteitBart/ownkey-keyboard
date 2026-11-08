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
 * Possibly offensive words per language, for the "Block possibly offensive words" setting. The dictionaries come
 * from subtitles and keep these words, so the block works on what the engine hands out: nothing on a list is offered,
 * predicted or auto-committed. The word as typed always stays, even when it is on a list.
 */
internal object PossiblyOffensiveWords {
    const val AssetDir = "ime/dict/offensive"

    /** Marks a stem: "fuck*" blocks every word that starts with "fuck". */
    private const val StemMarker = '*'

    /** One entry per line, lowercase; '#' starts a comment line; an entry ending in [StemMarker] is a stem. */
    fun parse(lines: Sequence<String>): Set<String> = LatinDictionaryCleanup.parseRemovalList(lines)

    /**
     * Whether the single word [normalizedWord] is on [blocked]: listed itself, or starting with a listed stem
     * ("fuck*").
     */
    fun isBlocked(normalizedWord: String, blocked: Set<String>): Boolean {
        if (blocked.isEmpty() || normalizedWord.isEmpty()) return false
        if (normalizedWord in blocked) return true
        for (end in 1..normalizedWord.length) {
            if (normalizedWord.substring(0, end) + StemMarker in blocked) return true
        }
        return false
    }

    /**
     * [candidates] without the ones that contain a word in [blocked], except a candidate that is the [typed] word
     * itself. A candidate of several words (a missed-space split such as "fuck you") is checked word by word.
     */
    fun <T> filter(candidates: List<T>, blocked: Set<String>, typed: String, textOf: (T) -> CharSequence): List<T> {
        if (blocked.isEmpty()) return candidates
        val typedWord = LatinText.normalizeDictionaryWord(typed)
        return candidates.filter { candidate ->
            val text = LatinText.normalizeDictionaryWord(textOf(candidate).toString())
            text == typedWord || text.split(' ').none { isBlocked(it, blocked) }
        }
    }
}
