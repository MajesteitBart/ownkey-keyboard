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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.compose.contrastRatio
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggStylesheet
import java.io.File

private const val ThemeAssetDir = "src/main/assets/ime/theme"

private fun themeAssetDir(): File {
    val moduleDir = listOf(File("."), File("app")).map { it.absoluteFile }.first { File(it, ThemeAssetDir).isDirectory }
    return File(moduleDir, ThemeAssetDir)
}

/** Every bundled stylesheet with the component name the theme manager loads it under. */
private fun bundledStylesheets(): List<Pair<ExtensionComponentName, File>> {
    return themeAssetDir().listFiles().orEmpty().filter { it.isDirectory }.flatMap { extDir ->
        File(extDir, "stylesheets").listFiles().orEmpty().filter { it.extension == "json" }.map { file ->
            ExtensionComponentName(extensionId = extDir.name, componentId = file.nameWithoutExtension) to file
        }
    }.sortedBy { it.first.toString() }
}

private fun defines(json: String): Map<String, String> {
    val root = Json.parseToJsonElement(json).jsonObject
    val defines = root["@defines"]?.jsonObject ?: JsonObject(emptyMap())
    return defines.mapValues { (_, value) -> value.jsonPrimitive.content }
}

private fun Map<String, String>.color(key: String): Color {
    val raw = this[key].shouldNotBeNull()
    return parseHexColor(raw).shouldNotBeNull()
}

private val Ember = Color(0xFFDE5F14)
private val PresetAccents = listOf(
    "Ember" to Ember,
    "Lime" to Color(0xFFD5FF5F),
    "Blue" to Color(0xFF0A84FF),
    "Violet" to Color(0xFFBF5AF2),
    "Pink" to Color(0xFFFF375F),
    "Mono" to Color(0xFFFFFFFF),
)

/**
 * Sub-element names the legacy (non-Signal) sheets still style but no code queries. They parse and do
 * nothing; the Signal sheets must not carry them.
 */
private val LegacyUnusedElementNames = setOf("smartbar-action-key-icon")

