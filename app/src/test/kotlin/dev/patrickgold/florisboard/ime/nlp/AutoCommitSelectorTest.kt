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
import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AutoCommitSelectorTest : FunSpec({
    fun candidate(text: String, eligible: Boolean) = WordSuggestionCandidate(
        text = text,
        confidence = 0.9,
        isEligibleForAutoCommit = eligible,
    )

    fun request(
        input: String,
        subtypeId: Long = 1L,
        inputSessionId: Long = 7L,
        isPrivateSession: Boolean = false,
        textBefore: String = "I saw ",
    ) = WordSuggestionRequest(input, subtypeId, inputSessionId, isPrivateSession, textBefore)

    test("uses the batch when it was computed for the same word") {
        val batch = WordSuggestionBatch(request("teh"), listOf(candidate("tea", false), candidate("the", true)))
        var decided = false
        val selection = AutoCommitSelector.select(request("teh"), batch) { decided = true; null }
        selection.source shouldBe AutoCommitSelector.Source.BATCH
        selection.candidate?.text shouldBe "the"
        decided shouldBe false
    }

    test("never takes a candidate from a batch for an earlier prefix") {
        // The user typed "thw" and pressed space before suggestions for "thw" arrived.
        val stale = WordSuggestionBatch(request("th"), listOf(candidate("the", true)))
        val selection = AutoCommitSelector.select(request("thw"), stale) { candidate("the", true) }
        selection.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
        selection.candidate?.text shouldBe "the"

        val keepAsTyped = AutoCommitSelector.select(request("thwart"), stale) { null }
        keepAsTyped.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
        keepAsTyped.candidate.shouldBeNull()
    }

    test("never takes a candidate computed for the same word under other conditions") {
        // The Dutch batch for "wiel" still sits there when the user switches to English and presses space.
        val dutch = WordSuggestionBatch(request("wiel", subtypeId = 2L), listOf(candidate("wel", true)))
        listOf(
            request("wiel", subtypeId = 1L),
            request("wiel", subtypeId = 2L, inputSessionId = 8L),
            request("wiel", subtypeId = 2L, isPrivateSession = true),
            request("wiel", subtypeId = 2L, textBefore = "Het "),
        ).forEach { other ->
            val selection = AutoCommitSelector.select(other, dutch) { null }
            selection.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
            selection.candidate.shouldBeNull()
        }
    }

    test("only the latest suggestion run counts, under the same settings") {
        val batch = WordSuggestionBatch(
            request("thr").copy(sequence = 4L, providerState = "Normal|rev1"),
            listOf(candidate("the", true)),
        )
        AutoCommitSelector.select(request("thr").copy(sequence = 4L, providerState = "Normal|rev1"), batch) { null }
            .source shouldBe AutoCommitSelector.Source.BATCH
        // The word was deleted and typed again with other taps: run 5 is still pending.
        listOf(
            request("thr").copy(sequence = 5L, providerState = "Normal|rev1"),
            request("thr").copy(sequence = 4L, providerState = "Strong|rev1"),
            request("thr").copy(sequence = 4L, providerState = "Normal|rev2"),
            request("thr").copy(sequence = 4L, providerState = "Normal|rev1", allowPossiblyOffensive = true),
        ).forEach { other ->
            AutoCommitSelector.select(other, batch) { null }.source shouldBe AutoCommitSelector.Source.DECIDED_NOW
        }
    }

    test("a word whose autocorrection was just undone stays as typed") {
        val batch = WordSuggestionBatch(request("teh"), listOf(candidate("the", true)))
        // Space pressed right after the undo, before the provider stored the word as one to leave alone.
        AutoCommitSelector.select(request("teh"), batch, revertedInput = "teh") { error("must not decide") }
            .candidate.shouldBeNull()
        AutoCommitSelector.select(request("Teh"), batch, revertedInput = "teh") { error("must not decide") }
            .candidate.shouldBeNull()
        // Other words are still corrected.
        AutoCommitSelector.select(request("thw"), batch, revertedInput = "teh") { candidate("the", true) }
            .candidate?.text shouldBe "the"
    }

    test("a batch with no eligible candidate keeps the word") {
        val batch = WordSuggestionBatch(request("hello"), listOf(candidate("hello", false), candidate("hell", false)))
        AutoCommitSelector.select(request("hello"), batch) { error("must not decide again") }.candidate.shouldBeNull()
    }

    test("nothing to correct without a word") {
        val selection = AutoCommitSelector.select(request(""), WordSuggestionBatch.Empty) { error("must not decide") }
        selection.source shouldBe AutoCommitSelector.Source.NONE
        selection.candidate.shouldBeNull()
    }

    test("the request holds the word and the text before it") {
        val text = "I saw teh"
        val composing = EditorContent(
            text = text,
            offset = 0,
            localSelection = EditorRange.cursor(text.length),
            localComposing = EditorRange(6, 9),
            localCurrentWord = EditorRange(6, 9),
        )
        val request = WordSuggestionRequest.of(composing, subtypeId = 1L, inputSessionId = 7L, isPrivateSession = false)
        request shouldBe request("teh")

        // The cursor inside a word that is not composing: the context still ends where the word starts.
        val inside = composing.copy(localSelection = EditorRange.cursor(7), localComposing = EditorRange.Unspecified)
        WordSuggestionRequest.of(inside, 1L, 7L, false).textBefore shouldBe "I saw "

        val long = "x".repeat(100) + " teh"
        val far = EditorContent(long, 0, EditorRange.cursor(long.length), EditorRange(101, 104), EditorRange(101, 104))
        WordSuggestionRequest.of(far, 1L, 7L, false).textBefore.length shouldBe 48
    }
})
