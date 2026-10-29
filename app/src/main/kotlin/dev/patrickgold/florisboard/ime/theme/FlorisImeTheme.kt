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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme

@Composable
fun FlorisImeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val windowController = LocalWindowController.current

    val keyboardManager by context.keyboardManager()
    val themeManager by context.themeManager()

    val prefs by FlorisPreferenceStore
    val accentColor by prefs.theme.accentColor.collectAsState()

    val activeThemeInfo by themeManager.activeThemeInfo.collectAsState()

    val assetResolver = remember(activeThemeInfo) {
        FlorisAssetResolver(context, activeThemeInfo)
    }
    val snyggTheme = rememberSnyggTheme(activeThemeInfo.stylesheet, assetResolver)
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val fontScale by remember { derivedStateOf { windowSpec.fontScale } }

    val state by keyboardManager.activeState.collectAsState()
    val attributes = mapOf(
        FlorisImeUi.Attr.Mode to state.keyboardMode.toString(),
        FlorisImeUi.Attr.ShiftState to state.inputShiftState.toString(),
    )

    MaterialTheme {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle.Default,
        ) {
            ProvideSnyggTheme(
                snyggTheme = snyggTheme,
                dynamicAccentColor = accentColor,
                fontSizeMultiplier = fontScale,
                assetResolver = assetResolver,
                rootAttributes = attributes,
                content = { ProvideKeyboardColorScheme(content) },
                materialYouFlags = activeThemeInfo.config.materialYouFlags
            )
        }
    }
}

/**
 * Gives Material components inside the keyboard a colour scheme built from the keyboard theme, so library
 * defaults (scrollbars, unset component colours) follow the active stylesheet instead of the baseline
 * Material palette. Keyboard code still passes explicit colours; this is the safety net.
 */
@Composable
private fun ProvideKeyboardColorScheme(content: @Composable () -> Unit) {
    val palette = rememberKeyboardPalette()
    val accent = rememberKeyboardAccent()
    val colorScheme = remember(palette, accent) {
        val base = if (palette.isLight) lightColorScheme() else darkColorScheme()
        base.copy(
            primary = accent.color,
            onPrimary = accent.onColor,
            secondary = accent.color,
            onSecondary = accent.onColor,
            tertiary = accent.color,
            onTertiary = accent.onColor,
            background = palette.background,
            onBackground = palette.ink,
            surface = palette.background,
            onSurface = palette.ink,
            surfaceVariant = palette.key,
            onSurfaceVariant = palette.inkSecondary,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = palette.background,
            surfaceContainerLow = palette.key,
            surfaceContainer = palette.key,
            surfaceContainerHigh = palette.popup,
            surfaceContainerHighest = palette.keyPressed,
            inverseSurface = palette.ink,
            inverseOnSurface = palette.background,
            outline = palette.inkSecondary,
            outlineVariant = palette.key,
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        // MaterialTheme resets the text style; the keyboard keeps the platform default, without Inter.
        CompositionLocalProvider(LocalTextStyle provides TextStyle.Default, content = content)
    }
}