class SignalStylesheetTest : FunSpec({
    val elementNames = FlorisImeUi.elementNames.toSet()
    val sheets = bundledStylesheets()
    val signalSheets = sheets.filter { it.first.extensionId == SignalThemesExtensionId }

    test("the three Signal stylesheets are bundled") {
        signalSheets.map { it.first.componentId } shouldContainAll listOf(
            "ownkey_signal_black",
            "ownkey_signal_graphite",
            "ownkey_signal_bone",
        )
        sheets.size shouldBeGreaterThan 3
    }

    context("every bundled stylesheet and its accent-injected form parses with the keyboard's loader") {
        sheets.forEach { (name, file) ->
            test("$name") {
                val json = file.readText()
                SnyggStylesheet.fromJson(json).getOrThrow()
                PresetAccents.forEach { (accentName, accent) ->
                    withClue("$accentName injected") {
                        SnyggStylesheet.fromJson(injectAccent(json, name, accent.toArgb())).getOrThrow()
                    }
                }
            }
        }
    }

    context("every element rule names a keyboard UI element") {
        sheets.forEach { (name, file) ->
            test("$name") {
                val isSignal = name.extensionId == SignalThemesExtensionId
                val stylesheet = SnyggStylesheet.fromJson(injectAccent(file.readText(), name, Ember.toArgb())).getOrThrow()
                val unknown = stylesheet.rules.keys.filterIsInstance<SnyggElementRule>()
                    .map { it.elementName }
                    .filter { it !in elementNames && (isSignal || it !in LegacyUnusedElementNames) }
                    .distinct()
                unknown.shouldBeEmpty()
            }
        }
    }

    context("Signal contrast") {
        signalSheets.forEach { (name, file) ->
            val d = defines(file.readText())
            val background = d.color("--background")
            val key = d.color("--surface")
            val functionKey = d.color("--surface-fn")
            val label = d.color("--on-surface")
            val secondary = d.color("--on-surface-variant")
            val hint = d.color("--on-hint")
            val toolbarIcon = d.color("--toolbar-icon")
            val popup = d.color("--popup-surface")

            test("${name.componentId}: key labels reach 7:1 on letter and function keys") {
                contrastRatio(label, key) shouldBeGreaterThanOrEqual 7.0
                contrastRatio(label, functionKey) shouldBeGreaterThanOrEqual 7.0
            }
            test("${name.componentId}: secondary text and hints reach 4.5:1 on the letter key") {
                contrastRatio(secondary, key) shouldBeGreaterThanOrEqual 4.5
                contrastRatio(hint, key) shouldBeGreaterThanOrEqual 4.5
            }
            test("${name.componentId}: toolbar icons reach 3:1 on the background") {
                contrastRatio(toolbarIcon, background) shouldBeGreaterThanOrEqual 3.0
            }
            test("${name.componentId}: candidates reach 4.5:1 on the background") {
                contrastRatio(d.color("--candidate"), background) shouldBeGreaterThanOrEqual 4.5
            }
            test("${name.componentId}: enter keeps a 4.5:1 glyph and is not the accent") {
                contrastRatio(d.color("--on-enter"), d.color("--enter")) shouldBeGreaterThanOrEqual 4.5
            }
            test("${name.componentId}: pills and the focused popup element read at 4.5:1") {
                contrastRatio(d.color("--on-pill"), d.color("--pill")) shouldBeGreaterThanOrEqual 4.5
                contrastRatio(d.color("--on-focused-popup"), d.color("--focused-popup-surface")) shouldBeGreaterThanOrEqual 4.5
            }
            test("${name.componentId}: popups stand apart from the keys by fill, or by a shadow when nothing is lighter") {
                if (popup == key) {
                    withClue("a popup as white as the keys needs its shadow") {
                        d["--popup-shadow"] shouldNotBe "0dp"
                    }
                } else {
                    popup shouldNotBe key
                    d["--popup-shadow"] shouldBe "0dp"
                }
            }
            test("${name.componentId}: function keys and letter keys differ from the background") {
                key shouldNotBe background
                functionKey shouldNotBe background
                functionKey shouldNotBe key
            }
        }
    }

    context("Signal rules") {
        signalSheets.forEach { (name, file) ->
            val stylesheet = SnyggStylesheet.fromJson(file.readText()).getOrThrow()
            val keyRules = stylesheet.rules.keys.filterIsInstance<SnyggElementRule>().filter { it.elementName == "key" }

            test("${name.componentId}: the function-key rule never covers enter or space") {
                val functionKeyRule = keyRules.single { rule ->
                    rule.attributes["code"]?.contains("-11") == true && rule.attributes.size == 1 &&
                        rule.selector == org.florisboard.lib.snygg.SnyggSelector.NONE
                }
                val codes = functionKeyRule.attributes["code"].shouldNotBeNull()
                codes shouldNotContain "10"
                codes shouldNotContain "32"
                codes shouldContainAll listOf("-7", "-202", "-212", "-227", "-233", "9", "-13", "-1")
            }
            test("${name.componentId}: text never sits on the accent") {
                val json = Json.parseToJsonElement(file.readText()).jsonObject
                val textOnPrimary = json.filterKeys { !it.startsWith("@") && !it.startsWith("$") }.filter { (selector, body) ->
                    val props = body.jsonObject
                    val onPrimaryBackground = props["background"]?.jsonPrimitive?.content == "var(--primary)"
                    onPrimaryBackground && !selector.startsWith("window")
                }.keys
                textOnPrimary.shouldBeEmpty()
            }
            test("${name.componentId}: no borders anywhere") {
                val json = Json.parseToJsonElement(file.readText()).jsonObject
                val bordered = json.filter { (selector, body) ->
                    !selector.startsWith("@") && !selector.startsWith("$") &&
                        (body.jsonObject["border-width"]?.jsonPrimitive?.content ?: "0dp") != "0dp"
                }.keys
                bordered.shouldBeEmpty()
            }
        }
    }

    test("the three Signal stylesheets differ only in their defines") {
        val bodies = signalSheets.map { (_, file) ->
            Json.parseToJsonElement(file.readText()).jsonObject.filterKeys { it != "@defines" }
        }
        bodies.distinct().size shouldBe 1
        val defineKeys = signalSheets.map { (_, file) -> defines(file.readText()).keys }
        defineKeys.distinct().size shouldBe 1
    }

    test("the stored Signal defines already carry the Ember accent they would get injected") {
        signalSheets.forEach { (name, file) ->
            val json = file.readText()
            withClue(name.componentId) {
                defines(injectAccent(json, name, Ember.toArgb())) shouldBe defines(json)
            }
        }
    }
})
