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

import dev.patrickgold.florisboard.ime.nlp.latin.LatinPredictionShortcuts

/**
 * A candidate word found for an input, before scoring.
 */
internal data class RankedCandidate(
    val word: String,
    val distance: Int,
    val frequency: Int,
    val isPrefixMatch: Boolean,
)

/**
 * In-memory word model for one language: frequencies, a distance-1 delete index for typo lookup and a prefix
 * index for completions.
 */
internal class LatinWordModel private constructor(
    val words: Map<String, Int>,
    val deleteIndex: Map<String, List<String>>,
    val maxFrequency: Int,
    val predictionShortcuts: LatinPredictionShortcuts,
    /** Sum of all word frequencies, to turn a frequency into a probability. */
    val totalFrequency: Double,
    /** Which words follow which; empty for languages without a bigram list. */
    val bigrams: LatinBigramModel = LatinBigramModel.Empty,
) {
    companion object {
        const val MaxEditDistance = 1
        private const val ShortcutPrefixDepth = 3
        private const val ShortcutPrefixPoolSize = 48
        private const val ShortcutFallbackPoolSize = 64
        // Bound typo-delete index size to avoid startup OOM on constrained heaps.
        private const val MaxDeleteIndexWordCount = 20_000
        private const val MaxDeleteIndexedWordLength = 18

        val Empty = LatinWordModel(
            words = emptyMap(),
            deleteIndex = emptyMap(),
            maxFrequency = 1,
            predictionShortcuts = LatinPredictionShortcuts(emptyMap()),
            totalFrequency = 1.0,
        )

        fun build(words: Map<String, Int>, bigrams: LatinBigramModel = LatinBigramModel.Empty): LatinWordModel {
            if (words.isEmpty()) return Empty

            val deleteIndex = mutableMapOf<String, MutableList<String>>()
            words.entries
                .asSequence()
                .filter { (word, _) -> word.length in 2..MaxDeleteIndexedWordLength }
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key }
                )
                .take(MaxDeleteIndexWordCount)
                .forEach { (word, _) ->
                    val uniqueDeletes = LinkedHashSet<String>()
                    LatinText.generateDeletes(word, MaxEditDistance).forEach { deletedWord ->
                        if (uniqueDeletes.add(deletedWord)) {
                            deleteIndex.getOrPut(deletedWord) { mutableListOf() }.add(word)
                        }
                    }
                }

            val predictionShortcuts = LatinPredictionShortcuts(
                words = words,
                maxPrefixDepth = ShortcutPrefixDepth,
                prefixPoolSize = ShortcutPrefixPoolSize,
                fallbackPoolSize = ShortcutFallbackPoolSize,
            )

            return LatinWordModel(
                words = words,
                deleteIndex = deleteIndex.mapValues { (_, list) -> list.toList() },
                maxFrequency = words.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
                predictionShortcuts = predictionShortcuts,
                totalFrequency = words.values.fold(0.0) { sum, frequency -> sum + frequency }.coerceAtLeast(1.0),
                bigrams = bigrams,
            )
        }
    }

    fun isKnown(normalizedWord: String): Boolean = words.containsKey(normalizedWord)

    fun lookupCorrections(input: String, maxCount: Int): List<RankedCandidate> {
        if (input.isBlank()) return emptyList()

        val candidateWords = LinkedHashSet<String>()
        deleteIndex[input]?.let { candidateWords.addAll(it) }
        LatinText.generateDeletes(input, MaxEditDistance).forEach { deletedWord ->
            if (words.containsKey(deletedWord)) {
                candidateWords.add(deletedWord)
            }
            deleteIndex[deletedWord]?.let { candidateWords.addAll(it) }
        }

        if (candidateWords.isEmpty()) return emptyList()

        val rankedCandidates = mutableListOf<RankedCandidate>()
        candidateWords.forEach { candidateWord ->
            if (candidateWord == input) return@forEach

            val frequency = words[candidateWord] ?: return@forEach
            val distance = LatinText.boundedDamerauLevenshtein(input, candidateWord, MaxEditDistance)
            if (distance <= MaxEditDistance) {
                rankedCandidates.add(
                    RankedCandidate(
                        word = candidateWord,
                        distance = distance,
                        frequency = frequency,
                        isPrefixMatch = candidateWord.startsWith(input),
                    )
                )
            }
        }

        return rankedCandidates
            .sortedWith(
                compareBy<RankedCandidate> { it.distance }
                    .thenByDescending { it.frequency }
                    .thenBy { it.word }
            )
            .take(maxCount)
    }

    fun lookupPrefixCandidates(input: String, maxCount: Int): List<RankedCandidate> {
        return predictionShortcuts.lookupPrefixCandidates(input, maxCount)
            .map { candidate ->
                RankedCandidate(
                    word = candidate.word,
                    distance = 0,
                    frequency = candidate.frequency,
                    isPrefixMatch = true,
                )
            }
    }

    /**
     * Words two edits away from [input] that the distance-1 lookup misses: two deletions from the input matched
     * against the delete index (an extra letter plus a wrong one, or two extra letters), and two substituted
     * letters, where [alternatives] names the letters worth trying at each position (neighboring keys, or the keys
     * the tap was closest to). At most [maxVariants] substitution pairs are tried, so long words stay cheap.
     */
    fun lookupTwoEditCandidates(
        input: String,
        alternatives: (Int) -> List<Char>,
        maxCount: Int,
        maxVariants: Int = 2_000,
    ): List<String> {
        if (input.length < 3) return emptyList()
        val found = LinkedHashSet<String>()
        for (deleted in LatinText.generateDeletes(input, 2)) {
            if (deleted.length < input.length - 1 && words.containsKey(deleted)) found.add(deleted)
            deleteIndex[deleted]?.forEach { found.add(it) }
        }
        val options = List(input.length) { alternatives(it) }
        var tried = 0
        val chars = input.toCharArray()
        outer@ for (i in input.indices) {
            for (j in i + 1 until input.length) {
                for (a in options[i]) {
                    for (b in options[j]) {
                        if (++tried > maxVariants) break@outer
                        chars[i] = a
                        chars[j] = b
                        val variant = String(chars)
                        if (words.containsKey(variant)) found.add(variant)
                    }
                }
                chars[j] = input[j]
            }
            chars[i] = input[i]
        }
        return found.asSequence()
            .filter { it != input && LatinText.boundedDamerauLevenshtein(input, it, 2) <= 2 }
            .sortedByDescending { words[it] ?: 0 }
            .take(maxCount)
            .toList()
    }
}
