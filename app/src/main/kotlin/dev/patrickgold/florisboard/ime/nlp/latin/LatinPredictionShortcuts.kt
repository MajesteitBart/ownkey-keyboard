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

package dev.patrickgold.florisboard.ime.nlp.latin

internal data class LatinWordFrequency(
    val word: String,
    val frequency: Int,
)

/**
 * In-memory shortcut indexes for low-latency top candidate generation.
 *
 * Indexes are built from static dictionary frequencies and do not store raw user-typed content.
 */
internal class LatinPredictionShortcuts(
    words: Map<String, Int>,
    private val maxPrefixDepth: Int = 3,
    private val prefixPoolSize: Int = 48,
    fallbackPoolSize: Int = 64,
) {
    private val prefixIndex: Map<String, List<LatinWordFrequency>>
    private val fallbackWords: List<LatinWordFrequency>

    init {
        val prefixBuckets = mutableMapOf<String, MutableList<LatinWordFrequency>>()
        words.forEach { (word, frequency) ->
            if (word.isEmpty()) return@forEach
            val candidate = LatinWordFrequency(word = word, frequency = frequency)
            val maxDepth = minOf(maxPrefixDepth, word.length)
            for (depth in 1..maxDepth) {
                val prefix = word.substring(0, depth)
                prefixBuckets.getOrPut(prefix) { mutableListOf() }.add(candidate)
            }
        }
        prefixIndex = prefixBuckets.mapValues { (_, entries) ->
            entries.sortedWith(
                compareByDescending<LatinWordFrequency> { it.frequency }
                    .thenBy { it.word }
            ).take(prefixPoolSize)
        }
        fallbackWords = words.entries
            .asSequence()
            .filter { (word, _) -> word.length >= 2 }
            .map { (word, frequency) ->
                LatinWordFrequency(word = word, frequency = frequency)
            }
            .sortedWith(
                compareByDescending<LatinWordFrequency> { it.frequency }
                    .thenBy { it.word }
            )
            .take(fallbackPoolSize)
            .toList()
    }

    fun lookupPrefixCandidates(input: String, maxCount: Int): List<LatinWordFrequency> {
        if (input.isBlank() || maxCount <= 0) return emptyList()

        val maxDepth = minOf(maxPrefixDepth, input.length)
        val selectedBucket = (maxDepth downTo 1)
            .asSequence()
            .mapNotNull { depth ->
                prefixIndex[input.substring(0, depth)]
            }
            .firstOrNull()
            ?: return emptyList()

        return selectedBucket.asSequence()
            .filter { it.word != input && it.word.startsWith(input) }
            .take(maxCount)
            .toList()
    }

    fun fallbackCandidates(previousWord: String, maxCount: Int): List<LatinWordFrequency> {
        if (maxCount <= 0) return emptyList()
        return fallbackWords.asSequence()
            .filter { it.word != previousWord }
            .take(maxCount)
            .toList()
    }
}
