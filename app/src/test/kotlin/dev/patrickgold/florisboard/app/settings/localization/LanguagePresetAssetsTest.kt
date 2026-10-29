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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.keyboard.KeyboardExtension
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.extCorePopupMapping
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

private const val KeyboardAssetDir = "src/main/assets/ime/keyboard"

private fun keyboardAssetDir(): File {
    val moduleDir = listOf(File("."), File("app")).map { it.absoluteFile }.first { File(it, KeyboardAssetDir).isDirectory }
    return File(moduleDir, KeyboardAssetDir)
}

private fun keyboardExtension(id: String): KeyboardExtension =
    ExtensionJsonConfig.decodeFromString(File(keyboardAssetDir(), "$id/extension.json").readText())

/** The Add language screen's names for a preset: the chosen display form, the native name and the English name. */
private fun namesOf(preset: SubtypePreset): List<String> = listOf(
    preset.locale.nameFor(DisplayLanguageNamesIn.SYSTEM_LOCALE),
    preset.locale.displayName(preset.locale),
    preset.locale.displayName(FlorisLocale.ENGLISH),
)

class LanguagePresetAssetsTest : FunSpec({
    val localization = keyboardExtension("org.florisboard.localization")
    val presets = localization.subtypePresets
    val dutch = FlorisLocale.fromTag("nl-NL")

    test("the Dutch preset parses and every component it names exists") {
        val preset = presets.firstOrNull { it.locale == dutch }.shouldNotBeNull()
        preset.composer shouldBe ExtensionComponentName("org.florisboard.composers", "appender")
        preset.currencySet shouldBe ExtensionComponentName("org.florisboard.currencysets", "euro")
        preset.preferred.characters shouldBe ExtensionComponentName("org.florisboard.layouts", "qwerty")

        keyboardExtension("org.florisboard.composers").composers.map { it.id } shouldContain preset.composer.componentId
        keyboardExtension("org.florisboard.currencysets").currencySets.map { it.id } shouldContain
            preset.currencySet.componentId
        val layouts = keyboardExtension("org.florisboard.layouts").layouts
        LayoutType.entries.forEach { type ->
            val layout = preset.preferred[type] ?: return@forEach
            withClue("$type layout $layout") {
                layouts[type.id].orEmpty().map { it.id } shouldContain layout.componentId
            }
        }
        preset.popupMapping.extensionId shouldBe localization.meta.id
        val mapping = localization.popupMappings.firstOrNull { it.id == preset.popupMapping.componentId }.shouldNotBeNull()
        File(keyboardAssetDir(), "${localization.meta.id}/${mapping.mappingFile()}").isFile shouldBe true
    }

    test("searching Dutch, Nederlands or nl finds the Dutch preset") {
        listOf("Dutch", "Nederlands", "nl", " dutch ").forEach { query ->
            withClue("query \"$query\"") {
                val sections = sectionPresets(
                    presets = presets,
                    localeOf = { it.locale },
                    namesOf = ::namesOf,
                    phoneLocales = listOf(FlorisLocale.fromTag("en-US")),
                    query = query,
                )
                (sections.suggested + sections.others).map { it.locale } shouldContain dutch
            }
        }
    }

    test("a Dutch phone suggests the Dutch preset first") {
        val sections = sectionPresets(
            presets = presets,
            localeOf = { it.locale },
            namesOf = ::namesOf,
            phoneLocales = listOf(dutch),
            query = "",
        )
        sections.suggested.map { it.locale } shouldBe listOf(dutch)
    }

    test("a language without a preset still gets a complete, saveable subtype in the editor") {
        // What the new-language editor holds before a language is picked, then after picking Frisian.
        val unset = ExtensionComponentName("00", "00")
        val frisian = FlorisLocale.fromTag("fy-NL")
        presets.none { it.locale.language == frisian.language } shouldBe true
        val fresh = Subtype.DEFAULT.copy(
            id = -1,
            primaryLocale = frisian,
            composer = unset,
            currencySet = unset,
            popupMapping = extCorePopupMapping("default"),
            layoutMap = SubtypeLayoutMap(
                characters = unset, symbols = unset, symbols2 = unset, numeric = unset,
                numericAdvanced = unset, numericRow = unset, phone = unset, phone2 = unset,
            ),
        )
        val filled = fillUnsetSubtypeFields(fresh, Subtype.DEFAULT, unset)

        filled.primaryLocale shouldBe frisian
        filled.composer shouldNotBe unset
        filled.currencySet shouldNotBe unset
        filled.punctuationRule shouldNotBe unset
        filled.popupMapping shouldBe extCorePopupMapping("default")
        EditableLayoutTypes.forEach { type ->
            withClue(type) { filled.layoutMap[type] shouldBe Subtype.DEFAULT.layoutMap[type] }
        }
    }
})
