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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Tunable costs of the noisy-channel scorer. Costs are negative log-probabilities (nats) of one typing error.
 */
data class NoisyChannelParams(
    val substitutionBaseCost: Double = 3.5,
    val substitutionDistanceCost: Double = 2.0,
    val maxSubstitutionCost: Double = 10.0,
    val diacriticSubstitutionCost: Double = 1.5,
    val omissionCost: Double = 5.0,
    val doubledInsertionCost: Double = 5.0,
    val adjacentInsertionCost: Double = 5.5,
    val otherInsertionCost: Double = 8.0,
    val transpositionCost: Double = 5.0,
    /** Spelling (not tapping) errors: one vowel written for another, as in "seperate". */
    val vowelSubstitutionCost: Double = 5.5,
    /** Spelling errors: a doubled letter written once ("acomodate") or a single letter doubled ("untill"). */
    val doubledLetterCost: Double = 4.0,
    /** Log-probability that a word the user typed is a real word missing from the dictionaries. */
    val unknownWordLogProb: Double = -20.0,
    /** Extra weight for keeping a three-letter word as typed; short words have many near neighbors. */
    val shortInputLiteralBonus: Double = 2.5,
    /** Extra weight for keeping a capitalized word in mid-sentence as typed; it is probably a name. */
    val capitalizedLiteralBonus: Double = 6.0,
    /** Extra weight for keeping an all-caps word as typed; it is probably an acronym. */
    val allCapsLiteralBonus: Double = 6.0,
    /** Display ranking only: cost per letter the user has not typed yet. */
    val completionCostPerLetter: Double = 0.8,
    val personalContinuationWeight: Double = 4.0,
    /** Put the most likely finished word first; later slots favor completions of the typed prefix. */
    val bestFinishedWordFirst: Boolean = true,
)

/**
 * When autocorrect may replace a word: [threshold] is the minimum posterior probability of the correction.
 */
data class AutocorrectSettings(
    val enabled: Boolean = true,
    val threshold: Double = DefaultThreshold,
    val minInputLength: Int = 3,
) {
    /**
     * Shifts the threshold for an app profile. [percent] above 100 corrects more eagerly, below 100 more carefully.
     */
    fun withAggressiveness(percent: Int): AutocorrectSettings {
        val factor = percent.coerceIn(70, 130) / 100.0
        return copy(threshold = (1.0 - (1.0 - threshold) * factor).coerceIn(0.5, 0.995))
    }

    companion object {
        const val DefaultThreshold = 0.95
    }
}

/**
 * Scores candidates as P(word | context) * P(typed | word), with the typed word competing as "keep what I typed".
 * Autocorrect fires when the best correction's posterior probability clears [AutocorrectSettings.threshold].
 */
