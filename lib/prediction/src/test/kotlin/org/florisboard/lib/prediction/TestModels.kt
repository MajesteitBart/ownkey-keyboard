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
import kotlin.math.ln

/** Shared fixtures: the bundled 50k frequency lists and engines built from them (loaded once per test JVM). */
object TestModels {
    val repoRoot: File by lazy {
        System.getProperty("ownkey.repoRoot")?.let { return@lazy File(it) }
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        requireNotNull(dir) { "repository root not found" }
    }

    fun repoFile(path: String): File = File(repoRoot, path)

    val en: UnigramListModel by lazy {
        UnigramListModel.fromFile("en", repoFile("app/src/main/assets/ime/dict/frequencywords/en_50k.txt"))
    }

    val nl: UnigramListModel by lazy {
        UnigramListModel.fromFile("nl", repoFile("app/src/main/assets/ime/dict/frequencywords/nl_50k.txt"))
    }

    /** Dutch primary plus English, the benchmark setup. */
    val nlEn: PredictionEngine by lazy { PredictionEngine(listOf(nl, en)) }

    val enOnly: PredictionEngine by lazy { PredictionEngine(listOf(en)) }

    val nlOnly: PredictionEngine by lazy { PredictionEngine(listOf(nl)) }

    fun model(language: String, vararg counts: Pair<String, Long>): UnigramListModel =
        UnigramListModel.fromCounts(language, counts.toList())
}

/**
 * A tiny context-aware model with a sentence-start token, for the engine's context paths. Scores use stupid
 * backoff (trigram, then 0.4 x bigram, then 0.16 x unigram), which is not normalised but ranks like an n-gram
 * model. Context words are given as strings; [BOS] is the sentence-start token.
 */
class TestNgramModel(
    override val language: String,
    unigrams: Map<String, Long>,
    bigrams: Map<Pair<String, String>, Long> = emptyMap(),
    trigrams: Map<Triple<String, String, String>, Long> = emptyMap(),
) : WordLanguageModel {
    private val words = listOf(BOS) + unigrams.keys.sorted()
    private val ids = words.withIndex().associate { (i, w) -> w to i }
    private val uni: DoubleArray
    private val bi: Map<Pair<Int, Int>, Double>
    private val tri: Map<Triple<Int, Int, Int>, Double>

    init {
        val total = unigrams.values.sum().toDouble()
        uni = DoubleArray(words.size) { if (it == 0) -99.0 else ln(unigrams.getValue(words[it]) / total) }
        val biTotals = bigrams.entries.groupBy { it.key.first }.mapValues { e -> e.value.sumOf { it.value }.toDouble() }
        bi = bigrams.entries.associate { (k, c) -> (id(k.first) to id(k.second)) to ln(c / biTotals.getValue(k.first)) }
        val triTotals = trigrams.entries.groupBy { it.key.first to it.key.second }.mapValues { e -> e.value.sumOf { it.value }.toDouble() }
        tri = trigrams.entries.associate { (k, c) ->
            Triple(id(k.first), id(k.second), id(k.third)) to ln(c / triTotals.getValue(k.first to k.second))
        }
    }

    private fun id(w: String): Int = requireNotNull(ids[w]) { "unknown test word '$w'" }

    override val vocabularySize: Int get() = words.size
    override val bosId: Int get() = 0

    override fun wordId(word: String): Int = ids[word]?.takeIf { it != 0 } ?: -1
    override fun word(id: Int): String = words[id]
    override fun isCapitalized(id: Int): Boolean = false
    override fun unigramLogProb(id: Int): Double = uni[id]

    override fun logProb(id: Int, prev1: Int, prev2: Int): Double {
        if (prev1 >= 0 && prev2 >= 0) tri[Triple(prev2, prev1, id)]?.let { return it }
        if (prev1 >= 0) bi[prev1 to id]?.let { return ln(0.4) + it }
        return 2 * ln(0.4) + uni[id]
    }

    override fun topNext(prev1: Int, prev2: Int, limit: Int): List<ScoredWord> =
        (1 until words.size).map { ScoredWord(it, logProb(it, prev1, prev2)) }
            .sortedWith(compareByDescending<ScoredWord> { it.logProb }.thenBy { it.id }).take(limit)

    companion object {
        const val BOS = "<s>"
    }
}
