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
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan

class MixedLanguageScoringPolicyTest : FunSpec({
    test("primary language stays preferred without mixed signals") {
        val policy = MixedLanguageScoringPolicy()
        val weights = policy.computeLanguageWeights(
            listOf(
                LanguageConfidenceSignal(language = "en", isPrimary = true, contextEvidence = 0.0, hasExactInputMatch = false),
                LanguageConfidenceSignal(language = "nl", isPrimary = false, contextEvidence = 0.0, hasExactInputMatch = false),
            )
        )

        (weights["en"] ?: 0.0) shouldBeGreaterThan (weights["nl"] ?: 0.0)
    }

    test("context evidence can prioritize secondary language in mixed sentence") {
        val policy = MixedLanguageScoringPolicy()
        val weights = policy.computeLanguageWeights(
            listOf(
                LanguageConfidenceSignal(language = "en", isPrimary = true, contextEvidence = 0.3, hasExactInputMatch = false),
                LanguageConfidenceSignal(language = "nl", isPrimary = false, contextEvidence = 2.2, hasExactInputMatch = false),
            )
        )

        (weights["nl"] ?: 0.0) shouldBeGreaterThan (weights["en"] ?: 0.0)
    }

    test("exact input match boosts the matching language strongly") {
        val policy = MixedLanguageScoringPolicy()
        val weights = policy.computeLanguageWeights(
            listOf(
                LanguageConfidenceSignal(language = "en", isPrimary = true, contextEvidence = 0.0, hasExactInputMatch = false),
                LanguageConfidenceSignal(language = "nl", isPrimary = false, contextEvidence = 0.0, hasExactInputMatch = true),
            )
        )

        (weights["nl"] ?: 0.0) shouldBeGreaterThan 0.45
        (weights["en"] ?: 0.0) shouldBeLessThan 0.55
    }

    test("language weight affects rank and confidence blending") {
        val policy = MixedLanguageScoringPolicy()
        val highWeight = 0.72
        val lowWeight = 0.28

        policy.applyLanguageWeight(baseRankScore = 0.9, languageWeight = highWeight) shouldBeGreaterThan
            policy.applyLanguageWeight(baseRankScore = 0.9, languageWeight = lowWeight)
        policy.blendCandidateConfidence(baseConfidence = 0.64, languageWeight = highWeight) shouldBeGreaterThan
            policy.blendCandidateConfidence(baseConfidence = 0.64, languageWeight = lowWeight)
    }
})
