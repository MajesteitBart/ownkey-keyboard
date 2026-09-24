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
    /** Leaving out an apostrophe ("dont") is a habit, not a slip. */
    val apostropheOmissionCost: Double = 1.5,
    /** A form listed in [ApostropheForms] ("dont") is how many people spell the word, so it is not an error. */
    val listedApostropheFormCost: Double = 0.0,
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
    /**
     * Word context (Witten-Bell): after a previous word seen N times with T different successors, the pair
     * probability gets weight N / (N + [bigramBackoffWeight] * T) and the plain word frequency the rest.
     */
    val bigramBackoffWeight: Double = 1.0,
    /** Writing one word of a [ConfusionSets] pair for the other ("then" for "than"): a spelling habit. */
    val confusionCost: Double = 3.0,
    /**
     * Language weights on mixed keyboards: every recent word adds how much better it fits each language (word-pair
     * probability, capped at [languageEvidenceCap] nats per word), and each language keeps at least
     * [languageSwitchFloor] of the weight so a word from the other language can still be corrected.
     */
    val languageEvidenceCap: Double = 3.0,
    val languageSwitchFloor: Double = 0.05,
    /** Off: language weights from which dictionaries know the recent words, as before T-012. */
    val languageWeightsFromContext: Boolean = true,
    /** Put the most likely finished word first; later slots favor completions of the typed prefix. */
    val bestFinishedWordFirst: Boolean = true,
)

/**
 * How eagerly autocorrect replaces words. Calibrated on the tap-noise benchmark sets; see
 * `AutocorrectStrengthCalibrationTest`. Off is the separate autocorrect switch.
 */
enum class AutocorrectStrength(val threshold: Double, val literalBias: Double) {
    /** Only the clearest typos: at least 99% precision on the tap-noise sets. */
    GENTLE(threshold = 0.985, literalBias = 2.0),
    /** Common typos: at least 97% precision. The default. */
    NORMAL(threshold = 0.95, literalBias = 0.0),
    /** More typos, at the cost of an occasional wrong correction: at least 95% precision. */
    STRONG(threshold = 0.80, literalBias = -1.0);

    fun toSettings(enabled: Boolean) = AutocorrectSettings(enabled = enabled, threshold = threshold, literalBias = literalBias)

    companion object {
        /** Maps the pre-rebuild minimum-confidence pref (hidden, default 88%) to the nearest level. */
        fun fromLegacyMinConfidencePercent(percent: Int): AutocorrectStrength = when {
            percent >= 95 -> GENTLE
            percent <= 75 -> STRONG
            else -> NORMAL
        }
    }
}

/**
 * When autocorrect may replace a word: [threshold] is the minimum posterior probability of the correction, and
 * [literalBias] is added to the log-probability of keeping the typed word.
 */
