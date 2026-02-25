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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class HighCertaintyAutocorrectPolicyTest : FunSpec({
    test("auto-commit allowed for high-confidence typo correction") {
        val policy = HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(
                enabled = true,
                minConfidence = 0.88,
                minConfidenceGap = 0.12,
                minInputLength = 3,
                maxAutoCorrectEditDistance = 1,
            )
        )

        policy.shouldAutoCommit(
            normalizedInput = "teh",
            candidateWord = "the",
            candidateEditDistance = 1,
            candidateConfidence = 0.91,
            runnerUpConfidence = 0.70,
            hasExactInputMatch = false,
        ).shouldBeTrue()
    }

    test("auto-commit blocked when confidence is below threshold") {
        val policy = HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(minConfidence = 0.90, minInputLength = 3)
        )

        policy.shouldAutoCommit(
            normalizedInput = "teh",
            candidateWord = "the",
            candidateEditDistance = 1,
            candidateConfidence = 0.89,
            runnerUpConfidence = 0.60,
            hasExactInputMatch = false,
        ).shouldBeFalse()
    }

    test("auto-commit blocked when confidence lead over runner-up is too small") {
        val policy = HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(minConfidence = 0.85, minConfidenceGap = 0.10, minInputLength = 3)
        )

        policy.shouldAutoCommit(
            normalizedInput = "teh",
            candidateWord = "the",
            candidateEditDistance = 1,
            candidateConfidence = 0.90,
            runnerUpConfidence = 0.83,
            hasExactInputMatch = false,
        ).shouldBeFalse()
    }

    test("auto-commit blocked for known exact word") {
        val policy = HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(minInputLength = 3)
        )

        policy.shouldAutoCommit(
            normalizedInput = "their",
            candidateWord = "there",
            candidateEditDistance = 1,
            candidateConfidence = 0.93,
            runnerUpConfidence = 0.40,
            hasExactInputMatch = true,
        ).shouldBeFalse()
    }

    test("auto-commit blocked for short input and larger edit distance") {
        val policy = HighCertaintyAutocorrectPolicy(
            HighCertaintyAutocorrectConfig(minInputLength = 4, maxAutoCorrectEditDistance = 1)
        )

        policy.shouldAutoCommit(
            normalizedInput = "cat",
            candidateWord = "cart",
            candidateEditDistance = 1,
            candidateConfidence = 0.99,
            runnerUpConfidence = null,
            hasExactInputMatch = false,
        ).shouldBeFalse()

        policy.shouldAutoCommit(
            normalizedInput = "speling",
            candidateWord = "spelling",
            candidateEditDistance = 2,
            candidateConfidence = 0.99,
            runnerUpConfidence = null,
            hasExactInputMatch = false,
        ).shouldBeFalse()
    }
})