internal class NoisyChannelLatinScorer(
    private val params: NoisyChannelParams = NoisyChannelParams(),
    private val mixedLanguageScoringPolicy: MixedLanguageScoringPolicy = MixedLanguageScoringPolicy(),
) : LatinCurrentWordScorer {
    companion object {
        private const val PrefixLookupCount = 16
        private const val CorrectionLookupCount = 32
        private const val SuggestionContextTailLength = 96
        private const val Unreachable = 1e9
        private val Vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
    }

    private class Scored(
        val word: String,
        val locale: Locale,
        val finishedScore: Double,
        val displayScore: Double,
        val frequency: Int,
    )

    override suspend fun score(request: LatinScoringRequest, hooks: LatinScoringHooks): List<LatinScoredCandidate> {
        val rawInput = request.rawInput.trim()
        val input = LatinText.normalizeInputWord(rawInput, request.primaryLocale)
        if (input.isBlank()) return emptyList()
        val languages = request.languages.filter { it.model.words.isNotEmpty() }
        if (languages.isEmpty()) return emptyList()
        val geometry = request.geometry ?: KeyGeometry.QwertyPhone

        val tokens = LatinText.extractWordTokens(
            request.textBeforeSelection.takeLast(SuggestionContextTailLength),
            request.primaryLocale,
        )
        val endsWithInput = tokens.lastOrNull() == input
        val contextTokens = (if (endsWithInput) tokens.dropLast(1) else tokens)
            .takeLast(LatinText.RecentContextTokenWindowSize)
        val previousWord = if (endsWithInput) tokens.getOrNull(tokens.size - 2) else null
        val logWeights = languageLogWeights(languages, contextTokens)

        val inputKnown = languages.any { it.model.isKnown(input) } ||
            hooks.isUserDictionaryWord(input) ||
            ChatShorthand.contains(input)

        val candidateWords = LinkedHashSet<String>()
        if (languages.any { it.model.isKnown(input) }) candidateWords.add(input)
        for (language in languages) {
            language.model.lookupPrefixCandidates(input, PrefixLookupCount).forEach { candidateWords.add(it.word) }
            if (input.length >= 2) {
                language.model.lookupCorrections(input, CorrectionLookupCount).forEach { candidateWords.add(it.word) }
            }
        }
        if (candidateWords.isEmpty()) return emptyList()

        val scored = ArrayList<Scored>(candidateWords.size)
        for (word in candidateWords) {
            var bestLanguageScore = Double.NEGATIVE_INFINITY
            var bestLocale = request.primaryLocale
            var bestFrequency = 0
            var logPrior = Double.NEGATIVE_INFINITY
            for (language in languages) {
                val frequency = language.model.words[word] ?: continue
                val languageScore = logWeights.getValue(language.language) +
                    ln(frequency.toDouble()) - ln(language.model.totalFrequency)
                logPrior = logSumExp(logPrior, languageScore)
                if (languageScore > bestLanguageScore) {
                    bestLanguageScore = languageScore
                    bestLocale = language.locale
                    bestFrequency = frequency
                }
            }
            if (previousWord != null) {
                val continuation = hooks.personalContinuationScore(previousWord, word)
                if (continuation > 0.0) logPrior += ln(1.0 + params.personalContinuationWeight * continuation)
            }
            val channel = if (word == input) 0.0 else channelCost(input, word, geometry)
            val finishedScore = logPrior - channel
            val isCompletion = word.length > input.length && word.startsWith(input)
            val displayScore = if (isCompletion) {
                logPrior - params.completionCostPerLetter * (word.length - input.length)
            } else {
                finishedScore
            }
            scored.add(Scored(word, bestLocale, finishedScore, displayScore, bestFrequency))
        }

        // "Keep what I typed" competes with every candidate. A known word is already a candidate itself.
        val literalScore = if (input in candidateWords) null else literalScore(rawInput, input, request.textBeforeSelection)
        var logNormalizer = literalScore ?: Double.NEGATIVE_INFINITY
        scored.forEach { logNormalizer = logSumExp(logNormalizer, it.finishedScore) }
        fun posterior(score: Double) = exp(score - logNormalizer)

        val bestCorrection = scored.filter { it.word != input }.maxByOrNull { it.finishedScore }
        val settings = request.autocorrect
        val isAutoCommit = bestCorrection != null &&
            settings.enabled &&
            !inputKnown &&
            input.length >= settings.minInputLength &&
            rawInput.none { it == '\'' || it == '’' || it == '-' } &&
            !hooks.isBlockedByUserPreference(input) &&
            posterior(bestCorrection.finishedScore) >= settings.threshold

        val bestFinished = scored.maxByOrNull { it.finishedScore }
        val lead = when {
            isAutoCommit -> bestCorrection
            params.bestFinishedWordFirst -> bestFinished
            else -> null
        }
        val ranked = scored
            .sortedWith(compareByDescending<Scored> { it.displayScore }.thenByDescending { it.frequency }.thenBy { it.word })
            .let { list -> if (lead != null) listOf(lead) + (list - lead) else list }
            .take(request.maxCandidateCount)

        return ranked.map { candidate ->
            LatinScoredCandidate(
                word = candidate.word,
                text = LatinText.applyInputCase(rawInput, candidate.word, candidate.locale),
                locale = candidate.locale,
                confidence = posterior(candidate.finishedScore).coerceIn(0.0, 1.0),
                editDistance = LatinText.boundedDamerauLevenshtein(input, candidate.word, 3),
                isAutoCommit = isAutoCommit && candidate === bestCorrection,
            )
        }
    }

    private fun literalScore(rawInput: String, input: String, textBeforeSelection: String): Double {
        var score = params.unknownWordLogProb
        if (input.length <= 3) score += params.shortInputLiteralBonus
        val letters = rawInput.filter { it.isLetter() }
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) {
            score += params.allCapsLiteralBonus
        } else if (rawInput.firstOrNull()?.isUpperCase() == true && !isSentenceStart(textBeforeSelection, rawInput)) {
            score += params.capitalizedLiteralBonus
        }
        return score
    }

    private fun isSentenceStart(textBeforeSelection: String, rawInput: String): Boolean {
        val before = textBeforeSelection.removeSuffix(rawInput).trimEnd()
        if (before.isEmpty()) return true
        return before.last() in ".!?\n"
    }

    private fun languageLogWeights(languages: List<LatinScoringLanguage>, contextTokens: List<String>): Map<String, Double> {
        val signals = languages.map { language ->
            val evidence = contextTokens.mapIndexed { index, token ->
                if (!language.model.isKnown(token)) 0.0 else 0.6 + 0.4 * ((index + 1).toDouble() / contextTokens.size)
            }.sum()
            LanguageConfidenceSignal(language.language, language.isPrimary, evidence, hasExactInputMatch = false)
        }
        return mixedLanguageScoringPolicy.computeLanguageWeights(signals).mapValues { (_, weight) ->
            ln(weight.coerceAtLeast(1e-6))
        }
    }

    /**
     * Cost of typing [typed] when [intended] was meant: weighted Damerau-Levenshtein with key-distance substitution.
     */
    internal fun channelCost(typed: String, intended: String, geometry: KeyGeometry): Double {
        val n = typed.length
        val m = intended.length
        val d = Array(n + 1) { DoubleArray(m + 1) { Unreachable } }
        d[0][0] = 0.0
        for (i in 0..n) {
            for (j in 0..m) {
                val current = d[i][j]
                if (current >= Unreachable) continue
                if (i < n && j < m) {
                    val cost = if (typed[i] == intended[j]) 0.0 else substitutionCost(typed[i], intended[j], geometry)
                    if (current + cost < d[i + 1][j + 1]) d[i + 1][j + 1] = current + cost
                }
                if (j < m) {
                    // Leaving out one letter of a doubled pair is a common spelling error.
                    val isDoubled = intended.getOrNull(j - 1) == intended[j] || intended.getOrNull(j + 1) == intended[j]
                    val cost = current + if (isDoubled) params.doubledLetterCost else params.omissionCost
                    if (cost < d[i][j + 1]) d[i][j + 1] = cost
                }
                if (i < n) {
                    val cost = current + insertionCost(typed, i, geometry)
                    if (cost < d[i + 1][j]) d[i + 1][j] = cost
                }
                if (i + 1 < n && j + 1 < m && typed[i] != typed[i + 1] &&
                    typed[i] == intended[j + 1] && typed[i + 1] == intended[j]
                ) {
                    val cost = current + params.transpositionCost
                    if (cost < d[i + 2][j + 2]) d[i + 2][j + 2] = cost
                }
            }
        }
        return d[n][m]
    }

    private fun substitutionCost(typed: Char, intended: Char, geometry: KeyGeometry): Double {
        val a = stripDiacritics(typed)
        val b = stripDiacritics(intended)
        if (a == b) return params.diacriticSubstitutionCost
        val distance = geometry.distance(a, b)
        val tapCost = if (distance == null) {
            params.maxSubstitutionCost
        } else {
            (params.substitutionBaseCost + params.substitutionDistanceCost * distance * distance)
                .coerceAtMost(params.maxSubstitutionCost)
        }
        return if (a in Vowels && b in Vowels) minOf(tapCost, params.vowelSubstitutionCost) else tapCost
    }

    private fun insertionCost(typed: String, index: Int, geometry: KeyGeometry): Double {
        val ch = typed[index]
        val previous = typed.getOrNull(index - 1)
        val next = typed.getOrNull(index + 1)
        if (ch == previous || ch == next) return minOf(params.doubledInsertionCost, params.doubledLetterCost)
        val nearPrevious = previous?.let { geometry.distance(ch, it) }?.let { it <= 1.3 } == true
        val nearNext = next?.let { geometry.distance(ch, it) }?.let { it <= 1.3 } == true
        return if (nearPrevious || nearNext) params.adjacentInsertionCost else params.otherInsertionCost
    }

    private fun stripDiacritics(ch: Char): Char {
        if (ch.code < 128) return ch
        return Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() ?: ch
    }

    private fun logSumExp(a: Double, b: Double): Double {
        if (a == Double.NEGATIVE_INFINITY) return b
        if (b == Double.NEGATIVE_INFINITY) return a
        val m = max(a, b)
        return m + ln(exp(a - m) + exp(b - m))
    }
}
