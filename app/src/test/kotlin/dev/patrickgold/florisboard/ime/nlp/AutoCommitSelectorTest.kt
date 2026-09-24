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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AutoCommitSelectorTest : FunSpec({
    fun candidate(text: String, eligible: Boolean) = WordSuggestionCandidate(
        text = text,
        confidence = 0.9,
        isEligibleForAutoCommit = eligible,
    )

    test("uses the batch when it was computed for the same word") {
        val batch = WordSuggestionBatch("teh", listOf(candidate("tea", false), candidate("the", true)))
        var decided = false
        val selection = AutoCommitSelector.select("teh", batch) { decided = true; null }
        selection.source shouldBe AutoCommitSelector.Source.BATCH
        selection.candidate?.text shouldBe "the"
        decided shouldBe false
    }

    test("never takes a candidate from a batch for an earlier prefix") {
        // The user typed "thw" and pressed space before suggestions for "thw" arrived.
        val stale = WordSuggestionBatch("th", listOf(candidate("the", true)))
        val selection = AutoCommitSelector.select("thw", stale) { candidate("the", true) }
        selection.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
        selection.candidate?.text shouldBe "the"

        val keepAsTyped = AutoCommitSelector.select("thwart", stale) { null }
        keepAsTyped.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
        keepAsTyped.candidate.shouldBeNull()
    }

    test("a batch with no eligible candidate keeps the word") {
        val batch = WordSuggestionBatch("hello", listOf(candidate("hello", false), candidate("hell", false)))
        AutoCommitSelector.select("hello", batch) { error("must not decide again") }.candidate.shouldBeNull()
    }

    test("nothing to correct without a word") {
        val selection = AutoCommitSelector.select("", WordSuggestionBatch.Empty) { error("must not decide") }
        selection.source shouldBe AutoCommitSelector.Source.NONE
        selection.candidate.shouldBeNull()
    }
})
