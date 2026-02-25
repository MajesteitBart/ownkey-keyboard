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

internal data class LanguageConfidenceSignal(
    val language: String,
    val isPrimary: Boolean,
    val contextEvidence: Double,
    val hasExactInputMatch: Boolean,
)

internal class MixedLanguageScoringPolicy(
    private val primaryPrior: Double = 1.0,
    private val secondaryPrior: Double = 0.78,
    private val contextEvidenceWeight: Double = 0.40,
    private val exactInputBoost: Double = 0.42,
) {
    fun computeLanguageWeights(signals: List<LanguageConfidenceSignal>): Map<String, Double> {
        if (signals.isEmpty()) return emptyMap()

        val rawScores = signals.associate { signal ->
            val score = (
                if (signal.isPrimary) primaryPrior else secondaryPrior
                ) + contextEvidenceWeight * signal.contextEvidence + if (signal.hasExactInputMatch) exactInputBoost else 0.0
            signal.language to score.coerceAtLeast(0.01)
        }

        val sum = rawScores.values.sum().coerceAtLeast(0.01)
        return rawScores.mapValues { (_, score) -> score / sum }
    }

    fun applyLanguageWeight(baseRankScore: Double, languageWeight: Double): Double {
        val multiplier = (0.60 + languageWeight).coerceIn(0.60, 1.60)
        return baseRankScore * multiplier
    }

    fun blendCandidateConfidence(baseConfidence: Double, languageWeight: Double): Double {
        return (0.88 * baseConfidence + 0.12 * languageWeight).coerceIn(0.05, 1.0)
    }
}
