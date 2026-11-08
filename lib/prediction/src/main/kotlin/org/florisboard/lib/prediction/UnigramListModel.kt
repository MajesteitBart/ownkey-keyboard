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

import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlin.math.ln

/**
 * A context-free [WordLanguageModel] built from a `word count` frequency list such as
 * `app/src/main/assets/ime/dict/frequencywords/nl_50k.txt`.
 *
 * Words are normalised with [TextFolding.normalizeWord], entries that are not letters plus apostrophes
 * (`"dr."`, `"mp3"`) are skipped and duplicates are merged. Ids are assigned by descending count, so id 0 is the
 * most frequent word. [topNext] ignores the context and returns the top unigrams; [bosId] is -1 and
 * [usesContext] is false.
 *
 * The written form of a word ([displayForm]) is its most frequent spelling in the input (curly apostrophes
 * straightened), so `"Sanne"` or `"iPhone"` keep their capitals and [isCapitalized] is true for them.
 */
class UnigramListModel private constructor(
    override val language: String,
    private val words: Array<String>,
    private val logProbs: FloatArray,
    /** Written form per id, or null when it is the lower-case [words] entry itself. */
    private val displayForms: Array<String?>,
) : WordLanguageModel {
    private val ids = HashMap<String, Int>(words.size * 2).also { map ->
        words.forEachIndexed { i, w -> map[w] = i }
    }

    @Volatile
    private var topCache: List<ScoredWord> = emptyList()

    override val vocabularySize: Int get() = words.size

    override val bosId: Int get() = -1

    override val usesContext: Boolean get() = false

    override fun wordId(word: String): Int = ids[word] ?: -1

    override fun word(id: Int): String = words[id]

    override fun isCapitalized(id: Int): Boolean = displayForms[id] != null

    override fun displayForm(id: Int): String = displayForms[id] ?: words[id]

    override fun unigramLogProb(id: Int): Double = logProbs[id].toDouble()

    override fun logProb(id: Int, prev1: Int, prev2: Int): Double = logProbs[id].toDouble()

    override fun topNext(prev1: Int, prev2: Int, limit: Int): List<ScoredWord> {
        val n = minOf(limit, words.size)
        var cached = topCache
        if (cached.size < n) {
            cached = List(n) { ScoredWord(it, logProbs[it].toDouble()) }
            topCache = cached
        }
        return if (cached.size == n) cached else cached.subList(0, n)
    }

    companion object {
        /** Builds a model from `(word, count)` pairs; see the class documentation for normalisation. */
        fun fromCounts(language: String, counts: Iterable<Pair<String, Long>>): UnigramListModel {
            val merged = LinkedHashMap<String, Long>()
            // Count per written spelling of each normalised word; the most frequent one is displayed.
            val spellings = HashMap<String, LinkedHashMap<String, Long>>()
            for ((raw, count) in counts) {
                if (count <= 0 || !TextFolding.isWordLike(raw)) continue
                val w = TextFolding.normalizeWord(raw)
                merged[w] = (merged[w] ?: 0L) + count
                val written = raw.replace('’', '\'')
                if (written != w || spellings.containsKey(w)) {
                    val forms = spellings.getOrPut(w) { LinkedHashMap() }
                    forms[written] = (forms[written] ?: 0L) + count
                }
            }
            val entries = merged.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            val total = entries.sumOf { it.value }.toDouble()
            val words = Array(entries.size) { entries[it].key }
            val logProbs = FloatArray(entries.size) { ln(entries[it].value / total).toFloat() }
            val displayForms = Array(entries.size) { i ->
                val w = words[i]
                val forms = spellings[w] ?: return@Array null
                // Lower-case occurrences seen before the first capitalised one were not recorded; they are the
                // remainder of the merged count.
                val lower = entries[i].value - forms.entries.sumOf { (f, c) -> if (f == w) 0L else c }
                val best = forms.entries.filter { it.key != w }.maxByOrNull { it.value } ?: return@Array null
                if (best.value >= lower && best.key.lowercase(Locale.ROOT) == w) best.key else null
            }
            return UnigramListModel(language, words, logProbs, displayForms)
        }

        /** Parses a `word count` list (one entry per line, separated by whitespace). Malformed lines are skipped. */
        fun fromFrequencyList(language: String, lines: Sequence<String>, maxWords: Int = Int.MAX_VALUE): UnigramListModel {
            val pairs = ArrayList<Pair<String, Long>>()
            for (line in lines) {
                if (pairs.size >= maxWords) break
                val t = line.trim()
                val sp = t.lastIndexOf(' ').takeIf { it > 0 } ?: t.lastIndexOf('\t').takeIf { it > 0 } ?: continue
                val count = t.substring(sp + 1).toLongOrNull() ?: continue
                pairs.add(t.substring(0, sp).trim() to count)
            }
            return fromCounts(language, pairs)
        }

        fun fromStream(language: String, input: InputStream, maxWords: Int = Int.MAX_VALUE): UnigramListModel {
            return input.bufferedReader(Charsets.UTF_8).useLines { fromFrequencyList(language, it, maxWords) }
        }

        fun fromFile(language: String, file: File, maxWords: Int = Int.MAX_VALUE): UnigramListModel {
            return file.inputStream().use { fromStream(language, it, maxWords) }
        }
    }
}
