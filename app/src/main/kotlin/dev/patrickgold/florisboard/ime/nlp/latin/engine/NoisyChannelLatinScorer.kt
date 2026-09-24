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
    /**
     * Extra weight for keeping a word the user typed and kept at least [learnedWordMinCount] times:
     * [learnedWordBonus] * ln(1 + times). Three times gives about 5.5 nats: enough to keep a name like "thijs",
     * not enough to keep "teh" from becoming "the".
     */
    val learnedWordBonus: Double = 4.0,
    val learnedWordMinCount: Int = 3,
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
    /**
     * Touch model, used when tap positions are known: a substitution costs [touchBaseCost] plus how much less likely
     * the tap is under a Gaussian around the intended key than around the typed key (spreads in key widths).
     */
    val touchSigmaX: Double = 0.30,
    val touchSigmaY: Double = 0.35,
    val touchBaseCost: Double = 1.0,
    /** Also look for words two edits away when the typed word is not a word itself. */
    val twoEditCandidates: Boolean = true,
    /**
     * Try two substituted letters on neighboring keys also without tap positions. Off: without taps there are too
     * many neighbors, and the extra readings cost more in speed than they add.
     */
    val twoEditSubstitutionsWithoutTaps: Boolean = false,
    /**
     * A two-edit reading auto-commits only when it leaves this share of the usual doubt: 0.1 turns the Normal
     * threshold of 0.95 into 0.995. Two edits are a bigger guess, and a name typed in lowercase ("gijs", "trello")
     * is often two edits away from a common word.
     */
    val twoEditDoubtFactor: Double = 0.1,
    /**
     * Two known words run together ("thisis"): reading them as two words costs this much, on top of how likely the
     * pair is. Off: no splits.
     */
    val missedSpaceCost: Double = 3.0,
    val missedSpaceSplits: Boolean = true,
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
        private val EnglishPronounForms = LatinText.EnglishPronounForms
        private const val LanguageContextWindow = 4
        private const val MaxTokenLanguageMargin = 3.0
        private val SentenceEnds = setOf('.', '!', '?', ';', ':', '\n')
        private const val TwoEditLookupCount = 12
        private const val MinSplitInputLength = 4
        private const val MaxSplitCandidates = 3
        private val CompoundingLanguages = setOf("nl", "de")
        /** Keys worth trying instead of a typed letter: neighbors within this distance, or the keys nearest the tap. */
        private const val NeighborDistance = 1.3
        private const val NearestKeysPerTap = 3
        /** Successors looked at per language, as a multiple of the predictions asked for. */
        private const val PredictionPoolFactor = 4
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
        // Taps only count when there is exactly one per character, also after lowercasing: outside Turkish, "İ"
        // lowercases to two characters.
        val taps = request.taps?.takeIf { it.size == rawInput.length && it.size == input.length }

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
        val pairContexts = languages.associate { it.language to contextWord?.let { word -> it.model.bigrams.context(word) } }

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
        suspend fun scoreWord(word: String): Scored {
            var bestLanguageScore = Double.NEGATIVE_INFINITY
            var bestLocale = request.primaryLocale
            var bestFrequency = 0
            var logPrior = Double.NEGATIVE_INFINITY
            for (language in languages) {
                val frequency = language.model.words[word] ?: continue
                val unigram = frequency / language.model.totalFrequency
                val probability = withContext(language.model.bigrams, pairContexts[language.language], word, unigram)
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
                in confusions -> minOf(params.confusionCost, channelCost(input, word, geometry, taps))
                else -> channelCost(input, word, geometry, taps)
            }
            val finishedScore = logPrior - channel
            val isCompletion = word.length > input.length && word.startsWith(input)
            val displayScore = if (isCompletion) {
                logPrior - params.completionCostPerLetter * (word.length - input.length)
            } else {
                finishedScore
            }
            return Scored(word, bestLocale, finishedScore, displayScore, bestFrequency)
        }

        val scored = ArrayList<Scored>(candidateWords.size)
        for (word in candidateWords) scored.add(scoreWord(word))

        // "Keep what I typed" competes with every candidate. A known word is already a candidate itself.
        val literalScore = if (input in candidateWords) {
            null
        } else {
            literalScore(rawInput, input, request.textBeforeSelection) + request.autocorrect.literalBias +
                learnedWordBonus(hooks.timesTyped(input))
        }
        fun normalizer(candidates: List<Scored>): Double =
            candidates.fold(literalScore ?: Double.NEGATIVE_INFINITY) { sum, candidate -> logSumExp(sum, candidate.finishedScore) }

        // Words two edits away are only looked up when no single-edit reading is already confident enough to
        // auto-commit; they cost more time than every other candidate source together.
        val twoEditOnly = HashSet<String>()
        if (params.twoEditCandidates && !inputKnown && input.length >= 3) {
            val firstBest = scored.filter { it.word != input }.maxByOrNull { it.finishedScore }
            if (firstBest == null || exp(firstBest.finishedScore - normalizer(scored)) < request.autocorrect.threshold) {
                val alternatives = substitutionAlternatives(input, geometry, taps)
                for (language in languages) {
                    language.model.lookupTwoEditCandidates(input, alternatives, TwoEditLookupCount).forEach {
                        if (candidateWords.add(it)) {
                            twoEditOnly.add(it)
                            scored.add(scoreWord(it))
                        }
                    }
                }
            }
        }
        // A missed space: two known words run together. Only exact splits; typos inside the parts are left out. A
        // split of two uncommon words ("tree house" for "treehouse") is more likely a compound missing from the word
        // list than a missed space, so it is only suggested.
        val suggestOnlySplits = HashSet<String>()
        if (params.missedSpaceSplits && !inputKnown && input.length >= MinSplitInputLength && input.all { it.isLetter() }) {
            for (split in missedSpaceSplits(input, languages, logWeights, pairContexts)) {
                if (!candidateWords.add(split.word)) continue
                scored.add(split)
                val parts = split.word.split(' ')
                if (parts.none { part -> languages.any { it.model.isCommonWord(part) } }) suggestOnlySplits.add(split.word)
            }
        }
        if (scored.isEmpty()) return emptyList()

        val logNormalizer = normalizer(scored)
        fun posterior(score: Double) = exp(score - logNormalizer)

        val bestCorrection = scored.filter { it.word != input }.maxByOrNull { it.finishedScore }
        // Two-edit readings only weigh in on auto-commit when one of them is the best reading. Otherwise they are
        // suggestions, and they must not dilute a confident single-edit correction below the threshold.
        val decisionNormalizer = if (bestCorrection == null || bestCorrection.word in twoEditOnly || twoEditOnly.isEmpty()) {
            logNormalizer
        } else {
            normalizer(scored.filter { it.word !in twoEditOnly })
        }
        val settings = request.autocorrect
        // A listed apostrophe form ("im", "zn") is not a guess, so it may fire below the minimum length.
        val isAutoCommit = bestCorrection != null &&
            settings.enabled &&
            !inputKnown &&
            (input.length >= settings.minInputLength || bestCorrection.word in listedForms) &&
            bestCorrection.word !in suggestOnlySplits &&
            rawInput.none { it == '\'' || it == '’' || it == '-' } &&
            !hooks.isBlockedByUserPreference(input) &&
            exp(bestCorrection.finishedScore - decisionNormalizer) >= if (bestCorrection.word in twoEditOnly) {
                1.0 - (1.0 - settings.threshold) * params.twoEditDoubtFactor
            } else {
                settings.threshold
            }

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

    /**
     * Readings of [input] as two known words ("this is" for "thisis"), best first. Dutch writes compounds as one
     * word, so a Dutch split also needs the two words to occur as a pair in the example sentences; otherwise a
     * compound that is missing from the word list would be broken up.
     */
    private fun missedSpaceSplits(
        input: String,
        languages: List<LatinScoringLanguage>,
        logWeights: Map<String, Double>,
        pairContexts: Map<String, LatinBigramModel.Context?>,
    ): List<Scored> {
        val splits = ArrayList<Scored>()
        for (i in 1 until input.length) {
            val left = input.substring(0, i)
            val right = input.substring(i)
            if (!isSplitPart(left) || !isSplitPart(right)) continue
            var score = Double.NEGATIVE_INFINITY
            var bestLanguageScore = Double.NEGATIVE_INFINITY
            var bestLocale: Locale? = null
            var frequency = 0
            for (language in languages) {
                val model = language.model
                val leftFrequency = model.words[left] ?: continue
                val rightFrequency = model.words[right] ?: continue
                val leftContext = model.bigrams.context(left)
                if (language.language in CompoundingLanguages && (leftContext == null || model.bigrams.count(leftContext, right) <= 0.0)) continue
                val leftProbability = withContext(model.bigrams, pairContexts[language.language], left, leftFrequency / model.totalFrequency)
                val rightProbability = withContext(model.bigrams, leftContext, right, rightFrequency / model.totalFrequency)
                val languageScore = (logWeights[language.language] ?: continue) + ln(leftProbability) + ln(rightProbability)
                score = logSumExp(score, languageScore)
                if (languageScore > bestLanguageScore) {
                    bestLanguageScore = languageScore
                    bestLocale = language.locale
                    frequency = minOf(leftFrequency, rightFrequency)
                }
            }
            val locale = bestLocale ?: continue
            val finished = score - params.missedSpaceCost
            splits.add(Scored("$left $right", locale, finished, finished, frequency))
        }
        return splits.sortedByDescending { it.finishedScore }.take(MaxSplitCandidates)
    }

    /** One-letter parts only for "a", "i" and the Dutch "u"; anything else would split far too eagerly. */
    private fun isSplitPart(part: String): Boolean = part.length >= 2 || part == "a" || part == "i" || part == "u"

    /**
     * The most likely next words after the text before the cursor, from the bigram models of [languages]. After a
     * sentence end or at the start of the text the sentence-start statistics apply. Empty when no language has pair
     * counts for the previous word.
     */
    suspend fun predictNextWords(
        languages: List<LatinScoringLanguage>,
        primaryLocale: Locale,
        textBeforeSelection: String,
        maxCount: Int,
        hooks: LatinScoringHooks = LatinScoringHooks.None,
    ): List<LatinScoredCandidate> {
        val usable = languages.filter { !it.model.bigrams.isEmpty() }
        if (usable.isEmpty() || maxCount <= 0) return emptyList()
        val textBefore = textBeforeSelection.takeLast(SuggestionContextTailLength)
        val tokens = LatinText.extractWordTokens(textBefore, primaryLocale)
        val logWeights = languageLogWeights(languages, tokens.takeLast(LatinText.RecentContextTokenWindowSize))
        // The text ends at a word boundary, so the last word of the sentence (if any) is the previous word.
        val previous = currentSentenceTokens(textBefore, "", primaryLocale).lastOrNull() ?: LatinBigramModel.SentenceStart

        val scores = HashMap<String, Double>()
        val locales = HashMap<String, Pair<Locale, Double>>()
        for (language in usable) {
            val bigrams = language.model.bigrams
            if (bigrams.totalAfter(previous) <= 0.0) continue
            val weight = logWeights[language.language] ?: continue
            for ((word, count) in bigrams.successors(previous, maxCount * PredictionPoolFactor, predictableOnly = true)) {
                // The pair's share of all pairs, not of the pairs after [previous]: a language that rarely sees the
                // previous word ("de" in English, mostly "de Janeiro") must not win with its few successors.
                val score = weight + ln(count / bigrams.totalPairs)
                scores[word] = logSumExp(scores[word] ?: Double.NEGATIVE_INFINITY, score)
                if (score > (locales[word]?.second ?: Double.NEGATIVE_INFINITY)) locales[word] = language.locale to score
            }
        }
        if (scores.isEmpty()) return emptyList()
        if (previous != LatinBigramModel.SentenceStart) {
            for (word in scores.keys.toList()) {
                val continuation = hooks.personalContinuationScore(previous, word)
                if (continuation > 0.0) scores[word] = scores.getValue(word) + ln(1.0 + params.personalContinuationWeight * continuation)
            }
        }
        val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(maxCount)
        return ranked.map { (word, score) ->
            val locale = locales.getValue(word).first
            val isPronoun = locale.language == EnglishLanguage && word in EnglishPronounForms
            LatinScoredCandidate(
                word = word,
                text = if (isPronoun) word.replaceFirstChar { it.uppercaseChar() } else word,
                locale = locale,
                confidence = exp(score).coerceIn(0.0, 1.0),
                editDistance = 0,
                isAutoCommit = false,
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
        val context = previous?.let { language.model.bigrams.context(it) }
        return ln(withContext(language.model.bigrams, context, token, unigram))
    }

    /** P(word | previous word), interpolated with the plain word probability [unigram]. */
    private fun withContext(bigrams: LatinBigramModel, context: LatinBigramModel.Context?, word: String, unigram: Double): Double {
        if (context == null) return unigram
        val weight = context.total / (context.total + params.bigramBackoffWeight * context.distinct)
        return weight * bigrams.count(context, word) / context.total + (1.0 - weight) * unigram
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

    private fun learnedWordBonus(timesTyped: Int): Double {
        if (timesTyped < params.learnedWordMinCount) return 0.0
        return params.learnedWordBonus * ln(1.0 + timesTyped)
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
     * With [taps] (one per typed character), substitutions are priced by where the key was actually tapped.
     */
    internal fun channelCost(typed: String, intended: String, geometry: KeyGeometry, taps: List<LatinTap>? = null): Double {
        val n = typed.length
        val m = intended.length
        val d = Array(n + 1) { DoubleArray(m + 1) { Unreachable } }
        d[0][0] = 0.0
        for (i in 0..n) {
            for (j in 0..m) {
                val current = d[i][j]
                if (current >= Unreachable) continue
                if (i < n && j < m) {
                    val cost = when {
                        typed[i] == intended[j] -> 0.0
                        taps != null && i < taps.size -> touchSubstitutionCost(typed[i], intended[j], taps[i], geometry)
                        else -> substitutionCost(typed[i], intended[j], geometry)
                    }
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

    /**
     * Letters worth trying at each position of [input] for two-substitution candidates: the keys nearest the tap when
     * it is known, otherwise the neighboring keys, plus the other vowels for a vowel (spelling errors).
     */
    private fun substitutionAlternatives(input: String, geometry: KeyGeometry, taps: List<LatinTap>?): (Int) -> List<Char> {
        if (taps == null && !params.twoEditSubstitutionsWithoutTaps) return { emptyList() }
        val cache = arrayOfNulls<List<Char>>(input.length)
        return { index ->
            cache[index] ?: run {
                val ch = input[index]
                val tap = taps?.getOrNull(index)?.takeIf { !it.x.isNaN() && !it.y.isNaN() }
                val keys = if (tap != null) {
                    geometry.nearestKeys(tap.x, tap.y, NearestKeysPerTap + 1).filter { it != ch }
                } else {
                    geometry.neighbors(ch, NeighborDistance)
                }
                val vowels = if (ch in Vowels) Vowels.filter { it != ch && it !in keys } else emptyList()
                (keys + vowels).also { cache[index] = it }
            }
        }
    }

    /**
     * A substitution seen through the tap: cheap when the tap landed between the typed and the intended key, never
     * more expensive than without the tap, because a confident tap on the wrong key can still be a spelling error.
     */
    private fun touchSubstitutionCost(typed: Char, intended: Char, tap: LatinTap, geometry: KeyGeometry): Double {
        val withoutTap = substitutionCost(typed, intended, geometry)
        if (tap.x.isNaN() || tap.y.isNaN()) return withoutTap
        val typedCenter = geometry.center(typed) ?: return withoutTap
        val intendedCenter = geometry.center(intended) ?: return withoutTap
        fun squared(center: Pair<Double, Double>): Double {
            val dx = (tap.x - center.first) / params.touchSigmaX
            val dy = (tap.y - center.second) / params.touchSigmaY
            return dx * dx + dy * dy
        }
        val logRatio = (squared(intendedCenter) - squared(typedCenter)) / 2.0
        return minOf(withoutTap, params.touchBaseCost + logRatio.coerceAtLeast(0.0))
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
