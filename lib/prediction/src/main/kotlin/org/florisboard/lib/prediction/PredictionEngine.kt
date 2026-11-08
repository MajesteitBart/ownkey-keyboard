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

package org.florisboard.lib.prediction

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * The words before the input, used as language-model context.
 *
 * @property words Previous words of the current sentence, most recent last, normalised lower case
 *  ([TextFolding.normalizeWord]).
 * @property startsSentence True when [words] begins at a sentence or field start, so the sentence-start token
 *  precedes `words[0]` (or the input itself when [words] is empty). When false, the context before `words[0]` is
 *  unknown.
 * @property languageHintWords Earlier words (previous sentences) used only as evidence for the language mix, never
 *  as n-gram context.
 */
data class PredictionContext(
    val words: List<String> = emptyList(),
    val startsSentence: Boolean = true,
    val languageHintWords: List<String> = emptyList(),
) {
    companion object {
        /** The start of an empty field. */
        val FieldStart = PredictionContext()
    }
}

enum class CandidateKind {
    /** A lexicon word whose key form equals the input. */
    EXACT,
    /** A longer word that starts with the input, or with a corrected form of it. */
    COMPLETION,
    /** A different full word within the edit budget. */
    CORRECTION,
    /** A prediction for empty input. */
    NEXT_WORD,
    /** The typed input itself when it is not a lexicon word. */
    LITERAL,
}

/**
 * One suggestion.
 *
 * @property word Surface form to commit (lower case; see [isCapitalized]).
 * @property keyForm [TextFolding.keyForm] of [word].
 * @property language Language whose model gives the word the highest weighted probability.
 * @property logPosterior Natural log of [prob].
 * @property prob Posterior over all candidates of this call (including the unknown literal, if any).
 * @property editCost Channel cost `-ln P(input | word)` in nats (0 for next-word predictions).
 * @property edits Number of edit operations on the cheapest alignment.
 * @property isCapitalized The language model writes this word with capitals (an initial capital, all capitals or
 *  mixed case). Sentence-start capitalisation is left to the caller.
 * @property isPersonal The word comes from the [PersonalOverlay] or is a protected literal.
 * @property displayForm The written form to show and commit: [word] with the model's letter case when
 *  [isCapitalized] ("I'm", "Amsterdam", "iPhone"), otherwise [word] itself. Input case is left to the caller.
 */
data class Candidate(
    val word: String,
    val keyForm: String,
    val language: String,
    val kind: CandidateKind,
    val logPosterior: Double,
    val prob: Double,
    val editCost: Double,
    val edits: Int,
    val isCapitalized: Boolean,
    val isPersonal: Boolean = false,
    val displayForm: String = word,
)

/**
 * The best alternative to a committed word given the next word ([PredictionEngine.proposeRevision]), before the
 * thresholds of [PredictionEngine.revise] are applied.
 *
 * @property candidate The replacement (best surface of the best key form other than the committed word's).
 * @property margin Joint log-odds of [candidate]'s key form against the committed word's key form.
 * @property rightGain `ln P(next | candidate) - ln P(next | committed word)` in the language mixture.
 * @property extraCost Channel cost of the typed letters under [candidate] minus under the committed word (nats).
 * @property isShort The typed word has at most [EngineConfig.shortInputLength] letters.
 * @property committedClass How the committed word relates to the vocabularies.
 * @property passes Every condition of [PredictionEngine.revise] holds (margin, right gain, extra cost, taps, committed
 *  class); [PredictionEngine.revise] returns [candidate] exactly then.
 */
data class RevisionProposal(
    val candidate: Candidate,
    val margin: Double,
    val rightGain: Double,
    val extraCost: Double,
    val isShort: Boolean,
    val committedClass: CommittedClass,
    val passes: Boolean,
) {
    enum class CommittedClass {
        /** A dictionary word ([WordLanguageModel.isListedWord]) of some language. */
        LISTED,
        /** A valid word that no language lists: a name, a brand, chat slang. */
        VALID,
        /** In a vocabulary, but not a valid word. */
        NOT_VALID,
        /** The typed letters, in no vocabulary. */
        UNKNOWN,
    }
}

/**
 * Result of [PredictionEngine.suggest].
 *
 * @property input The normalised input.
 * @property candidates Candidates by descending posterior, deduplicated by surface form.
 * @property autocorrect The word to commit instead of the input when the user ends the word (space), or null.
 * @property literal The typed input as a candidate (a known [CandidateKind.EXACT] word or a
 *  [CandidateKind.LITERAL]); null for empty input.
 * @property isLiteralKnown The input's key form is a word in at least one language or the personal overlay, or the
 *  input is protected.
 * @property languageWeights Mixture weight per language used for this call.
 */
class SuggestResult internal constructor(
    val input: String,
    val keyForm: String,
    val candidates: List<Candidate>,
    val autocorrect: Candidate?,
    val literal: Candidate?,
    val isLiteralKnown: Boolean,
    val languageWeights: Map<String, Double>,
    private val config: EngineConfig,
) {
    /**
     * Ordered entries for the suggestion strip, at most [limit].
     *
     * Slot 0 is the [autocorrect] target if there is one (with [EngineConfig.autocorrectRankedSlot] it is ranked
     * by posterior instead, but never placed after index 2). The rest follow by posterior, one per key form,
     * optionally favouring longer words ([EngineConfig.displaySavingsWeight]). While an autocorrect is pending, the
     * literal input goes to [EngineConfig.pendingLiteralSlot] (inside the first three slots) so the user can keep
     * it. Without one, a known literal goes to index 3 when at least three other candidates have non-negligible
     * probability (tapping it would save no keystroke; space commits it) and keeps its rank otherwise, and an
     * unknown literal is not shown. Remaining room is filled with other surface forms of already shown key forms
     * ("één" after "een").
     */
    fun displaySlots(limit: Int = 8): List<Candidate> {
        if (limit <= 0) return emptyList()
        val ac = autocorrect
        val savingsWeight = config.displaySavingsWeight
        fun order(c: Candidate) = c.logPosterior + savingsWeight * ln(1.0 + maxOf(0, c.keyForm.length - keyForm.length))

        val rest = ArrayList<Candidate>()
        val restKeyForms = HashSet<String>()
        ac?.let { restKeyForms.add(it.keyForm) }
        for (c in candidates) {
            if (c.keyForm == keyForm && keyForm.isNotEmpty()) continue
            if (restKeyForms.add(c.keyForm)) rest.add(c)
        }
        if (savingsWeight != 0.0) rest.sortByDescending { order(it) }
        val main = ArrayList<Candidate>(rest.size + 1)
        main.addAll(rest)
        if (ac != null) {
            val at = if (config.autocorrectRankedSlot) minOf(2, rest.count { order(it) > order(ac) }) else 0
            main.add(at, ac)
        }

        val lit = literal?.takeIf { l -> ac == null || l.word != ac.word }
        val literalIndex = when {
            lit == null -> -1
            ac != null -> config.pendingLiteralSlot.coerceAtLeast(0)
            !isLiteralKnown -> -1
            rest.count { it.prob >= config.negligibleProb } >= 3 -> 3
            else -> -2 // natural rank
        }
        val out = ArrayList<Candidate>(limit)
        val seenKeyForms = HashSet<String>()
        val seenWords = HashSet<String>()
        var literalPlaced = lit == null || literalIndex == -1
        for (c in main) {
            if (out.size >= limit) break
            if (!literalPlaced && (out.size == literalIndex || (literalIndex == -2 && lit!!.prob >= c.prob))) {
                out.add(lit!!)
                literalPlaced = true
                if (out.size >= limit) break
            }
            out.add(c)
            seenKeyForms.add(c.keyForm)
            seenWords.add(c.word)
        }
        if (!literalPlaced && out.size < limit) out.add(lit!!)
        lit?.let { seenWords.add(it.word); seenKeyForms.add(it.keyForm) }
        if (out.size < limit) {
            for (c in candidates) {
                if (out.size >= limit) break
                if (c.word !in seenWords && c.keyForm in seenKeyForms && c.prob >= config.negligibleProb) {
                    out.add(c)
                    seenWords.add(c.word)
                }
            }
        }
        return out
    }

    override fun toString(): String = "SuggestResult(input=$input, autocorrect=${autocorrect?.word}, " +
        "slots=${displaySlots().map { it.word }})"
}

