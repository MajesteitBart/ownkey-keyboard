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

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.core.SubtypeNlpProviderMap
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private data class FakePreset(val tag: String, val name: String, val layout: String = "qwerty") {
    val locale: FlorisLocale = FlorisLocale.fromTag(tag)
}

private val presets = listOf(
    FakePreset("en-US", "English (United States)"),
    FakePreset("en-GB", "English (United Kingdom)"),
    FakePreset("nl-NL", "Dutch (Netherlands)"),
    FakePreset("de-DE", "German (Germany)", layout = "qwertz"),
    FakePreset("fr-FR", "French (France)", layout = "azerty"),
    FakePreset("fr-CA", "French (Canada)"),
)

private fun section(phone: List<String>, query: String = "") = sectionPresets(
    presets = presets,
    localeOf = { it.locale },
    namesOf = { listOf(it.name) },
    phoneLocales = phone.map { FlorisLocale.fromTag(it) },
    query = query,
)

private val Unset = ExtensionComponentName("00", "00")

class LanguageChoicesTest : FunSpec({
    test("the phone's languages come first, in the phone's order") {
        val sections = section(phone = listOf("nl-NL", "en-US"))
        sections.suggested.map { it.tag } shouldBe listOf("nl-NL", "en-US")
        sections.others.map { it.tag } shouldBe listOf("en-GB", "fr-CA", "fr-FR", "de-DE")
    }

    test("a phone language without its own preset suggests the presets for that language") {
        val sections = section(phone = listOf("fr-BE"))
        sections.suggested.map { it.tag } shouldBe listOf("fr-FR", "fr-CA")
    }

    test("an exact match hides the language's other variants from the suggestions") {
        section(phone = listOf("en-GB")).suggested.map { it.tag } shouldBe listOf("en-GB")
    }

    test("a language the presets don't cover suggests nothing and lists everything") {
        val sections = section(phone = listOf("ja-JP"))
        sections.suggested shouldBe emptyList()
        sections.others.size shouldBe presets.size
    }

    test("the search matches names case-insensitively and language tags by prefix") {
        section(phone = listOf("nl-NL"), query = "FRENCH").let {
            it.suggested shouldBe emptyList()
            it.others.map { p -> p.tag } shouldBe listOf("fr-CA", "fr-FR")
        }
        section(phone = listOf("nl-NL"), query = "de").others.map { it.tag } shouldBe listOf("de-DE")
        section(phone = listOf("nl-NL"), query = " dutch ").suggested.map { it.tag } shouldBe listOf("nl-NL")
        section(phone = listOf("nl-NL"), query = "klingon").let {
            it.suggested shouldBe emptyList()
            it.others shouldBe emptyList()
        }
    }

    test("changing the language fills only the fields that are still unset") {
        val current = Subtype.DEFAULT.copy(
            composer = Unset,
            currencySet = Subtype.DEFAULT.currencySet,
            nlpProviders = SubtypeNlpProviderMap(spelling = Unset.toString(), suggestion = Unset.toString()),
            layoutMap = SubtypeLayoutMap(characters = extCoreLayout("qwertz"), symbols = Unset, phone = Unset),
        )
        val fallback = Subtype.DEFAULT.copy(
            composer = ExtensionComponentName("org.florisboard.composers", "fallback"),
            currencySet = ExtensionComponentName("org.florisboard.currencysets", "euro"),
            layoutMap = SubtypeLayoutMap(characters = extCoreLayout("azerty"), symbols = extCoreLayout("eastern")),
        )
        val filled = fillUnsetSubtypeFields(current, fallback, Unset)

        filled.composer shouldBe fallback.composer
        filled.currencySet shouldBe current.currencySet
        filled.nlpProviders shouldBe fallback.nlpProviders
        filled.layoutMap[LayoutType.CHARACTERS] shouldBe extCoreLayout("qwertz")
        filled.layoutMap[LayoutType.SYMBOLS] shouldBe extCoreLayout("eastern")
        filled.layoutMap[LayoutType.PHONE] shouldBe fallback.layoutMap[LayoutType.PHONE]
        filled.primaryLocale shouldBe current.primaryLocale
    }

    test("a complete subtype comes back unchanged") {
        fillUnsetSubtypeFields(Subtype.DEFAULT, Subtype.DEFAULT.copy(composer = Unset), Unset) shouldBe
            Subtype.DEFAULT
    }

    test("every layout slot can be filled") {
        val allUnset = Subtype.DEFAULT.copy(
            layoutMap = SubtypeLayoutMap(
                characters = Unset, symbols = Unset, symbols2 = Unset, numeric = Unset,
                numericAdvanced = Unset, numericRow = Unset, phone = Unset, phone2 = Unset,
            ),
        )
        val filled = fillUnsetSubtypeFields(allUnset, Subtype.DEFAULT, Unset)
        EditableLayoutTypes.forEach { type ->
            filled.layoutMap[type] shouldBe Subtype.DEFAULT.layoutMap[type]
        }
    }
})