data class AutocorrectSettings(
    val enabled: Boolean = true,
    val threshold: Double = DefaultThreshold,
    val minInputLength: Int = 3,
    val literalBias: Double = 0.0,
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
        private const val EnglishLanguage = "en"
        private val EnglishPronounForms = setOf("i", "i'm", "i've", "i'll", "i'd")
        private const val LanguageContextWindow = 4
        private const val MaxTokenLanguageMargin = 3.0
        private val SentenceEnds = setOf('.', '!', '?', ';', ':', '\n')
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
        // Language checks for "I" and apostrophe forms only look at the sentence being written.
        val textBefore = request.textBeforeSelection.takeLast(SuggestionContextTailLength)
        val sentenceTokens = currentSentenceTokens(textBefore, input, request.primaryLocale)
        // The word before the typed one in the same sentence. The first word of a sentence gets no word context: the
        // sentence starts in the bigram counts are dominated by a few stock openings ("Tom", "What").
        val contextWord = sentenceTokens.lastOrNull()

        val inputKnown = languages.any { it.model.isKnown(input) } ||
            hooks.isUserDictionaryWord(input) ||
            ChatShorthand.contains(input)

        // A listed form only counts as written in lowercase (or capitalized at the start of a sentence) and when its
        // language fits the words before it: "MN" is an abbreviation, and "the mn" is not Dutch.
        val typedAsWord = rawInput == input ||
            (rawInput == input.replaceFirstChar { it.uppercaseChar() } && isSentenceStart(request.textBeforeSelection, rawInput))
        val listedForms = if (!typedAsWord) emptySet() else languages.mapNotNull { language ->
            ApostropheForms.lookup(language.language, input)
                ?.takeIf { language.model.isKnown(it) && contextFavors(language, languages, sentenceTokens) }
        }.toSet()
        val candidateWords = LinkedHashSet<String>()
        if (languages.any { it.model.isKnown(input) }) candidateWords.add(input)
        candidateWords.addAll(listedForms)
        // Real words that are often written for another word; they only reorder suggestions.
        val confusions = if (!languages.any { it.model.isKnown(input) }) emptySet() else languages.flatMap { language ->
            ConfusionSets.alternatives(language.language, input) { language.model.isKnown(it) }
        }.toSet()
        candidateWords.addAll(confusions)
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
                val unigram = frequency / language.model.totalFrequency
                val probability = if (contextWord == null) unigram else withContext(language.model.bigrams, contextWord, word, unigram)
                val languageScore = logWeights.getValue(language.language) + ln(probability)
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
            val channel = when (word) {
                input -> 0.0
                in listedForms -> params.listedApostropheFormCost
                in confusions -> minOf(params.confusionCost, channelCost(input, word, geometry))
                else -> channelCost(input, word, geometry)
            }
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
        val literalScore = if (input in candidateWords) {
            null
        } else {
            literalScore(rawInput, input, request.textBeforeSelection) + request.autocorrect.literalBias
        }
        var logNormalizer = literalScore ?: Double.NEGATIVE_INFINITY
        scored.forEach { logNormalizer = logSumExp(logNormalizer, it.finishedScore) }
        fun posterior(score: Double) = exp(score - logNormalizer)

        val bestCorrection = scored.filter { it.word != input }.maxByOrNull { it.finishedScore }
        val settings = request.autocorrect
        // A listed apostrophe form ("im", "zn") is not a guess, so it may fire below the minimum length.
        val isAutoCommit = bestCorrection != null &&
            settings.enabled &&
            !inputKnown &&
            (input.length >= settings.minInputLength || bestCorrection.word in listedForms) &&
            rawInput.none { it == '\'' || it == '’' || it == '-' } &&
            !hooks.isBlockedByUserPreference(input) &&
            posterior(bestCorrection.finishedScore) >= settings.threshold

        val bestFinished = scored.maxByOrNull { it.finishedScore }
        val lead = when {
            isAutoCommit -> bestCorrection
            params.bestFinishedWordFirst -> bestFinished
            else -> null
        }
        // A lowercase "i", "i'm", "i've", ... becomes "I" when the words before it read as English. Not next to a
        // language that borrows the English word list: there, "i" may be a word of that language ("and" in Polish).
        val english = languages.firstOrNull { it.language == EnglishLanguage }
        val pronounCandidate = scored.firstOrNull { it.word == input && it.locale.language == EnglishLanguage }
            ?.takeIf {
                !isAutoCommit && settings.enabled && input in EnglishPronounForms && rawInput.first() == 'i' &&
                    english != null && languages.all { it === english || it.hasOwnDictionary } &&
                    contextFavors(english, languages, sentenceTokens) &&
                    !hooks.isBlockedByUserPreference(input)
            }

        val ranked = scored
            .sortedWith(compareByDescending<Scored> { it.displayScore }.thenByDescending { it.frequency }.thenBy { it.word })
            .let { list -> if (lead != null) listOf(lead) + (list - lead) else list }
            .let { list -> if (pronounCandidate != null) listOf(pronounCandidate) + (list - pronounCandidate) else list }
            .take(request.maxCandidateCount)

        return ranked.map { candidate ->
            LatinScoredCandidate(
                word = candidate.word,
                text = pronounCase(rawInput, LatinText.applyInputCase(rawInput, candidate.word, candidate.locale), candidate),
                locale = candidate.locale,
                confidence = posterior(candidate.finishedScore).coerceIn(0.0, 1.0),
                editDistance = LatinText.boundedDamerauLevenshtein(input, candidate.word, 3),
                isAutoCommit = (isAutoCommit && candidate === bestCorrection) || candidate === pronounCandidate,
            )
        }
    }

    /** English writes the pronoun "I" and its contractions with a capital letter. */
    private fun pronounCase(rawInput: String, text: String, candidate: Scored): String {
        if (candidate.locale.language != EnglishLanguage || candidate.word !in EnglishPronounForms) return text
        // The typed word itself keeps its apostrophe ("i’m" becomes "I’m").
        if (LatinText.normalizeInputWord(rawInput, candidate.locale) == candidate.word) return "I" + rawInput.drop(1)
        return text.replaceFirstChar { it.uppercaseChar() }
    }

    /** ln P(token | previous) in [language], or the unknown-word probability when the language lacks the word. */
    private fun contextLogProbability(language: LatinScoringLanguage, previous: String?, token: String): Double {
        val frequency = language.model.words[token] ?: return params.unknownWordLogProb
        val unigram = frequency / language.model.totalFrequency
        return ln(if (previous == null) unigram else withContext(language.model.bigrams, previous, token, unigram))
    }

    /** P(word | previous word), interpolated with the plain word probability [unigram]. */
    private fun withContext(bigrams: LatinBigramModel, previous: String, word: String, unigram: Double): Double {
        val total = bigrams.totalAfter(previous)
        if (total <= 0.0) return unigram
        val weight = total / (total + params.bigramBackoffWeight * bigrams.distinctAfter(previous))
        return weight * bigrams.count(previous, word) / total + (1.0 - weight) * unigram
    }

    /**
     * The words of the sentence being written, before [input]. A line break, ";" and ":" also end a sentence, as in
     * the bigram counts.
     */
    private fun currentSentenceTokens(textBefore: String, input: String, locale: Locale): List<String> {
        val start = textBefore.indexOfLast { it in SentenceEnds } + 1
        val tokens = LatinText.extractWordTokens(textBefore.substring(start), locale)
        return if (tokens.lastOrNull() == input) tokens.dropLast(1) else tokens
    }

    /**
     * Whether the last few words fit [target] better than any other active language. The Dutch list contains common
     * English words ("and", "then") from subtitles, so this compares word frequencies instead of only checking which
     * dictionaries know a word. The primary language keeps the lead unless the words lean clearly toward another
     * language; a secondary language needs at least one clearly matching word. Chat abbreviations such as "lol" (also
     * a Dutch word) count for no language.
     */
    private fun contextFavors(
        target: LatinScoringLanguage,
        languages: List<LatinScoringLanguage>,
        sentenceTokens: List<String>,
    ): Boolean {
        val others = languages.filter { it !== target }
        if (others.isEmpty()) return true
        val recent = sentenceTokens.filterNot { ChatShorthand.contains(it) }.takeLast(LanguageContextWindow)
        if (recent.isEmpty()) return target.isPrimary
        fun logProbability(language: LatinScoringLanguage, token: String): Double {
            val frequency = language.model.words[token] ?: return params.unknownWordLogProb
            return ln(frequency.toDouble()) - ln(language.model.totalFrequency)
        }
        val lead = recent.sumOf { token ->
            val margin = logProbability(target, token) - others.maxOf { logProbability(it, token) }
            margin.coerceIn(-MaxTokenLanguageMargin, MaxTokenLanguageMargin)
        }
        return if (target.isPrimary) lead > -MaxTokenLanguageMargin else lead >= MaxTokenLanguageMargin
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
        if (languages.size == 1) return mapOf(languages.first().language to 0.0)
        val recent = contextTokens.filterNot { ChatShorthand.contains(it) }
        if (params.languageWeightsFromContext && recent.isNotEmpty()) {
            val priors = mixedLanguageScoringPolicy.computeLanguageWeights(
                languages.map { LanguageConfidenceSignal(it.language, it.isPrimary, 0.0, hasExactInputMatch = false) }
            )
            val scores = languages.associate { it.language to ln(priors.getValue(it.language).coerceAtLeast(1e-6)) }.toMutableMap()
            recent.forEachIndexed { index, token ->
                val previous = recent.getOrNull(index - 1)
                val fits = languages.map { language -> language.language to contextLogProbability(language, previous, token) }
                val best = fits.maxOf { it.second }
                for ((language, fit) in fits) {
                    scores[language] = scores.getValue(language) + maxOf(fit - best, -params.languageEvidenceCap)
                }
            }
            val max = scores.values.max()
            val sum = scores.values.sumOf { exp(it - max) }
            val floor = params.languageSwitchFloor
            return scores.mapValues { (_, score) ->
                ln(floor + (1.0 - floor * languages.size) * exp(score - max) / sum)
            }
        }
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
                    val cost = current + when {
                        intended[j] == '\'' -> params.apostropheOmissionCost
                        isDoubled -> params.doubledLetterCost
                        else -> params.omissionCost
                    }
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