/**
 * Word prediction, completion and autocorrect over one or more languages.
 *
 * Every candidate `w` gets the score `ln P_mix(w | context) + alpha * ln P(input | w)`, where
 * `P_mix(w | ctx) = sum_l pi_l * P_l(w | ctx)` mixes the language models with per-call weights `pi` (a prior
 * favouring the primary language, updated by how well each model explains the context words), and the channel
 * term comes from the [Decoder] (key-code or touch-point spatial model plus edit costs). Scores are normalised
 * into a posterior with log-sum-exp. See [EngineConfig] for the tunables and [suggest] for the decision rules.
 * [revise] re-decides the previous word once the next one is committed (right-context revision).
 *
 * Models are indexed by position: index 0 is the primary language. User words come in through an optional
 * [PersonalOverlay] ([withPersonalOverlay]), never-correct words through the `isProtected` predicate of [suggest]
 * and words that must not be offered (possibly offensive words, see [WordBlocklist]) through its `isBlocked`
 * predicate.
 *
 * Scope: the channel models the 26 letter keys `a`-`z` on the given geometry (QWERTY by default). Input with other
 * letters (`ä`, `ö`, `ß`, `é`, ...) is matched and completed on its key form, but without touch points and never
 * autocorrected, so enable the engine only for languages typed on such a layout (nl, en) or pass real key centres.
 *
 * The public constructor builds a [Lexicon] per model, which sorts the whole vocabulary (tens of milliseconds per
 * 150k words on a desktop JVM, more on a phone): construct engines off the main thread. [fromLexicons] reuses
 * lexicons that were built before, so engines for several language orders (`nl, en` and `en, nl`) share them.
 * Instances are immutable and thread-safe; [withConfig], [withGeometry] and [withPersonalOverlay] reuse the
 * lexicons.
 */
