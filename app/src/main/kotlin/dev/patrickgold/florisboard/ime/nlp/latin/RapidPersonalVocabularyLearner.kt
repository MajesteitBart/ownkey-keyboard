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

import java.util.Locale
import kotlin.math.max

internal data class LearningPromotion(
    val language: String,
    val word: String,
    val confirmations: Int,
)

/**
 * Tracks short-term confirmation exposures for rapid personal vocabulary promotion.
 *
 * Keeps all transient state in memory and only emits a promotion event when quality
 * constraints are met within a bounded time window.
 */
internal class RapidPersonalVocabularyLearner(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val decayWindowMillis: Long = 12L * 60L * 60L * 1000L,
    private val suppressionWindowMillis: Long = 24L * 60L * 60L * 1000L,
) {
    private data class ExposureState(
        val confirmations: Int,
        val lastSeenAt: Long,
    )

    private val exposuresByLanguage = mutableMapOf<String, MutableMap<String, ExposureState>>()
    private val suppressionByLanguage = mutableMapOf<String, MutableMap<String, Long>>()

    fun onSuggestionAccepted(language: String, candidateWord: String, confidence: Double): LearningPromotion? {
        val normalizedLanguage = normalizeLanguage(language) ?: return null
        val normalizedWord = normalizeWord(candidateWord) ?: return null
        val now = nowProvider()

        pruneSuppression(normalizedLanguage, now)
        pruneExposures(normalizedLanguage, now)
        val suppressionUntil = suppressionByLanguage[normalizedLanguage]?.get(normalizedWord)
        if (suppressionUntil != null && suppressionUntil > now) {
            return null
        }

        val languageExposures = exposuresByLanguage.getOrPut(normalizedLanguage) { mutableMapOf() }
        val previous = languageExposures[normalizedWord]
        val decayedConfirmations = previous?.let {
            val elapsedWindows = ((now - it.lastSeenAt) / decayWindowMillis).toInt().coerceAtLeast(0)
            max(0, it.confirmations - elapsedWindows)
        } ?: 0

        val updatedConfirmations = decayedConfirmations + 1
        val requiredConfirmations = requiredConfirmations(confidence)
        return if (updatedConfirmations >= requiredConfirmations) {
            languageExposures.remove(normalizedWord)
            LearningPromotion(
                language = normalizedLanguage,
                word = normalizedWord,
                confirmations = updatedConfirmations,
            )
        } else {
            languageExposures[normalizedWord] = ExposureState(
                confirmations = updatedConfirmations,
                lastSeenAt = now,
            )
            null
        }
    }

    fun onSuggestionReverted(language: String, candidateWord: String) {
        val normalizedLanguage = normalizeLanguage(language) ?: return
        val normalizedWord = normalizeWord(candidateWord) ?: return
        val now = nowProvider()

        exposuresByLanguage[normalizedLanguage]?.remove(normalizedWord)
        suppressionByLanguage
            .getOrPut(normalizedLanguage) { mutableMapOf() }
            .set(normalizedWord, now + suppressionWindowMillis)
    }

    private fun requiredConfirmations(confidence: Double): Int {
        return when {
            confidence >= 0.92 -> 1
            confidence >= 0.72 -> 2
            else -> 3
        }
    }

    private fun normalizeLanguage(language: String): String? {
        val normalized = language.trim().lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun normalizeWord(word: String): String? {
        val normalized = word.trim()
            .replace('’', '\'')
            .lowercase(Locale.ROOT)

        if (normalized.length !in 3..32) return null
        if (!normalized.any { it.isLetter() }) return null
        if (normalized.any { it.isDigit() }) return null
        if (normalized.count { it == '\'' || it == '-' } > 2) return null
        if (normalized.any { !(it.isLetter() || it == '\'' || it == '-') }) return null

        return normalized
    }

    private fun pruneExposures(language: String, now: Long) {
        val languageExposures = exposuresByLanguage[language] ?: return
        val maxAgeMillis = decayWindowMillis * 3L
        languageExposures.entries.removeAll { (_, exposure) ->
            now - exposure.lastSeenAt >= maxAgeMillis
        }
        if (languageExposures.isEmpty()) {
            exposuresByLanguage.remove(language)
        }
    }

    private fun pruneSuppression(language: String, now: Long) {
        val languageSuppression = suppressionByLanguage[language] ?: return
        languageSuppression.entries.removeAll { (_, suppressionUntil) ->
            suppressionUntil <= now
        }
        if (languageSuppression.isEmpty()) {
            suppressionByLanguage.remove(language)
        }
    }
}
