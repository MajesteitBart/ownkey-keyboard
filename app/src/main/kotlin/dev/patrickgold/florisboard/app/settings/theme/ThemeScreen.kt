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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.theme.ThemeKeyRadius
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extMyTheme
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

private fun styleThemeId(
    isNight: Boolean,
    showKeyBorders: Boolean,
    keyRadius: ThemeKeyRadius,
): ExtensionComponentName {
    val dayNight = if (isNight) "night" else "day"
    val border = if (showKeyBorders) "bordered" else "borderless"
    val radius = when (keyRadius) {
        ThemeKeyRadius.NONE -> "none"
        ThemeKeyRadius.SMALL -> "small"
        ThemeKeyRadius.MEDIUM -> "medium"
        ThemeKeyRadius.LARGE -> "large"
    }
    return extMyTheme("voxtral_${dayNight}_${border}_$radius")
}

@Composable
fun ThemeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__theme__title)
    previewFieldVisible = true

    val context = LocalContext.current

    content {
        val mode by prefs.theme.mode.collectAsState()
        val showKeyBorders by prefs.theme.showKeyBorders.collectAsState()
        val keyRadius by prefs.theme.keyRadius.collectAsState()
        val dayThemeId by prefs.theme.dayThemeId.collectAsState()
        val nightThemeId by prefs.theme.nightThemeId.collectAsState()

        val targetDayThemeId = styleThemeId(
            isNight = false,
            showKeyBorders = showKeyBorders,
            keyRadius = keyRadius,
        )
        val targetNightThemeId = styleThemeId(
            isNight = true,
            showKeyBorders = showKeyBorders,
            keyRadius = keyRadius,
        )

        LaunchedEffect(mode, targetDayThemeId, targetNightThemeId, dayThemeId, nightThemeId) {
            if (mode == ThemeMode.FOLLOW_TIME) {
                prefs.theme.mode.set(ThemeMode.FOLLOW_SYSTEM)
            }
            if (dayThemeId != targetDayThemeId) {
                prefs.theme.dayThemeId.set(targetDayThemeId)
            }
            if (nightThemeId != targetNightThemeId) {
                prefs.theme.nightThemeId.set(targetNightThemeId)
            }
        }

        ListPreference(
            prefs.theme.mode,
            icon = Icons.Default.BrightnessAuto,
            title = stringRes(R.string.pref__theme__mode__label),
            entries = listPrefEntries {
                entry(
                    key = ThemeMode.ALWAYS_DAY,
                    label = stringRes(R.string.pref__theme__appearance__light),
                )
                entry(
                    key = ThemeMode.ALWAYS_NIGHT,
                    label = stringRes(R.string.pref__theme__appearance__dark),
                )
                entry(
                    key = ThemeMode.FOLLOW_SYSTEM,
                    label = stringRes(R.string.pref__theme__appearance__system),
                )
            },
        )

        SwitchPreference(
            prefs.theme.showKeyBorders,
            title = stringRes(R.string.pref__theme__show_key_borders__label),
            summary = stringRes(R.string.pref__theme__show_key_borders__summary),
        )

        ListPreference(
            prefs.theme.keyRadius,
            title = stringRes(R.string.pref__theme__key_radius__label),
            entries = enumDisplayEntriesOf(ThemeKeyRadius::class),
        )

        ColorPickerPreference(
            pref = prefs.theme.accentColor,
            title = stringRes(R.string.pref__theme__theme_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.ColorLens,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = true,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )
    }
}
