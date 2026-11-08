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

import dev.patrickgold.florisboard.ime.nlp.latin.EngineAutocorrectProfile
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryAssets
import dev.patrickgold.florisboard.ime.nlp.latin.PredictionContextMapper
import org.florisboard.lib.prediction.Candidate
import org.florisboard.lib.prediction.CandidateKind
import org.florisboard.lib.prediction.EngineConfig
import org.florisboard.lib.prediction.PersonalOverlay
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.SpatialModel
import org.florisboard.lib.prediction.TextFolding
import org.florisboard.lib.prediction.TouchPoint
import java.util.Locale
import kotlin.math.ln

/**
 * Scores the current word with the prediction engine of `lib/prediction` (lexicon trie, weighted edit-distance decoder
 * with a touch model, interpolated Kneser-Ney trigram models per language) instead of the frequency lists.
 *
 * Main's inputs map onto the engine as follows: [LatinScoringRequest.taps] and [LatinScoringRequest.geometry] become the
 * engine's spatial model and touch points (both in key pitches; rows one unit apart), [LatinScoringRequest.languages]
 * become the language mixture (primary first, the others with the engine's secondary-language prior), the text before
 * the cursor becomes the trigram context ([PredictionContextMapper]), and [AutocorrectSettings] (strength, app profile,
 * on/off) become the engine thresholds ([EngineAutocorrectProfile]). The hooks give the protected words (user and speech
 * dictionary, never-correct words), the personal continuation score (a log bonus for words the user writes after the
 * previous word) and how often the typed word was kept (a bonus for keeping it).
 *
 * Main's rules stay on top of the engine's decision:
 * - AC-011 ([RealWordRule.CONFUSION_PAIRS], the default): a typed word of a [ConfusionSets] group of one of the
 *   subtype's languages is never replaced by a word of its own group on space ("then" for "than"; this also keeps the
 *   ambiguous apostrophe-less words of FR-09, "its", "cant", "well"), and never revised to one while it stands as
 *   typed. Every other replacement and revision is the engine's as tuned, like the noisy-channel scorer, which also
 *   replaces a known word when another reading is far more likely. [RealWordRule.DICTIONARY_WORDS] is a stricter
 *   devtools option: no word of main's word lists is replaced, and no valid word revised while it stands as typed.
 * - [ChatShorthand] words are kept unless the engine clearly prefers another word in context: keeping one on space
 *   gets [shorthandKeepBonus] nats ("wss", "idd", "egt", "kga" stay), and revision treats them as any other word
 *   ("ok heb" at the start of a Dutch sentence becomes "ik heb").
 * - Never-correct and user dictionary words are protected literals; possibly offensive words are never offered unless
 *   typed exactly. Input length is left to the engine's own limits (two-letter typos such as "jw" for "je" are fixed).
 * - A lowercase English pronoun ("i", "i'm") still becomes "I", "I'm" on space; other letter case is never changed.
 * - A capitalized word in mid-sentence (a name) or an all-caps word (an acronym) is kept more readily (FR-06), like a
 *   word the user kept three times.
 * - Missed spaces ("thisis") come from [splitScorer], main's noisy-channel splits: when the typed letters are no word
 *   of main's lists, a split it would auto-commit wins, and its best split leads the strip when the engine has no
 *   correction.
 *
 * Without an engine for all languages of the request (another language, models still loading or failed to load) every
 * call goes to [fallback]. Inputs with digits or symbols go there too. Thread-safe.
 */
