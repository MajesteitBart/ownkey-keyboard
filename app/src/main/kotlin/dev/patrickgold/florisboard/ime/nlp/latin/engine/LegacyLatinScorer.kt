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

import dev.patrickgold.florisboard.ime.nlp.latin.LanguageConfidenceSignal
import dev.patrickgold.florisboard.ime.nlp.latin.MixedLanguageScoringPolicy
import java.util.Locale
import kotlin.math.abs

/**
 * The scoring that shipped until the autocorrect engine rebuild: a hand-weighted confidence formula on linear
 * frequencies, gated by [dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectPolicy]. Kept as the
 * baseline for the benchmark and as the fallback behind the devtools engine switch.
 */
internal class LegacyLatinScorer(
    private val mixedLanguageScoringPolicy: MixedLanguageScoringPolicy = MixedLanguageScoringPolicy(),
) : LatinCurrentWordScorer {
    companion object {
        private const val MaxLookupCandidateCount = 16
        private const val MinLengthForTypoCorrections = 4
        private const val SuggestionContextTailLength = 96
    }

    private data class AggregatedScoredCandidate(
        val ranked: RankedCandidate,
        val locale: Locale,
        val rankingScore: Double,
        val confidence: Double,
    )

    override suspend fun score(request: LatinScoringRequest, hooks: LatinScoringHooks): List<LatinScoredCandidate> {
        val primaryLocale = request.primaryLocale
        val rawInput = request.rawInput.trim()
        val normalizedInput = LatinText.normalizeInputWord(rawInput, primaryLocale)
        if (normalizedInput.isBlank()) return emptyList()

        val languages = request.languages.filter { it.model.words.isNotEmpty() }
        if (languages.isEmpty()) return emptyList()

        val contextTokens = LatinText.extractRecentContextTokens(request.textBeforeSelection)
        val languageWeights = computeLanguageConfidenceWeights(languages, contextTokens, normalizedInput)
        val hasExactMatch = languages.any { it.model.isKnown(normalizedInput) } ||
            hooks.isUserDictionaryWord(normalizedInput)
        val aggregatedCandidates = LinkedHashMap<String, AggregatedScoredCandidate>()

        for (language in languages) {
            val model = language.model
            val perLanguageCandidates = LinkedHashMap<String, RankedCandidate>()
            if (model.isKnown(normalizedInput)) {
                perLanguageCandidates[normalizedInput] = RankedCandidate(
                    word = normalizedInput,
                    distance = 0,
                    frequency = model.words[normalizedInput] ?: 1,
                    isPrefixMatch = true,
                )
            }

            for (candidate in model.lookupPrefixCandidates(normalizedInput, MaxLookupCandidateCount)) {
                perLanguageCandidates.putIfAbsent(candidate.word, candidate)
                if (perLanguageCandidates.size >= MaxLookupCandidateCount) break
            }

            if (normalizedInput.length >= MinLengthForTypoCorrections) {
                for (candidate in model.lookupCorrections(normalizedInput, MaxLookupCandidateCount)) {
                    perLanguageCandidates.putIfAbsent(candidate.word, candidate)
                    if (perLanguageCandidates.size >= MaxLookupCandidateCount) break
                }
            }

            val languageWeight = languageWeights[language.language] ?: 0.0
            perLanguageCandidates.values.forEach { candidate ->
                val baseRankScore = rankSuggestionCandidate(model, normalizedInput, candidate)
                val weightedRankScore = mixedLanguageScoringPolicy.applyLanguageWeight(baseRankScore, languageWeight)
                val baseConfidence = calculateConfidence(model, normalizedInput, candidate)
                val weightedConfidence = mixedLanguageScoringPolicy.blendCandidateConfidence(baseConfidence, languageWeight)
                val current = aggregatedCandidates[candidate.word]
                if (current == null ||
                    weightedRankScore > current.rankingScore ||
                    (weightedRankScore == current.rankingScore && weightedConfidence > current.confidence)
                ) {
                    aggregatedCandidates[candidate.word] = AggregatedScoredCandidate(
                        ranked = candidate,
                        locale = language.locale,
                        rankingScore = weightedRankScore,
                        confidence = weightedConfidence,
                    )
                }
            }
        }

        applyPersonalContextBoost(aggregatedCandidates, request, normalizedInput, hooks)

        if (aggregatedCandidates.isEmpty()) return emptyList()

        val sortedCandidates = aggregatedCandidates.values
            .sortedWith(
                compareByDescending<AggregatedScoredCandidate> { it.rankingScore }
                    .thenByDescending { it.ranked.frequency }
                    .thenBy { it.ranked.word }
            )
            .take(request.maxCandidateCount)
        val topCandidate = sortedCandidates.firstOrNull()
        val runnerUpConfidence = sortedCandidates.getOrNull(1)?.confidence
        val isBlockedByUserPreference = hooks.isBlockedByUserPreference(normalizedInput)

        return sortedCandidates.map { scoredCandidate ->
            val candidate = scoredCandidate.ranked
            val isAutoCommitCandidate = topCandidate == scoredCandidate && request.policy.shouldAutoCommit(
                normalizedInput = normalizedInput,
                candidateWord = candidate.word,
                candidateEditDistance = candidate.distance,
                candidateConfidence = scoredCandidate.confidence,
                runnerUpConfidence = runnerUpConfidence,
                hasExactInputMatch = hasExactMatch,
                isBlockedByUserPreference = isBlockedByUserPreference,
            )
            LatinScoredCandidate(
                word = candidate.word,
                text = LatinText.applyInputCase(rawInput, candidate.word, scoredCandidate.locale),
                locale = scoredCandidate.locale,
                confidence = scoredCandidate.confidence,
                editDistance = candidate.distance,
                isAutoCommit = isAutoCommitCandidate,
            )
        }
    }

    private fun computeLanguageConfidenceWeights(
        languages: List<LatinScoringLanguage>,
        contextTokens: List<String>,
        normalizedInput: String?,
    ): Map<String, Double> {
        val signals = languages.map { language ->
            val contextEvidence = contextTokens.mapIndexed { index, token ->
                if (!language.model.isKnown(token)) {
                    0.0
                } else {
                    val recencyWeight = (index + 1).toDouble() / contextTokens.size.coerceAtLeast(1).toDouble()
                    0.6 + 0.4 * recencyWeight
                }
            }.sum()
            LanguageConfidenceSignal(
                language = language.language,
                isPrimary = language.isPrimary,
                contextEvidence = contextEvidence,
                hasExactInputMatch = normalizedInput != null && language.model.isKnown(normalizedInput),
            )
        }
        return mixedLanguageScoringPolicy.computeLanguageWeights(signals)
    }

    /**
     * Boosts candidates which the personal n-gram model has seen following the previous word.
     */
    private suspend fun applyPersonalContextBoost(
        aggregatedCandidates: LinkedHashMap<String, AggregatedScoredCandidate>,
        request: LatinScoringRequest,
        normalizedInput: String,
        hooks: LatinScoringHooks,
    ) {
        if (aggregatedCandidates.isEmpty()) return
        val tokens = LatinText.extractWordTokens(
            request.textBeforeSelection.takeLast(SuggestionContextTailLength),
            request.primaryLocale,
        )
        if (tokens.size < 2 || tokens.last() != normalizedInput) return
        val previousWord = tokens[tokens.size - 2]
        for (entry in aggregatedCandidates.entries) {
            val boost = hooks.personalContinuationScore(previousWord, entry.key)
            if (boost > 0.0) {
                entry.setValue(
                    entry.value.copy(
                        rankingScore = entry.value.rankingScore + 0.30 * boost,
                        confidence = (entry.value.confidence + 0.10 * boost).coerceAtMost(1.0),
                    )
                )
            }
        }
    }

    private fun rankSuggestionCandidate(model: LatinWordModel, input: String, candidate: RankedCandidate): Double {
        val frequencyScore = candidate.frequency.toDouble() / model.maxFrequency.toDouble()
        val prefixBoost = if (candidate.isPrefixMatch) 0.35 else 0.0
        val distancePenalty = when (candidate.distance) {
            0 -> 0.0
            1 -> 0.20
            else -> 0.50
        }
        val inputLength = input.length.coerceAtLeast(1)
        val lengthDelta = abs(candidate.word.length - input.length)
        val lengthPenalty = (lengthDelta.toDouble() / inputLength.toDouble()) * 0.18
        val shortWordPenalty = if (input.length >= 5 && candidate.word.length <= 3) 0.30 else 0.0
        return frequencyScore + prefixBoost - distancePenalty - lengthPenalty - shortWordPenalty
    }

    private fun calculateConfidence(model: LatinWordModel, input: String, candidate: RankedCandidate): Double {
        val frequencyScore = (candidate.frequency.toDouble() / model.maxFrequency.toDouble()).coerceIn(0.0, 1.0)
        val distancePenalty = when (candidate.distance) {
            0 -> 1.0
            1 -> 0.75
            else -> 0.5
        }
        val prefixBoost = if (candidate.isPrefixMatch) 1.0 else 0.0
        val inputLength = input.length.coerceAtLeast(1)
        val lengthDelta = abs(candidate.word.length - input.length)
        val lengthCloseness = (1.0 - (lengthDelta.toDouble() / inputLength.toDouble())).coerceIn(0.0, 1.0)
        return (
            0.55 * frequencyScore +
                0.25 * distancePenalty +
                0.15 * prefixBoost +
                0.05 * lengthCloseness
            ).coerceIn(0.05, 1.0)
    }
}
