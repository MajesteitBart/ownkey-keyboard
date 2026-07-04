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

package dev.patrickgold.florisboard.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import dev.patrickgold.jetpref.datastore.model.collectAsState

/**
 * The user-selected accent color (Theme settings), falling back to the Liquid Glass default
 * accent. Use this instead of [OwnkeyBrand.Glass.Accent] in composables so accent changes apply
 * live to hardcoded surfaces like the mic pill and the AI rewrite panel.
 */
@Composable
fun ownkeyAccentColor(): Color {
    val prefs by FlorisPreferenceStore
    val accent by prefs.theme.accentColor.collectAsState()
    return accent.takeOrElse { OwnkeyBrand.Glass.Accent }
}

object OwnkeyBrand {
    val Key = Color(0xFF0A0B0C)
    val Graphite = Color(0xFF18191B)
    val Panel = Color(0xFF111315)
    val PanelRaised = Color(0xFF1C1D20)
    val Action = Color(0xFF25272D)
    val ActionPressed = Color(0xFF30333A)
    val Line = Color(0xFF2B3037)
    val Bone = Color(0xFFF3F1EC)
    val Ash = Color(0xFFB6BAC3)
    val SignalOrange = Color(0xFFF56C1E)
    val SignalAmber = Color(0xFFF5A524)
    val TrustBlue = Color(0xFF2F6BFF)
    val SuccessGreen = Color(0xFF3EDB83)
    val WarningYellow = Color(0xFFFFB84D)
    val ErrorRed = Color(0xFFFF7A7A)

    const val MotionFastMillis = 120
    const val MotionStandardMillis = 180

    /**
     * Dark Liquid Glass design system tokens (keyboard mockup spec). One accent, everything else
     * translucent grays; no borders, depth via inner top highlight and soft shadows.
     */
    object Glass {
        // Surface colors are opaque, pre-composited to the mockup's translucent glass appearance.
        // Actual translucency renders grainy on-device and lets drop shadows bleed through fills.
        val Stage = Color(0xFF12141D)
        val Canvas = Color(0xFF1A1E2B)
        val Key = Color(0xFF3E4253)
        val KeyPressed = Color(0xFF595F72)
        val Ink = Color(0xFFFFFFFF)
        val InkSoft = Color(0xB8FFFFFF)         // white 72%
        val Hint = Color(0x6BFFFFFF)            // white 42%
        val Accent = Color(0xFF0A84FF)
        val AccentDim = Color(0x590A84FF)       // accent 35%, glow only
        val Success = Color(0xFF30D158)
        val Danger = Color(0xFFFF453A)
        val Sheet = Color(0xFF232839)
        val CancelCapsule = Color(0xFF1E2230)
    }
}
