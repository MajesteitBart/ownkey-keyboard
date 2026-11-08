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
 * A word-level language model for one language, addressed by dense integer word ids.
 *
 * The contract is kept small so that a memory-mapped CSR n-gram reader can implement it without building
 * extra indexes: ids are `0 until vocabularySize`, words are stored lower case (diacritics and apostrophes
 * kept, see [TextFolding.normalizeWord]), and every probability is a natural logarithm.
 *
 * Implementations must be safe for concurrent reads.
 */
interface WordLanguageModel {
    /** Language tag, for example `"nl"` or `"en"`. */
    val language: String

    /** Number of word ids, including the sentence-start token if the model has one. */
    val vocabularySize: Int

    /** Id of the sentence-start context token, or -1 if the model has none. Never returned as a prediction. */
    val bosId: Int

    /**
     * False when [logProb] and [topNext] ignore the context (a unigram list). The engine then leaves the previous
     * word out of next-word predictions, because a context-free model would otherwise suggest it again.
     */
    val usesContext: Boolean get() = true

    /** Id of [word] (already normalised with [TextFolding.normalizeWord]), or -1 if it is not in the vocabulary. */
    fun wordId(word: String): Int

    /** Normalised surface form of [id]. */
    fun word(id: Int): String

    /**
     * True when the word is normally written with capitals: an initial capital (names, `I`, `I'm`), all capitals
     * (`FBI`) or mixed case (`iPhone`). [displayForm] gives the written form.
     */
    fun isCapitalized(id: Int): Boolean

    /**
     * The usual written form of [id] for display, for example `"I'm"`, `"Amsterdam"`, `"iPhone"`; [word] when the
     * word is written in lower case. Only differs from [word] in letter case. The default returns [word].
     */
    fun displayForm(id: Int): String = word(id)

    /**
     * True when [id] is really a word (in a curated word list, a name, or a word in its own right) rather than a token
     * that only occurs in the training text (a typo, an OCR error, a truncation). Autocorrect treats a typed word that
     * is in the vocabulary but not valid like an unknown word, and never corrects to one. The default is true (every
     * vocabulary word is valid).
     */
    fun isValidWord(id: Int): Boolean = true

    /**
     * True when [id] is a dictionary word (in a curated word list), not only a name or a chat word that is valid
     * through its capitals or its frequency. Context-aware real-word correction ([EngineConfig.deltaKnownLmSlope]) only
     * relaxes its margin for dictionary words. The default is false: a model without a curated word list (a frequency
     * list, a version 1 n-gram file) cannot tell names and slang from dictionary words, so it keeps the full margin.
     */
    fun isListedWord(id: Int): Boolean = false

    /** Context-free log probability of [id]. */
    fun unigramLogProb(id: Int): Double

    /**
     * Log probability of [id] after the context `prev2 prev1` (prev1 is the most recent word). Either context id
     * may be -1 (unknown or absent) or [bosId]; implementations back off to shorter contexts as needed.
     */
    fun logProb(id: Int, prev1: Int, prev2: Int): Double

    /**
     * The most probable next words after `prev2 prev1`, sorted by descending log probability, at most [limit]
     * entries, never containing [bosId]. A model without context information returns its top unigrams.
     */
    fun topNext(prev1: Int, prev2: Int, limit: Int): List<ScoredWord>
}

/** A word id with a natural-log probability. */
data class ScoredWord(val id: Int, val logProb: Double)
