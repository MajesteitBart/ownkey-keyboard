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

import dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectPolicy
import java.util.Locale

/**
 * One language of the active subtype, with its loaded word model.
 */
internal data class LatinScoringLanguage(
    val language: String,
    val locale: Locale,
    val model: LatinWordModel,
    val isPrimary: Boolean,
    /** False when the language has no dictionary of its own and borrows the legacy (English) word list. */
    val hasOwnDictionary: Boolean = true,
)

/**
 * Where one typed character was tapped, in key widths from the top-left corner of the letter area.
 */
internal data class LatinTap(val x: Double, val y: Double)

/**
 * Everything a scorer needs to rank candidates for the word currently being typed.
 *
 * [textBeforeSelection] is the editor text before the cursor, which includes the current word when it is being
 * composed. [taps] has one entry per character of [rawInput] when tap positions are known.
 */
internal data class LatinScoringRequest(
    val rawInput: String,
    val primaryLocale: Locale,
    val languages: List<LatinScoringLanguage>,
    val textBeforeSelection: String,
    val maxCandidateCount: Int,
    val policy: HighCertaintyAutocorrectPolicy,
    val taps: List<LatinTap>? = null,
    val autocorrect: AutocorrectSettings = AutocorrectSettings(),
    val geometry: KeyGeometry? = null,
)

/**
 * Lookups that need Android or user data. The benchmark passes [None].
 */
internal interface LatinScoringHooks {
    fun isUserDictionaryWord(normalizedWord: String): Boolean
    fun isBlockedByUserPreference(normalizedWord: String): Boolean
    suspend fun personalContinuationScore(previousWord: String, candidateWord: String): Double

    object None : LatinScoringHooks {
        override fun isUserDictionaryWord(normalizedWord: String) = false
        override fun isBlockedByUserPreference(normalizedWord: String) = false
        override suspend fun personalContinuationScore(previousWord: String, candidateWord: String) = 0.0
    }
}

/**
 * A ranked candidate for the current word.
 *
 * @property word Normalized (lowercase) candidate word.
 * @property text Candidate as it should be committed, with the input's capitalization applied.
 * @property editDistance Edit distance from the normalized input, 0 for exact and completion candidates.
 * @property isAutoCommit Whether this candidate should replace the input on space.
 */
internal data class LatinScoredCandidate(
    val word: String,
    val text: String,
    val locale: Locale,
    val confidence: Double,
    val editDistance: Int,
    val isAutoCommit: Boolean,
)

internal interface LatinCurrentWordScorer {
    suspend fun score(request: LatinScoringRequest, hooks: LatinScoringHooks): List<LatinScoredCandidate>
}
