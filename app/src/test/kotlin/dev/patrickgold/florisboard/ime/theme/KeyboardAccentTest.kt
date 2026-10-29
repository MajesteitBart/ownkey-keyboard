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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.compose.contrastRatio
import org.florisboard.lib.compose.onAccentFor

class KeyboardAccentTest : FunSpec({
    val ember = Color(0xFFDE5F14)
    val lime = Color(0xFFD5FF5F)
    val blue = Color(0xFF0A84FF)
    val violet = Color(0xFFBF5AF2)
    val pink = Color(0xFFFF375F)
    val mono = Color(0xFFFFFFFF)
    val presets = listOf("Ember" to ember, "Lime" to lime, "Blue" to blue, "Violet" to violet, "Pink" to pink, "Mono" to mono)

    // Background and label colour of each Signal stylesheet.
    val black = Color(0xFF000000) to Color.White
    val graphite = Color(0xFF1C1C1E) to Color.White
    val bone = Color(0xFFE8E6E1) to Color(0xFF111111)

    context("effective accent on the dark keyboards") {
        listOf("Black" to black, "Graphite" to graphite).forEach { (themeName, theme) ->
            presets.forEach { (accentName, accent) ->
                test("$accentName passes unchanged on $themeName") {
                    effectiveKeyboardAccent(accent, theme.first, theme.second) shouldBe accent
                }
            }
        }
    }

    context("effective accent on Bone") {
        val (background, label) = bone
        test("Ember is darkened just enough to reach 3:1") {
            val effective = effectiveKeyboardAccent(ember, background, label)
            effective shouldBe Color(0xFFD45B14)
            contrastRatio(effective, background) shouldBeGreaterThanOrEqual 3.0
            contrastRatio(ember, background) shouldBeLessThan 3.0
        }
        listOf("Blue" to blue, "Violet" to violet, "Pink" to pink).forEach { (name, accent) ->
            test("$name is mixed toward the label colour until it reaches 3:1") {
                val effective = effectiveKeyboardAccent(accent, background, label)
                contrastRatio(effective, background) shouldBeGreaterThanOrEqual 3.0
                // One 5% step at a time: the previous step must still have been below 3:1.
                effective shouldBe accentAfterSteps(accent, label, background)
            }
        }
        listOf("Lime" to lime, "Mono" to mono).forEach { (name, accent) ->
            test("$name falls back to the label colour") {
                effectiveKeyboardAccent(accent, background, label) shouldBe label
            }
        }
    }

    test("an unspecified accent (Material You) renders as Ember") {
        effectiveKeyboardAccent(Color.Unspecified, graphite.first, graphite.second) shouldBe ember
    }

    context("onAccentFor on the effective accent") {
        test("white glyphs on Ember, Blue, Violet and Pink on every Signal keyboard") {
            listOf(black, graphite, bone).forEach { (background, label) ->
                listOf(ember, blue, violet, pink).forEach { accent ->
                    onAccentFor(effectiveKeyboardAccent(accent, background, label)) shouldBe Color.White
                }
            }
        }
        test("black glyphs on Lime and Mono on the dark keyboards") {
            listOf(black, graphite).forEach { (background, label) ->
                onAccentFor(effectiveKeyboardAccent(lime, background, label)) shouldBe Color.Black
                onAccentFor(effectiveKeyboardAccent(mono, background, label)) shouldBe Color.Black
            }
        }
        test("white glyphs on the near-black mic Lime and Mono become on Bone") {
            onAccentFor(effectiveKeyboardAccent(lime, bone.first, bone.second)) shouldBe Color.White
            onAccentFor(effectiveKeyboardAccent(mono, bone.first, bone.second)) shouldBe Color.White
        }
    }

    context("injectAccent") {
        val signalBone = ExtensionComponentName(SignalThemesExtensionId, "ownkey_signal_bone")
        val signalGraphite = ExtensionComponentName(SignalThemesExtensionId, "ownkey_signal_graphite")
        val glass = ExtensionComponentName("org.florisboard.themes.my", "ownkey_glass_night_borderless_medium")
        val other = ExtensionComponentName("org.florisboard.themes", "floris_day")
        val sheet = """
            {"@defines": {"--primary": "#000000", "--background": "#E8E6E1", "--on-surface": "#111111",
            "--surface-fn": "#D5D2CB", "--surface": "#FFFFFF"}, "key": {"background": "var(--surface)"}}
        """.trimIndent()

        fun definesOf(json: String) = Json.parseToJsonElement(json).jsonObject["@defines"]!!.jsonObject
            .mapValues { it.value.jsonPrimitive.content }

        test("Signal gets the effective accent, its pressed variant, glyph colour and glide trail") {
            val d = definesOf(injectAccent(sheet, signalBone, ember.toArgb()))
            d["--primary"] shouldBe "#D45B14"
            d["--secondary"] shouldBe "#D45B14"
            d["--secondary-variant"] shouldBe "#D45B14"
            d["--primary-variant"] shouldBe "#BB5012"
            d["--on-primary"] shouldBe "#FFFFFF"
            d["--glide-trail"] shouldBe "#D45B14B3"
            // Below 3:1 on the Bone function key, caps lock keeps the label colour; its glyph carries the state.
            d["--caps-lock"] shouldBe "#111111"
            d["--surface"] shouldBe "#FFFFFF"
        }
        test("Mono on dark Signal writes a black glyph colour") {
            val dark = sheet.replace("#E8E6E1", "#1C1C1E").replace("#111111", "#FFFFFF").replace("#D5D2CB", "#2C2C2E")
            val d = definesOf(injectAccent(dark, signalGraphite, mono.toArgb()))
            d["--primary"] shouldBe "#FFFFFF"
            d["--on-primary"] shouldBe "#000000"
            d["--caps-lock"] shouldBe "#FFFFFF"
        }
        test("Glass keeps its old accent injection") {
            val d = definesOf(injectAccent(sheet, glass, blue.toArgb()))
            d["--primary"] shouldBe "#0a84ff"
            d["--secondary"] shouldBe "#0a84ff"
            d["--primary-variant"] shouldBe "#0874e0"
            d.containsKey("--on-primary") shouldBe false
        }
        test("other themes come back unchanged") {
            injectAccent(sheet, other, ember.toArgb()) shouldBe sheet
        }
        test("a sheet that doesn't parse comes back unchanged") {
            injectAccent("{not json", signalBone, ember.toArgb()) shouldBe "{not json"
        }
    }

    test("parseHexColor reads both bundled forms") {
        parseHexColor("#DE5F14") shouldBe ember
        parseHexColor("#DE5F14B3") shouldBe Color(0xB3DE5F14)
        parseHexColor("var(--x)") shouldBe null
    }
})

private fun accentAfterSteps(accent: Color, label: Color, background: Color): Color {
    var step = 0
    var mixed = accent
    while (contrastRatio(mixed, background) < 3.0) {
        step++
        mixed = blendSrgb(accent, label, step * 0.05f)
    }
    return mixed
}
