/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectTriggerPolicy
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    // The map itself never changes after construction, so main-thread callers may read it without the lock.
    private val providerMap = mapOf(
        LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
        HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
    )
    private val providers = guardedByLock { providerMap }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    @Volatile
    private var wordSuggestionBatch = WordSuggestionBatch.Empty
    // Numbers the suggestion runs as they are requested; see WordSuggestionRequest.sequence.
    private val suggestionSequence = AtomicLong(0L)
    // The word whose autocorrection the user just undid, and its subtype, while the provider is being told.
    @Volatile
    private var revertedAutocorrect: Pair<String, Long>? = null
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            // Only look the providers up under the lock. Preloading loads dictionaries for seconds, and the main
            // thread takes this lock on every text commit (composing region lookup), so holding it while loading
            // froze typing right after the keyboard process started.
            val wrappers = providers.withLock { providers ->
                buildList {
                    subtype.nlpProviders.forEach { _, providerId ->
                        providers[providerId]?.let { add(it) }
                    }
                }
            }.distinct()
            wrappers.forEach { wrapper ->
                wrapper.createIfNecessary()
                wrapper.preload(subtype)
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        val providerId = subtype.nlpProviders.suggestion
        providersForceSuggestionOn[providerId]?.let { return it }
        // Called on the main thread, so read the constant provider map without the lock.
        val forcesSuggestionOn = (providerMap[providerId]?.provider as? SuggestionProvider)?.forcesSuggestionOn ?: false
        providersForceSuggestionOn[providerId] = forcesSuggestionOn
        return forcesSuggestionOn
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        val isPrivateSession = keyboardManager.activeState.isIncognitoMode
        val allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get()
        val request = wordSuggestionRequest(
            content, subtype, isPrivateSession, allowPossiblyOffensive, suggestionSequence.incrementAndGet(),
        )
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = allowPossiblyOffensive,
                        isPrivateSession = isPrivateSession,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                else -> {
                    getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = allowPossiblyOffensive,
                        isPrivateSession = isPrivateSession,
                    )
                }
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    wordSuggestionBatch = WordSuggestionBatch(request, suggestions)
                    internalSuggestions = reqTime to buildList {
                        addAll(emojiSuggestions)
                        addAll(suggestions)
                    }
                }
            }
            TypingSpeedMetrics.recordSuggestionLatency(SystemClock.uptimeMillis() - reqTime)
        }
    }

    /**
     * Notifies the active suggestion provider that a word has just been completed (the user typed a separator
     * such as space, punctuation or enter). Used for on-device personalized learning (n-gram prediction and
     * user-specific data such as e-mail addresses). Never fires in incognito sessions, password/URI fields or
     * when the user has disabled personalized learning.
     */
    fun notifyTextBoundary(content: EditorContent) {
        if (!prefs.suggestion.personalizedLearningEnabled.get()) return
        if (keyboardManager.activeState.isIncognitoMode) return
        when (keyboardManager.activeState.keyVariation) {
            KeyVariation.NORMAL, KeyVariation.EMAIL_ADDRESS -> Unit
            else -> return
        }
        val subtype = subtypeManager.activeSubtype
        scope.launch {
            getSuggestionProvider(subtype).notifyTextBoundary(subtype, content)
        }
    }

    /**
     * Deletes all personalized/learned typing data (personal n-grams and remembered personal data) from all
     * suggestion providers.
     */
    suspend fun clearPersonalizedData() {
        providers.withLock { providerMap ->
            providerMap.values.forEach { wrapper ->
                (wrapper.provider as? SuggestionProvider)?.clearPersonalizedData()
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        // Under the lock, so a suggestion run that finishes meanwhile cannot put its batch back afterwards.
        runBlocking {
            internalSuggestionsGuard.withLock {
                wordSuggestionBatch = WordSuggestionBatch.Empty
                internalSuggestions = reqTime to suggestions
            }
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestionsGuard.withLock {
                wordSuggestionBatch = WordSuggestionBatch.Empty
                internalSuggestions = reqTime to emptyList()
            }
        }
    }

    /**
     * Notes synchronously that the user undid the autocorrection of [originalToken], so a space pressed right away
     * cannot redo it before the provider has stored the word as one to leave alone. [clearAutocorrectReverted] ends
     * this once the provider has been told.
     */
    fun noteAutocorrectReverted(originalToken: String) {
        revertedAutocorrect = originalToken.trim() to subtypeManager.activeSubtype.id
    }

    fun clearAutocorrectReverted(originalToken: String) {
        if (revertedAutocorrect?.first == originalToken.trim()) revertedAutocorrect = null
    }

    private fun wordSuggestionRequest(
        content: EditorContent,
        subtype: Subtype,
        isPrivateSession: Boolean,
        allowPossiblyOffensive: Boolean,
        sequence: Long,
    ): WordSuggestionRequest {
        // Main thread: read the constant provider map without the lock.
        val provider = providerMap[subtype.nlpProviders.suggestion]?.provider as? SuggestionProvider
        return WordSuggestionRequest.of(
            content = content,
            subtypeId = subtype.id,
            inputSessionId = editorInstance.activeInputSessionId,
            isPrivateSession = isPrivateSession,
            sequence = sequence,
            allowPossiblyOffensive = allowPossiblyOffensive,
            providerState = provider?.autoCommitStateKey(subtype).orEmpty(),
        )
    }

    /**
     * Returns the candidate that should replace the word the user just finished in [content], or null to keep the
     * word as typed. Only uses suggestions computed for exactly this word; when the latest suggestions belong to an
     * earlier prefix (fast typing), the provider decides on the spot from in-memory data. [trigger] is the character
     * that ends the word.
     */
    fun autoCommitCandidateFor(content: EditorContent, trigger: String = " "): SuggestionCandidate? {
        // The toggle can flip between the last suggestion run and the next space press.
        if (!prefs.correction.highCertaintyAutocorrectEnabled.get()) return null
        if (!isSuggestionOn()) return null
        // Right after an accepted suggestion the keyboard is predicting the next word; the accepted word stays.
        if (editorInstance.phantomSpace.isActive) return null
        val editorInfo = editorInstance.activeInfo
        if (!AutocorrectTriggerPolicy.allowsField(
                variation = editorInfo.inputAttributes.variation,
                flagTextNoSuggestions = editorInfo.inputAttributes.flagTextNoSuggestions,
                isRichInputEditor = editorInfo.isRichInputEditor,
            )
        ) {
            return null
        }
        if (!AutocorrectTriggerPolicy.isCorrectableToken(AutocorrectTriggerPolicy.tokenBeforeCursor(content.textBeforeSelection), trigger)) {
            return null
        }
        val subtype = subtypeManager.activeSubtype
        val selection = AutoCommitSelector.select(
            // Only the latest suggestion run can stand for this word.
            request = wordSuggestionRequest(
                content = content,
                subtype = subtype,
                isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                sequence = suggestionSequence.get(),
            ),
            batch = wordSuggestionBatch,
            revertedInput = revertedAutocorrect?.takeIf { it.second == subtype.id }?.first,
        ) {
            val start = SystemClock.uptimeMillis()
            // Main thread: read the constant provider map without the lock.
            val provider = providerMap[subtype.nlpProviders.suggestion]?.provider as? SuggestionProvider
            // A scoring failure must cost one autocorrection, never the keyboard.
            val decided = try {
                val allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get()
                provider?.let { runBlocking { it.decideAutoCommit(subtype, content, allowPossiblyOffensive) } }
            } catch (e: Exception) {
                flogError { "Autocorrect decision failed: ${e.javaClass.simpleName}" }
                null
            }
            val latencyMs = SystemClock.uptimeMillis() - start
            TypingSpeedMetrics.recordAutoCommitDecidedNow(latencyMs)
            flogDebug { "Autocorrect decided on the spot in $latencyMs ms" }
            decided
        }
        // Never log the typed word itself.
        flogDebug { "Autocorrect decision source=${selection.source} applies=${selection.candidate != null}" }
        return selection.candidate
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private fun assembleCandidates() {
        runBlocking {
            val candidates = when {
                isSuggestionOn() -> {
                    clipboardSuggestionProvider.suggest(
                        subtype = Subtype.DEFAULT,
                        content = editorInstance.activeContent,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    ).ifEmpty {
                        buildList {
                            internalSuggestionsGuard.withLock {
                                addAll(internalSuggestions.second)
                            }
                        }
                    }
                }
                else -> emptyList()
            }
            // Right after an autocorrection, the typed word comes first so one tap restores it.
            val revert = if (isSuggestionOn()) keyboardManager.autocorrectRevertCandidate() else null
            activeCandidates = if (revert != null) listOf(revert) + candidates.filter { it.text != revert.text } else candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(
            subtype: Subtype,
            candidate: SuggestionCandidate,
            originalToken: String?,
        ) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }
}
