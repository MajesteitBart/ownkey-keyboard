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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.patrickgold.florisboard.ime.theme.ThemeGlassPreset
import dev.patrickgold.florisboard.ime.theme.ThemeKeyRadius
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extMyTheme
import dev.patrickgold.florisboard.ime.theme.extSignalTheme
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName

/** The keyboard theme ids Appearance offers. Theme ids change only when the user taps something. */
object SignalThemes {
    val Graphite = extSignalTheme("ownkey_signal_graphite")
    val Black = extSignalTheme("ownkey_signal_black")
    val Bone = extSignalTheme("ownkey_signal_bone")

    fun isSignal(id: ExtensionComponentName): Boolean = id.extensionId == Bone.extensionId

    /**
     * Themes Ownkey picked for the user: the Signal themes and the older Glass and Voxtral styles. Anything
     * else came from the theme manager and stays the user's choice.
     */
    fun isOwnkeyStyle(id: ExtensionComponentName): Boolean {
        return isSignal(id) ||
            id.componentId.startsWith("ownkey_glass_") ||
            id.componentId.startsWith("voxtral_") ||
            id.componentId == "ownkey_liquid_glass"
    }
}

/** The two dark keyboard styles shown as tiles. */
enum class DarkStyle(val themeId: ExtensionComponentName, val preset: ThemeGlassPreset, val palette: SignalPalette) {
    GRAPHITE(SignalThemes.Graphite, ThemeGlassPreset.SIGNAL, SignalPalette.Graphite),
    BLACK(SignalThemes.Black, ThemeGlassPreset.SIGNAL_BLACK, SignalPalette.Black);

    companion object {
        /** The style the night theme id stands for, or null for any other theme. */
        fun of(nightThemeId: ExtensionComponentName): DarkStyle? = entries.firstOrNull { it.themeId == nightThemeId }
    }
}

/** Theme mode as the segmented control shows it. */
enum class AppearanceMode(val mode: ThemeMode) {
    DARK(ThemeMode.ALWAYS_NIGHT),
    LIGHT(ThemeMode.ALWAYS_DAY),
    AUTO(ThemeMode.FOLLOW_SYSTEM);

    companion object {
        fun of(mode: ThemeMode): AppearanceMode = when (mode) {
            ThemeMode.ALWAYS_NIGHT -> DARK
            ThemeMode.ALWAYS_DAY -> LIGHT
            // FOLLOW_TIME is migrated to FOLLOW_SYSTEM; read it the same way meanwhile.
            ThemeMode.FOLLOW_SYSTEM, ThemeMode.FOLLOW_TIME -> AUTO
        }
    }
}

/** Pref writes for one Appearance tap. A null field stays as stored. */
data class ThemeWrites(
    val mode: ThemeMode? = null,
    val glassPreset: ThemeGlassPreset? = null,
    val dayThemeId: ExtensionComponentName? = null,
    val nightThemeId: ExtensionComponentName? = null,
)

object AppearanceChoices {
    /** Picking a dark style switches the night theme to it. */
    fun writesFor(style: DarkStyle): ThemeWrites =
        ThemeWrites(glassPreset = style.preset, nightThemeId = style.themeId)

    /**
     * Picking a mode. Light and Auto show the day theme, so an Ownkey style there moves to Signal Bone; a day
     * theme picked in the theme manager stays.
     */
    fun writesFor(mode: AppearanceMode, storedDayThemeId: ExtensionComponentName): ThemeWrites {
        val showsDayTheme = mode == AppearanceMode.LIGHT || mode == AppearanceMode.AUTO
        val moveDayTheme = showsDayTheme &&
            storedDayThemeId != SignalThemes.Bone &&
            SignalThemes.isOwnkeyStyle(storedDayThemeId)
        return ThemeWrites(
            mode = mode.mode,
            dayThemeId = SignalThemes.Bone.takeIf { moveDayTheme },
        )
    }
}

/** What Home shows as the Appearance value. */
enum class AppearanceSummary {
    GRAPHITE,
    BLACK,
    LIGHT,
    AUTO_GRAPHITE,
    AUTO_BLACK,
    CUSTOM;

    companion object {
        fun of(
            mode: ThemeMode,
            dayThemeId: ExtensionComponentName,
            nightThemeId: ExtensionComponentName,
        ): AppearanceSummary {
            val dark = DarkStyle.of(nightThemeId)
            val light = dayThemeId == SignalThemes.Bone
            return when (AppearanceMode.of(mode)) {
                AppearanceMode.DARK -> when (dark) {
                    DarkStyle.GRAPHITE -> GRAPHITE
                    DarkStyle.BLACK -> BLACK
                    null -> CUSTOM
                }
                AppearanceMode.LIGHT -> if (light) LIGHT else CUSTOM
                AppearanceMode.AUTO -> when {
                    !light -> CUSTOM
                    dark == DarkStyle.GRAPHITE -> AUTO_GRAPHITE
                    dark == DarkStyle.BLACK -> AUTO_BLACK
                    else -> CUSTOM
                }
            }
        }
    }
}

/** The accent presets, in display order. Ember is the default. */
enum class AccentPreset(val color: Color) {
    EMBER(Color(0xFFDE5F14)),
    LIME(Color(0xFFD5FF5F)),
    BLUE(Color(0xFF0A84FF)),
    VIOLET(Color(0xFFBF5AF2)),
    PINK(Color(0xFFFF375F)),
    MONO(Color(0xFFFFFFFF));

    companion object {
        /** The preset a stored accent matches. Unspecified (the old Material You choice) renders Ember. */
        fun of(accent: Color): AccentPreset? {
            if (!accent.isSpecified) return EMBER
            return entries.firstOrNull { it.color == accent.copy(alpha = 1f) }
        }
    }
}

/**
 * The Glass stylesheet id with new key borders or corner radius, or null when [id] isn't a Glass id. The
 * Glass styles predate the Signal themes; their border and radius rows only show for users who changed them.
 */
fun glassThemeVariant(
    id: ExtensionComponentName,
    showKeyBorders: Boolean,
    keyRadius: ThemeKeyRadius,
): ExtensionComponentName? {
    val match = GlassIdRegex.matchEntire(id.componentId) ?: return null
    if (id.extensionId != extMyTheme("").extensionId) return null
    val palette = match.groupValues[1]
    val border = if (showKeyBorders) "bordered" else "borderless"
    val radius = when (keyRadius) {
        ThemeKeyRadius.NONE -> "none"
        ThemeKeyRadius.SMALL -> "small"
        ThemeKeyRadius.MEDIUM -> "medium"
        ThemeKeyRadius.LARGE -> "large"
    }
    return extMyTheme("ownkey_glass_${palette}_${border}_$radius")
}

private val GlassIdRegex = Regex("ownkey_glass_([a-z]+)_(bordered|borderless)_(none|small|medium|large)")