internal class NgramLatinScorer(
    private val engineFor: (languages: List<String>) -> PredictionEngine?,
    private val fallback: LatinCurrentWordScorer,
    private val splitScorer: NoisyChannelLatinScorer? = fallback as? NoisyChannelLatinScorer,
    private val baseConfig: EngineConfig = AppEngineConfig,
    private val overlayFor: (languages: List<String>) -> PersonalOverlay? = { null },
    /** Which correctly spelled words may be replaced or revised; see [RealWordRule]. */
    private val realWordRule: () -> RealWordRule = { RealWordRule.CONFUSION_PAIRS },
    /** Nats for keeping a [ChatShorthand] word as typed; [Double.POSITIVE_INFINITY] protects them outright. */
    private val shorthandKeepBonus: Double = ShorthandKeepBonus,
    /**
     * Also require [AutocorrectSettings.minInputLength] letters (the noisy-channel scorer's limit) unless the
     * correction only respells the same letters. Off: the engine's own limits apply. Kept for the pipeline replay,
     * which measures what the limit costs.
     */
    private val noisyChannelLengthLimit: Boolean = false,
    /**
     * Let the engine decide confusion pairs that only differ by an apostrophe ("its"/"it's", "lets"/"let's") instead of
     * the AC-011 rule. Off (AC-011 as written); kept for the pipeline replay, which measures the difference.
     */
    private val apostrophePairsFollowEngine: Boolean = false,
) : LatinCurrentWordScorer {
    companion object {
        /** The engine's dev-tuned defaults; [RealWordRule] gates the decisions on top. */
        val AppEngineConfig = EngineConfig()

        /**
         * Nats for keeping a chat shorthand word as typed on space. Dev pipeline replay (5 seeds, NL+EN), word error
         * rate keys / taps: protected outright 1.090% / 0.661%, bonus 0: 1.051% / 0.614%, 2: 1.077% / 0.614%,
         * 4: 1.077% / 0.634%, 6: 1.084% / 0.641%. Literal checks keep 96.4% at 0 ("kga" became "ga", "tog" became
         * "tot") and 97.2% from 2 up, as with full protection. Revision gets no bonus: the right context decides.
         */
        const val ShorthandKeepBonus = 2.0

        /** Same as main's noisy-channel scorer: a word kept three times gets 4 * ln(1 + times) nats for keeping it. */
        const val LearnedWordMinCount = 3
        const val LearnedWordBonus = 4.0

        /** Same weight as main's noisy-channel scorer: ln(1 + 4 * continuation score) nats. */
        const val PersonalContinuationWeight = 4.0

        /** Same as main's noisy-channel scorer ([NoisyChannelParams]): FR-06, names and acronyms. */
        const val CapitalizedLiteralBonus = 6.0
        const val AllCapsLiteralBonus = 6.0

        private const val MinSplitInputLength = 4
        private const val MaxDerivedEngines = 8
        private const val EnglishLanguage = "en"
    }

    private data class DerivedKey(
        val base: PredictionEngine,
        val spatial: SpatialModel?,
        val config: EngineConfig,
        val overlay: PersonalOverlay?,
    )

    private val derived = object : LinkedHashMap<DerivedKey, PredictionEngine>(MaxDerivedEngines, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DerivedKey, PredictionEngine>?): Boolean {
            return size > MaxDerivedEngines
        }
    }

    private class SpatialCache(val geometry: KeyGeometry, val spatial: SpatialModel?)

    @Volatile
    private var spatialCache: SpatialCache? = null

    private val protectsShorthand: Boolean get() = shorthandKeepBonus.isInfinite()

    private fun currentBaseConfig(): EngineConfig {
        return if (realWordRule() == RealWordRule.DICTIONARY_WORDS) baseConfig.copy(reviseTypedValidWords = false) else baseConfig
    }

    /** The engine languages of [languages] (primary first) when every one of them has an n-gram model, else null. */
    fun engineLanguagesOf(languages: List<LatinScoringLanguage>): List<String>? =
        LatinDictionaryAssets.ngramLanguages(languages.map { it.language })

    /** True when the engine serves [languages] right now (all models loaded). Never waits. */
    fun isReady(languages: List<LatinScoringLanguage>): Boolean = engineLanguagesOf(languages)?.let(engineFor) != null

    override suspend fun score(request: LatinScoringRequest, hooks: LatinScoringHooks): List<LatinScoredCandidate> {
        val rawInput = request.rawInput.trim()
        if (rawInput.isEmpty()) return emptyList()
        val codes = engineLanguagesOf(request.languages) ?: return fallback.score(request, hooks)
        val base = engineFor(codes) ?: return fallback.score(request, hooks)
        if (!isEngineInput(rawInput)) return fallback.score(request, hooks)

        val locale = request.primaryLocale
        val mainInput = LatinText.normalizeInputWord(rawInput, locale)
        val settings = request.autocorrect
        // Like the noisy-channel scorer: taps without a measured layout are in the frame of the standard phone layout.
        val geometry = request.geometry ?: KeyGeometry.QwertyPhone
        val spatial = spatialOf(geometry)
        val rule = realWordRule()
        val engineConfig = EngineAutocorrectProfile.configFor(currentBaseConfig(), settings)
        val engine = derive(base, spatial, engineConfig, overlayFor(codes))
        val context = PredictionContextMapper.map(request.textBeforeSelection, rawInput, request.textStartsAtFieldStart)
        val touches = touchesOf(rawInput, request.taps, geometry, spatial)
        val isProtected: (String) -> Boolean = { word ->
            (protectsShorthand && ChatShorthand.contains(word)) || hooks.isUserDictionaryWord(word) ||
                hooks.isBlockedByUserPreference(word)
        }
        val isBlocked = blockedPredicate(request.blockedWords)
        val timesTyped = hooks.timesTyped(mainInput)
        val literalBonus = (if (timesTyped >= LearnedWordMinCount) LearnedWordBonus * ln(1.0 + timesTyped) else 0.0) +
            nameBonus(rawInput, request.textBeforeSelection) +
            (if (!protectsShorthand && ChatShorthand.contains(mainInput)) shorthandKeepBonus else 0.0)

        var result = engine.suggest(context, rawInput, touches, isProtected, isBlocked, literalBonus = literalBonus)
        // The user's own word pairs: scored for the words the engine considers, then the engine decides again with them.
        val previous = context.words.lastOrNull()
        if (previous != null) {
            val boosts = HashMap<String, Double>()
            val words = LinkedHashSet<String>()
            result.candidates.forEach { words.add(it.word) }
            result.autocorrect?.let { words.add(it.word) }
            for (word in words) {
                val continuation = hooks.personalContinuationScore(previous, word)
                if (continuation > 0.0) boosts[word] = ln(1.0 + PersonalContinuationWeight * continuation)
            }
            if (boosts.isNotEmpty()) {
                result = engine.suggest(
                    context, rawInput, touches, isProtected, isBlocked,
                    contextBoost = { word -> boosts[word] ?: 0.0 },
                    literalBonus = literalBonus,
                )
            }
        }

        val mainKnown = request.languages.any { it.model.isKnown(mainInput) } || ChatShorthand.contains(mainInput)
        val autocorrect = result.autocorrect?.takeIf { target ->
            settings.enabled &&
                !(protectsShorthand && ChatShorthand.contains(mainInput)) &&
                when (rule) {
                    RealWordRule.CONFUSION_PAIRS -> !isConfusionPartner(request.languages, mainInput, target.word)
                    RealWordRule.DICTIONARY_WORDS -> !mainKnown
                    RealWordRule.NONE -> true
                } &&
                !hooks.isBlockedByUserPreference(mainInput) &&
                (!noisyChannelLengthLimit || mainInput.length >= settings.minInputLength || target.keyForm == result.keyForm)
        }
        val pronounFix = if (autocorrect == null && settings.enabled) pronounCaseFix(result, rawInput, hooks, mainInput) else null
        val split = if (!mainKnown && mainInput.length >= MinSplitInputLength &&
            mainInput.all { it.isLetter() } && splitScorer != null
        ) {
            // A split main would auto-commit wins over the engine's reading. Restricting that to engine readings that
            // drop letters fixed one dev word ("yoube" as "you be" instead of "youve") but cost #17's run-together
            // sets 5.5 (EN) and 7.4 (NL) points of right splits, so the split keeps priority.
            splitScorer.score(request, hooks).firstOrNull()?.takeIf { ' ' in it.word }
                ?.takeIf { it.isAutoCommit || result.autocorrect == null }
        } else {
            null
        }

        val out = ArrayList<LatinScoredCandidate>(request.maxCandidateCount + 2)
        val seenTexts = HashSet<String>()
        fun add(candidate: LatinScoredCandidate) {
            if (seenTexts.add(candidate.text)) out.add(candidate)
        }
        split?.let { add(it) }
        pronounFix?.let { add(toScored(it, rawInput, request, isLiteral = true, isAutoCommit = true)) }
        for (candidate in result.displaySlots(request.maxCandidateCount)) {
            val isAutoCommit = autocorrect != null && split?.isAutoCommit != true &&
                candidate.word == autocorrect.word && candidate.keyForm == autocorrect.keyForm
            add(toScored(candidate, rawInput, request, isLiteral = candidate.word == result.literal?.word, isAutoCommit))
        }
        return out.take(request.maxCandidateCount)
    }

    /**
     * Next-word predictions from the trigram models after [textBeforeSelection], in the engine's display order, or
     * null when the engine does not serve [languages] right now. Blocked words are left out.
     */
    fun predictNextWords(
        languages: List<LatinScoringLanguage>,
        textBeforeSelection: CharSequence,
        textStartsAtFieldStart: Boolean,
        maxCount: Int,
        blockedWords: Set<String> = emptySet(),
    ): List<Candidate>? {
        val codes = engineLanguagesOf(languages) ?: return null
        val base = engineFor(codes) ?: return null
        val engine = derive(base, null, baseConfig, overlayFor(codes))
        val context = PredictionContextMapper.map(textBeforeSelection, "", textStartsAtFieldStart)
        val result = engine.suggest(context, "", isBlocked = blockedPredicate(blockedWords))
        return result.displaySlots(maxCount)
    }

    /**
     * Right-context revision of the previous word once the next one is committed ([PredictionEngine.revise]), or null
     * to keep it. A word that stands as typed is never revised when it is valid for the engine or a word of main's
     * dictionaries (AC-011); a typo left as typed and a word that autocorrect produced can be. [taps] are the taps of
     * [typed] in key widths, from [geometry].
     */
    fun revise(
        languages: List<LatinScoringLanguage>,
        textBeforeWord: CharSequence,
        textStartsAtFieldStart: Boolean,
        typed: String,
        committed: String,
        next: String,
        taps: List<LatinTap>?,
        geometry: KeyGeometry?,
        settings: AutocorrectSettings,
        isProtected: (String) -> Boolean,
        blockedWords: Set<String>,
    ): LatinScoredCandidate? {
        if (!settings.enabled) return null
        val codes = engineLanguagesOf(languages) ?: return null
        val base = engineFor(codes) ?: return null
        val primaryLocale = languages.firstOrNull()?.locale ?: Locale.ROOT
        val typedWord = LatinText.normalizeInputWord(typed, primaryLocale)
        val standsAsTyped = typedWord == LatinText.normalizeInputWord(committed, primaryLocale)
        val rule = realWordRule()
        if (standsAsTyped && rule == RealWordRule.DICTIONARY_WORDS && languages.any { it.model.isKnown(typedWord) }) return null
        val layout = geometry ?: KeyGeometry.QwertyPhone
        val spatial = spatialOf(layout)
        val engine = derive(base, spatial, EngineAutocorrectProfile.configFor(currentBaseConfig(), settings), overlayFor(codes))
        val context = PredictionContextMapper.map(textBeforeWord, "", textStartsAtFieldStart)
        val touches = touchesOf(typed, taps, layout, spatial)
        val candidate = engine.revise(
            context, typed, committed, next, touches,
            isProtected = { word -> (protectsShorthand && ChatShorthand.contains(word)) || isProtected(word) },
            isBlocked = blockedPredicate(blockedWords),
        ) ?: return null
        val request = LatinScoringRequest(
            rawInput = typed,
            primaryLocale = primaryLocale,
            languages = languages,
            textBeforeSelection = "",
            maxCandidateCount = 1,
            policy = dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectPolicy(),
        )
        if (standsAsTyped && rule == RealWordRule.CONFUSION_PAIRS && isConfusionPartner(languages, typedWord, candidate.word)) {
            return null
        }
        val scored = toScored(candidate, typed, request, isLiteral = candidate.word == TextFolding.normalizeWord(typed), true)
        return scored.takeIf { it.text != committed }
    }

    /**
     * Main's name signal (FR-06), as in the noisy-channel scorer: a capitalized word in mid-sentence is probably a
     * name, an all-caps word an acronym, so keeping either as typed gets [CapitalizedLiteralBonus] nats.
     */
    private fun nameBonus(rawInput: String, textBeforeSelection: String): Double {
        val letters = rawInput.filter { it.isLetter() }
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) return AllCapsLiteralBonus
        if (rawInput.firstOrNull()?.isUpperCase() != true) return 0.0
        val before = textBeforeSelection.removeSuffix(rawInput).trimEnd()
        val isSentenceStart = before.isEmpty() || before.last() in ".!?\n"
        return if (isSentenceStart) 0.0 else CapitalizedLiteralBonus
    }

    /** Whether [typed] and [replacement] are two words of one [ConfusionSets] group of one of [languages]. */
    private fun isConfusionPartner(languages: List<LatinScoringLanguage>, typed: String, replacement: String): Boolean {
        val target = LatinText.normalizeDictionaryWord(replacement)
        val word = LatinText.normalizeDictionaryWord(typed)
        if (word == target) return false
        if (apostrophePairsFollowEngine && TextFolding.keyForm(word) == TextFolding.keyForm(target)) return false
        return languages.any { language -> target in ConfusionSets.alternatives(language.language, word) { true } }
    }

    /** Letters and apostrophes go to the engine; words with digits or symbols ("mp3", "e-mail") to [fallback]. */
    private fun isEngineInput(rawInput: String): Boolean {
        if (TextFolding.isKeyLetters(TextFolding.keyForm(TextFolding.normalizeWord(rawInput)))) return true
        return TextFolding.isWordLike(rawInput)
    }

    private fun blockedPredicate(blockedWords: Set<String>): ((String) -> Boolean)? {
        if (blockedWords.isEmpty()) return null
        return { word -> PossiblyOffensiveWords.isBlocked(LatinText.normalizeDictionaryWord(word), blockedWords) }
    }

    /**
     * The typed English pronoun ("i", "i'm") in its written form, when the engine has no other correction for it and
     * reads it as English in this context.
     */
    private fun pronounCaseFix(
        result: org.florisboard.lib.prediction.SuggestResult,
        rawInput: String,
        hooks: LatinScoringHooks,
        mainInput: String,
    ): Candidate? {
        val literal = result.literal ?: return null
        if (literal.kind != CandidateKind.EXACT || !literal.isCapitalized || literal.word != result.input) return null
        if (literal.language != EnglishLanguage || literal.word !in LatinText.EnglishPronounForms) return null
        if (rawInput.any { it.isUpperCase() } || hooks.isBlockedByUserPreference(mainInput)) return null
        return literal
    }

    private fun toScored(
        candidate: Candidate,
        rawInput: String,
        request: LatinScoringRequest,
        isLiteral: Boolean,
        isAutoCommit: Boolean,
    ): LatinScoredCandidate {
        val locale = request.languages.firstOrNull { it.language == candidate.language }?.locale ?: request.primaryLocale
        return LatinScoredCandidate(
            word = candidate.word,
            text = displayText(candidate, rawInput, isLiteral, locale),
            locale = locale,
            confidence = candidate.prob.coerceIn(0.0, 1.0),
            editDistance = candidate.edits,
            isAutoCommit = isAutoCommit,
        )
    }

    /**
     * The model's written form when it writes the word with capitals ("I'm", "Amsterdam"), then the case of the typed
     * input: all capitals (two or more letters) give all capitals, a typed first capital gives a capital first letter.
     * The typed literal keeps an explicit mixed case such as "McFly".
     */
    private fun displayText(candidate: Candidate, rawInput: String, isLiteral: Boolean, locale: Locale): String {
        val typed = rawInput.trim()
        val letters = typed.filter { it.isLetter() }
        val mixedCase = letters.length >= 2 && !letters.all { it.isUpperCase() } && letters.drop(1).any { it.isUpperCase() }
        if (isLiteral && mixedCase) return typed
        val base = if (candidate.isCapitalized) candidate.displayForm else candidate.word
        return when {
            letters.length >= 2 && letters.all { it.isUpperCase() } -> base.uppercase(locale)
            typed.firstOrNull { it.isLetter() }?.isUpperCase() == true && base.none { it.isUpperCase() } ->
                base.replaceFirstChar { it.titlecase(locale) }
            else -> base
        }
    }

    private fun derive(base: PredictionEngine, spatial: SpatialModel?, config: EngineConfig, overlay: PersonalOverlay?): PredictionEngine {
        val key = DerivedKey(base, spatial, config, overlay)
        synchronized(derived) { derived[key]?.let { return it } }
        var engine = base
        if (spatial != null) engine = engine.withGeometry(spatial)
        if (config != engine.config) engine = engine.withConfig(config)
        if (overlay != null) engine = engine.withPersonalOverlay(overlay)
        synchronized(derived) { derived[key] = engine }
        return engine
    }

    /**
     * The engine's spatial model of [geometry]: key centres in key widths, rows [KeyGeometry.rowHeight] apart, divided
     * into key pitches. Null without a geometry or when a letter `a`-`z` is missing (the engine keeps QWERTY).
     */
    private fun spatialOf(geometry: KeyGeometry?): SpatialModel? {
        if (geometry == null) return null
        spatialCache?.let { if (it.geometry === geometry) return it.spatial }
        val rowHeight = geometry.rowHeight
        val spatial = if (rowHeight > 0.0 && !rowHeight.isInfinite()) {
            val centres = HashMap<Char, Pair<Double, Double>>(32)
            var complete = true
            for (ch in 'a'..'z') {
                val centre = geometry.center(ch)
                if (centre == null || centre.first.isNaN() || centre.second.isNaN()) {
                    complete = false
                    break
                }
                centres[ch] = centre
            }
            if (complete) SpatialModel.fromKeyCentres(centres, pitchX = 1.0, pitchY = rowHeight) else null
        } else {
            null
        }
        spatialCache = SpatialCache(geometry, spatial)
        return spatial
    }

    /**
     * One touch point per letter of [rawInput] in the frame of [spatial], or null when the taps do not match the letters
     * one to one or the word has letters outside `a`-`z`. A tap without a position sits at the centre of its key.
     */
    private fun touchesOf(rawInput: String, taps: List<LatinTap>?, geometry: KeyGeometry?, spatial: SpatialModel?): List<TouchPoint>? {
        if (taps == null || geometry == null || spatial == null) return null
        val typed = rawInput.trim()
        val norm = TextFolding.normalizeWord(typed)
        if (!TextFolding.isKeyLetters(norm) || taps.size != norm.length || typed.length != norm.length) return null
        val rowHeight = geometry.rowHeight
        return taps.mapIndexed { i, tap ->
            if (tap.x.isNaN() || tap.y.isNaN()) spatial.centre(norm[i]) else TouchPoint(tap.x, tap.y / rowHeight)
        }
    }
}
