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

import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

class AppSpecificAutocorrectProfilePolicyTest : FunSpec({
    test("resolves known chat app packages to chat profile") {
        val policy = AppSpecificAutocorrectProfilePolicy()

        val profile = policy.resolveProfile(
            AutocorrectAppContext(
                packageName = "com.whatsapp",
                inputVariation = InputAttributes.Variation.NORMAL,
                imeAction = ImeOptions.Action.NONE,
            )
        )

        profile.shouldBe(AutocorrectAppProfile.CHAT)
    }

    test("resolves known email app packages to email profile") {
        val policy = AppSpecificAutocorrectProfilePolicy()

        val profile = policy.resolveProfile(
            AutocorrectAppContext(
                packageName = "com.google.android.gm",
                inputVariation = InputAttributes.Variation.NORMAL,
                imeAction = ImeOptions.Action.NONE,
            )
        )

        profile.shouldBe(AutocorrectAppProfile.EMAIL)
    }

    test("falls back to variation and ime action heuristics when app is unknown") {
        val policy = AppSpecificAutocorrectProfilePolicy()

        policy.resolveProfile(
            AutocorrectAppContext(
                packageName = "com.example.unknown",
                inputVariation = InputAttributes.Variation.EMAIL_SUBJECT,
                imeAction = ImeOptions.Action.NONE,
            )
        ).shouldBe(AutocorrectAppProfile.EMAIL)

        policy.resolveProfile(
            AutocorrectAppContext(
                packageName = "com.example.unknown",
                inputVariation = InputAttributes.Variation.NORMAL,
                imeAction = ImeOptions.Action.SEND,
            )
        ).shouldBe(AutocorrectAppProfile.CHAT)
    }

    test("uses safe default profile when context cannot be classified") {
        val policy = AppSpecificAutocorrectProfilePolicy()

        val profile = policy.resolveProfile(
            AutocorrectAppContext(
                packageName = null,
                inputVariation = InputAttributes.Variation.NORMAL,
                imeAction = ImeOptions.Action.NONE,
            )
        )

        profile.shouldBe(AutocorrectAppProfile.DEFAULT)
    }

    test("chat profile increases autocorrect aggressiveness") {
        val policy = AppSpecificAutocorrectProfilePolicy(
            AppSpecificAutocorrectConfig(
                enabled = true,
                chatAggressivenessPercent = 120,
                emailAggressivenessPercent = 85,
            )
        )
        val baseConfig = HighCertaintyAutocorrectConfig(
            minConfidence = 0.90,
            minConfidenceGap = 0.12,
            minInputLength = 4,
        )

        val adjustedConfig = policy.applyProfile(baseConfig, AutocorrectAppProfile.CHAT)

        adjustedConfig.minConfidence.shouldBeLessThan(baseConfig.minConfidence)
        adjustedConfig.minConfidenceGap.shouldBeLessThan(baseConfig.minConfidenceGap)
        adjustedConfig.minInputLength.shouldBeLessThan(baseConfig.minInputLength)
    }

    test("email profile reduces autocorrect aggressiveness") {
        val policy = AppSpecificAutocorrectProfilePolicy(
            AppSpecificAutocorrectConfig(
                enabled = true,
                chatAggressivenessPercent = 118,
                emailAggressivenessPercent = 82,
            )
        )
        val baseConfig = HighCertaintyAutocorrectConfig(
            minConfidence = 0.88,
            minConfidenceGap = 0.12,
            minInputLength = 4,
        )

        val adjustedConfig = policy.applyProfile(baseConfig, AutocorrectAppProfile.EMAIL)

        adjustedConfig.minConfidence.shouldBeGreaterThan(baseConfig.minConfidence)
        adjustedConfig.minConfidenceGap.shouldBeGreaterThan(baseConfig.minConfidenceGap)
        adjustedConfig.minInputLength.shouldBeGreaterThan(baseConfig.minInputLength)
    }

    test("disabled app specific profiles keep base config unchanged") {
        val policy = AppSpecificAutocorrectProfilePolicy(
            AppSpecificAutocorrectConfig(
                enabled = false,
                chatAggressivenessPercent = 125,
                emailAggressivenessPercent = 80,
            )
        )
        val baseConfig = HighCertaintyAutocorrectConfig(
            minConfidence = 0.88,
            minConfidenceGap = 0.12,
            minInputLength = 4,
        )

        val adjustedConfig = policy.applyProfile(baseConfig, AutocorrectAppProfile.CHAT)

        adjustedConfig.shouldBe(baseConfig)
    }
})
