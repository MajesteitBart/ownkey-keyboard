/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.onAccentFor

/**
 * The fixed Ownkey settings scheme: pure black, Card and Raised fill steps, one accent. Every role is
 * set so no baseline M3 colour can leak into jetpref or stock components.
 */
fun ownkeyColorScheme(accent: Color): ColorScheme {
    val primary = if (accent.isSpecified) accent else OwnkeyTokens.DefaultAccent
    val onPrimary = onAccentFor(primary)
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = OwnkeyTokens.Raised,
        onPrimaryContainer = OwnkeyTokens.Label,
        inversePrimary = primary,
        secondary = OwnkeyTokens.Secondary,
        onSecondary = OwnkeyTokens.Black,
        secondaryContainer = OwnkeyTokens.Raised,
        onSecondaryContainer = OwnkeyTokens.Label,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = OwnkeyTokens.Raised,
        onTertiaryContainer = OwnkeyTokens.Label,
        background = OwnkeyTokens.Black,
        onBackground = OwnkeyTokens.Label,
        surface = OwnkeyTokens.Black,
        onSurface = OwnkeyTokens.Label,
        surfaceVariant = OwnkeyTokens.Raised,
        onSurfaceVariant = OwnkeyTokens.Secondary,
        surfaceTint = Color.Transparent,
        inverseSurface = OwnkeyTokens.Label,
        inverseOnSurface = OwnkeyTokens.Black,
        error = OwnkeyTokens.Danger,
        onError = OwnkeyTokens.Black,
        errorContainer = OwnkeyTokens.Raised,
        onErrorContainer = OwnkeyTokens.Danger,
        outline = OwnkeyTokens.Outline,
        outlineVariant = OwnkeyTokens.Raised,
        scrim = OwnkeyTokens.Black,
        surfaceBright = OwnkeyTokens.Raised,
        surfaceContainer = OwnkeyTokens.Card,
        surfaceContainerHigh = OwnkeyTokens.Card,
        surfaceContainerHighest = OwnkeyTokens.Raised,
        surfaceContainerLow = OwnkeyTokens.Card,
        surfaceContainerLowest = OwnkeyTokens.Card,
        surfaceDim = OwnkeyTokens.Black,
    )
}

val OwnkeyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The settings app is dark only. [theme] is kept for source compatibility and ignored; the stored
 * settings theme and settings accent are no longer read.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val accent = ownkeyAccentColor()
    val colors = remember(accent) { ownkeyColorScheme(accent) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = OwnkeyShapes,
        content = content,
    )
}
