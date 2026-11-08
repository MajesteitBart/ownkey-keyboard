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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.nlp.WordRevisionRequest
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectTriggerPolicy
import dev.patrickgold.florisboard.ime.nlp.latin.engine.KeyGeometry
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinCurrentWordScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoredCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringHooks
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringRequest
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.PossiblyOffensiveWords

/**
 * The steps of [LatinLanguageProvider] between the editor and the scorers that decide what space does to a word: the
 * on-the-spot auto-commit decision ([LatinLanguageProvider.decideAutoCommit]) and the right-context revision of the
 * previous word ([LatinLanguageProvider.revisePreviousWord]). Pure; no Android APIs, so the pipeline replay on the
 * kbbench dev split (`LatinPipelineReplayTest`) runs exactly this code.
 */
internal object LatinAutocorrectPipeline {
    /** Candidates a space-time decision asks for, as in [LatinLanguageProvider.decideAutoCommit]. */
    const val AutoCommitCandidateCount = 8

    /** The request for the word the user just finished, as the provider builds it from the editor content. */
    fun autoCommitRequest(
        rawInput: String,
        languages: List<LatinScoringLanguage>,
        textBeforeSelection: String,
        textStartsAtFieldStart: Boolean,
        settings: AutocorrectSettings,
        policy: HighCertaintyAutocorrectPolicy,
        geometry: KeyGeometry?,
        taps: List<LatinTap>?,
        blockedWords: Set<String>,
    ) = LatinScoringRequest(
        rawInput = rawInput,
        primaryLocale = languages.first().locale,
        languages = languages,
        textBeforeSelection = textBeforeSelection,
        maxCandidateCount = AutoCommitCandidateCount,
        policy = policy,
        autocorrect = settings,
        geometry = geometry,
        taps = taps,
        textStartsAtFieldStart = textStartsAtFieldStart,
        blockedWords = blockedWords,
    )

    /** The candidate to commit instead of the typed word, or null to keep it: never a possibly offensive word. */
    suspend fun decideAutoCommit(
        scorer: LatinCurrentWordScorer,
        request: LatinScoringRequest,
        hooks: LatinScoringHooks,
    ): LatinScoredCandidate? {
        val scored = scorer.score(request, hooks)
        return PossiblyOffensiveWords.filter(scored, request.blockedWords, request.rawInput) { it.text }
            .firstOrNull { it.isAutoCommit }
    }

    /** Whether the previous and the last word of [request] are tokens autocorrect may touch at all. */
    fun revisionTokensAllowed(request: WordRevisionRequest): Boolean {
        return AutocorrectTriggerPolicy.isCorrectableToken(request.committed) &&
            AutocorrectTriggerPolicy.isCorrectableToken(request.next)
    }

    /** The replacement for the previous word of [request], or null to keep it; see [NgramLatinScorer.revise]. */
    fun revise(
        scorer: NgramLatinScorer,
        request: WordRevisionRequest,
        languages: List<LatinScoringLanguage>,
        settings: AutocorrectSettings,
        isProtected: (String) -> Boolean,
        blockedWords: Set<String>,
        timesTyped: (String) -> Int = { 0 },
    ): LatinScoredCandidate? {
        if (!settings.enabled || languages.isEmpty() || !revisionTokensAllowed(request)) return null
        val revised = scorer.revise(
            languages = languages,
            textBeforeWord = request.textBeforeWord,
            textStartsAtFieldStart = request.textStartsAtFieldStart,
            typed = request.typed,
            committed = request.committed,
            next = request.next,
            taps = request.taps,
            geometry = request.geometry,
            settings = settings,
            isProtected = isProtected,
            blockedWords = blockedWords,
            timesTyped = timesTyped,
        ) ?: return null
        return PossiblyOffensiveWords.filter(listOf(revised), blockedWords, request.typed) { it.text }.firstOrNull()
    }
}
