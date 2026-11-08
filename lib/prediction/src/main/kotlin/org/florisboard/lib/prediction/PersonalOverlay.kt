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

import kotlin.math.max

/**
 * The user's own words (user dictionary, learned words) as a small extra vocabulary for [PredictionEngine].
 *
 * Overlay words are decoded like lexicon words: they are completed, offered as corrections and suggestions, and an
 * overlay word typed exactly counts as a known literal. Their probability is [EngineConfig.personalWordLogProb]
 * (minus up to [FREQUENCY_RANGE] for less frequent ones), added to the language mixture without a language prior,
 * so they never shift the language weights. Building takes a few milliseconds for a few thousand words; rebuild the
 * overlay when the user's words change and swap it in with [PredictionEngine.withPersonalOverlay].
 *
 * Words are normalised like language-model words ([TextFolding.normalizeWord]). Entries that are not letters plus
 * apostrophes ("mp3", "e-mail") are skipped. Instances are immutable and thread-safe.
 */
class PersonalOverlay private constructor(internal val model: UnigramListModel) {
    internal val lexicon: Lexicon = Lexicon.build(model)

    private val topLogProb = if (model.vocabularySize > 0) model.unigramLogProb(0) else 0.0

    /** Number of words. */
    val size: Int get() = model.vocabularySize

    /** True when [word] is an overlay word. */
    fun contains(word: String): Boolean = model.wordId(TextFolding.normalizeWord(word)) >= 0

    /** Log probability of [id] relative to the most frequent overlay word, in `[-FREQUENCY_RANGE, 0]`. */
    internal fun logOffset(id: Int): Double = max(model.unigramLogProb(id) - topLogProb, -FREQUENCY_RANGE)

    companion object {
        /** Frequency differences between overlay words are capped at this many nats. */
        const val FREQUENCY_RANGE = 3.0

        /** An overlay without words. */
        val Empty: PersonalOverlay by lazy { build(emptyList()) }

        /** Overlay from `(word, count)` pairs; a higher count makes a word more probable than the others. */
        fun build(words: Iterable<Pair<String, Long>>): PersonalOverlay =
            PersonalOverlay(UnigramListModel.fromCounts("user", words))

        /** Overlay of equally probable words. */
        fun of(words: Iterable<String>): PersonalOverlay = build(words.map { it to 1L })
    }
}
