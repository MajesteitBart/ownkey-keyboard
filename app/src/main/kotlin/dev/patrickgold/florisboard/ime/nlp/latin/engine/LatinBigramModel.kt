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

import kotlin.math.exp
import kotlin.math.ln

/**
 * Which words follow which, for one language: counts of word pairs from `tools/dictionary-build/bigrams.py`.
 *
 * Built for a small heap: all words live in one sorted, packed string and are found by binary search, and each
 * word's successors are a range of shared id and count arrays, sorted by id. A lookup allocates nothing.
 */
internal class LatinBigramModel private constructor(
    /** All words, sorted, back to back; word i spans [wordStarts] i until i + 1. */
    private val packedWords: String,
    private val wordStarts: IntArray,
    /** Successors of word i are the entries [offsets] i until i + 1 of [successorIds] and [logCounts]. */
    private val offsets: IntArray,
    private val successorIds: IntArray,
    /** round(100 * ln(count)), as in the word lists. */
    private val logCounts: ShortArray,
    private val totals: FloatArray,
) {
    companion object {
        /** The previous "word" at the start of a sentence. */
        const val SentenceStart = "<s>"
        private const val Header = "# ownkey-latin-bigrams"

        val Empty = LatinBigramModel("", IntArray(1), IntArray(1), IntArray(0), ShortArray(0), FloatArray(0))

        /**
         * Parses the v2 format written by `tools/dictionary-build/bigrams.py`: a sorted `@words` section, then
         * `@pairs` lines of `previousId<TAB>idDelta logCount idDelta logCount ...` in ascending previous-word order.
         * Nothing is allocated per pair.
         */
        fun parse(lines: Sequence<String>): LatinBigramModel {
            var sawHeader = false
            var inWords = false
            var inPairs = false
            var expectedWords = 0
            val packed = StringBuilder()
            var wordStarts = IntArray(1)
            var wordCount = 0
            var previousWord: String? = null
            var offsets = IntArray(1)
            val successorIds = IntList()
            val logCounts = ShortList()
            var lastPrevious = -1
            for (line in lines) {
                if (line.startsWith("#")) {
                    if (line.startsWith(Header)) {
                        require(line.startsWith("$Header v2 ")) { "Unsupported bigram list version: $line" }
                        sawHeader = true
                    }
                    continue
                }
                if (line.isEmpty()) continue
                when {
                    inPairs -> {
                        val tab = line.indexOf('\t')
                        require(tab > 0) { "Malformed pairs line" }
                        val previous = parseInt(line, 0, tab)
                        require(previous in (lastPrevious + 1) until wordCount) { "Pairs out of order at id $previous" }
                        for (id in lastPrevious + 1..previous) offsets[id] = successorIds.size
                        lastPrevious = previous
                        var id = 0
                        var position = tab + 1
                        while (position < line.length) {
                            val deltaEnd = line.indexOf(' ', position)
                            require(deltaEnd > position) { "Malformed pairs line for id $previous" }
                            val countEnd = line.indexOf(' ', deltaEnd + 1).let { if (it < 0) line.length else it }
                            id += parseInt(line, position, deltaEnd)
                            require(id < wordCount) { "Successor id out of range for id $previous" }
                            successorIds.add(id)
                            logCounts.add(parseInt(line, deltaEnd + 1, countEnd).coerceIn(0, Short.MAX_VALUE.toInt()).toShort())
                            position = countEnd + 1
                        }
                    }
                    inWords -> {
                        if (line == "@pairs") {
                            require(wordCount == expectedWords) { "Expected $expectedWords words, found $wordCount" }
                            wordStarts[wordCount] = packed.length
                            offsets = IntArray(wordCount + 1)
                            inWords = false
                            inPairs = true
                        } else {
                            require(wordCount < expectedWords) { "More words than announced" }
                            require(previousWord == null || previousWord < line) { "Words are not sorted at: $line" }
                            wordStarts[wordCount++] = packed.length
                            packed.append(line)
                            previousWord = line
                        }
                    }
                    else -> {
                        require(sawHeader && line.startsWith("@words ")) { "Not an Ownkey bigram list" }
                        expectedWords = line.substring("@words ".length).trim().toInt()
                        wordStarts = IntArray(expectedWords + 1)
                        inWords = true
                    }
                }
            }
            if (!inPairs) return Empty
            for (id in lastPrevious + 1..wordCount) offsets[id] = successorIds.size

            val ids = successorIds.toArray()
            val counts = logCounts.toArray()
            val totals = FloatArray(wordCount) { word ->
                var sum = 0.0
                for (j in offsets[word] until offsets[word + 1]) sum += exp(counts[j] / 100.0)
                sum.toFloat()
            }
            return LatinBigramModel(packed.toString(), wordStarts, offsets, ids, counts, totals)
        }

        /** Parses the non-negative decimal number in [text] from [start] until [end]. */
        private fun parseInt(text: String, start: Int, end: Int): Int {
            require(end > start) { "Empty number" }
            var value = 0
            for (i in start until end) {
                val digit = text[i] - '0'
                require(digit in 0..9) { "Not a number: ${text.substring(start, end)}" }
                value = value * 10 + digit
            }
            return value
        }
    }

    val pairCount: Int get() = successorIds.size

    fun isEmpty(): Boolean = successorIds.isEmpty()

    /** How often [previous] was seen with any successor; 0 when it never was. */
    fun totalAfter(previous: String): Double = idOf(previous).let { if (it < 0) 0.0 else totals[it].toDouble() }

    /** How many different words were seen after [previous]. */
    fun distinctAfter(previous: String): Int = idOf(previous).let { if (it < 0) 0 else offsets[it + 1] - offsets[it] }

    /** How often [next] was seen right after [previous]; 0 when never. */
    fun count(previous: String, next: String): Double {
        val previousId = idOf(previous)
        if (previousId < 0) return 0.0
        val id = idOf(next)
        if (id < 0) return 0.0
        var low = offsets[previousId]
        var high = offsets[previousId + 1] - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midId = successorIds[mid]
            when {
                midId < id -> low = mid + 1
                midId > id -> high = mid - 1
                else -> return exp(logCounts[mid] / 100.0)
            }
        }
        return 0.0
    }

    /** ln P(next | previous) from the pair counts alone, or null when the pair was never seen. */
    fun logProbability(previous: String, next: String): Double? {
        val count = count(previous, next)
        if (count <= 0.0) return null
        return ln(count / totalAfter(previous))
    }

    /** The [maxCount] most frequent words after [previous], most frequent first, with their counts. */
    fun successors(previous: String, maxCount: Int): List<Pair<String, Double>> {
        val previousId = idOf(previous)
        if (previousId < 0 || maxCount <= 0) return emptyList()
        // Partial selection: keep the best maxCount positions in a small sorted list.
        val best = ArrayList<Int>(maxCount + 1)
        for (j in offsets[previousId] until offsets[previousId + 1]) {
            if (best.size == maxCount && logCounts[j] <= logCounts[best.last()]) continue
            var insertAt = best.size
            while (insertAt > 0 && logCounts[best[insertAt - 1]] < logCounts[j]) insertAt--
            best.add(insertAt, j)
            if (best.size > maxCount) best.removeAt(best.lastIndex)
        }
        return best.map { wordAt(successorIds[it]) to exp(logCounts[it] / 100.0) }
    }

    private fun wordAt(id: Int): String = packedWords.substring(wordStarts[id], wordStarts[id + 1])

    /** Sorted id of [word], or -1. */
    private fun idOf(word: String): Int {
        var low = 0
        var high = wordStarts.size - 2
        while (low <= high) {
            val mid = (low + high) ushr 1
            val cmp = compareWordAt(mid, word)
            when {
                cmp < 0 -> low = mid + 1
                cmp > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Compares word [id] with [word] like String.compareTo, without allocating. */
    private fun compareWordAt(id: Int, word: String): Int {
        val start = wordStarts[id]
        val length = wordStarts[id + 1] - start
        val common = minOf(length, word.length)
        for (i in 0 until common) {
            val diff = packedWords[start + i] - word[i]
            if (diff != 0) return diff
        }
        return length - word.length
    }

    private class IntList {
        private var data = IntArray(1024)
        var size = 0
            private set

        fun add(value: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        operator fun get(index: Int): Int = data[index]

        fun toArray(): IntArray = data.copyOf(size)
    }

    private class ShortList {
        private var data = ShortArray(1024)
        private var size = 0

        fun add(value: Short) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        operator fun get(index: Int): Short = data[index]

        fun toArray(): ShortArray = data.copyOf(size)
    }
}
