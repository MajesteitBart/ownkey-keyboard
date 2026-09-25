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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.editor.EditorContent
import org.florisboard.lib.kotlin.safeSubstring

/**
 * Word suggestions together with the request they were computed for.
 */
data class WordSuggestionBatch(
    val request: WordSuggestionRequest,
    val candidates: List<SuggestionCandidate>,
) {
    companion object {
        val Empty = WordSuggestionBatch(WordSuggestionRequest.None, emptyList())
    }
}

/**
 * The word to suggest for and what else its candidates depend on. The same word gets other candidates in another
 * language, text field or private mode, after other words, or under other settings, so a batch only stands for an
 * identical request.
 */
data class WordSuggestionRequest(
    val input: String,
    val subtypeId: Long,
    val inputSessionId: Long,
    val isPrivateSession: Boolean,
    /** The text right before the word: the context that ranks its candidates. */
    val textBefore: String,
    /**
     * Which suggestion run this is; runs are numbered as they are requested. Only the latest run can stand for the
     * word: a word deleted and typed again gets a new run with new taps.
     */
    val sequence: Long = 0L,
    val allowPossiblyOffensive: Boolean = false,
    /** [SuggestionProvider.autoCommitStateKey] when the run was requested: settings, protected words. */
    val providerState: String = "",
) {
    companion object {
        /** Enough text before the word to hold the words that rank it. */
        private const val ContextLength = 48

        val None = WordSuggestionRequest("", subtypeId = -1L, inputSessionId = 0L, isPrivateSession = false, textBefore = "")

        fun of(
            content: EditorContent,
            subtypeId: Long,
            inputSessionId: Long,
            isPrivateSession: Boolean,
            sequence: Long = 0L,
            allowPossiblyOffensive: Boolean = false,
            providerState: String = "",
        ): WordSuggestionRequest {
            val wordStart = when {
                content.localComposing.isValid -> content.localComposing.start
                content.localCurrentWord.isValid -> content.localCurrentWord.start
                else -> content.localSelection.start
            }
            val textBefore = if (wordStart > 0) {
                content.text.safeSubstring((wordStart - ContextLength).coerceAtLeast(0), wordStart)
            } else {
                ""
            }
            return WordSuggestionRequest(
                inputOf(content), subtypeId, inputSessionId, isPrivateSession, textBefore,
                sequence, allowPossiblyOffensive, providerState,
            )
        }

        /** The word the suggestion providers score for [content]. */
        fun inputOf(content: EditorContent): String {
            return content.composingText.ifBlank { content.currentWordText }.trim()
        }
    }
}

/**
 * Picks the candidate to auto-commit for the word the user just finished. Suggestions run asynchronously, so the
 * latest batch can belong to an earlier prefix of the word when the user types fast, or to the same word before the
 * language or text field changed. A candidate is only taken from a batch computed for exactly [request]; otherwise
 * [decideNow] computes a fresh decision.
 */
object AutoCommitSelector {
    enum class Source { BATCH, DECIDED_NOW, NONE }

    data class Selection(val candidate: SuggestionCandidate?, val source: Source)

    /**
     * [revertedInput] is a word the user just restored by undoing its autocorrection. It stays as typed: the
     * provider learns to leave it alone in the background, and until then no batch or fresh decision may redo it.
     */
    inline fun select(
        request: WordSuggestionRequest,
        batch: WordSuggestionBatch,
        revertedInput: String? = null,
        decideNow: () -> SuggestionCandidate?,
    ): Selection {
        if (request.input.isBlank()) return Selection(null, Source.NONE)
        if (revertedInput != null && request.input.equals(revertedInput, ignoreCase = true)) {
            return Selection(null, Source.NONE)
        }
        if (batch.request == request) {
            return Selection(batch.candidates.firstOrNull { it.isEligibleForAutoCommit }, Source.BATCH)
        }
        return Selection(decideNow(), Source.DECIDED_NOW)
    }
}
