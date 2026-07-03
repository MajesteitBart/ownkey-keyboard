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

package dev.patrickgold.florisboard.ime.nlp.personal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class PersonalNgramModelTest : FunSpec({
    test("learned bigram is predicted as next word") {
        val model = PersonalNgramModel()
        model.learn(listOf("see", "you", "tomorrow"))

        val predictions = model.predictNext(prev2 = null, prev1 = "you", limit = 5)

        predictions.map { it.word } shouldBe listOf("tomorrow")
    }

    test("repeated sequences increase prediction score") {
        val model = PersonalNgramModel()
        model.learn(listOf("good", "morning"))
        model.learn(listOf("good", "morning"))
        model.learn(listOf("good", "night"))

        val predictions = model.predictNext(prev2 = null, prev1 = "good", limit = 5)

        predictions.first().word shouldBe "morning"
        predictions.first().score shouldBeGreaterThan predictions.last().score
    }

    test("trigram context outranks pure bigram frequency") {
        val model = PersonalNgramModel()
        // "you too" typed more often overall...
        model.learn(listOf("you", "too"))
        model.learn(listOf("you", "too"))
        model.learn(listOf("you", "too"))
        // ...but "see you tomorrow" is the trigram context we are in.
        model.learn(listOf("see", "you", "tomorrow"))
        model.learn(listOf("see", "you", "tomorrow"))

        val predictions = model.predictNext(prev2 = "see", prev1 = "you", limit = 5)

        predictions.first().word shouldBe "tomorrow"
    }

    test("continuation score is positive for learned pairs and zero otherwise") {
        val model = PersonalNgramModel()
        model.learn(listOf("kind", "regards"))

        model.continuationScore("kind", "regards") shouldBeGreaterThan 0.0
        model.continuationScore("kind", "wishes") shouldBe 0.0
        model.continuationScore("warm", "regards") shouldBe 0.0
    }

    test("continuation score grows with repetition and stays below one") {
        val model = PersonalNgramModel()
        model.learn(listOf("kind", "regards"))
        val scoreAfterOne = model.continuationScore("kind", "regards")
        repeat(10) { model.learn(listOf("kind", "regards")) }
        val scoreAfterMany = model.continuationScore("kind", "regards")

        scoreAfterMany shouldBeGreaterThan scoreAfterOne
        (scoreAfterMany < 1.0) shouldBe true
    }

    test("tokens with digits or symbols are not learned") {
        val model = PersonalNgramModel()
        model.learn(listOf("call", "555123"))
        model.learn(listOf("visit", "foo.bar"))

        model.predictNext(prev2 = null, prev1 = "call", limit = 5).shouldBeEmpty()
        model.predictNext(prev2 = null, prev1 = "visit", limit = 5).shouldBeEmpty()
    }

    test("bigram storage is capped by eviction") {
        val model = PersonalNgramModel(maxBigrams = 10, maxTrigrams = 10)
        for (i in 0 until 100) {
            val word = buildString {
                var n = i
                repeat(3) {
                    append('a' + n % 26)
                    n /= 26
                }
            }
            model.learn(listOf(word, "next"))
        }
        // Distinct first words -> distinct bigrams; ensure the map does not grow unbounded.
        model.bigramCount shouldBeLessThanOrEqual 12
    }

    test("restore round trips learned data") {
        val model = PersonalNgramModel()
        model.learn(listOf("see", "you", "tomorrow"))

        val restored = PersonalNgramModel()
        restored.restore(model.snapshotBigrams(), model.snapshotTrigrams())

        restored.predictNext(prev2 = "see", prev1 = "you", limit = 5).map { it.word } shouldBe listOf("tomorrow")
    }
})
