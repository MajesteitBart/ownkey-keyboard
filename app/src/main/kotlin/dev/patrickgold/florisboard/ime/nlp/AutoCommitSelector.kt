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

/**
 * Word suggestions together with the word they were computed for.
 */
data class WordSuggestionBatch(
    val input: String,
    val candidates: List<SuggestionCandidate>,
) {
    companion object {
        val Empty = WordSuggestionBatch("", emptyList())

        /** The word the suggestion providers score for [content]. */
        fun inputOf(content: EditorContent): String {
            return content.composingText.ifBlank { content.currentWordText }.trim()
        }
    }
}

/**
 * Picks the candidate to auto-commit for the word the user just finished. Suggestions run asynchronously, so the
 * latest batch can belong to an earlier prefix of the word when the user types fast. A candidate is only taken from
 * a batch computed for exactly [input]; otherwise [decideNow] computes a fresh decision.
 */
object AutoCommitSelector {
    enum class Source { BATCH, DECIDED_NOW, NONE }

    data class Selection(val candidate: SuggestionCandidate?, val source: Source)

    inline fun select(
        input: String,
        batch: WordSuggestionBatch,
        decideNow: () -> SuggestionCandidate?,
    ): Selection {
        if (input.isBlank()) return Selection(null, Source.NONE)
        if (batch.input == input) {
            return Selection(batch.candidates.firstOrNull { it.isEligibleForAutoCommit }, Source.BATCH)
        }
        return Selection(decideNow(), Source.DECIDED_NOW)
    }
}