class PredictionEngine private constructor(
    private val shared: Languages,
    private val geometry: SpatialModel,
    val config: EngineConfig,
    /** The user's words, or null. */
    val personalOverlay: PersonalOverlay?,
) {
    private class Lang(val model: WordLanguageModel, val lexicon: Lexicon)

    /** Models and lexicons shared between engines that differ only in configuration. */
    private class Languages(val list: List<Lang>)

    private val langs: List<Lang> get() = shared.list

    /** Decoding sources: the languages, then the personal overlay (if it has words) at [overlayIndex]. */
    private val sources: List<Lang> = personalOverlay?.takeIf { it.size > 0 }
        ?.let { langs + Lang(it.model, it.lexicon) } ?: langs
    private val overlayIndex = if (sources.size > langs.size) langs.size else -1

    private val spatial = geometry.withParameters(config.keySigma, config.touchSigma, config.spatialFloor)
    private val decoder = Decoder(config)
    private val logPriors = DoubleArray(langs.size) { if (it == 0) 0.0 else config.secondaryLanguageLogPrior }

    /** Language tags in model order (index 0 = primary). */
    val languages: List<String> = langs.map { it.model.language }

    /** Lexicons in model order. */
    val lexicons: List<Lexicon> get() = langs.map { it.lexicon }

    /** The spatial model with this configuration's channel parameters. */
    val spatialModel: SpatialModel get() = spatial

    /**
     * Builds lexicons for [models] (index 0 = primary language, at least one model) and the engine around them.
     * This sorts every vocabulary; call it off the main thread.
     */
    constructor(
        models: List<WordLanguageModel>,
        spatial: SpatialModel = SpatialModel.qwerty(),
        config: EngineConfig = EngineConfig(),
        personalOverlay: PersonalOverlay? = null,
    ) : this(Languages(buildLanguages(models)), spatial, config, personalOverlay)

    /** Same models and lexicons with another configuration. */
    fun withConfig(config: EngineConfig): PredictionEngine = PredictionEngine(shared, geometry, config, personalOverlay)

    /** Same models and lexicons with another keyboard geometry. */
    fun withGeometry(spatial: SpatialModel): PredictionEngine = PredictionEngine(shared, spatial, config, personalOverlay)

    /** Same models and lexicons with another personal overlay (null = none). Cheap; does not rebuild lexicons. */
    fun withPersonalOverlay(overlay: PersonalOverlay?): PredictionEngine = PredictionEngine(shared, geometry, config, overlay)

    /**
     * Suggestions for [input] (the letters typed so far for the current word) after [context].
     *
     * [input] is normalised with [TextFolding.normalizeWord]; empty input yields next-word predictions. [touches],
     * when given with one point per input character in key-pitch units, switch the channel to touch likelihoods.
     *
     * Autocorrect (applied by the caller when the word ends) considers full words only, with their posterior
     * among full words plus, for an unknown literal, the hypothesis that the user meant exactly what was typed:
     * - unknown literal: correct to the best key form `W` when `P(W) >= tauUnknown` (`tauUnknownShort` for short
     *   input), but never when it is shorter than `minUnknownAutocorrectLength`; words that start with exactly the
     *   typed letters count as competing explanations;
     * - known (valid) literal: correct when `ln P(W) - ln P(literal) >= deltaKnown` (`deltaKnownShort`), otherwise
     *   switch to another surface of the same key form ("dont" -> "don't") when the other surfaces beat the typed
     *   spelling by `surfaceSwitchMargin`; between dictionary words ([WordLanguageModel.isListedWord]) the margin
     *   shrinks by `deltaKnownLmSlope` per nat by which the language model prefers `W` beyond `deltaKnownLmOffset`,
     *   in context and without it, for cheap edits only (`deltaKnownLmMaxEditCost`, not below `deltaKnownFloor`);
     * - literal in a vocabulary but not a valid word ([WordLanguageModel.isValidWord] false for every surface of its
     *   key form, with [EngineConfig.validityAware]): like a known literal with the margin `deltaInvalid`
     *   (`deltaInvalidShort`), or like an unknown one with `invalidAsUnknown`, where its own probability still
     *   competes as the hypothesis "keep it";
     * - targets are valid words only ([EngineConfig.validTargetsOnly]); other words keep their probability mass;
     * - protected literal ([isProtected] returns true for the normalised input): counts as a known word and is never
     *   replaced, not even by another surface;
     * - never for input with characters outside `a`-`z` or shorter than `minAutocorrectLength`.
     * The surface of the chosen key form is its most probable one in context. Input longer than
     * `maxDecodeLength` is only looked up exactly.
     *
     * Blocked words ([isBlocked] returns true for the normalised word) are left out of [SuggestResult.candidates],
     * next-word predictions and autocorrect targets, unless the user typed exactly that word. They still count as
     * explanations of the input, so a blocked word that explains a typo best leaves the typo as typed instead of
     * correcting it to the next best word.
     *
     * Two optional inputs come from the user's own data and are off (neutral) by default:
     * - [contextBoost] adds a non-negative log bonus to a word's language-model score (for example how often the user
     *   wrote it after the previous word); it applies to corrections, completions and next-word predictions alike.
     * - [literalBonus] (nats, non-negative) makes keeping the typed letters more likely, for example for a word the
     *   user typed and kept several times: it is added to the unknown-word hypothesis and to every margin a known
     *   literal must be beaten by.
     */
    fun suggest(
        context: PredictionContext,
        input: String,
        touches: List<TouchPoint>? = null,
        isProtected: ((String) -> Boolean)? = null,
        isBlocked: ((String) -> Boolean)? = null,
        contextBoost: ((String) -> Double)? = null,
        literalBonus: Double = 0.0,
    ): SuggestResult {
        val norm = TextFolding.normalizeWord(input.trim())
        val kf = TextFolding.keyForm(norm)
        val ctx = contextIds(context)
        val weights = languageWeights(context, ctx)
        val logWeights = DoubleArray(weights.size) { ln(weights[it]) }
        val weightMap = languages.indices.associate { languages[it] to weights[it] }
        if (kf.isEmpty()) return nextWord(ctx, logWeights, weightMap, isBlocked, contextBoost)
        val offered = offeredPredicate(norm, isBlocked)
        val interp = interpret(norm, kf, ctx, logWeights, touches, isProtected, contextBoost, literalBonus.coerceAtLeast(0.0))
        val all = interp.all
        val protectedAgg = interp.protectedAgg
        val literalKnown = interp.literalKnown

        val candidates = ArrayList<Candidate>(minOf(all.size, config.maxCandidates))
        val dropInvalid = config.dropInvalidSuggestions
        for (a in all) {
            if (candidates.size >= config.maxCandidates) break
            if (!offered(a)) continue
            // Invalid words still explain the input, but are only shown when typed exactly (as the literal).
            if (dropInvalid && !a.isValid && a.keyForm != kf) continue
            candidates.add(interp.toCandidate(a))
        }

        val autocorrectAgg = if (protectedAgg != null) {
            null
        } else {
            val literalClass = when {
                !literalKnown -> LiteralClass.UNKNOWN
                interp.literalValid -> LiteralClass.VALID
                else -> LiteralClass.INVALID
            }
            decideAutocorrect(norm, kf, all, literalClass, interp.rawUnknownScore, offered, logWeights, interp.literalBonus)
        }
        val autocorrect = autocorrectAgg?.let { a ->
            val kind = if (a.keyForm == kf) CandidateKind.EXACT else CandidateKind.CORRECTION
            candidates.firstOrNull { it.word == a.surface && it.kind == kind } ?: interp.toCandidate(a, kind, a.fullScore)
        }

        val literal = if (literalKnown) {
            val own = interp.literalAgg()!!
            candidates.firstOrNull { it.word == own.surface && it.kind == CandidateKind.EXACT }
                ?: interp.toCandidate(own, CandidateKind.EXACT, own.fullScore)
        } else {
            val lp = interp.unknownScore - interp.lse
            Candidate(
                word = norm, keyForm = kf, language = languages.first(), kind = CandidateKind.LITERAL,
                logPosterior = lp, prob = exp(lp), editCost = interp.literalCost.toDouble(), edits = 0,
                isCapitalized = false,
            )
        }
        return SuggestResult(norm, kf, candidates, autocorrect, literal, literalKnown, weightMap, config)
    }

    /**
     * Right-context revision: once the user has committed the next word [next] (with a separator) after the previous
     * word, decides whether the previous word should become a different word. Returns the replacement, or null to
     * leave the text as it is.
     *
     * [context] holds the words before the previous word (as for [suggest] when it was typed), [typed] the letters the
     * user typed for it, [committed] the word now in the text (the typed letters or their autocorrection) and
     * [touches] the touch points of [typed], if any. Every interpretation `w` of [typed] that [suggest] would consider
     * as a full word (the [EngineConfig.reviseCandidates] best by left-context score, plus the committed word, plus
     * the typed letters as an unknown word when no vocabulary has them) gets the joint score
     *
     *     ln P_mix(w | left context) + ln P_mix(next | w, left context) - alpha * channel(typed | w)
     *
     * where the second factor mixes the languages with the weights of the left context. Scores are summed per key
     * form, and the best key form other than the committed one replaces it when
     * - it wins by [EngineConfig.reviseMargin] ([EngineConfig.reviseMarginShort] for short [typed]) in log-odds,
     * - [next] alone prefers it by at least [EngineConfig.reviseMinRightGain] nats,
     * - its channel cost exceeds the committed word's by at most [EngineConfig.reviseMaxExtraCost] (a slip of the hand),
     * - with [touches], when it only substitutes letters of a valid word typed exactly as committed, by at most
     *   [EngineConfig.reviseMaxTouchSubstitutionCost] (the taps lay off-centre towards the replacement letters), and
     * - the committed word is not a short word that is in a vocabulary but not valid, unless
     *   [EngineConfig.reviseInvalidShort] (chat shorthand such as "wss" that autocorrect kept on purpose).
     * [keepBonus] (nats, non-negative) raises the margin, like the `literalBonus` of [suggest] for a word the user
     * tends to keep. The replacement is the key form's best valid surface ([EngineConfig.validTargetsOnly]; the typed letters
     * themselves are always allowed when a vocabulary has them), never a blocked word other than the typed letters,
     * never another surface of the committed word's key form, and never the typed letters as an unknown word. Nothing
     * is revised when autocorrect or revision is off, when [typed] (with its letter case) has a capital that a
     * sentence start does not explain (a name or acronym the user wrote that way), when [typed] or
     * [committed] is protected ([isProtected], for example never-correct and user dictionary words), when [typed] has
     * characters outside `a`-`z`, is shorter than [EngineConfig.minAutocorrectLength] or longer than
     * [EngineConfig.maxDecodeLength], or when [committed] is not an interpretation of [typed].
     *
     * The caller decides where a revision may apply (the same sentence, words typed letter by letter, no edits in
     * between); see the app's commit path.
     */
    fun revise(
        context: PredictionContext,
        typed: String,
        committed: String,
        next: String,
        touches: List<TouchPoint>? = null,
        isProtected: ((String) -> Boolean)? = null,
        isBlocked: ((String) -> Boolean)? = null,
        keepBonus: Double = 0.0,
    ): Candidate? = proposeRevision(context, typed, committed, next, touches, isProtected, isBlocked, keepBonus)
        ?.takeIf { it.passes }?.candidate

    /**
     * The best replacement [revise] considered, with its margin and right-context gain, whether or not it meets the
     * thresholds (for tuning and diagnostics); null where [revise] does not consider any.
     */
    fun proposeRevision(
        context: PredictionContext,
        typed: String,
        committed: String,
        next: String,
        touches: List<TouchPoint>? = null,
        isProtected: ((String) -> Boolean)? = null,
        isBlocked: ((String) -> Boolean)? = null,
        keepBonus: Double = 0.0,
    ): RevisionProposal? = reviseInternal(context, typed, committed, next, touches, isProtected, isBlocked, trace = null, keepBonus)

    /**
     * [revise] with a line per scored interpretation (surface, left-context score, right-context log probability,
     * joint score, valid/listed flags) and the decision, for diagnostics such as the simulator's `--explain`.
     */
    fun explainRevision(
        context: PredictionContext,
        typed: String,
        committed: String,
        next: String,
        touches: List<TouchPoint>? = null,
    ): List<String> {
        val lines = ArrayList<String>()
        val p = reviseInternal(context, typed, committed, next, touches, null, null, trace = { lines.add(it) })
        lines.add(
            if (p == null) {
                "revision: none considered"
            } else {
                String.format(
                    java.util.Locale.ROOT, "revision: %s %s (margin %.2f, right gain %.2f, extra cost %.2f, committed %s)",
                    if (p.passes) "->" else "not", p.candidate.word, p.margin, p.rightGain, p.extraCost, p.committedClass,
                )
            },
        )
        return lines
    }

    private fun reviseInternal(
        context: PredictionContext,
        typed: String,
        committed: String,
        next: String,
        touches: List<TouchPoint>?,
        isProtected: ((String) -> Boolean)?,
        isBlocked: ((String) -> Boolean)?,
        trace: ((String) -> Unit)?,
        keepBonus: Double = 0.0,
    ): RevisionProposal? {
        if (!config.autocorrectEnabled || !config.reviseEnabled) return null
        if (hasExplicitCapitals(typed.trim(), context)) return null
        val norm = TextFolding.normalizeWord(typed.trim())
        val kf = TextFolding.keyForm(norm)
        val committedNorm = TextFolding.normalizeWord(committed.trim())
        val nextNorm = TextFolding.normalizeWord(next.trim())
        if (!TextFolding.isKeyLetters(norm) || kf.length < config.minAutocorrectLength || kf.length > config.maxDecodeLength) return null
        if (committedNorm.isEmpty() || TextFolding.keyForm(nextNorm).isEmpty()) return null
        if (isProtected != null && (isProtected(norm) || isProtected(committedNorm))) return null
        val ctx = contextIds(context)
        val weights = languageWeights(context, ctx)
        val logWeights = DoubleArray(weights.size) { ln(weights[it]) }
        val interp = interpret(norm, kf, ctx, logWeights, touches, isProtected = null)
        val offered = offeredPredicate(norm, isBlocked)

        // Interpretations: the best full words by left-context score, the committed word and the typed word.
        val full = interp.all.filter { a -> a.fullScore > Double.NEGATIVE_INFINITY }.sortedByDescending { a -> a.fullScore }
        val hyps = LinkedHashSet<Agg>(full.take(config.reviseCandidates.coerceAtLeast(1)))
        val committedAgg = interp.aggs[committedNorm]?.takeIf { a -> a.fullScore > Double.NEGATIVE_INFINITY }
        committedAgg?.let { a -> hyps.add(a) }
        interp.literalAgg()?.let { a -> hyps.add(a) }
        val committedIsUnknownLiteral = committedAgg == null && !interp.literalKnown && committedNorm == norm
        if (committedAgg == null && !committedIsUnknownLiteral) return null
        val committedKf = committedAgg?.keyForm ?: kf

        val nextIds = IntArray(langs.size) { l -> idOf(langs[l], nextNorm) }
        fun right(prevIds: IntArray?): Double {
            var best = Double.NEGATIVE_INFINITY
            val terms = DoubleArray(langs.size)
            for ((l, lang) in langs.withIndex()) {
                val nid = nextIds[l]
                val prev1 = prevIds?.get(l) ?: -1
                val prev2 = if (prev1 >= 0) ctx[l].prev1 else -1
                terms[l] = logWeights[l] + if (nid >= 0) lang.model.logProb(nid, prev1, prev2) else config.oovLogProb
                if (terms[l] > best) best = terms[l]
            }
            var sum = 0.0
            for (t in terms) sum += exp(t - best)
            return best + ln(sum)
        }

        // Joint score per interpretation and per key form.
        val joint = HashMap<Agg, Double>(hyps.size * 2)
        val rightOf = HashMap<Agg, Double>(hyps.size * 2)
        val keyFormMass = HashMap<String, Double>()
        fun addMass(keyForm: String, score: Double) {
            keyFormMass[keyForm] = keyFormMass[keyForm]?.let { m -> logAdd(m, score) } ?: score
        }
        for (a in hyps) {
            val r = right(IntArray(langs.size) { l -> a.ids[l] })
            rightOf[a] = r
            joint[a] = a.fullScore + r
            addMass(a.keyForm, a.fullScore + r)
        }
        val unknownRight = if (!interp.literalKnown) right(null) else Double.NEGATIVE_INFINITY
        if (!interp.literalKnown) addMass(kf, interp.rawUnknownScore + unknownRight)
        val committedMass = keyFormMass[committedKf] ?: return null
        val committedRight = committedAgg?.let { a -> rightOf.getValue(a) } ?: unknownRight
        if (trace != null) {
            for (a in hyps.sortedByDescending { a -> joint.getValue(a) }) {
                trace(String.format(
                    java.util.Locale.ROOT, "  %-14s left %7.2f  right %7.2f  joint %7.2f  cost %5.2f  %s%s%s",
                    a.surface, a.fullScore, rightOf.getValue(a), joint.getValue(a), a.fullCost,
                    if (a.isValid) "valid" else "not-valid", if (a.isListed) " listed" else "", if (a === committedAgg) "  (committed)" else "",
                ))
            }
            if (!interp.literalKnown) {
                trace(String.format(java.util.Locale.ROOT, "  %-14s left %7.2f  right %7.2f  joint %7.2f  (unknown literal)",
                    norm, interp.rawUnknownScore, unknownRight, interp.rawUnknownScore + unknownRight))
            }
        }

        // The best replacement key form and its best allowed surface. The typed letters as an unknown word are never
        // a replacement: their right-context factor has no n-gram context to compare.
        var target: Agg? = null
        var targetMass = Double.NEGATIVE_INFINITY
        for ((keyForm, mass) in keyFormMass) {
            if (keyForm == committedKf || mass <= targetMass) continue
            val surface = hyps.filter { a ->
                a.keyForm == keyForm && offered(a) && (a.surface == norm || !config.validTargetsOnly || a.isValid)
            }.maxByOrNull { a -> joint.getValue(a) } ?: continue
            target = surface
            targetMass = mass
        }
        if (target == null) return null
        val short = kf.length <= config.shortInputLength
        val margin = targetMass - committedMass
        val rightGain = rightOf.getValue(target) - committedRight
        val committedCost = committedAgg?.fullCost ?: interp.literalCost
        val extraCost = (target.fullCost - committedCost).toDouble()
        // Taps show which keys the user aimed at: a valid word typed exactly is only respelled with other keys when the
        // taps lay off-centre towards them (see EngineConfig.reviseMaxTouchSubstitutionCost).
        val usedTouches = touches != null && touches.size == kf.length && norm == kf
        val tapsContradict = usedTouches && committedAgg != null && committedAgg.isValid && committedKf == kf &&
            isSubstitution(kf, target.keyForm) && extraCost > config.reviseMaxTouchSubstitutionCost
        val committedClass = when {
            committedAgg == null -> RevisionProposal.CommittedClass.UNKNOWN
            committedAgg.isListed -> RevisionProposal.CommittedClass.LISTED
            committedAgg.isValid -> RevisionProposal.CommittedClass.VALID
            else -> RevisionProposal.CommittedClass.NOT_VALID
        }
        // A short word that is not valid has almost no n-gram successors, so the right context cannot argue against it
        // (see EngineConfig.reviseInvalidShort).
        val keptInvalidShort = short && committedClass == RevisionProposal.CommittedClass.NOT_VALID && !config.reviseInvalidShort
        // A valid word the user typed exactly as it stands (no autocorrection changed its letters) is never revised
        // unless reviseTypedValidWords: replacing a correctly spelled word is a real-word correction.
        val keptTypedValid = !config.reviseTypedValidWords && committedNorm == norm &&
            (committedClass == RevisionProposal.CommittedClass.LISTED || committedClass == RevisionProposal.CommittedClass.VALID)
        val passes = margin >= (if (short) config.reviseMarginShort else config.reviseMargin) + keepBonus.coerceAtLeast(0.0) &&
            rightGain >= config.reviseMinRightGain && extraCost <= config.reviseMaxExtraCost && !tapsContradict &&
            !keptInvalidShort && !keptTypedValid

        // Posterior over the interpretations scored here.
        var lse = Double.NEGATIVE_INFINITY
        for (m in keyFormMass.values) lse = logAdd(lse, m)
        val kind = if (target.keyForm == kf) CandidateKind.EXACT else CandidateKind.CORRECTION
        val candidate = interp.toCandidate(target, kind, joint.getValue(target), lse)
        return RevisionProposal(candidate, margin, rightGain, extraCost, short, committedClass, passes)
    }

    /**
     * True when [typed] has a capital that a sentence start does not explain: a capital after the first letter
     * ("NS", "iPhone") or a first capital in the middle of a sentence ("met Mees en"). The user wrote the word that way
     * on purpose (a name, an acronym, a title-case field), so it is never revised.
     */
    private fun hasExplicitCapitals(typed: String, context: PredictionContext): Boolean {
        val letters = typed.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        if (letters.drop(1).any { it.isUpperCase() }) return true
        val atSentenceStart = context.startsSentence && context.words.isEmpty()
        return letters[0].isUpperCase() && !atSentenceStart
    }

    /** [a] and [b] have the same length and differ by other letters, not by one swap of neighbouring letters. */
    private fun isSubstitution(a: String, b: String): Boolean {
        if (a.length != b.length || a == b) return false
        val diff = a.indices.filter { a[it] != b[it] }
        val isSwap = diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
        return !isSwap
    }

    /** The typed word itself is always offered, blocked or not. */
    private fun offeredPredicate(norm: String, isBlocked: ((String) -> Boolean)?): (Agg) -> Boolean =
        if (isBlocked == null) { _ -> true } else { a -> a.surface == norm || !isBlocked(a.surface) }

    /** Every interpretation of one non-empty input: decoded words with their scores and the literal hypotheses. */
    private inner class Interpretation(
        val norm: String,
        val keyForm: String,
        val aggs: HashMap<String, Agg>,
        /** By descending score, then surface. */
        val all: List<Agg>,
        val lse: Double,
        val literalKnown: Boolean,
        val literalValid: Boolean,
        val rawUnknownScore: Double,
        val unknownScore: Double,
        val protectedAgg: Agg?,
        val literalCost: Float,
        /** See the `literalBonus` parameter of [suggest]. */
        val literalBonus: Double = 0.0,
    ) {
        /** The known literal: the protected word, the typed surface or another surface of its key form; else null. */
        fun literalAgg(): Agg? {
            if (!literalKnown) return null
            return protectedAgg
                ?: aggs[norm]?.takeIf { it.fullCost < INF }
                ?: all.first { it.keyForm == keyForm && it.fullCost < INF }
        }

        fun toCandidate(a: Agg, kind: CandidateKind = a.kind(keyForm), score: Double = a.score, logNorm: Double = lse): Candidate {
            val lp = score - logNorm
            val cost = if (kind == CandidateKind.COMPLETION) a.complCost else a.fullCost
            val edits = if (kind == CandidateKind.COMPLETION) a.complEdits else a.fullEdits
            return Candidate(
                word = a.surface, keyForm = a.keyForm, language = langs[a.bestLang].model.language, kind = kind,
                logPosterior = lp, prob = exp(lp), editCost = cost.toDouble(), edits = edits,
                isCapitalized = isCapitalized(a), isPersonal = a.isPersonal, displayForm = displayForm(a),
            )
        }
    }

    private fun interpret(
        norm: String,
        kf: String,
        ctx: List<LangContext>,
        logWeights: DoubleArray,
        touches: List<TouchPoint>?,
        isProtected: ((String) -> Boolean)?,
        contextBoost: ((String) -> Double)? = null,
        literalBonus: Double = 0.0,
    ): Interpretation {
        val n = kf.length
        val exactOnly = n > config.maxDecodeLength
        val maxEdits = if (n <= config.shortInputLength) config.maxEditsShort else config.maxEdits
        val useTouches = touches != null && touches.size == n && norm == kf
        val channel = Channel.build(kf, spatial, if (useTouches) touches else null)
        val aggs = HashMap<String, Agg>(256)
        val hits = DecodeResult()
        val topBuf = IntArray(config.completionsPerSubtree.coerceAtLeast(1))
        for ((l, lang) in sources.withIndex()) {
            val lex = lang.lexicon
            if (exactOnly) {
                // The search would allocate (n + 1)^2 cells for an answer that can only be the exact key form.
                val pos = lex.find(kf)
                if (pos < 0) continue
                for (g in 0 until lex.groupSize(pos)) {
                    val agg = aggFor(aggs, lex, l, lex.groupId(pos, g), pos)
                    if (channel.literalCost < agg.fullCost) {
                        agg.fullCost = channel.literalCost
                        agg.fullEdits = 0
                    }
                }
                continue
            }
            decoder.decode(lex, channel, maxEdits, hits)
            for (h in 0 until hits.fullCount) {
                val pos = hits.fullPos[h]
                val cost = hits.fullCost[h]
                for (g in 0 until lex.groupSize(pos)) {
                    val agg = aggFor(aggs, lex, l, lex.groupId(pos, g), pos)
                    if (cost < agg.fullCost) {
                        agg.fullCost = cost
                        agg.fullEdits = hits.fullEdits[h]
                    }
                }
            }
            if (hits.subtreeCount > 0) expandCompletions(l, lang, ctx.getOrNull(l), hits, aggs, topBuf)
        }

        // A protected literal is a known word with its typed spelling, even if no lexicon has it.
        val protectedAgg = if (isProtected != null && isProtected(norm)) {
            aggs.getOrPut(norm) { Agg(norm, kf, sources.size) }.also {
                if (channel.literalCost < it.fullCost) {
                    it.fullCost = channel.literalCost
                    it.fullEdits = 0
                }
            }
        } else {
            null
        }

        // Language-model scores.
        val alpha = config.alpha
        val all = ArrayList<Agg>(aggs.size)
        for (agg in aggs.values) {
            scoreLm(agg, ctx, logWeights)
            if (contextBoost != null) agg.logPmix += contextBoost(agg.surface).coerceAtLeast(0.0)
            if (agg === protectedAgg && !agg.isPersonal && agg.ids.all { it < 0 }) {
                agg.logPmix = logAdd(agg.logPmix, config.personalWordLogProb)
                agg.isPersonal = true
            }
            if (agg === protectedAgg) {
                agg.isValid = true
                agg.isListed = false
            }
            val fullScore = if (agg.fullCost < INF) agg.logPmix - alpha * agg.fullCost else Double.NEGATIVE_INFINITY
            val complScore = if (agg.complCost < INF) {
                agg.logPmix - alpha * agg.complCost + config.completionLogPenalty - config.completionEditPenalty * agg.complEdits
            } else {
                Double.NEGATIVE_INFINITY
            }
            agg.fullScore = fullScore
            agg.complScore = complScore
            agg.isCompletion = complScore > fullScore
            agg.score = max(fullScore, complScore)
            all.add(agg)
        }
        val literalKnown = protectedAgg != null || aggs.values.any { it.keyForm == kf && it.fullCost < INF }
        // A literal whose key form has no valid surface ("grea", "mischien") is replaced more readily (deltaInvalid).
        val literalValid = protectedAgg != null || !config.validityAware ||
            aggs.values.any { it.keyForm == kf && it.fullCost < INF && it.isValid }
        val rawUnknownScore = config.unknownWordLogProb + config.unknownWordPerLetterLogProb * n - alpha * channel.literalCost +
            literalBonus
        val unknownScore = if (literalKnown) Double.NEGATIVE_INFINITY else rawUnknownScore

        // Posterior over every interpretation.
        var maxScore = unknownScore
        for (a in all) if (a.score > maxScore) maxScore = a.score
        var sum = if (unknownScore > Double.NEGATIVE_INFINITY) exp(unknownScore - maxScore) else 0.0
        for (a in all) sum += exp(a.score - maxScore)
        val lse = maxScore + ln(sum)
        all.sortWith(compareByDescending<Agg> { it.score }.thenBy { it.surface })
        return Interpretation(
            norm, kf, aggs, all, lse, literalKnown, literalValid, rawUnknownScore, unknownScore, protectedAgg,
            channel.literalCost, literalBonus,
        )
    }

    // ------------------------------------------------------------------------------------------------ internals

    private class Agg(val surface: String, val keyForm: String, languages: Int) {
        val ids = IntArray(languages) { -1 }
        var fullCost = INF
        var fullEdits = 0
        var complCost = INF
        var complEdits = 0
        var logPmix = 0.0
        var bestLang = 0
        var score = 0.0
        var fullScore = Double.NEGATIVE_INFINITY
        var complScore = Double.NEGATIVE_INFINITY
        var isCompletion = false
        var isPersonal = false
        /** A valid word in at least one language, a personal word or a protected literal. */
        var isValid = true
        /** A dictionary word ([WordLanguageModel.isListedWord]) of at least one language and not a personal word. */
        var isListed = false

        fun kind(inputKeyForm: String): CandidateKind = when {
            isCompletion -> CandidateKind.COMPLETION
            keyForm == inputKeyForm -> CandidateKind.EXACT
            else -> CandidateKind.CORRECTION
        }
    }

    /** Context ids per language: `prev1`, `prev2`, then for every evidence word its id and context. */
    private class LangContext(
        val prev1: Int,
        val prev2: Int,
        val evidenceIds: IntArray,
        val evidencePrev1: IntArray,
        val evidencePrev2: IntArray,
    ) {
        /** Context successors as (lexicon positions ascending, ids), filled lazily once per call. */
        var successors: Pair<IntArray, IntArray>? = null
    }

    private fun aggFor(aggs: HashMap<String, Agg>, lex: Lexicon, l: Int, id: Int, pos: Int): Agg {
        val surface = lex.model.word(id)
        val agg = aggs.getOrPut(surface) { Agg(surface, lex.keyForm(pos), sources.size) }
        agg.ids[l] = id
        return agg
    }

    private fun isCapitalized(a: Agg): Boolean {
        val id = a.ids[a.bestLang]
        if (id >= 0) return langs[a.bestLang].model.isCapitalized(id)
        val oid = if (overlayIndex >= 0) a.ids[overlayIndex] else -1
        return oid >= 0 && sources[overlayIndex].model.isCapitalized(oid)
    }

    /** The written form from the same model that [isCapitalized] asks; the surface itself when it is lower case. */
    private fun displayForm(a: Agg): String {
        val id = a.ids[a.bestLang]
        if (id >= 0) {
            val model = langs[a.bestLang].model
            return if (model.isCapitalized(id)) model.displayForm(id) else a.surface
        }
        val oid = if (overlayIndex >= 0) a.ids[overlayIndex] else -1
        if (oid < 0) return a.surface
        val overlay = sources[overlayIndex].model
        return if (overlay.isCapitalized(oid)) overlay.displayForm(oid) else a.surface
    }

    private fun idOf(lang: Lang, word: String): Int {
        val norm = TextFolding.normalizeWord(word)
        val id = lang.model.wordId(norm)
        if (id >= 0) return id
        val pos = lang.lexicon.find(TextFolding.keyForm(norm))
        return if (pos >= 0) lang.lexicon.groupId(pos, 0) else -1
    }

    private fun contextIds(context: PredictionContext): List<LangContext> {
        val words = context.words
        val hints = context.languageHintWords
        val k = config.languageEvidenceWords.coerceAtLeast(0)
        val evidenceStart = maxOf(0, hints.size + words.size - k)
        return langs.map { lang ->
            val bos = lang.model.bosId
            val all = (hints + words).map { idOf(lang, it) }
            // Context of each position: within hints a plain chain; within words (and for the input) never a hint,
            // but <s> before the first word if the words start a sentence, otherwise unknown.
            fun prevOf(j: Int, back: Int): Int {
                val p = j - back
                val wordStart = hints.size
                if (j >= wordStart && p < wordStart) {
                    return if (context.startsSentence && p == wordStart - 1) bos else -1
                }
                return if (p >= 0) all[p] else -1
            }
            val total = all.size
            val prev1 = prevOf(total, 1)
            val prev2 = prevOf(total, 2)
            val m = total - evidenceStart
            LangContext(
                prev1 = prev1,
                prev2 = prev2,
                evidenceIds = IntArray(m) { all[evidenceStart + it] },
                evidencePrev1 = IntArray(m) { prevOf(evidenceStart + it, 1) },
                evidencePrev2 = IntArray(m) { prevOf(evidenceStart + it, 2) },
            )
        }
    }

    private fun languageWeights(context: PredictionContext, ctx: List<LangContext>): DoubleArray {
        val l = langs.size
        val s = DoubleArray(l) { logPriors[it] }
        for (i in 0 until l) {
            val c = ctx[i]
            val m = c.evidenceIds.size
            var acc = 0.0
            for (j in 0 until m) {
                val id = c.evidenceIds[j]
                val lp = if (id >= 0) {
                    max(langs[i].model.logProb(id, c.evidencePrev1[j], c.evidencePrev2[j]), config.oovLogProb)
                } else {
                    config.oovLogProb
                }
                acc += config.languageEvidenceDecay.pow(m - 1 - j) * lp
            }
            s[i] += config.languageEvidenceWeight * acc
        }
        val mx = s.max()
        val w = DoubleArray(l) { exp(s[it] - mx) }
        var sum = w.sum()
        for (i in 0 until l) w[i] = max(w[i] / sum, config.minLanguageWeight)
        sum = w.sum()
        for (i in 0 until l) w[i] /= sum
        return w
    }

    /** Successor ids of the context sorted by lexicon position, for range queries during completion. */
    private fun successors(lang: Lang, c: LangContext?): Pair<IntArray, IntArray> {
        if (c == null) return NO_SUCCESSORS
        c.successors?.let { return it }
        val next = if (config.contextSuccessors > 0) lang.model.topNext(c.prev1, c.prev2, config.contextSuccessors) else emptyList()
        val pairs = next.mapNotNull { sw -> lang.lexicon.positionOf(sw.id).takeIf { it >= 0 }?.let { it to sw.id } }
            .sortedBy { it.first }
        val result = IntArray(pairs.size) { pairs[it].first } to IntArray(pairs.size) { pairs[it].second }
        c.successors = result
        return result
    }

    /** [c] is the source's context, or null for the personal overlay (no context successors). */
    private fun expandCompletions(
        l: Int,
        lang: Lang,
        c: LangContext?,
        hits: DecodeResult,
        aggs: HashMap<String, Agg>,
        topBuf: IntArray,
    ) {
        val lex = lang.lexicon
        val order = (0 until hits.subtreeCount).sortedBy { hits.subCost[it] }
        val bestCost = hits.subCost[order[0]]
        val (succPos, succIds) = successors(lang, c)
        var expanded = 0
        for (s in order) {
            val cost = hits.subCost[s]
            if (expanded >= config.maxCompletionSubtrees || cost > bestCost + config.completionBeam) break
            expanded++
            val from = if (hits.subOwnWord[s]) hits.subLo[s] + 1 else hits.subLo[s]
            val hi = hits.subHi[s]
            val edits = hits.subEdits[s]
            val count = lex.topByUnigram(from, hi, topBuf.size, topBuf)
            for (t in 0 until count) {
                val pos = topBuf[t]
                for (g in 0 until lex.groupSize(pos)) {
                    addCompletion(aggFor(aggs, lex, l, lex.groupId(pos, g), pos), cost, edits)
                }
            }
            var j = lowerBound(succPos, from)
            while (j < succPos.size && succPos[j] < hi) {
                addCompletion(aggFor(aggs, lex, l, succIds[j], succPos[j]), cost, edits)
                j++
            }
        }
    }

    private fun addCompletion(agg: Agg, cost: Float, edits: Int) {
        if (cost < agg.complCost) {
            agg.complCost = cost
            agg.complEdits = edits
        }
    }

    private fun scoreLm(agg: Agg, ctx: List<LangContext>, logWeights: DoubleArray) {
        var best = Double.NEGATIVE_INFINITY
        var bestLang = 0
        val terms = DoubleArray(langs.size)
        var valid = false
        var listed = false
        for ((l, lang) in langs.withIndex()) {
            var id = agg.ids[l]
            if (id < 0) {
                id = lang.model.wordId(agg.surface)
                agg.ids[l] = id
            }
            if (id >= 0) {
                if (!valid) valid = lang.model.isValidWord(id)
                if (!listed) listed = lang.model.isListedWord(id)
            }
            val c = ctx[l]
            val lp = if (id >= 0) lang.model.logProb(id, c.prev1, c.prev2) else config.oovLogProb
            terms[l] = logWeights[l] + lp
            if (terms[l] > best) {
                best = terms[l]
                bestLang = l
            }
        }
        var sum = 0.0
        for (t in terms) sum += exp(t - best)
        var logPmix = best + ln(sum)
        if (overlayIndex >= 0) {
            val overlay = personalOverlay!!
            var oid = agg.ids[overlayIndex]
            if (oid < 0) {
                oid = overlay.model.wordId(agg.surface)
                agg.ids[overlayIndex] = oid
            }
            if (oid >= 0) {
                // No language prior: a personal word is as likely in every language.
                logPmix = logAdd(logPmix, config.personalWordLogProb + overlay.logOffset(oid))
                agg.isPersonal = true
                valid = true
            }
        }
        agg.logPmix = logPmix
        agg.bestLang = bestLang
        agg.isValid = valid || agg.isPersonal
        agg.isListed = listed && !agg.isPersonal
    }

    /** Context-free log probability of [a] in the language mixture (needs [scoreLm] first, which fills the ids). */
    private fun unigramMix(a: Agg, logWeights: DoubleArray): Double {
        var best = Double.NEGATIVE_INFINITY
        val terms = DoubleArray(langs.size)
        for ((l, lang) in langs.withIndex()) {
            val id = a.ids[l]
            terms[l] = logWeights[l] + if (id >= 0) lang.model.unigramLogProb(id) else config.oovLogProb
            if (terms[l] > best) best = terms[l]
        }
        var sum = 0.0
        for (t in terms) sum += exp(t - best)
        return best + ln(sum)
    }

    /** How the typed key form relates to the vocabularies. */
    private enum class LiteralClass {
        /** A valid word of some language, a personal word or a protected literal. */
        VALID,
        /** In a vocabulary, but no surface of it is a valid word ("grea", "mischien"). */
        INVALID,
        /** In no vocabulary. */
        UNKNOWN,
    }

    /**
     * [offered] is false for blocked words: they keep their probability mass but are never the target. Words that
     * are not valid ([Agg.isValid]) keep their mass too and are never the target with [EngineConfig.validTargetsOnly];
     * a key form without any valid surface is not a correction candidate at all. [unknownScore] is the score of the
     * hypothesis "the user meant exactly this unknown string"; it competes for [LiteralClass.UNKNOWN] literals, and
     * for [LiteralClass.INVALID] ones decided like unknown words ([EngineConfig.invalidAsUnknown]), where the literal's
     * own vocabulary surfaces compete as full words too.
     */
    private fun decideAutocorrect(
        norm: String,
        kf: String,
        all: List<Agg>,
        literalClass: LiteralClass,
        unknownScore: Double,
        offered: (Agg) -> Boolean,
        logWeights: DoubleArray,
        literalBonus: Double = 0.0,
    ): Agg? {
        if (!config.autocorrectEnabled) return null
        if (!TextFolding.isKeyLetters(norm) || kf.length < config.minAutocorrectLength) return null
        val full = all.filter { it.fullScore > Double.NEGATIVE_INFINITY }
        if (full.isEmpty()) return null
        val asUnknown = literalClass == LiteralClass.UNKNOWN ||
            (literalClass == LiteralClass.INVALID && config.invalidAsUnknown)
        val keepScore = if (asUnknown) unknownScore else Double.NEGATIVE_INFINITY
        var mx = keepScore
        for (a in full) if (a.fullScore > mx) mx = a.fullScore
        var sum = if (keepScore > Double.NEGATIVE_INFINITY) exp(keepScore - mx) else 0.0
        for (a in full) sum += exp(a.fullScore - mx)
        val lse = mx + ln(sum)
        val validOnly = config.validTargetsOnly
        // Probability mass and best surface per key form.
        val mass = HashMap<String, Double>()
        val bestSurface = HashMap<String, Agg>()
        val targetKeyForms = HashSet<String>()
        for (a in full) {
            mass[a.keyForm] = (mass[a.keyForm] ?: 0.0) + exp(a.fullScore - lse)
            if (validOnly && !a.isValid) continue
            targetKeyForms.add(a.keyForm)
            if (!offered(a)) continue
            val cur = bestSurface[a.keyForm]
            if (cur == null || a.fullScore > cur.fullScore) bestSurface[a.keyForm] = a
        }
        val short = kf.length <= config.shortInputLength
        val bestOther = mass.entries.filter { it.key != kf && it.key in targetKeyForms }.maxByOrNull { it.value }
        if (!asUnknown) {
            val pLit = mass[kf] ?: 0.0
            // A valid word typed exactly is only replaced by another key form with correctValidWords (real-word
            // errors); other spellings of its own key form ("dont" -> "don't") are decided below either way.
            if (bestOther != null && (literalClass != LiteralClass.VALID || config.correctValidWords)) {
                val target = bestSurface[bestOther.key]
                var delta = when {
                    literalClass == LiteralClass.INVALID -> if (short) config.deltaInvalidShort else config.deltaInvalid
                    short -> config.deltaKnownShort
                    else -> config.deltaKnown
                }
                if (config.deltaKnownLmSlope != 0.0 && target != null && literalClass == LiteralClass.VALID) {
                    // Real-word errors between dictionary words: the more the typed word is rarer than the target,
                    // both in general and in this context, the less evidence the whole score needs
                    // ("kun je de deur op slot den" -> "doen"). Names and chat words are never relaxed.
                    var litLm = Double.NEGATIVE_INFINITY
                    var litUni = Double.NEGATIVE_INFINITY
                    var litCost = INF
                    var listed = false
                    for (a in full) {
                        if (a.keyForm != kf) continue
                        if (a.isListed) listed = true
                        if (a.logPmix > litLm) litLm = a.logPmix
                        val u = unigramMix(a, logWeights)
                        if (u > litUni) litUni = u
                        if (a.fullCost < litCost) litCost = a.fullCost
                    }
                    // Only for slips of the hand (near keys, a dropped or swapped letter), never for an unrelated
                    // extra letter or a far key: "nice tho" stays.
                    val cheap = target.fullCost - litCost <= config.deltaKnownLmMaxEditCost
                    if (listed && target.isListed && cheap) {
                        var gap = target.logPmix - litLm
                        if (config.deltaKnownUnigramGap) gap = minOf(gap, unigramMix(target, logWeights) - litUni)
                        gap -= config.deltaKnownLmOffset
                        if (gap > 0) {
                            delta = maxOf(minOf(delta, config.deltaKnownFloor), delta - config.deltaKnownLmSlope * gap)
                        }
                    }
                }
                if (ln(bestOther.value) - ln(pLit) >= delta + literalBonus) return target
            }
            val top = bestSurface[kf] ?: return null
            if (top.surface == norm) return null
            // The typed spelling is not a word ("dont" when only "don't" exists): use the key form's best surface.
            val own = full.firstOrNull { it.surface == norm } ?: return top
            var others = 0.0
            for (a in full) if (a.keyForm == kf && a !== own) others += exp(a.fullScore - own.fullScore)
            return if (others > 0.0 && ln(others) >= config.surfaceSwitchMargin + literalBonus) top else null
        }
        if (bestOther == null || kf.length < config.minUnknownAutocorrectLength) return null
        // Words that start with exactly the typed letters explain them as well (an unfinished word, or an unknown
        // word with a known stem), so they compete with corrections: "consi" must not become "condo".
        // The target's own completion supports it rather than competing ("grea" -> "great") with
        // prefixMassExcludesTarget.
        val skipOwn = if (config.prefixMassExcludesTarget) bestOther.key else null
        var prefixMass = 0.0
        for (a in all) {
            if (a.complEdits == 0 && a.complScore > Double.NEGATIVE_INFINITY && a.keyForm.length > kf.length &&
                a.keyForm.startsWith(kf) && a.keyForm != skipOwn
            ) {
                prefixMass += exp(a.complScore - lse)
            }
        }
        val tau = if (short) config.tauUnknownShort else config.tauUnknown
        return if (bestOther.value / (1.0 + prefixMass) >= tau) bestSurface[bestOther.key] else null
    }

    private fun nextWord(
        ctx: List<LangContext>,
        logWeights: DoubleArray,
        weightMap: Map<String, Double>,
        isBlocked: ((String) -> Boolean)?,
        contextBoost: ((String) -> Double)? = null,
    ): SuggestResult {
        val aggs = HashMap<String, Agg>(128)
        for ((l, lang) in langs.withIndex()) {
            val c = ctx[l]
            // A context-free model would suggest the previous word again ("ik" after "ik").
            val skip = if (!lang.model.usesContext && c.prev1 >= 0 && c.prev1 != lang.model.bosId) c.prev1 else -1
            val limit = config.nextWordCandidates + if (skip >= 0) 1 else 0
            for (sw in lang.model.topNext(c.prev1, c.prev2, limit)) {
                if (sw.id == skip) continue
                val pos = lang.lexicon.positionOf(sw.id)
                if (pos < 0) continue
                aggFor(aggs, lang.lexicon, l, sw.id, pos)
            }
        }
        val all = aggs.values.toMutableList()
        for (a in all) {
            scoreLm(a, ctx, logWeights)
            if (contextBoost != null) a.logPmix += contextBoost(a.surface).coerceAtLeast(0.0)
            a.score = a.logPmix
        }
        all.sortWith(compareByDescending<Agg> { it.score }.thenBy { it.surface })
        val mx = all.firstOrNull()?.score ?: 0.0
        val lse = mx + ln(all.sumOf { exp(it.score - mx) }.coerceAtLeast(Double.MIN_VALUE))
        val dropInvalid = config.dropInvalidSuggestions
        val offered = all.filter { (isBlocked == null || !isBlocked(it.surface)) && (!dropInvalid || it.isValid) }
        val candidates = offered.take(config.maxCandidates).map { a ->
            val lp = a.score - lse
            Candidate(
                word = a.surface, keyForm = a.keyForm, language = langs[a.bestLang].model.language,
                kind = CandidateKind.NEXT_WORD, logPosterior = lp, prob = exp(lp), editCost = 0.0, edits = 0,
                isCapitalized = isCapitalized(a), isPersonal = a.isPersonal, displayForm = displayForm(a),
            )
        }
        return SuggestResult("", "", candidates, null, null, false, weightMap, config)
    }

    companion object {
        private const val INF = 1e9f

        private val NO_SUCCESSORS = IntArray(0) to IntArray(0)

        private fun buildLanguages(models: List<WordLanguageModel>): List<Lang> {
            require(models.isNotEmpty()) { "PredictionEngine needs at least one language model" }
            return models.map { Lang(it, Lexicon.build(it)) }
        }

        /**
         * An engine over already built lexicons (index 0 = primary language, at least one), each with its own model
         * ([Lexicon.model]). Cheap: nothing is sorted, so lexicons built once with [Lexicon.build] can serve any
         * number of engines and language orders.
         */
        fun fromLexicons(
            lexicons: List<Lexicon>,
            spatial: SpatialModel = SpatialModel.qwerty(),
            config: EngineConfig = EngineConfig(),
            personalOverlay: PersonalOverlay? = null,
        ): PredictionEngine {
            require(lexicons.isNotEmpty()) { "PredictionEngine needs at least one language model" }
            return PredictionEngine(Languages(lexicons.map { Lang(it.model, it) }), spatial, config, personalOverlay)
        }

        private fun logAdd(a: Double, b: Double): Double {
            if (a == Double.NEGATIVE_INFINITY) return b
            if (b == Double.NEGATIVE_INFINITY) return a
            val m = max(a, b)
            return m + ln(exp(a - m) + exp(b - m))
        }

        private fun lowerBound(a: IntArray, key: Int): Int {
            var lo = 0
            var hi = a.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (a[mid] < key) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }
}
