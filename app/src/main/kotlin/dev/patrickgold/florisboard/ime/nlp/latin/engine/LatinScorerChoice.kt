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

/** Which scorer the Latin provider uses for the current word and next-word predictions (devtools switch). */
enum class LatinScorerChoice {
    /**
     * The n-gram scorer ([NgramLatinScorer]) when every language of the subtype has a loaded n-gram model, otherwise
     * the noisy-channel scorer. The default.
     */
    NGRAM,
    /** The noisy-channel scorer of the autocorrect rebuild ([NoisyChannelLatinScorer]) with its bigram models. */
    NOISY_CHANNEL,
    /** The scorer from before the autocorrect rebuild ([LegacyLatinScorer]), for comparison. */
    LEGACY,
}

/**
 * Which correctly spelled words the n-gram scorer may replace on space or revise afterwards (devtools switch).
 */
enum class RealWordRule {
    /**
     * Spec AC-011 as written: a typed word of a [ConfusionSets] group is never replaced by, or revised to, a word of
     * its own group ("then" stays "then" after "rather"). Every other decision is the engine's as tuned. The default.
     */
    CONFUSION_PAIRS,
    /** Stricter: no word of main's word lists is replaced, and no valid word is revised while it stands as typed. */
    DICTIONARY_WORDS,
    /** The engine as tuned, without either rule (for comparison only; breaks AC-011). */
    NONE,
}
