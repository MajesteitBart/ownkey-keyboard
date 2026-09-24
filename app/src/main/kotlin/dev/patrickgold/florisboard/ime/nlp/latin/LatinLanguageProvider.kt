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
import android.os.SystemClock
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MAX
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectTriggerPolicy
import dev.patrickgold.florisboard.ime.nlp.latin.engine.ChatShorthand
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinBigramModel
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinCurrentWordScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinDictionaryCleanup
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringHooks
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringRequest
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinText
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinWordModel
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LegacyLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import dev.patrickgold.florisboard.speechDictionary
import dev.patrickgold.florisboard.ime.nlp.latin.engine.RankedCandidate
import dev.patrickgold.florisboard.ime.nlp.personal.PersonalDataStore
import dev.patrickgold.florisboard.ime.nlp.personal.PersonalNgramStore
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import java.util.Locale

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"

        private const val LegacyModelKey = "__legacy__"
        private const val LegacyDictionaryAssetPath = "ime/dict/data.json"
        private const val MaxLookupCandidateCount = 16
        private const val SuggestionCacheMaxSize = 128
        private const val SuggestionContextTailLength = 96
        // Next-word scores: field matches add 3 to 9, personal n-grams 6 to 14, word pairs 1.5 to 5.5.
        private const val PairPredictionBaseScore = 1.5
        private const val PairPredictionScoreRange = 4.0
        private const val AutoCommitCandidateCount = 8
        private const val UserDictionarySnapshotMaxAgeMs = 30_000L

        // Built by tools/dictionary-build/build.py. The raw FrequencyWords lists stay as a fallback for one release.
        private val FrequencyDictionaryAssets = mapOf(
            "en" to listOf("ime/dict/latin/en.txt", "ime/dict/frequencywords/en_50k.txt"),
            "nl" to listOf("ime/dict/latin/nl.txt", "ime/dict/frequencywords/nl_50k.txt"),
        )
    }

    private data class SuggestCacheKey(
        val language: String,
        val composingText: String,
        val currentWordText: String,
        val textBeforeTail: String,
        val maxCandidateCount: Int,
        val allowPossiblyOffensive: Boolean,
        val isPrivateSession: Boolean,
        val autocorrectPolicySignature: String,
        val isEmailField: Boolean,
        /** Where the word was tapped: the same word tapped differently can get a different correction. */
        val taps: List<LatinTap>?,
    )

    private data class AutocorrectPolicySnapshot(
        val profile: AutocorrectAppProfile,
        val config: HighCertaintyAutocorrectConfig,
        val settings: AutocorrectSettings,
        val isLegacyEngine: Boolean,
    ) {
        val policy: HighCertaintyAutocorrectPolicy = HighCertaintyAutocorrectPolicy(config)
        val signature: String = listOf(
            profile.name,
            config.enabled.toString(),
            config.minConfidence,
            config.minConfidenceGap,
            config.minInputLength,
            config.maxAutoCorrectEditDistance,
            settings,
            isLegacyEngine,
        ).joinToString(separator = "|")
    }

    private data class SubtypeLanguageContext(
        val language: String,
        val locale: FlorisLocale,
        val model: LatinWordModel,
        val isPrimary: Boolean,
        val hasOwnDictionary: Boolean,
    ) {
        fun toScoringLanguage() = LatinScoringLanguage(
            language = language,
            locale = locale.base,
            model = model,
            isPrimary = isPrimary,
            hasOwnDictionary = hasOwnDictionary,
        )
    }

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val prefs by FlorisPreferenceStore
    private val languageModels = guardedByLock { mutableMapOf<String, LatinWordModel>() }
    // Serializes model loading, so a suggestion request during preload waits for the load in progress instead of
    // loading a second copy of the same dictionary.
    private val modelLoadMutex = Mutex()
    // Read-only copy of [languageModels] for the main thread, replaced whenever a model is registered.
    @Volatile
    private var loadedModelsSnapshot: Map<String, LatinWordModel> = emptyMap()
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val emptyModel = LatinWordModel.Empty
    private val legacyScorer: LatinCurrentWordScorer = LegacyLatinScorer()
    private val noisyChannelScorer = NoisyChannelLatinScorer()

    @Volatile
    private var speechWordsCache: Pair<Any, Set<String>>? = null

    // In-memory copy of the user dictionaries, so autocorrect decisions on the main thread never query a database.
    @Volatile
    private var userDictionarySnapshot: Set<String> = emptySet()
    @Volatile
    private var userDictionarySnapshotUptimeMs = 0L
    private val rapidVocabularyLearner = RapidPersonalVocabularyLearner()
    private val mixedLanguageScoringPolicy = MixedLanguageScoringPolicy()
    private val personalNgramStore by lazy { PersonalNgramStore(appContext) }
    private val personalDataStore by lazy { PersonalDataStore(appContext) }
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
        subtype.locales().forEach { locale ->
            ensureLanguageModelLoaded(locale.language)
        }
        refreshUserDictionarySnapshotIfStale()
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

        val languageContexts = getLanguageContextsForSubtype(subtype)
        if (languageContexts.any { context -> context.model.isKnown(normalizedWord) } ||
            isUserDictionaryWord(subtype, normalizedWord) ||
            ChatShorthand.contains(normalizedWord) ||
            normalizedWord in speechDictionaryWords()
        ) {
            return SpellingResult.validWord()
        }
        if (!prefs.devtools.autocorrectLegacyEngine.get()) {
            val policySnapshot = currentHighCertaintyAutocorrectPolicySnapshot(currentAutocorrectAppContext())
            val scored = noisyChannelScorer.score(
                request = LatinScoringRequest(
                    rawInput = rawWord,
                    primaryLocale = locale,
                    languages = languageContexts.map { it.toScoringLanguage() },
                    textBeforeSelection = (precedingWords + rawWord).joinToString(" "),
                    maxCandidateCount = maxSuggestionCount + 1,
                    policy = policySnapshot.policy,
                    autocorrect = policySnapshot.settings.copy(enabled = true),
                    geometry = KeyboardGeometrySource.current(),
                ),
                hooks = scoringHooks(subtype, normalizeLanguageCode(subtype.primaryLocale.language)),
            )
            val suggestions = scored
                .filter { it.word != normalizedWord }
                .map { it.text }
                .distinct()
                .take(maxSuggestionCount)
            if (suggestions.isEmpty()) return SpellingResult.validWord()
            return SpellingResult.typo(suggestions.toTypedArray(), isHighConfidenceResult = scored.any { it.isAutoCommit })
        }

        val suggestedWords = LinkedHashSet<String>()
        userDictionarySuggestions(subtype, normalizedWord, maxSuggestionCount).forEach { suggestion ->
            suggestedWords.add(applyInputCase(rawWord, suggestion, locale))
        }

        val rankedCorrections = mutableListOf<Pair<RankedCandidate, Locale>>()
        languageContexts.forEach { context ->
            context.model.lookupCorrections(normalizedWord, MaxLookupCandidateCount).forEach { candidate ->
                rankedCorrections.add(candidate to context.locale.base)
            }
        }

        val rankedCandidates = rankedCorrections.sortedWith(
            compareBy<Pair<RankedCandidate, Locale>> { it.first.distance }
                .thenByDescending { it.first.frequency }
                .thenBy { it.first.word }
        )
        for ((candidate, candidateLocale) in rankedCandidates) {
            suggestedWords.add(applyInputCase(rawWord, candidate.word, candidateLocale))
            if (suggestedWords.size >= maxSuggestionCount) break
        }

        val suggestions = suggestedWords.take(maxSuggestionCount)
        if (suggestions.isEmpty()) {
            // Be conservative for unknown words so we don't underline names / domain words all the time.
            return SpellingResult.validWord()
        }

        val highConfidence = rankedCandidates.firstOrNull()?.first?.distance?.let { it <= 1 } == true
        return SpellingResult.typo(suggestions.toTypedArray(), isHighConfidenceResult = highConfidence)
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val autocorrectAppContext = currentAutocorrectAppContext()
        val autocorrectPolicySnapshot = currentHighCertaintyAutocorrectPolicySnapshot(autocorrectAppContext)
        val isEmailField = isEmailInputField()
        val cacheKey = buildSuggestCacheKey(
            subtype = subtype,
            content = content,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
            isPrivateSession = isPrivateSession,
            autocorrectPolicySignature = autocorrectPolicySnapshot.signature,
            isEmailField = isEmailField,
        )
        suggestionCache.withLock { cache ->
            cache[cacheKey]?.let { return it }
        }
        refreshUserDictionarySnapshotIfStale()

        val primaryLocale = subtype.primaryLocale.base
        val rawInput = content.composingText.ifBlank { content.currentWordText }.trim()
        val languageContexts = getLanguageContextsForSubtype(subtype)
        val emailCandidates = personalEmailCandidates(rawInput, content, isEmailField)
        val suggestions = if (rawInput.isBlank()) {
            suggestNextWordCandidates(
                content = content,
                maxCandidateCount = maxCandidateCount,
                locale = primaryLocale,
                languageContexts = languageContexts,
                subtype = subtype,
            )
        } else {
            val language = normalizeLanguageCode(subtype.primaryLocale.language)
            val scored = scorerFor(autocorrectPolicySnapshot).score(
                request = LatinScoringRequest(
                    rawInput = rawInput,
                    primaryLocale = primaryLocale,
                    languages = languageContexts.map { it.toScoringLanguage() },
                    textBeforeSelection = content.textBeforeSelection,
                    maxCandidateCount = maxCandidateCount,
                    policy = autocorrectPolicySnapshot.policy,
                    autocorrect = autocorrectPolicySnapshot.settings,
                    geometry = KeyboardGeometrySource.current(),
                    taps = cacheKey.taps,
                ),
                hooks = scoringHooks(subtype, language),
            )
            scored.map { candidate ->
                WordSuggestionCandidate(
                    text = candidate.text,
                    confidence = candidate.confidence,
                    isEligibleForAutoCommit = candidate.isAutoCommit,
                    sourceProvider = this@LatinLanguageProvider,
                )
            }
        }

        val mergedSuggestions = mergePersonalEmailCandidates(
            regular = suggestions,
            emailCandidates = emailCandidates,
            isEmailField = isEmailField,
            maxCandidateCount = maxCandidateCount,
        )
        suggestionCache.withLock { cache ->
            cache[cacheKey] = mergedSuggestions
        }
        return mergedSuggestions
    }

    override suspend fun decideAutoCommit(subtype: Subtype, content: EditorContent): SuggestionCandidate? {
        val rawInput = content.composingText.ifBlank { content.currentWordText }.trim()
        if (rawInput.isBlank()) return null
        val languages = loadedScoringLanguages(subtype)
        if (languages.isEmpty()) return null
        val policySnapshot = currentHighCertaintyAutocorrectPolicySnapshot(currentAutocorrectAppContext())
        val scored = scorerFor(policySnapshot).score(
            request = LatinScoringRequest(
                rawInput = rawInput,
                primaryLocale = subtype.primaryLocale.base,
                languages = languages,
                textBeforeSelection = content.textBeforeSelection,
                maxCandidateCount = AutoCommitCandidateCount,
                policy = policySnapshot.policy,
                autocorrect = policySnapshot.settings,
                geometry = KeyboardGeometrySource.current(),
                taps = TapTrail.tapsFor(rawInput),
            ),
            hooks = inMemoryScoringHooks(subtype, normalizeLanguageCode(subtype.primaryLocale.language)),
        )
        // Tap count and mean distance from the typed keys only; never the word itself.
        flogDebug { "Autocorrect taps: ${describeTaps(rawInput)}" }
        val chosen = scored.firstOrNull { it.isAutoCommit } ?: return null
        return WordSuggestionCandidate(
            text = chosen.text,
            confidence = chosen.confidence,
            isEligibleForAutoCommit = true,
            sourceProvider = this@LatinLanguageProvider,
        )
    }

    /**
     * Languages of [subtype] whose models are already loaded. Never loads a model and never takes the model lock,
     * so it is safe on the main thread.
     */
    private fun loadedScoringLanguages(subtype: Subtype): List<LatinScoringLanguage> {
        val seenLanguages = LinkedHashSet<String>()
        val languages = mutableListOf<LatinScoringLanguage>()
        loadedModelsSnapshot.let { models ->
            subtype.locales().forEachIndexed { index, locale ->
                val language = normalizeLanguageCode(locale.language)
                if (!seenLanguages.add(language)) return@forEachIndexed
                val model = models[language] ?: return@forEachIndexed
                languages.add(
                    LatinScoringLanguage(
                        language = language,
                        locale = locale.base,
                        model = model,
                        isPrimary = index == 0,
                        hasOwnDictionary = model !== models[LegacyModelKey],
                    )
                )
            }
        }
        return languages
    }

    override suspend fun notifyTextBoundary(subtype: Subtype, content: EditorContent) {
        val textBefore = content.textBeforeSelection
        if (textBefore.isBlank()) return
        // A word must end directly at the cursor, otherwise this boundary event carries no new word.
        if (!textBefore.last().isLetterOrDigit()) return

        val trailingToken = textBefore.takeLastWhile { !it.isWhitespace() }
        if (trailingToken.contains('@')) {
            if (NetworkUtils.isEmailAddress(trailingToken)) {
                personalDataStore.recordEmail(trailingToken)
                suggestionCache.withLock { it.clear() }
            }
            return
        }
        if (trailingToken.any { it.isDigit() }) return

        val locale = subtype.primaryLocale.base
        val tokens = extractWordTokens(textBefore.takeLast(SuggestionContextTailLength), locale).takeLast(4)
        if (tokens.size < 2) return
        val language = normalizeLanguageCode(subtype.primaryLocale.language)
        // A word only counts as kept when autocorrect had the chance to change it: with autocorrect off, or in a field
        // where it never runs, typos would otherwise be learned as words.
        val info = editorInstance.activeInfo
        val autocorrectCouldRun = prefs.correction.highCertaintyAutocorrectEnabled.get() &&
            AutocorrectTriggerPolicy.allowsField(
                variation = info.inputAttributes.variation,
                flagTextNoSuggestions = info.inputAttributes.flagTextNoSuggestions,
                isRichInputEditor = info.isRichInputEditor,
            )
        personalNgramStore.learn(language, tokens, countWord = autocorrectCouldRun)
        suggestionCache.withLock { it.clear() }
    }

    override suspend fun clearPersonalizedData() {
        personalNgramStore.clearAll()
        personalDataStore.clearAll()
        suggestionCache.withLock { it.clear() }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val resolvedLocale = resolveBestLocaleForWord(
            subtype = subtype,
            candidateWord = candidate.text.toString(),
        )
        val promotion = rapidVocabularyLearner.onSuggestionAccepted(
            language = resolvedLocale.language,
            candidateWord = candidate.text.toString(),
            confidence = candidate.confidence,
        ) ?: return

        val promotionLocale = resolveLocaleByLanguage(subtype, promotion.language) ?: resolvedLocale
        promotePersonalVocabulary(
            locale = promotionLocale,
            normalizedWord = promotion.word,
            confirmations = promotion.confirmations,
        )
        suggestionCache.withLock { it.clear() }
    }

    override suspend fun notifySuggestionReverted(
        subtype: Subtype,
        candidate: SuggestionCandidate,
        originalToken: String?,
    ) {
        val resolvedLocale = resolveBestLocaleForWord(
            subtype = subtype,
            candidateWord = candidate.text.toString(),
        )
        rapidVocabularyLearner.onSuggestionReverted(
            language = resolvedLocale.language,
            candidateWord = candidate.text.toString(),
        )

        val normalizedOriginalToken = originalToken
            ?.let { normalizeInputWord(it, resolvedLocale.base) }
            ?.takeIf { it.isNotBlank() }
        if (candidate.isEligibleForAutoCommit && normalizedOriginalToken != null) {
            val didUpdateNeverCorrectWords = NeverCorrectWordsHelper.suppressWord(
                prefs = prefs,
                normalizedWord = normalizedOriginalToken,
                language = resolvedLocale.language,
            )
            if (didUpdateNeverCorrectWords) {
                suggestionCache.withLock { it.clear() }
            }
        }
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
            loadedModelsSnapshot = emptyMap()
        }
        suggestionCache.withLock {
            it.clear()
        }
    }

    private suspend fun getLanguageModelForSubtype(subtype: Subtype): LatinWordModel {
        val language = normalizeLanguageCode(subtype.primaryLocale.language)
        ensureLanguageModelLoaded(language)
        return languageModels.withLock { models ->
            models[language] ?: models[LegacyModelKey] ?: emptyModel
        }
    }

    private suspend fun getLanguageContextsForSubtype(subtype: Subtype): List<SubtypeLanguageContext> {
        val seenLanguages = LinkedHashSet<String>()
        val contexts = mutableListOf<SubtypeLanguageContext>()
        val locales = subtype.locales()

        locales.forEachIndexed { index, locale ->
            val language = normalizeLanguageCode(locale.language)
            if (!seenLanguages.add(language)) return@forEachIndexed

            ensureLanguageModelLoaded(language)
            val (model, hasOwnDictionary) = languageModels.withLock { models ->
                val model = models[language] ?: models[LegacyModelKey] ?: emptyModel
                model to (model !== models[LegacyModelKey])
            }
            contexts.add(
                SubtypeLanguageContext(
                    language = language,
                    locale = locale,
                    model = model,
                    isPrimary = index == 0,
                    hasOwnDictionary = hasOwnDictionary,
                )
            )
        }

        if (contexts.isNotEmpty()) return contexts

        val primaryLanguage = normalizeLanguageCode(subtype.primaryLocale.language)
        ensureLanguageModelLoaded(primaryLanguage)
        val (fallbackModel, hasOwnDictionary) = languageModels.withLock { models ->
            val model = models[primaryLanguage] ?: models[LegacyModelKey] ?: emptyModel
            model to (model !== models[LegacyModelKey])
        }
        return listOf(
            SubtypeLanguageContext(
                language = primaryLanguage,
                locale = subtype.primaryLocale,
                model = fallbackModel,
                isPrimary = true,
                hasOwnDictionary = hasOwnDictionary,
            )
        )
    }

    private suspend fun ensureLanguageModelLoaded(languageCode: String) {
        val language = normalizeLanguageCode(languageCode)
        if (languageModels.withLock { models -> models.containsKey(language) }) return

        modelLoadMutex.withLock {
            if (languageModels.withLock { models -> models.containsKey(language) }) return
            val model = loadLanguageModel(language)
            languageModels.withLock { models ->
                models.putIfAbsent(language, model)
                loadedModelsSnapshot = models.toMap()
            }
        }
    }

    private suspend fun loadLanguageModel(language: String): LatinWordModel {
        for (dictionaryAsset in FrequencyDictionaryAssets[language].orEmpty()) {
            try {
                val start = SystemClock.uptimeMillis()
                val model = buildLanguageModelFromFrequencyAsset(dictionaryAsset, language)
                flogDebug { "Loaded '$language' dictionary: ${model.words.size} words in ${SystemClock.uptimeMillis() - start} ms" }
                return model
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

    private fun buildLanguageModelFromFrequencyAsset(assetPath: String, language: String): LatinWordModel {
        val words = appContext.assets.open(assetPath).bufferedReader().useLines { lines ->
            LatinText.parseDictionary(lines)
        }
        val removals = try {
            appContext.assets.open("${LatinDictionaryCleanup.RemovalAssetDir}/$language.txt").bufferedReader()
                .useLines { lines -> LatinDictionaryCleanup.parseRemovalList(lines) }
        } catch (_: java.io.IOException) {
            emptySet()
        }
        val bigrams = try {
            val start = SystemClock.uptimeMillis()
            appContext.assets.open("${LatinText.BigramAssetDir}/$language.bigrams.txt").bufferedReader()
                .useLines { lines -> LatinBigramModel.parse(lines) }
                .also { flogDebug { "Loaded '$language' bigrams: ${it.pairCount} pairs in ${SystemClock.uptimeMillis() - start} ms" } }
        } catch (_: java.io.IOException) {
            LatinBigramModel.Empty
        } catch (e: Exception) {
            // A broken pair list must not cost the word list: the language keeps working without word context.
            flogError { "Failed loading bigrams for '$language': $e" }
            LatinBigramModel.Empty
        }
        return LatinWordModel.build(LatinDictionaryCleanup.apply(words, language, removals), bigrams)
    }

    private fun buildLanguageModelFromLegacyAsset(): LatinWordModel {
        val rawData = appContext.assets.readText(LegacyDictionaryAssetPath)
        val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
        val words = mutableMapOf<String, Int>()
        jsonData.forEach { (word, frequency) ->
            val normalizedWord = normalizeDictionaryWord(word)
            if (normalizedWord.isNotBlank()) {
                words[normalizedWord] = frequency.coerceAtLeast(1)
            }
        }
        return LatinWordModel.build(words)
    }

    private suspend fun suggestNextWordCandidates(
        content: EditorContent,
        maxCandidateCount: Int,
        locale: Locale,
        languageContexts: List<SubtypeLanguageContext>,
        subtype: Subtype,
    ): List<SuggestionCandidate> {
        val textBeforeSelection = content.textBeforeSelection
        if (textBeforeSelection.isBlank() || !isNextWordBoundary(textBeforeSelection)) {
            return emptyList()
        }

        val tokens = extractWordTokens(textBeforeSelection, locale)
        if (tokens.isEmpty()) return emptyList()

        val previousWord = tokens.last()
        val previousPreviousWord = tokens.getOrNull(tokens.lastIndex - 1)
        val contextTokens = extractRecentContextTokens(textBeforeSelection)
        val nonEmptyLanguageContexts = languageContexts.filter { context -> context.model.words.isNotEmpty() }
        if (nonEmptyLanguageContexts.isEmpty()) return emptyList()

        val languageConfidenceWeights = computeLanguageConfidenceWeights(
            languageContexts = nonEmptyLanguageContexts,
            contextTokens = contextTokens,
            normalizedInput = null,
        )

        val scores = mutableMapOf<String, Double>()
        val frequencies = mutableMapOf<String, Int>()

        for (context in nonEmptyLanguageContexts) {
            val languageWeight = languageConfidenceWeights[context.language] ?: 0.0
            val languageMultiplier = (0.50 + languageWeight).coerceIn(0.50, 1.50)
            val model = context.model

            for (index in 0 until tokens.lastIndex) {
                val current = tokens[index]
                val next = tokens[index + 1]

                if (next == previousWord) continue
                if (current != previousWord) continue
                if (!model.words.containsKey(next)) continue

                var score = 3.0
                if (previousPreviousWord != null && index > 0 && tokens[index - 1] == previousPreviousWord) {
                    score += 6.0
                }

                scores[next] = scores.getOrDefault(next, 0.0) + score * languageMultiplier
                frequencies[next] = maxOf(frequencies.getOrDefault(next, 0), model.words[next] ?: 1)
            }
        }

        // Blend in the persistent personal n-gram model, which remembers the user's own word sequences
        // across sessions and apps (unlike the in-field mining above, which only sees the current text).
        val primaryLanguage = normalizeLanguageCode(subtype.primaryLocale.language)
        val personalPredictions = personalNgramStore.predictNext(
            language = primaryLanguage,
            prev2 = previousPreviousWord,
            prev1 = previousWord,
            limit = maxCandidateCount,
        )
        if (personalPredictions.isNotEmpty()) {
            val maxPersonalScore = personalPredictions.first().score.coerceAtLeast(1)
            for (prediction in personalPredictions) {
                if (prediction.word == previousWord) continue
                val normalizedScore = prediction.score.toDouble() / maxPersonalScore.toDouble()
                scores[prediction.word] = scores.getOrDefault(prediction.word, 0.0) + 6.0 + 8.0 * normalizedScore
                val modelFrequency = nonEmptyLanguageContexts.maxOf { context ->
                    context.model.words[prediction.word] ?: 0
                }
                frequencies[prediction.word] = maxOf(frequencies.getOrDefault(prediction.word, 0), modelFrequency, 1)
            }
        }

        // What usually follows the previous word, from the bigram models. Ranks below the user's own sequences.
        if (!prefs.devtools.autocorrectLegacyEngine.get()) {
            val pairPredictions = noisyChannelScorer.predictNextWords(
                languages = nonEmptyLanguageContexts.map { it.toScoringLanguage() },
                primaryLocale = locale,
                textBeforeSelection = textBeforeSelection,
                maxCount = maxCandidateCount,
            )
            val maxConfidence = pairPredictions.maxOfOrNull { it.confidence }?.takeIf { it > 0.0 } ?: 1.0
            for (prediction in pairPredictions) {
                if (prediction.word == previousWord) continue
                scores[prediction.word] = scores.getOrDefault(prediction.word, 0.0) +
                    PairPredictionBaseScore + PairPredictionScoreRange * prediction.confidence / maxConfidence
                val modelFrequency = nonEmptyLanguageContexts.maxOf { context -> context.model.words[prediction.word] ?: 0 }
                frequencies[prediction.word] = maxOf(frequencies.getOrDefault(prediction.word, 0), modelFrequency, 1)
            }
        }

        if (scores.isEmpty()) {
            return suggestFallbackNextWordCandidates(
                languageContexts = nonEmptyLanguageContexts,
                languageConfidenceWeights = languageConfidenceWeights,
                previousWord = previousWord,
                maxCandidateCount = maxCandidateCount,
            )
        }

        val ranked = scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Double>> { it.value }
                    .thenByDescending { frequencies.getOrDefault(it.key, 0) }
                    .thenBy { it.key }
            )
            .take(maxCandidateCount)

        if (ranked.isEmpty()) return emptyList()

        val maxScore = ranked.first().value.coerceAtLeast(1.0)
        val maxFrequency = frequencies.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return ranked.map { entry ->
            val candidateWord = entry.key
            val candidateScore = entry.value
            val candidateFrequency = frequencies.getOrDefault(candidateWord, 1)
            val confidence = (
                0.25 +
                    0.55 * (candidateScore / maxScore) +
                    0.20 * (candidateFrequency.toDouble() / maxFrequency.toDouble())
                ).coerceIn(0.05, 0.98)

            WordSuggestionCandidate(
                text = nextWordText(candidateWord, nonEmptyLanguageContexts),
                confidence = confidence,
                isEligibleForAutoCommit = false,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    /** English "I" and "I'm" keep their capital as predictions, unless another active language knows the word. */
    private fun nextWordText(word: String, languageContexts: List<SubtypeLanguageContext>): String {
        if (word !in LatinText.EnglishPronounForms) return word
        val hasEnglish = languageContexts.any { it.language == "en" }
        val otherLanguageKnowsWord = languageContexts.any { it.language != "en" && it.model.isKnown(word) }
        return if (hasEnglish && !otherLanguageKnowsWord) word.replaceFirstChar { it.uppercaseChar() } else word
    }

    private fun isNextWordBoundary(textBeforeSelection: String): Boolean {
        val lastChar = textBeforeSelection.lastOrNull() ?: return false
        return lastChar.isWhitespace() || lastChar in setOf('.', ',', ';', ':', '!', '?')
    }

    private fun extractWordTokens(text: String, locale: Locale): List<String> =
        LatinText.extractWordTokens(text, locale)

    private fun extractRecentContextTokens(textBeforeSelection: String): List<String> =
        LatinText.extractRecentContextTokens(textBeforeSelection)

    private fun computeLanguageConfidenceWeights(
        languageContexts: List<SubtypeLanguageContext>,
        contextTokens: List<String>,
        normalizedInput: String?,
    ): Map<String, Double> {
        val signals = languageContexts.map { context ->
            val contextEvidence = contextTokens.mapIndexed { index, token ->
                if (!context.model.words.containsKey(token)) {
                    0.0
                } else {
                    val recencyWeight = (index + 1).toDouble() / contextTokens.size.coerceAtLeast(1).toDouble()
                    0.6 + 0.4 * recencyWeight
                }
            }.sum()
            val hasExactInputMatch = normalizedInput != null && context.model.words.containsKey(normalizedInput)
            LanguageConfidenceSignal(
                language = context.language,
                isPrimary = context.isPrimary,
                contextEvidence = contextEvidence,
                hasExactInputMatch = hasExactInputMatch,
            )
        }
        return mixedLanguageScoringPolicy.computeLanguageWeights(signals)
    }

    private fun suggestFallbackNextWordCandidates(
        languageContexts: List<SubtypeLanguageContext>,
        languageConfidenceWeights: Map<String, Double>,
        previousWord: String,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        val weightedCandidates = mutableMapOf<String, Pair<Double, Double>>()
        languageContexts.forEach { context ->
            val languageWeight = languageConfidenceWeights[context.language] ?: 0.0
            val languageMultiplier = (0.50 + languageWeight).coerceIn(0.50, 1.50)
            val fallbackWords = context.model.predictionShortcuts.fallbackCandidates(previousWord, maxCandidateCount)
            fallbackWords.forEach { entry ->
                val baseConfidence = (0.20 + 0.35 * (entry.frequency.toDouble() / context.model.maxFrequency.toDouble()))
                    .coerceIn(0.10, 0.55)
                val weightedRank = baseConfidence * languageMultiplier
                val weightedConfidence = mixedLanguageScoringPolicy.blendCandidateConfidence(baseConfidence, languageWeight)
                val current = weightedCandidates[entry.word]
                if (current == null || weightedRank > current.first) {
                    weightedCandidates[entry.word] = weightedRank to weightedConfidence
                }
            }
        }

        if (weightedCandidates.isEmpty()) return emptyList()

        return weightedCandidates.entries
            .sortedByDescending { it.value.first }
            .take(maxCandidateCount)
            .map { entry ->
            WordSuggestionCandidate(
                text = nextWordText(entry.key, languageContexts),
                confidence = entry.value.second,
                isEligibleForAutoCommit = false,
                sourceProvider = this@LatinLanguageProvider,
            )
        }
    }

    /** For debug logs: how many taps matched the word and how far, on average, they landed from their keys. */
    private fun describeTaps(rawInput: String): String {
        val taps = TapTrail.tapsFor(rawInput) ?: return "none"
        val geometry = KeyboardGeometrySource.current() ?: return "${taps.size}, no geometry"
        var sum = 0.0
        var count = 0
        taps.forEachIndexed { index, tap ->
            val center = geometry.center(rawInput[index]) ?: return@forEachIndexed
            if (tap.x.isNaN()) return@forEachIndexed
            sum += kotlin.math.hypot(tap.x - center.first, tap.y - center.second)
            count++
        }
        val mean = if (count == 0) "n/a" else String.format(Locale.ROOT, "%.2f", sum / count)
        return "${taps.size}, mean distance from key centers $mean key widths"
    }

    private fun normalizeLanguageCode(languageCode: String): String = LatinText.normalizeLanguageCode(languageCode)

    private fun currentAutocorrectAppContext(): AutocorrectAppContext {
        val activeInfo = editorInstance.activeInfo
        return AutocorrectAppContext(
            packageName = activeInfo.packageName,
            inputVariation = activeInfo.inputAttributes.variation,
            imeAction = activeInfo.imeOptions.action,
        )
    }

    private fun currentHighCertaintyAutocorrectPolicySnapshot(
        appContext: AutocorrectAppContext,
    ): AutocorrectPolicySnapshot {
        val minConfidencePercent = prefs.correction.highCertaintyAutocorrectMinConfidencePercent.get().coerceIn(50, 99)
        val minGapPercent = prefs.correction.highCertaintyAutocorrectMinConfidenceGapPercent.get().coerceIn(0, 50)
        val minInputLength = prefs.correction.highCertaintyAutocorrectMinInputLength.get().coerceIn(3, 12)
        val baseConfig = HighCertaintyAutocorrectConfig(
            enabled = prefs.correction.highCertaintyAutocorrectEnabled.get(),
            minConfidence = minConfidencePercent / 100.0,
            minConfidenceGap = minGapPercent / 100.0,
            minInputLength = minInputLength,
            maxAutoCorrectEditDistance = LatinWordModel.MaxEditDistance,
        )

        val appSpecificPolicy = AppSpecificAutocorrectProfilePolicy(
            AppSpecificAutocorrectConfig(
                enabled = prefs.correction.appSpecificAutocorrectProfilesEnabled.get(),
                chatAggressivenessPercent = prefs.correction.appSpecificAutocorrectChatAggressivenessPercent.get(),
                emailAggressivenessPercent = prefs.correction.appSpecificAutocorrectEmailAggressivenessPercent.get(),
            )
        )
        val profile = appSpecificPolicy.resolveProfile(appContext)
        val effectiveConfig = appSpecificPolicy.applyProfile(baseConfig, profile)
        val baseSettings = prefs.correction.autocorrectStrength.get().toSettings(enabled = baseConfig.enabled)
        val settings = if (prefs.correction.appSpecificAutocorrectProfilesEnabled.get()) {
            baseSettings.withAggressiveness(appSpecificPolicy.profileAggressivenessPercent(profile))
        } else {
            baseSettings
        }

        return AutocorrectPolicySnapshot(
            profile = profile,
            config = effectiveConfig,
            settings = settings,
            isLegacyEngine = prefs.devtools.autocorrectLegacyEngine.get(),
        )
    }

    private fun normalizeDictionaryWord(word: String): String = LatinText.normalizeDictionaryWord(word)

    private fun normalizeInputWord(word: String, locale: Locale): String = LatinText.normalizeInputWord(word, locale)

    private fun shouldSkipSpellcheck(word: String): Boolean {
        if (word.length <= 2) return true
        if (word.length > 48) return true
        if (word.any { it.isDigit() }) return true
        val letterCount = word.count { it.isLetter() }
        return letterCount == 0
    }

    private fun scoringHooks(subtype: Subtype, language: String): LatinScoringHooks {
        return object : LatinScoringHooks {
            override fun isUserDictionaryWord(normalizedWord: String): Boolean {
                return isUserDictionaryWord(subtype, normalizedWord) || normalizedWord in speechDictionaryWords()
            }

            override fun isBlockedByUserPreference(normalizedWord: String): Boolean {
                return isAutoCorrectBlockedByUserPreference(subtype, normalizedWord)
            }

            override suspend fun personalContinuationScore(previousWord: String, candidateWord: String): Double {
                return personalNgramStore.continuationScore(language, previousWord, candidateWord)
            }

            override fun timesTyped(normalizedWord: String): Int {
                return personalNgramStore.timesTypedIfLoaded(language, normalizedWord)
            }
        }
    }

    private fun inMemoryScoringHooks(subtype: Subtype, language: String): LatinScoringHooks {
        val userWords = userDictionarySnapshot
        val speechWords = speechDictionaryWords()
        return object : LatinScoringHooks {
            override fun isUserDictionaryWord(normalizedWord: String): Boolean {
                return normalizedWord in userWords || normalizedWord in speechWords
            }

            override fun isBlockedByUserPreference(normalizedWord: String): Boolean {
                return isAutoCorrectBlockedByUserPreference(subtype, normalizedWord)
            }

            override suspend fun personalContinuationScore(previousWord: String, candidateWord: String): Double {
                return personalNgramStore.continuationScoreIfLoaded(language, previousWord, candidateWord)
            }

            override fun timesTyped(normalizedWord: String): Int {
                return personalNgramStore.timesTypedIfLoaded(language, normalizedWord)
            }
        }
    }

    /**
     * Refreshes [userDictionarySnapshot] from both user dictionaries. Must run off the main thread.
     */
    private fun refreshUserDictionarySnapshotIfStale() {
        val now = SystemClock.uptimeMillis()
        val last = userDictionarySnapshotUptimeMs
        if (last != 0L && now - last < UserDictionarySnapshotMaxAgeMs) return
        userDictionarySnapshotUptimeMs = now
        userDictionarySnapshot = try {
            val dictionaryManager = DictionaryManager.default()
            dictionaryManager.loadUserDictionariesIfNecessary()
            buildSet {
                listOfNotNull(
                    dictionaryManager.florisUserDictionaryDao(),
                    dictionaryManager.systemUserDictionaryDao(),
                ).forEach { dao ->
                    dao.queryAll().forEach { entry ->
                        add(normalizeDictionaryWord(entry.word))
                        entry.shortcut?.let { add(normalizeDictionaryWord(it)) }
                    }
                }
            }
        } catch (e: Throwable) {
            flogError { "Failed refreshing user dictionary snapshot: $e" }
            userDictionarySnapshot
        }
    }

    private fun scorerFor(snapshot: AutocorrectPolicySnapshot): LatinCurrentWordScorer {
        return if (snapshot.isLegacyEngine) legacyScorer else noisyChannelScorer
    }

    /**
     * Words from the personal speech dictionary (dictation vocabulary). The user added them on purpose, so
     * autocorrect never replaces them. Reads in-memory state only.
     */
    private fun speechDictionaryWords(): Set<String> {
        val document = try {
            appContext.speechDictionary().value.state.value.document
        } catch (_: Throwable) {
            return emptySet()
        }
        speechWordsCache?.let { (cachedDocument, words) -> if (cachedDocument === document) return words }
        val words = document.words.mapTo(HashSet()) { normalizeDictionaryWord(it.word) }
        speechWordsCache = document to words
        return words
    }

    private fun isEmailInputField(): Boolean {
        return when (editorInstance.activeInfo.inputAttributes.variation) {
            InputAttributes.Variation.EMAIL_ADDRESS,
            InputAttributes.Variation.WEB_EMAIL_ADDRESS -> true
            else -> false
        }
    }

    private suspend fun personalEmailCandidates(
        rawInput: String,
        content: EditorContent,
        isEmailField: Boolean,
    ): List<SuggestionCandidate> {
        if (rawInput.contains('@')) return emptyList()
        if (rawInput.isBlank()) {
            // Offer remembered addresses proactively when focusing an empty e-mail field.
            if (!isEmailField || content.textBeforeSelection.isNotBlank()) return emptyList()
            return personalDataStore.emailsByRelevance(limit = 3).map { emailCandidate(it) }
        }
        val minPrefixLength = if (isEmailField) 1 else 3
        if (rawInput.length < minPrefixLength) return emptyList()
        val textBefore = content.textBeforeSelection
        // Only suggest when the typed token starts fresh (start of field or after whitespace).
        if (textBefore.endsWith(rawInput)) {
            val charBefore = textBefore.dropLast(rawInput.length).lastOrNull()
            if (charBefore != null && !charBefore.isWhitespace()) return emptyList()
        }
        val limit = if (isEmailField) 3 else 2
        return personalDataStore.emailsByRelevance(prefix = rawInput.lowercase(Locale.ROOT), limit = limit)
            .map { emailCandidate(it) }
    }

    private fun emailCandidate(address: String): SuggestionCandidate {
        return WordSuggestionCandidate(
            text = address,
            confidence = 0.9,
            isEligibleForAutoCommit = false,
            sourceProvider = this@LatinLanguageProvider,
        )
    }

    private fun mergePersonalEmailCandidates(
        regular: List<SuggestionCandidate>,
        emailCandidates: List<SuggestionCandidate>,
        isEmailField: Boolean,
        maxCandidateCount: Int,
    ): List<SuggestionCandidate> {
        if (emailCandidates.isEmpty()) return regular
        return when {
            isEmailField || regular.isEmpty() -> emailCandidates + regular
            else -> buildList {
                add(regular.first())
                addAll(emailCandidates)
                addAll(regular.drop(1))
            }
        }.take(maxCandidateCount)
    }

    private fun buildSuggestCacheKey(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
        autocorrectPolicySignature: String,
        isEmailField: Boolean,
    ): SuggestCacheKey {
        // Also while a word is typed: the words before it decide "I" and apostrophe forms such as "z'n".
        val textBeforeTail = content.textBeforeSelection.takeLast(SuggestionContextTailLength)
        return SuggestCacheKey(
            language = subtype.locales()
                .joinToString(separator = ",") { locale -> normalizeLanguageCode(locale.language) },
            composingText = content.composingText,
            currentWordText = content.currentWordText,
            textBeforeTail = textBeforeTail,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
            isPrivateSession = isPrivateSession,
            autocorrectPolicySignature = autocorrectPolicySignature,
            isEmailField = isEmailField,
            taps = TapTrail.tapsFor(content.composingText.ifBlank { content.currentWordText }.trim()),
        )
    }

    private fun isUserDictionaryWord(subtype: Subtype, normalizedWord: String): Boolean {
        return try {
            val dictionaryManager = DictionaryManager.default()
            subtype.locales().any { locale ->
                dictionaryManager.spell(normalizedWord, locale)
            }
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
            val dictionaryManager = DictionaryManager.default()
            subtype.locales()
                .asSequence()
                .flatMap { locale ->
                    dictionaryManager.queryUserDictionary(normalizedWord, locale).asSequence()
                }
                .map { normalizeDictionaryWord(it.text.toString()) }
                .filter { it.isNotBlank() && it != normalizedWord }
                .distinct()
                .take(maxCount)
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun promotePersonalVocabulary(
        locale: FlorisLocale,
        normalizedWord: String,
        confirmations: Int,
    ) {
        try {
            val dictionaryManager = DictionaryManager.default()
            dictionaryManager.loadUserDictionariesIfNecessary()
            val florisDao = dictionaryManager.florisUserDictionaryDao() ?: return
            val existing = florisDao.queryExact(normalizedWord, locale).firstOrNull()
            val confidenceBoost = (confirmations * 16).coerceAtMost(64)
            val updatedFrequency = ((existing?.freq ?: 160) + confidenceBoost).coerceAtMost(FREQUENCY_MAX)

            if (existing == null) {
                florisDao.insert(
                    UserDictionaryEntry(
                        id = 0L,
                        word = normalizedWord,
                        freq = updatedFrequency,
                        locale = locale.localeTag(),
                        shortcut = null,
                    )
                )
            } else {
                florisDao.update(
                    existing.copy(
                        freq = updatedFrequency,
                        locale = existing.locale ?: locale.localeTag(),
                    )
                )
            }
        } catch (e: Throwable) {
            flogError { "Failed promoting rapid personal vocabulary entry: $e" }
        }
        // Pick up the promoted word in the next snapshot.
        userDictionarySnapshotUptimeMs = 0L
    }

    private suspend fun resolveBestLocaleForWord(subtype: Subtype, candidateWord: String): FlorisLocale {
        val normalizedWord = normalizeDictionaryWord(candidateWord)
        if (normalizedWord.isBlank()) return subtype.primaryLocale

        val contexts = getLanguageContextsForSubtype(subtype)
        val matchingLanguage = contexts.firstOrNull { context ->
            context.model.words.containsKey(normalizedWord)
        }?.language ?: return subtype.primaryLocale

        return resolveLocaleByLanguage(subtype, matchingLanguage) ?: subtype.primaryLocale
    }

    private fun resolveLocaleByLanguage(subtype: Subtype, language: String): FlorisLocale? {
        val normalizedLanguage = normalizeLanguageCode(language)
        return subtype.locales().firstOrNull { locale ->
            normalizeLanguageCode(locale.language) == normalizedLanguage
        }
    }

    private fun isAutoCorrectBlockedByUserPreference(
        subtype: Subtype,
        normalizedInput: String,
    ): Boolean {
        val languages = subtype.locales().mapTo(linkedSetOf()) { locale ->
            normalizeLanguageCode(locale.language)
        }
        return NeverCorrectWordsHelper.isBlocked(
            prefs = prefs,
            normalizedWord = normalizedInput,
            languages = languages,
        )
    }

    private fun applyInputCase(rawInput: String, suggestion: String, locale: Locale): String =
        LatinText.applyInputCase(rawInput, suggestion, locale)

}
