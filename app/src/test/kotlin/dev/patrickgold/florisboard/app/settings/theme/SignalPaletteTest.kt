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

import androidx.compose.ui.graphics.toArgb
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private const val SignalStylesheetDir = "src/main/assets/ime/theme/ai.bvdm.ownkey.themes.signal/stylesheets"

private fun signalStylesheet(name: String): File {
    // Gradle runs unit tests from the module dir; an IDE may run them from the repository root.
    val moduleDir = listOf(File("."), File("app")).map { it.absoluteFile }
        .first { File(it, SignalStylesheetDir).isDirectory }
    return File(File(moduleDir, SignalStylesheetDir), "$name.json")
}

/** The `@defines` of a stylesheet, parsed as JSON so whitespace, order and key quoting don't matter. */
private fun definesOf(file: File): Map<String, String> {
    val root = Json.parseToJsonElement(file.readText()).jsonObject
    val defines = root["@defines"].shouldNotBeNull().jsonObject
    return defines.mapValues { (_, value) -> value.jsonPrimitive.content.trim() }
}

/** `#RGB`, `#RRGGBB` or `#RRGGBBAA` (Snygg's order) as an ARGB int, case-insensitive; null for anything else. */
private fun parseDefineColor(raw: String): Int? {
    val hex = raw.removePrefix("#").lowercase()
    if (!raw.startsWith("#") || hex.any { it !in "0123456789abcdef" }) return null
    val rgba = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("") + "ff"
        6 -> hex + "ff"
        8 -> hex
        else -> return null
    }
    val rgb = rgba.substring(0, 6).toLong(16)
    val alpha = rgba.substring(6, 8).toLong(16)
    return ((alpha shl 24) or rgb).toInt()
}

class SignalPaletteTest : FunSpec({
    val palettes = listOf(
        "ownkey_signal_black" to SignalPalette.Black,
        "ownkey_signal_graphite" to SignalPalette.Graphite,
        "ownkey_signal_bone" to SignalPalette.Bone,
    )

    for ((stylesheet, palette) in palettes) {
        test("$stylesheet: the Appearance tile colours equal the stylesheet defines") {
            val defines = definesOf(signalStylesheet(stylesheet))
            for ((define, color) in palette.defines) {
                withClue("$stylesheet $define") {
                    val raw = defines[define].shouldNotBeNull()
                    parseDefineColor(raw) shouldBe color.toArgb()
                }
            }
        }
    }

    test("the define colour parser reads every hex form a stylesheet uses") {
        parseDefineColor("#1C1C1E") shouldBe 0xFF1C1C1E.toInt()
        parseDefineColor("#1c1c1e") shouldBe 0xFF1C1C1E.toInt()
        parseDefineColor("#FFF") shouldBe 0xFFFFFFFF.toInt()
        parseDefineColor("#11111161") shouldBe 0x61111111
        parseDefineColor("var(--surface)") shouldBe null
    }
})
