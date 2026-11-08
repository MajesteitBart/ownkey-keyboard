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

/**
 * All tunables of [PredictionEngine]. Costs and probabilities are natural logs ("nats"). Defaults were tuned on
 * the kbbench `dev` split, with the autocorrect thresholds also constrained by the held-out literal checks
 * (`tools/kbbench/sim/literal_checks.jsonl`); [with] and [toMap] expose every field by name for overrides and grid
 * search. [touchSigma] is deliberately not tuned on the benchmark (its synthetic taps match the touch model).
 */
data class EngineConfig(
    // ---- channel model: log P(input | word) ----
    /** Weight of the channel log likelihood against the language model in the candidate score. */
    val alpha: Double = 1.0,
    /** Gaussian width of the key-code confusion model, in key pitches. */
    val keySigma: Double = SpatialModel.DEFAULT_SIGMA,
    /**
     * Gaussian width of the touch-point likelihood, in key pitches. Not tuned on the benchmark; set from real tap
     * logs once they exist (see [SpatialModel.DEFAULT_SIGMA_TOUCH]).
     */
    val touchSigma: Double = SpatialModel.DEFAULT_SIGMA_TOUCH,
    /** Uniform floor mixed into every spatial distribution (bounds far substitutions to `-ln(floor)`). */
    val spatialFloor: Double = SpatialModel.DEFAULT_FLOOR,
    /** Cost of an intended letter that was not typed. */
    val omissionCost: Double = 4.0,
    /** Cost of omitting a letter that repeats the previous intended letter ("helo" for "hello"). */
    val repeatOmissionCost: Double = 1.5,
    /**
     * Cost of an extra typed letter not related to the previous intended letter. Kept high: at 5 it turned "kheb",
     * "gboard" or "idk" into "heb", "board" and "ik". 8 was the best value on dev that also keeps the literal
     * checks at 95% or more; dev cannot argue for a higher value, because its typists never insert unrelated
     * letters.
     */
    val insertionCost: Double = 8.0,
    /** Base cost of an extra tap aimed at the previous intended letter (double tap); the spatial cost is added. */
    val repeatInsertionCost: Double = 2.0,
    /** Cost of swapping two adjacent letters; spatial costs of both letters are added. */
    val transpositionCost: Double = 2.5,
    /** Extra cost of any edit that touches the first letter of the word. */
    val firstLetterPenalty: Double = 1.0,
    /** Maximum number of edits (substitutions of a different key, omissions, insertions, transpositions). */
    val maxEdits: Int = 2,
    /** Maximum number of edits for inputs of at most [shortInputLength] letters. */
    val maxEditsShort: Int = 1,
    /** Inputs up to this length use the short-input limits and thresholds. */
    val shortInputLength: Int = 3,
    /** Channel cost budget; decoder paths above it are pruned. */
    val maxChannelCost: Double = 12.0,
    /** Inputs longer than this are only matched exactly (no search, corrections or completions). */
    val maxDecodeLength: Int = 32,

    // ---- language mixing ----
    /** Log prior of every non-primary language relative to the primary one. */
    val secondaryLanguageLogPrior: Double = -0.7,
    /** Number of most recent context words used as language evidence. */
    val languageEvidenceWords: Int = 6,
    /** Per-word decay of language evidence with age (1 = no decay). */
    val languageEvidenceDecay: Double = 0.85,
    /**
     * Weight of the summed context log likelihoods in the language posterior. Kept at 1.0 with the large n-gram models
     * (tools/lm, 250k nl / 150k en words): on dev, 1.25 fixed one more word on 2 of 5 tap seeds with key codes and on
     * 1 of 5 with touches and saved one tap of 15,639, which is noise, not evidence.
     */
    val languageEvidenceWeight: Double = 1.0,
    /** Every language keeps at least this mixture weight. */
    val minLanguageWeight: Double = 0.05,
    /** Log probability assigned to a word that a language does not contain (smoothing). */
    val oovLogProb: Double = -16.0,

    // ---- completions and next-word ----
    /** Score offset of completion candidates relative to full words. */
    val completionLogPenalty: Double = 0.0,
    /** Extra score penalty per edit for completions of a corrected prefix (display only, not autocorrect). */
    val completionEditPenalty: Double = 4.0,
    /** Words taken by unigram from each completion subtree. */
    val completionsPerSubtree: Int = 8,
    /** Completion subtrees expanded per language, cheapest first. */
    val maxCompletionSubtrees: Int = 24,
    /** Subtrees costing more than the cheapest one plus this are skipped. */
    val completionBeam: Double = 8.0,
    /** Context successors (from `topNext`) considered for completions. */
    val contextSuccessors: Int = 256,
    /** Next-word candidates gathered per language for empty input. */
    val nextWordCandidates: Int = 32,

    // ---- autocorrect ----
    val autocorrectEnabled: Boolean = true,
    /** Log probability of the hypothesis "the user meant exactly this unknown string". */
    val unknownWordLogProb: Double = -19.0,
    /** Extra log probability per letter for the unknown-word hypothesis (negative favours correcting long junk). */
    val unknownWordPerLetterLogProb: Double = 0.0,
    /** Minimum posterior of the best word to replace an unknown literal. */
    val tauUnknown: Double = 0.5,
    /** [tauUnknown] for short inputs. */
    val tauUnknownShort: Double = 0.7,
    /** Minimum log-odds of the best word against a known literal to replace it (real-word errors). */
    val deltaKnown: Double = 4.0,
    /** [deltaKnown] for short inputs. */
    val deltaKnownShort: Double = 5.0,
    /**
     * Frequency- and context-aware real-word correction between dictionary words
     * ([WordLanguageModel.isListedWord] for both the typed word and the correction): [deltaKnown] (and
     * [deltaKnownShort]) shrink by this many nats per nat by which the language model prefers the correction over the
     * typed word beyond [deltaKnownLmOffset], both in this context and, with [deltaKnownUnigramGap], without context
     * (the smaller of the two counts). 0 = a fixed margin. Tuned on dev with [deltaKnownLmOffset] 2: WER 1.49% -> 1.33%
     * with key codes, 0.87% -> 0.85% with touches, false corrections unchanged; slope 0.6 or offset 1 gained more but
     * turned "a travel vlogger" into "blogger", and without the unigram gap "the sprint" became "spring".
     */
    val deltaKnownLmSlope: Double = 0.45,
    /** Language-model preference (nats) for the correction that the margin ignores; see [deltaKnownLmSlope]. */
    val deltaKnownLmOffset: Double = 2.0,
    /**
     * [deltaKnownLmSlope] also needs the typed word to be rarer than the correction in general (unigram), not only in
     * this context, so a common word that merely fits the context worse ("sprint" in "the sprint") keeps the margin.
     */
    val deltaKnownUnigramGap: Boolean = true,
    /**
     * [deltaKnownLmSlope] only applies when the correction's channel cost exceeds the typed word's by at most this
     * many nats (a near-key substitution, an omission or a transposition; not an unrelated extra letter, which costs
     * [insertionCost]).
     */
    val deltaKnownLmMaxEditCost: Double = 6.0,
    /** [deltaKnownLmSlope] never lowers the margin below this. */
    val deltaKnownFloor: Double = 2.0,
    /**
     * A typed word that is in a vocabulary but not a valid word ([WordLanguageModel.isValidWord] false for all its
     * surfaces: subtitle typos, OCR debris, truncations, but also chat slang no word list has) is replaced more
     * readily than a valid word: with [deltaInvalid] / [deltaInvalidShort] instead of [deltaKnown] /
     * [deltaKnownShort], or like an unknown word with [invalidAsUnknown]. Its own probability always stays the
     * hypothesis that it was meant.
     */
    val validityAware: Boolean = true,
    /**
     * [deltaKnown] for a typed word that is not valid (see [validityAware]). 2 on dev; 0 and 1 gave the same WER but
     * lost a literal check.
     */
    val deltaInvalid: Double = 2.0,
    /**
     * [deltaKnownShort] for a typed word that is not valid. Short chat words ("wss", "idk", "tbh") are in no word list
     * and too rare to be valid by frequency, and 3 or 4 replaced them. 6 since the large n-gram models, whose
     * 250k-word Dutch vocabulary has "wss" as a word that is not valid (the 150k one did not have it, so it was decided
     * as an unknown word): 5 turned "ik ga wss vroeg" into "was" on the 4 tap seeds that type it as "wss". 6 was better
     * than 5 on those 4 seeds and equal on the fifth with key codes, better on 3 and equal on 2 with touches (false
     * corrections 0.07% -> 0.04%); literal checks unchanged. The cost: with touches, seed s1 no longer fixes "router tbe
     * internet" ("tbe" is not valid either). See also [reviseInvalidShort].
     */
    val deltaInvalidShort: Double = 6.0,
    /**
     * Decide a typed word that is not valid like an unknown word ([tauUnknown], [tauUnknownShort],
     * [minUnknownAutocorrectLength]) instead of with [deltaInvalid]. Off: on dev it raised WER (1.49% -> 1.55% with
     * key codes) and false corrections ("wss" -> "was", "idk" -> "ik", "hangry" -> "hungry").
     */
    val invalidAsUnknown: Boolean = false,
    /**
     * For unknown words, completions of the correction target itself do not count as competing explanations (an
     * unfinished "grea" supports "great" rather than arguing against it).
     */
    val prefixMassExcludesTarget: Boolean = true,
    /**
     * Autocorrect never replaces input with a word that is not valid; such words still explain the input. Free on dev
     * with the 1-per-million frequency rule of `tools/lm/lmtool/validity.py`.
     */
    val validTargetsOnly: Boolean = true,
    /**
     * Leave words that are not valid out of completions, corrections and next-word predictions (they are still shown
     * when typed exactly). Off: it cost 0.1 pp of dev KSR (chat words such as "wss" are not valid).
     */
    val dropInvalidSuggestions: Boolean = false,
    /** Inputs shorter than this are never corrected. */
    val minAutocorrectLength: Int = 2,
    /** Unknown literals (not in any lexicon) shorter than this are never corrected ("np", "ff", "jk"). */
    val minUnknownAutocorrectLength: Int = 3,
    /**
     * Minimum log-odds of the other surfaces of the literal's key form against the literal's own spelling to switch
     * to the best of them ("dont" -> "don't"). 2 nats means the typed spelling has less than 12% of the key form's
     * probability mass, so real words without an apostrophe or diacritic ("wed", "its", "shell") stay. The benchmark
     * folds apostrophes and diacritics, so this is set from the spelling literal checks only (all keeps and 33 of
     * 35 switches with the n-gram models; 1 nat turns "they wed" into "we'd", 3 nats misses "ideeën", "skiën").
     */
    val surfaceSwitchMargin: Double = 2.0,
    /**
     * Replace a typed word that is valid ([WordLanguageModel.isValidWord]) with a different word ([deltaKnown]: real-word
     * errors such as "then" for "than"). Other spellings of the typed word's own key form ([surfaceSwitchMargin]) are
     * not affected. On here, as tuned on the dev split. Off is the strict reading of a "never replace a correctly
     * spelled word" rule by the validity flags; the Ownkey app keeps it on and applies that rule (spec AC-011) with its
     * own curated word lists instead, which leave out subtitle misspellings the flags call valid ("mischien").
     */
    val correctValidWords: Boolean = true,

    // ---- right-context revision of the previous word ----
    /**
     * Revise the previous word once the next one is committed ([PredictionEngine.revise]): its interpretations are
     * scored again with the next word as right context, `ln P(w | left) + ln P(next | w, left) - alpha * channel`, and
     * a different word replaces it when it wins by [reviseMargin]. Needs [autocorrectEnabled].
     */
    val reviseEnabled: Boolean = true,
    /**
     * Minimum joint log-odds of the replacement against the word in the text (key forms, all surfaces summed). First
     * tuned with the 7 MB n-gram models to 7 (WER 1.33% -> 1.15% with key codes, 0.85% -> 0.72% with touches; 6 then
     * revised a correctly typed name into a common word, 8 left most real-word slips). With the large models
     * (tools/lm, default profile) 6 together with [reviseMarginShort] 5.5: every revision on dev right (noisy and
     * clean typist), as good or better than 7 on every tap seed with key codes and with touches, and the same literal
     * checks with a next word.
     */
    val reviseMargin: Double = 6.0,
    /**
     * [reviseMargin] when the previous word was typed with at most [shortInputLength] letters: real-word slips between
     * short words ("ok" for "ik", "in" for "i'm", "do" for "so") are common and the right context is most of the
     * evidence there. 5.5 since the large n-gram models (6 before): they find "ok heb" more likely than the 7 MB models
     * did, and 5.5 revises such slips again (dev, key codes: better on every tap seed, WER 1.12% -> 1.06%, every
     * revision right; with touches better on 1 seed and equal on 4). 5 revised correctly typed words on dev; 5.5
     * changes "ikr" before "and" or "that" in the literal checks (2 of 2024 pairs).
     */
    val reviseMarginShort: Double = 5.5,
    /**
     * Revise a previous word of at most [shortInputLength] letters that is in a vocabulary but not a valid word
     * ([RevisionProposal.CommittedClass.NOT_VALID]). Off. Such words are mostly chat shorthand that no word list has
     * ("wss", "gwn"), which autocorrect keeps on purpose with [deltaInvalidShort]. Because they are rare, the
     * n-gram has few or no successors for them, so their right-context factor is mostly a backed-off unigram and a
     * frequent next word favours a frequent neighbour ("was niet"). With the large models,
     * "dat is wss niet" -> "was" has margin 8.2 and right gain 3.4, past every margin dev supports. Free on dev: no
     * dev revision of such a word passes the thresholds (key codes or touches), and the only right one proposed ("tbe"
     * -> "the") fails on its right gain anyway. Off keeps "wss" before "niet", "de" and "dat" in the literal checks.
     * Longer words that are not valid are unaffected: autocorrect replaces them readily ([deltaInvalid]).
     */
    val reviseInvalidShort: Boolean = false,
    /**
     * Revise a previous word that is valid and stands in the text exactly as the user typed it (committed class
     * [RevisionProposal.CommittedClass.LISTED] or [RevisionProposal.CommittedClass.VALID], letters unchanged by
     * autocorrect). On here, as tuned on dev; the Ownkey app turns it off (spec AC-011), so it only revises typos that
     * were left as typed and words that autocorrect itself produced.
     */
    val reviseTypedValidWords: Boolean = true,
    /**
     * The next word alone must prefer the replacement by at least this many nats (`ln P(next | replacement) -
     * ln P(next | word in the text)`), so a revision is always driven by the right context and never repeats the
     * decision the user already saw with a different threshold.
     */
    val reviseMinRightGain: Double = 2.0,
    /**
     * The replacement may cost at most this many nats more channel cost than the word in the text: a revision undoes a
     * slip of the hand (a near key, a dropped, doubled or swapped letter), never a far key or an unrelated letter that
     * only the language model argues for (on dev, a correctly typed name two far keys from a common word; in the
     * literal checks, chat abbreviations such as "ty" before "the"). Same value as [deltaKnownLmMaxEditCost].
     */
    val reviseMaxExtraCost: Double = 6.0,
    /**
     * With touch points, the most extra channel cost (nats) a replacement may have that only substitutes letters of a
     * word typed exactly as it is in the text and valid. A tap shows which key the user aimed at: a tap squarely on the
     * typed key makes the neighbouring letter cost about 2.4 nats (key centre, [SpatialModel.DEFAULT_SIGMA_TOUCH]),
     * while a slip lands near the key boundary and costs less than 1. 1.5 nats means the tap lay at least a fifth of a
     * key off-centre towards the replacement letter. Without it, the language model alone changed correctly typed chat
     * openers ("ok heb ik gedaan", "ok wil je", "ok zie je morgen") into "ik" once the next word came. Omitted,
     * doubled or swapped letters are not affected (taps say nothing about them), nor are key codes without positions.
     * Every substitution revision on dev with touches cost at most 1.05 nats.
     */
    val reviseMaxTouchSubstitutionCost: Double = 1.5,
    /** Full-word interpretations of the previous word (best by left-context score) scored with the right context. */
    val reviseCandidates: Int = 24,

    // ---- personal words ----
    /**
     * Log probability of a word of the [PersonalOverlay] (the most frequent one; others get up to
     * [PersonalOverlay.FREQUENCY_RANGE] less), added to the language mixture without a language prior. Also used
     * for a protected literal that no lexicon contains.
     */
    val personalWordLogProb: Double = -9.0,

    // ---- display ----
    /** Candidates with a posterior below this do not count as alternatives for slot placement. */
    val negligibleProb: Double = 0.003,
    /** Maximum number of candidates returned in [SuggestResult.candidates]. */
    val maxCandidates: Int = 32,
    /**
     * Display order after slot 0 uses `ln P + displaySavingsWeight * ln(1 + letters saved)`, trading hit
     * probability against keystrokes saved per hit (0 = order by posterior).
     */
    val displaySavingsWeight: Double = 0.0,
    /**
     * Rank the autocorrect target by posterior in the suggestion strip (but within the first three slots) instead
     * of always putting it in slot 0.
     */
    val autocorrectRankedSlot: Boolean = false,
    /**
     * Strip index of the typed literal while an autocorrect is pending, so the user can keep it before space.
     * Keep it below 3 so it stays within the three visible slots.
     */
    val pendingLiteralSlot: Int = 1,
) {
    /** Returns a copy with one field changed by name; numbers and booleans are parsed from [value]. */
    fun with(key: String, value: String): EngineConfig {
        val setter = SETTERS[key] ?: throw IllegalArgumentException("unknown engine parameter '$key'; known: ${SETTERS.keys.sorted()}")
        return setter(this, value)
    }

    /** Applies several overrides in order. */
    fun with(overrides: Map<String, String>): EngineConfig = overrides.entries.fold(this) { c, (k, v) -> c.with(k, v) }

    /** All fields by name. */
    fun toMap(): Map<String, Any> = linkedMapOf(
        "alpha" to alpha, "keySigma" to keySigma, "touchSigma" to touchSigma, "spatialFloor" to spatialFloor,
        "omissionCost" to omissionCost, "repeatOmissionCost" to repeatOmissionCost, "insertionCost" to insertionCost,
        "repeatInsertionCost" to repeatInsertionCost, "transpositionCost" to transpositionCost,
        "firstLetterPenalty" to firstLetterPenalty, "maxEdits" to maxEdits, "maxEditsShort" to maxEditsShort,
        "shortInputLength" to shortInputLength, "maxChannelCost" to maxChannelCost, "maxDecodeLength" to maxDecodeLength,
        "secondaryLanguageLogPrior" to secondaryLanguageLogPrior, "languageEvidenceWords" to languageEvidenceWords,
        "languageEvidenceDecay" to languageEvidenceDecay, "languageEvidenceWeight" to languageEvidenceWeight,
        "minLanguageWeight" to minLanguageWeight, "oovLogProb" to oovLogProb,
        "completionLogPenalty" to completionLogPenalty, "completionEditPenalty" to completionEditPenalty,
        "completionsPerSubtree" to completionsPerSubtree,
        "maxCompletionSubtrees" to maxCompletionSubtrees, "completionBeam" to completionBeam,
        "contextSuccessors" to contextSuccessors, "nextWordCandidates" to nextWordCandidates,
        "autocorrectEnabled" to autocorrectEnabled, "unknownWordLogProb" to unknownWordLogProb,
        "unknownWordPerLetterLogProb" to unknownWordPerLetterLogProb, "tauUnknown" to tauUnknown,
        "tauUnknownShort" to tauUnknownShort, "deltaKnown" to deltaKnown, "deltaKnownShort" to deltaKnownShort,
        "deltaKnownLmSlope" to deltaKnownLmSlope, "deltaKnownLmOffset" to deltaKnownLmOffset,
        "deltaKnownFloor" to deltaKnownFloor, "deltaKnownUnigramGap" to deltaKnownUnigramGap,
        "deltaKnownLmMaxEditCost" to deltaKnownLmMaxEditCost,
        "validityAware" to validityAware, "deltaInvalid" to deltaInvalid,
        "deltaInvalidShort" to deltaInvalidShort, "invalidAsUnknown" to invalidAsUnknown,
        "prefixMassExcludesTarget" to prefixMassExcludesTarget, "validTargetsOnly" to validTargetsOnly,
        "dropInvalidSuggestions" to dropInvalidSuggestions,
        "minAutocorrectLength" to minAutocorrectLength, "minUnknownAutocorrectLength" to minUnknownAutocorrectLength,
        "surfaceSwitchMargin" to surfaceSwitchMargin, "correctValidWords" to correctValidWords,
        "reviseEnabled" to reviseEnabled, "reviseMargin" to reviseMargin, "reviseMarginShort" to reviseMarginShort,
        "reviseInvalidShort" to reviseInvalidShort, "reviseTypedValidWords" to reviseTypedValidWords,
        "reviseMinRightGain" to reviseMinRightGain, "reviseMaxExtraCost" to reviseMaxExtraCost,
        "reviseMaxTouchSubstitutionCost" to reviseMaxTouchSubstitutionCost, "reviseCandidates" to reviseCandidates,
        "personalWordLogProb" to personalWordLogProb,
        "negligibleProb" to negligibleProb, "maxCandidates" to maxCandidates,
        "displaySavingsWeight" to displaySavingsWeight, "autocorrectRankedSlot" to autocorrectRankedSlot,
        "pendingLiteralSlot" to pendingLiteralSlot,
    )

    companion object {
        private fun d(v: String) = v.toDouble()
        private fun i(v: String) = v.toInt()
        private fun b(v: String) = when (v.lowercase()) {
            "true", "on", "1", "yes" -> true
            "false", "off", "0", "no" -> false
            else -> throw IllegalArgumentException("not a boolean: $v")
        }

        private val SETTERS: Map<String, (EngineConfig, String) -> EngineConfig> = mapOf(
            "alpha" to { c, v -> c.copy(alpha = d(v)) },
            "keySigma" to { c, v -> c.copy(keySigma = d(v)) },
            "touchSigma" to { c, v -> c.copy(touchSigma = d(v)) },
            "spatialFloor" to { c, v -> c.copy(spatialFloor = d(v)) },
            "omissionCost" to { c, v -> c.copy(omissionCost = d(v)) },
            "repeatOmissionCost" to { c, v -> c.copy(repeatOmissionCost = d(v)) },
            "insertionCost" to { c, v -> c.copy(insertionCost = d(v)) },
            "repeatInsertionCost" to { c, v -> c.copy(repeatInsertionCost = d(v)) },
            "transpositionCost" to { c, v -> c.copy(transpositionCost = d(v)) },
            "firstLetterPenalty" to { c, v -> c.copy(firstLetterPenalty = d(v)) },
            "maxEdits" to { c, v -> c.copy(maxEdits = i(v)) },
            "maxEditsShort" to { c, v -> c.copy(maxEditsShort = i(v)) },
            "shortInputLength" to { c, v -> c.copy(shortInputLength = i(v)) },
            "maxChannelCost" to { c, v -> c.copy(maxChannelCost = d(v)) },
            "maxDecodeLength" to { c, v -> c.copy(maxDecodeLength = i(v)) },
            "secondaryLanguageLogPrior" to { c, v -> c.copy(secondaryLanguageLogPrior = d(v)) },
            "languageEvidenceWords" to { c, v -> c.copy(languageEvidenceWords = i(v)) },
            "languageEvidenceDecay" to { c, v -> c.copy(languageEvidenceDecay = d(v)) },
            "languageEvidenceWeight" to { c, v -> c.copy(languageEvidenceWeight = d(v)) },
            "minLanguageWeight" to { c, v -> c.copy(minLanguageWeight = d(v)) },
            "oovLogProb" to { c, v -> c.copy(oovLogProb = d(v)) },
            "completionLogPenalty" to { c, v -> c.copy(completionLogPenalty = d(v)) },
            "completionEditPenalty" to { c, v -> c.copy(completionEditPenalty = d(v)) },
            "completionsPerSubtree" to { c, v -> c.copy(completionsPerSubtree = i(v)) },
            "maxCompletionSubtrees" to { c, v -> c.copy(maxCompletionSubtrees = i(v)) },
            "completionBeam" to { c, v -> c.copy(completionBeam = d(v)) },
            "contextSuccessors" to { c, v -> c.copy(contextSuccessors = i(v)) },
            "nextWordCandidates" to { c, v -> c.copy(nextWordCandidates = i(v)) },
            "autocorrectEnabled" to { c, v -> c.copy(autocorrectEnabled = b(v)) },
            "unknownWordLogProb" to { c, v -> c.copy(unknownWordLogProb = d(v)) },
            "unknownWordPerLetterLogProb" to { c, v -> c.copy(unknownWordPerLetterLogProb = d(v)) },
            "tauUnknown" to { c, v -> c.copy(tauUnknown = d(v)) },
            "tauUnknownShort" to { c, v -> c.copy(tauUnknownShort = d(v)) },
            "deltaKnown" to { c, v -> c.copy(deltaKnown = d(v)) },
            "deltaKnownShort" to { c, v -> c.copy(deltaKnownShort = d(v)) },
            "deltaKnownLmSlope" to { c, v -> c.copy(deltaKnownLmSlope = d(v)) },
            "deltaKnownLmOffset" to { c, v -> c.copy(deltaKnownLmOffset = d(v)) },
            "deltaKnownFloor" to { c, v -> c.copy(deltaKnownFloor = d(v)) },
            "deltaKnownUnigramGap" to { c, v -> c.copy(deltaKnownUnigramGap = b(v)) },
            "deltaKnownLmMaxEditCost" to { c, v -> c.copy(deltaKnownLmMaxEditCost = d(v)) },
            "validityAware" to { c, v -> c.copy(validityAware = b(v)) },
            "deltaInvalid" to { c, v -> c.copy(deltaInvalid = d(v)) },
            "deltaInvalidShort" to { c, v -> c.copy(deltaInvalidShort = d(v)) },
            "invalidAsUnknown" to { c, v -> c.copy(invalidAsUnknown = b(v)) },
            "prefixMassExcludesTarget" to { c, v -> c.copy(prefixMassExcludesTarget = b(v)) },
            "validTargetsOnly" to { c, v -> c.copy(validTargetsOnly = b(v)) },
            "dropInvalidSuggestions" to { c, v -> c.copy(dropInvalidSuggestions = b(v)) },
            "minAutocorrectLength" to { c, v -> c.copy(minAutocorrectLength = i(v)) },
            "minUnknownAutocorrectLength" to { c, v -> c.copy(minUnknownAutocorrectLength = i(v)) },
            "surfaceSwitchMargin" to { c, v -> c.copy(surfaceSwitchMargin = d(v)) },
            "correctValidWords" to { c, v -> c.copy(correctValidWords = b(v)) },
            "reviseEnabled" to { c, v -> c.copy(reviseEnabled = b(v)) },
            "reviseMargin" to { c, v -> c.copy(reviseMargin = d(v)) },
            "reviseMarginShort" to { c, v -> c.copy(reviseMarginShort = d(v)) },
            "reviseInvalidShort" to { c, v -> c.copy(reviseInvalidShort = b(v)) },
            "reviseTypedValidWords" to { c, v -> c.copy(reviseTypedValidWords = b(v)) },
            "reviseMinRightGain" to { c, v -> c.copy(reviseMinRightGain = d(v)) },
            "reviseMaxExtraCost" to { c, v -> c.copy(reviseMaxExtraCost = d(v)) },
            "reviseMaxTouchSubstitutionCost" to { c, v -> c.copy(reviseMaxTouchSubstitutionCost = d(v)) },
            "reviseCandidates" to { c, v -> c.copy(reviseCandidates = i(v)) },
            "personalWordLogProb" to { c, v -> c.copy(personalWordLogProb = d(v)) },
            "negligibleProb" to { c, v -> c.copy(negligibleProb = d(v)) },
            "maxCandidates" to { c, v -> c.copy(maxCandidates = i(v)) },
            "displaySavingsWeight" to { c, v -> c.copy(displaySavingsWeight = d(v)) },
            "autocorrectRankedSlot" to { c, v -> c.copy(autocorrectRankedSlot = b(v)) },
            "pendingLiteralSlot" to { c, v -> c.copy(pendingLiteralSlot = i(v)) },
        )

        /** Names accepted by [with]. */
        val parameterNames: Set<String> get() = SETTERS.keys
    }
}
