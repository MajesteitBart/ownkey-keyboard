/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        private const val LegacyModelKey = "__legacy__"
        private const val LegacyDictionaryAssetPath = "ime/dict/data.json"
        private const val MaxEditDistance = 1
        private const val MaxLookupCandidateCount = 16
        private const val MinLengthForTypoCorrections = 4
        private const val SuggestionCacheMaxSize = 128
        private const val SuggestionContextTailLength = 96
        private const val ShortcutPrefixDepth = 3
        private const val ShortcutPrefixPoolSize = 48
        private const val ShortcutFallbackPoolSize = 64

        private val FrequencyDictionaryAssets = mapOf(
            "en" to "ime/dict/frequencywords/en_50k.txt",
            "nl" to "ime/dict/frequencywords/nl_50k.txt",
        )
    }

    private data class RankedCandidate(
        val word: String,
        val distance: Int,
        val frequency: Int,
        val isPrefixMatch: Boolean,
    )

    private data class ScoredCandidate(
        val ranked: RankedCandidate,
        val confidence: Double,
    )

    private data class SuggestCacheKey(
        val language: String,
        val composingText: String,
        val currentWordText: String,
        val textBeforeTail: String,
        val maxCandidateCount: Int,
        val allowPossiblyOffensive: Boolean,
        val isPrivateSession: Boolean,
    )

    private data class LanguageModel(
        val words: Map<String, Int>,
        val deleteIndex: Map<String, Set<String>>,
        val maxFrequency: Int,
        val predictionShortcuts: LatinPredictionShortcuts,
    )

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore
    private val languageModels = guardedByLock { mutableMapOf<String, LanguageModel>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val emptyModel = LanguageModel(
        words = emptyMap(),
        deleteIndex = emptyMap(),
        maxFrequency = 1,
        predictionShortcuts = LatinPredictionShortcuts(emptyMap()),
    )
    private val suggestionCache = guardedByLock {
        object : LinkedHashMap<SuggestCacheKey, List<SuggestionCandidate>>(SuggestionCacheMaxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SuggestCacheKey, List<SuggestionCandidate>>?): Boolean {
                return size > SuggestionCacheMaxSize
            }
        }
    }

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        ensureLanguageModelLoaded(subtype.primaryLocale.language)
        subtype.secondaryLocales.forEach { secondaryLocale ->
            ensureLanguageModelLoaded(secondaryLocale.language)
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val rawWord = word.trim()
        if (shouldSkipSpellcheck(rawWord)) {
            return SpellingResult.validWord()
        }

        val locale = subtype.primaryLocale.base
        val normalizedWord = normalizeInputWord(rawWord, locale)
        if (normalizedWord.isBlank() || shouldSkipSpellcheck(normalizedWord)) {
            return SpellingResult.validWord()
        }

        val model = getLanguageModelForSubtype(subtype)
        if (isExactKnownWord(model, normalizedWord) || isUserDictionaryWord(subtype, normalizedWord)) {
            return SpellingResult.validWord()
        }

        val suggestedWords = LinkedHashSet<String>()
        userDictionarySuggestions(subtype, normalizedWord, maxSuggestionCount).forEach { suggestion ->
            suggestedWords.add(applyInputCase(rawWord, suggestion, locale))
        }

        val ranked = lookupCorrections(model, normalizedWord, MaxLookupCandidateCount)
        for (candidate in ranked) {
            suggestedWords.add(applyInputCase(rawWord, candidate.word, locale))
            if (suggestedWords.size >= maxSuggestionCount) break
        }

        val suggestions = suggestedWords.take(maxSuggestionCount)
        if (suggestions.isEmpty()) {
            // Be conservative for unknown words so we don't underline names / domain words all the time.
            return SpellingResult.validWord()
        }

        val highConfidence = ranked.firstOrNull()?.distance?.let { it <= 1 } == true
        return SpellingResult.typo(suggestions.toTypedArray(), isHighConfidenceResult = highConfidence)
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val cacheKey = buildSuggestCacheKey(
            subtype = subtype,
            content = content,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
            isPrivateSession = isPrivateSession,
        )
        suggestionCache.withLock { cache ->
            cache[cacheKey]?.let { return it }
        }

        val locale = subtype.primaryLocale.base
        val rawInput = content.composingText.ifBlank { content.currentWordText }.trim()
        val suggestions = if (rawInput.isBlank()) {
            suggestNextWordCandidates(subtype, content, maxCandidateCount, locale)
        } else {
            val normalizedInput = normalizeInputWord(rawInput, locale)
            if (normalizedInput.isBlank()) {
                emptyList()
            } else {
                val model = getLanguageModelForSubtype(subtype)
                if (model.words.isEmpty()) {
                    emptyList()
                } else {
                    val hasExactMatch = isExactKnownWord(model, normalizedInput) || isUserDictionaryWord(subtype, normalizedInput)
                    val rankedCandidates = LinkedHashMap<String, RankedCandidate>()

                    if (hasExactMatch) {
                        val frequency = model.words[normalizedInput] ?: 1
                        rankedCandidates[normalizedInput] = RankedCandidate(
                            word = normalizedInput,
                            distance = 0,
                            frequency = frequency,
                            isPrefixMatch = true,
                        )
                    }

                    val prefixes = lookupPrefixCandidates(model, normalizedInput, MaxLookupCandidateCount)
                    for (candidate in prefixes) {
                        rankedCandidates.putIfAbsent(candidate.word, candidate)
                        if (rankedCandidates.size >= MaxLookupCandidateCount) break
                    }

                    if (shouldUseTypoCorrections(normalizedInput)) {
                        val corrections = lookupCorrections(model, normalizedInput, MaxLookupCandidateCount)
                        for (candidate in corrections) {
                            rankedCandidates.putIfAbsent(candidate.word, candidate)
                            if (rankedCandidates.size >= MaxLookupCandidateCount) break
                        }
                    }

                    if (rankedCandidates.isEmpty()) {
                        emptyList()
                    } else {
                        val sortedCandidates = rankedCandidates.values
                            .sortedWith(
                                compareByDescending<RankedCandidate> { rankSuggestionCandidate(model, normalizedInput, it) }
                                    .thenByDescending { it.frequency }
                                    .thenBy { it.word }
                            )
                            .take(maxCandidateCount)
                            .map { candidate ->
                                ScoredCandidate(
                                    ranked = candidate,
                                    confidence = calculateConfidence(model, normalizedInput, candidate),
                                )
                            }
                        val autoCorrectPolicy = currentHighCertaintyAutocorrectPolicy()
                        val topCandidate = sortedCandidates.firstOrNull()
                        val runnerUpConfidence = sortedCandidates.getOrNull(1)?.confidence

                        sortedCandidates.map { scoredCandidate ->
                                val candidate = scoredCandidate.ranked
                                val suggestionText = applyInputCase(rawInput, candidate.word, locale)
                                val isAutoCommitCandidate = topCandidate == scoredCandidate && autoCorrectPolicy.shouldAutoCommit(
                                    normalizedInput = normalizedInput,
                                    candidateWord = candidate.word,
                                    candidateEditDistance = candidate.distance,
                                    candidateConfidence = scoredCandidate.confidence,
                                    runnerUpConfidence = runnerUpConfidence,
                                    hasExactInputMatch = hasExactMatch,
                                )
                                WordSuggestionCandidate(
                                    text = suggestionText,
                                    confidence = scoredCandidate.confidence,
                                    isEligibleForAutoCommit = isAutoCommitCandidate,
                                    sourceProvider = this@LatinLanguageProvider,
                                )
                            }
                    }
                }
            }
        }

        suggestionCache.withLock { cache ->
            cache[cacheKey] = suggestions
        }
        return suggestions
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return getLanguageModelForSubtype(subtype).words.keys.toList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val model = getLanguageModelForSubtype(subtype)
        val normalizedWord = normalizeInputWord(word, subtype.primaryLocale.base)
        val frequency = model.words[normalizedWord] ?: return 0.0
        return (frequency.toDouble() / model.maxFrequency.toDouble()).coerceIn(0.0, 1.0)
    }

    override suspend fun destroy() {
        languageModels.withLock {
            it.clear()
        }
        suggestionCache.withLock {
            it.clear()
        }
    }

    private suspend fun getLanguageModelForSubtype(subtype: Subtype): LanguageModel {
        val language = normalizeLanguageCode(subtype.primaryLocale.language)
        ensureLanguageModelLoaded(language)
        return languageModels.withLock { models ->
            models[language] ?: models[LegacyModelKey] ?: emptyModel
        }
    }

    private suspend fun ensureLanguageModelLoaded(languageCode: String) {
        val language = normalizeLanguageCode(languageCode)
        val isLoaded = languageModels.withLock { models ->
            models.containsKey(language)
        }
        if (isLoaded) return

        val model = loadLanguageModel(language)
        languageModels.withLock { models ->
            models.putIfAbsent(language, model)
        }
    }

    private suspend fun loadLanguageModel(language: String): LanguageModel {
        val dictionaryAsset = FrequencyDictionaryAssets[language]
        if (dictionaryAsset != null) {
            try {
                return buildLanguageModelFromFrequencyAsset(dictionaryAsset)
            } catch (e: Exception) {
                flogError { "Failed loading frequency dictionary for '$language' from '$dictionaryAsset': $e" }
            }
        }

        val cachedLegacyModel = languageModels.withLock { models ->
            models[LegacyModelKey]
        }
        if (cachedLegacyModel != null) {
            return cachedLegacyModel
        }

        val legacyModel = try {
            buildLanguageModelFromLegacyAsset()
        } catch (e: Exception) {
            flogError { "Failed loading legacy dictionary model: $e" }
            emptyModel
        }

        languageModels.withLock { models ->
            models.putIfAbsent(LegacyModelKey, legacyModel)
        }
        return legacyModel
    }

    private fun buildLanguageModelFromFrequencyAsset(assetPath: String): LanguageModel {
        val words = mutableMapOf<String, Int>()
        appContext.assets.open(assetPath).bufferedReader().use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine

                val separatorIndex = trimmed.lastIndexOfAny(charArrayOf(' ', '\t'))
                if (separatorIndex <= 0 || separatorIndex >= trimmed.lastIndex) return@forEachLine

                val rawWord = trimmed.substring(0, separatorIndex)
                val normalizedWord = normalizeDictionaryWord(rawWord)
                if (normalizedWord.isBlank()) return@forEachLine

                val frequency = trimmed.substring(separatorIndex + 1).toIntOrNull() ?: return@forEachLine
                val safeFrequency = frequency.coerceAtLeast(1)
                val currentFrequency = words[normalizedWord] ?: 0
                if (safeFrequency > currentFrequency) {
                    words[normalizedWord] = safeFrequency
                }
            }
        }
        return buildLanguageModel(words)
    }

    private fun buildLanguageModelFromLegacyAsset(): LanguageModel {
        val rawData = appContext.assets.readText(LegacyDictionaryAssetPath)
        val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
        val words = mutableMapOf<String, Int>()
        jsonData.forEach { (word, frequency) ->
            val normalizedWord = normalizeDictionaryWord(word)
            if (normalizedWord.isNotBlank()) {
                words[normalizedWord] = frequency.coerceAtLeast(1)
            }
        }
        return buildLanguageModel(words)
    }

    private fun buildLanguageModel(words: Map<String, Int>): LanguageModel {
        if (words.isEmpty()) return emptyModel

        val deleteIndex = mutableMapOf<String, MutableSet<String>>()
        words.keys.forEach { word ->
            indexWord(word, deleteIndex)
        }

        val predictionShortcuts = LatinPredictionShortcuts(
            words = words,
            maxPrefixDepth = ShortcutPrefixDepth,
            prefixPoolSize = ShortcutPrefixPoolSize,
            fallbackPoolSize = ShortcutFallbackPoolSize,
        )

        return LanguageModel(
            words = words,
            deleteIndex = deleteIndex.mapValues { (_, set) -> set.toSet() },
            maxFrequency = words.values.maxOrNull()?.coerceAtLeast(1) ?: 1,
            predictionShortcuts = predictionShortcuts,
        )
    }

    private fun indexWord(word: String, deleteIndex: MutableMap<String, MutableSet<String>>) {
        deleteIndex.getOrPut(word) { mutableSetOf() }.add(word)
        generateDeletes(word, MaxEditDistance).forEach { deletedWord ->
            deleteIndex.getOrPut(deletedWord) { mutableSetOf() }.add(word)
        }
    }

    private fun generateDeletes(word: String, maxDistance: Int): Set<String> {
        if (word.isEmpty() || maxDistance <= 0) return emptySet()

        val deletes = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(word to 0)

        while (queue.isNotEmpty()) {
            val (candidate, distance) = queue.removeFirst()
            if (distance >= maxDistance || candidate.length <= 1) continue

            for (i in candidate.indices) {
                val deletedWord = candidate.removeRange(i, i + 1)
                if (deletes.add(deletedWord)) {
                    queue.add(deletedWord to distance + 1)
                }
            }
        }

        return deletes
    }

    private fun lookupCorrections(
        model: LanguageModel,
        input: String,
        maxCount: Int,
    ): List<RankedCandidate> {
        if (input.isBlank()) return emptyList()

        val candidateWords = LinkedHashSet<String>()
        model.deleteIndex[input]?.let { candidateWords.addAll(it) }
        generateDeletes(input, MaxEditDistance).forEach { deletedWord ->
            model.deleteIndex[deletedWord]?.let { candidateWords.addAll(it) }
        }

        if (candidateWords.isEmpty()) return emptyList()

        val rankedCandidates = mutableListOf<RankedCandidate>()
        candidateWords.forEach { candidateWord ->
            if (candidateWord == input) return@forEach

            val frequency = model.words[candidateWord] ?: return@forEach
            val distance = boundedDamerauLevenshtein(input, candidateWord, MaxEditDistance)
            if (distance <= MaxEditDistance) {
                rankedCandidates.add(
                    RankedCandidate(
                        word = candidateWord,
                        distance = distance,
                        frequency = frequency,
                        isPrefixMatch = candidateWord.startsWith(input),
                    )
                )
            }
        }

        return rankedCandidates
            .sortedWith(
                compareBy<RankedCandidate> { it.distance }
                    .thenByDescending { it.frequency }
                    .thenBy { it.word }
            )
            .take(maxCount)
    }

    private fun lookupPrefixCandidates(
        model: LanguageModel,
        input: String,
        maxCount: Int,
    ): List<RankedCandidate> {
        return model.predictionShortcuts.lookupPrefixCandidates(input, maxCount)
            .map { candidate ->
                RankedCandidate(
                    word = candidate.word,
                    distance = 0,
                    frequency = candidate.frequency,
                    isPrefixMatch = true,
                )
            }
    }

    private suspend fun suggestNextWordCandidates(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        locale: Locale,
    ): List<SuggestionCandidate> {
        val textBeforeSelection = content.textBeforeSelection
        if (textBeforeSelection.isBlank() || !isNextWordBoundary(textBeforeSelection)) {
            return emptyList()
        }

        val tokens = extractWordTokens(textBeforeSelection, locale)
        if (tokens.isEmpty()) return emptyList()

        val previousWord = tokens.last()
        val previousPreviousWord = tokens.getOrNull(tokens.lastIndex - 1)
        val model = getLanguageModelForSubtype(subtype)

        val scores = mutableMapOf<String, Int>()
        val frequencies = mutableMapOf<String, Int>()

        for (index in 0 until tokens.lastIndex) {
            val current = tokens[index]
            val next = tokens[index + 1]

            if (next == previousWord) continue
            if (current != previousWord) continue

            var score = 3
            if (previousPreviousWord != null && index > 0 && tokens[index - 1] == previousPreviousWord) {
                score += 6
            }

            scores[next] = scores.getOrDefault(next, 0) + score
            frequencies[next] = maxOf(frequencies.getOrDefault(next, 0), model.words[next] ?: 1)
        }

        if (scores.isEmpty()) {
            return suggestFallbackNextWordCandidates(model, previousWord, maxCandidateCount)
        }

        val ranked = scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { frequencies.getOrDefault(it.key, 0) }
                    .thenBy { it.key }
            )
            .take(maxCandidateCount)

        if (ranked.isEmpty()) return emptyList()

        val maxScore = ranked.first().value.coerceAtLeast(1)
        return ranked.map { entry ->
            val candidateWord = entry.key
            val candidateScore = entry.value
            val candidateFrequency = frequencies.getOrDefault(candidateWord, 1)
            val confidence = (
                0.25 +
                    0.55 * (candidateScore.toDouble() / maxScore.toDouble()) +
                    0.20 * (candidateFrequency.toDouble() / model.maxFrequency.toDouble())
                ).coerceIn(0.05, 0.98)

            WordSuggestionCandidate(
                text = candidateWord,
                confidence = confidence,
                isEligibleForAutoCommit = false,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    private fun isNextWordBoundary(textBeforeSelection: String): Boolean {
        val lastChar = textBeforeSelection.lastOrNull() ?: return false
        return lastChar.isWhitespace() || lastChar in setOf('.', ',', ';', ':', '!', '?')
    }

    private fun extractWordTokens(text: String, locale: Locale): List<String> {
        val tokens = mutableListOf<String>()
        val builder = StringBuilder()

        fun flushToken() {
            if (builder.isNotEmpty()) {
                val token = normalizeInputWord(builder.toString(), locale)
                if (token.isNotBlank()) {
                    tokens.add(token)
                }
                builder.clear()
            }
        }

        for (ch in text) {
            when {
                ch.isLetter() -> builder.append(ch)
                (ch == '\'' || ch == '’' || ch == '-') && builder.isNotEmpty() -> builder.append(ch)
                else -> flushToken()
            }
        }
        flushToken()

        return tokens
    }

    private fun boundedDamerauLevenshtein(source: String, target: String, limit: Int): Int {
        if (source == target) return 0
        if (abs(source.length - target.length) > limit) return limit + 1

        var previousPreviousRow = IntArray(target.length + 1)
        var previousRow = IntArray(target.length + 1) { it }
        var currentRow = IntArray(target.length + 1)

        for (sourceIndex in 1..source.length) {
            currentRow[0] = sourceIndex
            var rowMin = currentRow[0]
            val sourceChar = source[sourceIndex - 1]

            for (targetIndex in 1..target.length) {
                val targetChar = target[targetIndex - 1]
                val substitutionCost = if (sourceChar == targetChar) 0 else 1

                var value = minOf(
                    previousRow[targetIndex] + 1,
                    currentRow[targetIndex - 1] + 1,
                    previousRow[targetIndex - 1] + substitutionCost,
                )

                if (sourceIndex > 1 && targetIndex > 1 &&
                    source[sourceIndex - 1] == target[targetIndex - 2] &&
                    source[sourceIndex - 2] == target[targetIndex - 1]
                ) {
                    value = minOf(value, previousPreviousRow[targetIndex - 2] + 1)
                }

                currentRow[targetIndex] = value
                if (value < rowMin) rowMin = value
            }

            if (rowMin > limit) return limit + 1

            val temp = previousPreviousRow
            previousPreviousRow = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[target.length]
    }

    private fun rankSuggestionCandidate(model: LanguageModel, input: String, candidate: RankedCandidate): Double {
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

    private fun suggestFallbackNextWordCandidates(
        model: LanguageModel,
        previousWord: String,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        val fallbackWords = model.predictionShortcuts.fallbackCandidates(previousWord, maxCandidateCount)

        if (fallbackWords.isEmpty()) return emptyList()

        return fallbackWords.map { entry ->
            WordSuggestionCandidate(
                text = entry.word,
                confidence = (0.20 + 0.35 * (entry.frequency.toDouble() / model.maxFrequency.toDouble())).coerceIn(0.10, 0.55),
                isEligibleForAutoCommit = false,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    private fun calculateConfidence(model: LanguageModel, input: String, candidate: RankedCandidate): Double {
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

    private fun normalizeLanguageCode(languageCode: String): String {
        return languageCode.trim().lowercase(Locale.ROOT)
    }

    private fun currentHighCertaintyAutocorrectPolicy(): HighCertaintyAutocorrectPolicy {
        val minConfidencePercent = prefs.correction.highCertaintyAutocorrectMinConfidencePercent.get().coerceIn(50, 99)
        val minGapPercent = prefs.correction.highCertaintyAutocorrectMinConfidenceGapPercent.get().coerceIn(0, 50)
        val minInputLength = prefs.correction.highCertaintyAutocorrectMinInputLength.get().coerceIn(3, 12)

        return HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(
                enabled = prefs.correction.highCertaintyAutocorrectEnabled.get(),
                minConfidence = minConfidencePercent / 100.0,
                minConfidenceGap = minGapPercent / 100.0,
                minInputLength = minInputLength,
                maxAutoCorrectEditDistance = MaxEditDistance,
            )
        )
    }

    private fun normalizeDictionaryWord(word: String): String {
        return word.trim()
            .replace('’', '\'')
            .lowercase(Locale.ROOT)
    }

    private fun normalizeInputWord(word: String, locale: Locale): String {
        return word.trim()
            .replace('’', '\'')
            .lowercase(locale)
    }

    private fun isExactKnownWord(model: LanguageModel, normalizedWord: String): Boolean {
        return model.words.containsKey(normalizedWord)
    }

    private fun shouldSkipSpellcheck(word: String): Boolean {
        if (word.length <= 2) return true
        if (word.length > 48) return true
        if (word.any { it.isDigit() }) return true
        val letterCount = word.count { it.isLetter() }
        return letterCount == 0
    }

    private fun shouldUseTypoCorrections(normalizedInput: String): Boolean {
        return normalizedInput.length >= MinLengthForTypoCorrections
    }

    private fun buildSuggestCacheKey(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SuggestCacheKey {
        val hasCurrentWordInput = content.composingText.isNotBlank() || content.currentWordText.isNotBlank()
        val textBeforeTail = if (hasCurrentWordInput) {
            ""
        } else {
            content.textBeforeSelection.takeLast(SuggestionContextTailLength)
        }
        return SuggestCacheKey(
            language = normalizeLanguageCode(subtype.primaryLocale.language),
            composingText = content.composingText,
            currentWordText = content.currentWordText,
            textBeforeTail = textBeforeTail,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
            isPrivateSession = isPrivateSession,
        )
    }

    private fun isUserDictionaryWord(subtype: Subtype, normalizedWord: String): Boolean {
        return try {
            DictionaryManager.default().spell(normalizedWord, subtype.primaryLocale)
        } catch (_: Throwable) {
            false
        }
    }

    private fun userDictionarySuggestions(
        subtype: Subtype,
        normalizedWord: String,
        maxCount: Int,
    ): List<String> {
        return try {
            DictionaryManager.default().queryUserDictionary(normalizedWord, subtype.primaryLocale)
                .asSequence()
                .map { normalizeDictionaryWord(it.text.toString()) }
                .filter { it.isNotBlank() && it != normalizedWord }
                .distinct()
                .take(maxCount)
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun applyInputCase(rawInput: String, suggestion: String, locale: Locale): String {
        val lettersOnly = rawInput.filter { it.isLetter() }
        return when {
            lettersOnly.isNotEmpty() && lettersOnly.all { it.isUpperCase() } -> {
                suggestion.uppercase(locale)
            }
            rawInput.firstOrNull()?.isUpperCase() == true -> {
                suggestion.replaceFirstChar { firstChar ->
                    firstChar.titlecase(locale)
                }
            }
            else -> suggestion
        }
    }
}
