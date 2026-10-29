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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.contrastRatio
import org.florisboard.lib.compose.onAccentFor
import org.florisboard.lib.compose.relativeLuminance
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.roundToInt

/** Extension id of the Ownkey Signal keyboard themes (Graphite, Black, Bone). */
const val SignalThemesExtensionId = "ai.bvdm.ownkey.themes.signal"

/** Accent-to-background contrast the keyboard needs for the mic, rings and other non-text accents. */
private const val AccentMinContrast = 3.0

/**
 * The accent as the keyboard draws it on [background]. The user's accent if it reaches 3:1 against the
 * keyboard background. Otherwise a light accent (Lime, Mono on Bone) becomes [onSurface], and a darker one
 * is mixed toward [onSurface] in 5% steps until it reaches 3:1 (Ember on Bone becomes `#D45B14`).
 */
fun effectiveKeyboardAccent(accent: Color, background: Color, onSurface: Color): Color {
    val base = if (accent.isSpecified) accent.copy(alpha = 1f) else OwnkeyTokens.DefaultAccent
    if (!background.isSpecified || !onSurface.isSpecified) return base
    if (contrastRatio(base, background) >= AccentMinContrast) return base
    if (relativeLuminance(base) > 0.5) return onSurface.copy(alpha = 1f)
    for (step in 1..20) {
        val mixed = blendSrgb(base, onSurface, step * 0.05f)
        if (contrastRatio(mixed, background) >= AccentMinContrast) return mixed
    }
    return onSurface.copy(alpha = 1f)
}

/** Straight sRGB channel blend rounded to 8 bits, like `ColorUtils.blendARGB`, so results are stable hex values. */
internal fun blendSrgb(from: Color, to: Color, ratio: Float): Color {
    val inverse = 1f - ratio
    fun channel(a: Float, b: Float): Int = ((a * inverse + b * ratio) * 255f).roundToInt().coerceIn(0, 255)
    return Color(
        red = channel(from.red, to.red),
        green = channel(from.green, to.green),
        blue = channel(from.blue, to.blue),
    )
}

/** The accent and the glyph colour on it, as the keyboard draws them. */
@Immutable
data class KeyboardAccent(val color: Color, val onColor: Color)

/**
 * The one place keyboard Compose code gets its accent: the mic, the recording ring and dot, the waveform,
 * the hold ring and the transcribing arc. It matches the `--primary` that [injectAccent] writes into the
 * Signal stylesheets, and gives the same treatment to any other theme from its window background and key
 * label colour. Recomputed only when the accent or the theme changes.
 */
@Composable
fun rememberKeyboardAccent(): KeyboardAccent {
    val prefs by FlorisPreferenceStore
    val accent by prefs.theme.accentColor.collectAsState()
    val background = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName).background()
    val onSurface = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName).foreground()
    return remember(accent, background, onSurface) {
        val color = effectiveKeyboardAccent(accent, background, onSurface)
        KeyboardAccent(color = color, onColor = onAccentFor(color))
    }
}

private fun isGlassTheme(name: ExtensionComponentName): Boolean =
    name.componentId.startsWith("ownkey_glass") || name.componentId == "ownkey_liquid_glass"

/**
 * Writes the user's accent into a stylesheet's `@defines` before it is parsed. Other themes come back
 * unchanged.
 *
 * - Ownkey Glass keeps its old behaviour: `--primary`/`--secondary` get the accent, the variants the accent
 *   12% toward black.
 * - Ownkey Signal gets the [effective accent][effectiveKeyboardAccent] for its `--background`:
 *   `--primary`, `--secondary` and `--secondary-variant` are that accent, `--primary-variant` is it mixed
 *   12% toward black, `--on-primary` is [onAccentFor] it, `--glide-trail` is it at 70% alpha, and
 *   `--caps-lock` is it when it reaches 3:1 on the function-key fill, otherwise the label colour.
 */
internal fun injectAccent(json: String, name: ExtensionComponentName, accentArgb: Int): String {
    val isSignal = name.extensionId == SignalThemesExtensionId
    if (!isSignal && !isGlassTheme(name)) return json
    return try {
        val root = Json.parseToJsonElement(json).jsonObject
        val defines = root["@defines"]?.jsonObject ?: return json
        val accent = Color(accentArgb)
        val replacements = if (isSignal) {
            val background = defines.colorOrNull("--background") ?: return json
            val onSurface = defines.colorOrNull("--on-surface") ?: return json
            val effective = effectiveKeyboardAccent(accent, background, onSurface)
            val functionKey = defines.colorOrNull("--surface-fn")
            val capsLock = if (functionKey == null || contrastRatio(effective, functionKey) >= AccentMinContrast) {
                effective
            } else {
                onSurface.copy(alpha = 1f)
            }
            mapOf(
                "--primary" to effective.toHex(),
                "--primary-variant" to blendSrgb(effective, Color.Black, 0.12f).toHex(),
                "--on-primary" to onAccentFor(effective).toHex(),
                "--secondary" to effective.toHex(),
                "--secondary-variant" to effective.toHex(),
                "--glide-trail" to effective.toHex(alpha = 0xB3),
                "--caps-lock" to capsLock.toHex(),
            )
        } else {
            val accentHex = String.format("#%06x", accentArgb and 0xFFFFFF)
            val pressedHex = String.format("#%06x", truncatingBlendToBlack(accentArgb, 0.12f))
            mapOf(
                "--primary" to accentHex,
                "--primary-variant" to pressedHex,
                "--secondary" to accentHex,
                "--secondary-variant" to pressedHex,
            )
        }
        val newDefines = JsonObject(defines.toMutableMap().apply {
            replacements.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        })
        JsonObject(root.toMutableMap().apply { put("@defines", newDefines) }).toString()
    } catch (_: Exception) {
        json
    }
}

/** `ColorUtils.blendARGB(argb, BLACK, ratio)` without Android, which truncates each channel. RGB only. */
private fun truncatingBlendToBlack(argb: Int, ratio: Float): Int {
    val inverse = 1f - ratio
    val r = ((argb shr 16 and 0xFF) * inverse).toInt()
    val g = ((argb shr 8 and 0xFF) * inverse).toInt()
    val b = ((argb and 0xFF) * inverse).toInt()
    return (r shl 16) or (g shl 8) or b
}

private fun JsonObject.colorOrNull(key: String): Color? {
    val raw = runCatching { this[key]?.jsonPrimitive?.content }.getOrNull() ?: return null
    return parseHexColor(raw)
}

/** Parses `#RRGGBB` and `#RRGGBBAA`, the two forms the bundled stylesheets use. */
internal fun parseHexColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) {
        Color(0xFF000000L or value)
    } else {
        val rgb = value shr 8
        val alpha = value and 0xFF
        Color((alpha shl 24) or rgb)
    }
}

private fun Color.toHex(alpha: Int? = null): String {
    val rgb = toArgb() and 0xFFFFFF
    return if (alpha == null) String.format("#%06X", rgb) else String.format("#%06X%02X", rgb, alpha)
}
