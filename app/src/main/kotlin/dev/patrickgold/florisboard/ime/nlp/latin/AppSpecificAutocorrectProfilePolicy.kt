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
import kotlin.math.roundToInt

internal enum class AutocorrectAppProfile {
    CHAT,
    DEFAULT,
    EMAIL,
}

internal data class AppSpecificAutocorrectConfig(
    val enabled: Boolean = true,
    val chatAggressivenessPercent: Int = 112,
    val emailAggressivenessPercent: Int = 88,
)

internal data class AutocorrectAppContext(
    val packageName: String?,
    val inputVariation: InputAttributes.Variation,
    val imeAction: ImeOptions.Action,
)

internal class AppSpecificAutocorrectProfilePolicy(
    private val config: AppSpecificAutocorrectConfig = AppSpecificAutocorrectConfig(),
) {
    companion object {
        private const val MinAggressivenessPercent = 70
        private const val MaxAggressivenessPercent = 130

        private val ChatPackageNames = setOf(
            "com.discord",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.slack",
            "com.snapchat.android",
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
        )

        private val EmailPackageNames = setOf(
            "ch.protonmail.android",
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.readdle.spark",
            "com.samsung.android.email.provider",
            "com.yahoo.mobile.client.android.mail",
        )
    }

    fun resolveProfile(context: AutocorrectAppContext): AutocorrectAppProfile {
        val normalizedPackageName = context.packageName
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (normalizedPackageName in ChatPackageNames) return AutocorrectAppProfile.CHAT
        if (normalizedPackageName in EmailPackageNames) return AutocorrectAppProfile.EMAIL

        return when {
            context.inputVariation in setOf(
                InputAttributes.Variation.EMAIL_ADDRESS,
                InputAttributes.Variation.EMAIL_SUBJECT,
                InputAttributes.Variation.WEB_EMAIL_ADDRESS,
            ) -> AutocorrectAppProfile.EMAIL
            context.inputVariation == InputAttributes.Variation.SHORT_MESSAGE -> AutocorrectAppProfile.CHAT
            context.imeAction == ImeOptions.Action.SEND -> AutocorrectAppProfile.CHAT
            else -> AutocorrectAppProfile.DEFAULT
        }
    }

    fun applyProfile(
        baseConfig: HighCertaintyAutocorrectConfig,
        profile: AutocorrectAppProfile,
    ): HighCertaintyAutocorrectConfig {
        if (!config.enabled) return baseConfig

        val aggressivenessFactor = profileAggressivenessPercent(profile).toDouble() / 100.0
        return baseConfig.copy(
            minConfidence = (baseConfig.minConfidence / aggressivenessFactor).coerceIn(0.50, 0.99),
            minConfidenceGap = (baseConfig.minConfidenceGap / aggressivenessFactor).coerceIn(0.0, 0.50),
            minInputLength = (baseConfig.minInputLength / aggressivenessFactor)
                .roundToInt()
                .coerceIn(2, 12),
        )
    }

    fun profileAggressivenessPercent(profile: AutocorrectAppProfile): Int {
        val rawPercent = when (profile) {
            AutocorrectAppProfile.CHAT -> config.chatAggressivenessPercent
            AutocorrectAppProfile.EMAIL -> config.emailAggressivenessPercent
            AutocorrectAppProfile.DEFAULT -> 100
        }
        return rawPercent.coerceIn(MinAggressivenessPercent, MaxAggressivenessPercent)
    }
}
