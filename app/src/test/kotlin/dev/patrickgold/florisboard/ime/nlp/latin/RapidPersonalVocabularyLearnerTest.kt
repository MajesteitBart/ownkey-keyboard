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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class RapidPersonalVocabularyLearnerTest : FunSpec({
    test("promotes high-confidence candidate on first confirmation") {
        var now = 0L
        val learner = RapidPersonalVocabularyLearner(
            nowProvider = { now },
            decayWindowMillis = 1000L,
            suppressionWindowMillis = 2000L,
        )

        val promotion = learner.onSuggestionAccepted(language = "en", candidateWord = "Zephyr", confidence = 0.95)

        promotion?.word shouldBe "zephyr"
        promotion?.language shouldBe "en"
        promotion?.confirmations shouldBe 1
    }

    test("promotes medium-confidence candidate after two confirmations") {
        var now = 0L
        val learner = RapidPersonalVocabularyLearner(
            nowProvider = { now },
            decayWindowMillis = 1000L,
            suppressionWindowMillis = 2000L,
        )

        learner.onSuggestionAccepted(language = "en", candidateWord = "Bramble", confidence = 0.80).shouldBeNull()
        now += 500L
        val promotion = learner.onSuggestionAccepted(language = "en", candidateWord = "Bramble", confidence = 0.80)

        promotion?.confirmations shouldBe 2
        promotion?.word shouldBe "bramble"
    }

    test("temporary noisy exposures decay before promotion") {
        var now = 0L
        val learner = RapidPersonalVocabularyLearner(
            nowProvider = { now },
            decayWindowMillis = 1000L,
            suppressionWindowMillis = 2000L,
        )

        learner.onSuggestionAccepted(language = "en", candidateWord = "orbital", confidence = 0.60).shouldBeNull()
        now += 2500L
        learner.onSuggestionAccepted(language = "en", candidateWord = "orbital", confidence = 0.60).shouldBeNull()
        now += 500L
        learner.onSuggestionAccepted(language = "en", candidateWord = "orbital", confidence = 0.60).shouldBeNull()
        now += 500L
        val promotion = learner.onSuggestionAccepted(language = "en", candidateWord = "orbital", confidence = 0.60)

        promotion?.confirmations shouldBe 3
    }

    test("reverted candidate is suppressed within suppression window") {
        var now = 0L
        val learner = RapidPersonalVocabularyLearner(
            nowProvider = { now },
            decayWindowMillis = 1000L,
            suppressionWindowMillis = 3000L,
        )

        learner.onSuggestionAccepted(language = "en", candidateWord = "novacore", confidence = 0.95)
        learner.onSuggestionReverted(language = "en", candidateWord = "novacore")

        learner.onSuggestionAccepted(language = "en", candidateWord = "novacore", confidence = 0.95).shouldBeNull()
        now += 3000L
        val promotion = learner.onSuggestionAccepted(language = "en", candidateWord = "novacore", confidence = 0.95)

        promotion?.word shouldBe "novacore"
    }

    test("learning state is partitioned by language") {
        var now = 0L
        val learner = RapidPersonalVocabularyLearner(
            nowProvider = { now },
            decayWindowMillis = 1000L,
            suppressionWindowMillis = 2000L,
        )

        learner.onSuggestionAccepted(language = "en", candidateWord = "delta", confidence = 0.80).shouldBeNull()
        val promotion = learner.onSuggestionAccepted(language = "nl", candidateWord = "delta", confidence = 0.95)

        promotion?.language shouldBe "nl"
        promotion?.confirmations shouldBe 1
    }

    test("candidate with digits is ignored for quality safeguards") {
        val learner = RapidPersonalVocabularyLearner()

        val promotion = learner.onSuggestionAccepted(language = "en", candidateWord = "abc123", confidence = 0.99)

        promotion.shouldBeNull()
    }
})
