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

import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

/**
 * Text helpers shared by the Latin scorers, the language provider and the JVM benchmark. Nothing in here may
 * depend on Android.
 */
internal object LatinText {
    const val RecentContextTokenWindowSize = 6
    /** English "I" and its contractions, which are always written with a capital I. */
    val EnglishPronounForms = setOf("i", "i'm", "i've", "i'll", "i'd")

    /** Asset folder of the built word lists and their `{language}.bigrams.txt` companions. */
    const val BigramAssetDir = "ime/dict/latin"

    fun normalizeDictionaryWord(word: String): String {
        return word.trim()
            .replace('’', '\'')
            .lowercase(Locale.ROOT)
    }

    fun normalizeInputWord(word: String, locale: Locale): String {
        return word.trim()
            .replace('’', '\'')
            .lowercase(locale)
    }

    fun normalizeLanguageCode(languageCode: String): String {
        return languageCode.trim().lowercase(Locale.ROOT)
    }

    /**
     * Parses a `word frequency` list (FrequencyWords format). Later duplicates only raise the frequency.
     */
    fun parseFrequencyList(lines: Sequence<String>): Map<String, Int> {
        val words = LinkedHashMap<String, Int>()
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            val separatorIndex = trimmed.lastIndexOfAny(charArrayOf(' ', '\t'))
            if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) return@forEach

            val normalizedWord = normalizeDictionaryWord(trimmed.substring(0, separatorIndex))
            if (normalizedWord.isBlank()) return@forEach

            val frequency = trimmed.substring(separatorIndex + 1).toIntOrNull() ?: return@forEach
            val safeFrequency = frequency.coerceAtLeast(1)
            val currentFrequency = words[normalizedWord] ?: 0
            if (safeFrequency > currentFrequency) {
                words[normalizedWord] = safeFrequency
            }
        }
        return words
    }

    private const val Log100Header = "# ownkey-latin-dictionary v1 format=log100"

    /**
     * Parses a shipped dictionary: either the built format (header line, then `word<TAB>round(100 * ln(count))`)
     * or a plain FrequencyWords `word count` list.
     */
    fun parseDictionary(lines: Sequence<String>): Map<String, Int> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyMap()
        val first = iterator.next()
        if (!first.startsWith(Log100Header)) {
            return parseFrequencyList(sequenceOf(first) + iterator.asSequence())
        }
        val words = LinkedHashMap<String, Int>()
        iterator.forEach { line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) return@forEach
            val word = normalizeDictionaryWord(line.substring(0, tab))
            val value = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEach
            val count = kotlin.math.exp(value / 100.0).toInt().coerceAtLeast(1)
            if (word.isNotBlank() && count > (words[word] ?: 0)) words[word] = count
        }
        return words
    }

    fun extractWordTokens(text: String, locale: Locale): List<String> {
        val tokens = mutableListOf<String>()
        val builder = StringBuilder()

        fun flushToken() {
            if (builder.isNotEmpty()) {
                val token = normalizeInputWord(builder.toString(), locale)
                if (token.isNotBlank()) {
                    tokens.add(token)
                }
                builder.clear()
            }
        }

        for (ch in text) {
            when {
                ch.isLetter() -> builder.append(ch)
                (ch == '\'' || ch == '’' || ch == '-') && builder.isNotEmpty() -> builder.append(ch)
                else -> flushToken()
            }
        }
        flushToken()

        return tokens
    }

    fun extractRecentContextTokens(textBeforeSelection: String): List<String> {
        return extractWordTokens(textBeforeSelection, Locale.ROOT)
            .takeLast(RecentContextTokenWindowSize)
    }

    fun applyInputCase(rawInput: String, suggestion: String, locale: Locale): String {
        val lettersOnly = rawInput.filter { it.isLetter() }
        return when {
            lettersOnly.isNotEmpty() && lettersOnly.all { it.isUpperCase() } -> {
                suggestion.uppercase(locale)
            }
            rawInput.firstOrNull()?.isUpperCase() == true -> {
                suggestion.replaceFirstChar { firstChar ->
                    firstChar.titlecase(locale)
                }
            }
            else -> suggestion
        }
    }

    fun generateDeletes(word: String, maxDistance: Int): Set<String> {
        if (word.isEmpty() || maxDistance <= 0) return emptySet()

        if (maxDistance == 1) {
            val deletes = LinkedHashSet<String>(word.length)
            for (i in word.indices) {
                deletes.add(word.removeRange(i, i + 1))
            }
            return deletes
        }

        val deletes = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(word to 0)

        while (queue.isNotEmpty()) {
            val (candidate, distance) = queue.removeFirst()
            if (distance >= maxDistance || candidate.length <= 1) continue

            for (i in candidate.indices) {
                val deletedWord = candidate.removeRange(i, i + 1)
                if (deletes.add(deletedWord)) {
                    queue.add(deletedWord to distance + 1)
                }
            }
        }

        return deletes
    }

    /**
     * Optimal string alignment distance, with an early exit once every cell of a row exceeds [limit].
     */
    fun boundedDamerauLevenshtein(source: String, target: String, limit: Int): Int {
        if (source == target) return 0
        if (abs(source.length - target.length) > limit) return limit + 1

        var previousPreviousRow = IntArray(target.length + 1)
        var previousRow = IntArray(target.length + 1) { it }
        var currentRow = IntArray(target.length + 1)

        for (sourceIndex in 1..source.length) {
            currentRow[0] = sourceIndex
            var rowMin = currentRow[0]
            val sourceChar = source[sourceIndex - 1]

            for (targetIndex in 1..target.length) {
                val targetChar = target[targetIndex - 1]
                val substitutionCost = if (sourceChar == targetChar) 0 else 1

                var value = minOf(
                    previousRow[targetIndex] + 1,
                    currentRow[targetIndex - 1] + 1,
                    previousRow[targetIndex - 1] + substitutionCost,
                )

                if (sourceIndex > 1 && targetIndex > 1 &&
                    source[sourceIndex - 1] == target[targetIndex - 2] &&
                    source[sourceIndex - 2] == target[targetIndex - 1]
                ) {
                    value = minOf(value, previousPreviousRow[targetIndex - 2] + 1)
                }

                currentRow[targetIndex] = value
                if (value < rowMin) rowMin = value
            }

            if (rowMin > limit) return limit + 1

            val temp = previousPreviousRow
            previousPreviousRow = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[target.length]
    }
}
