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

package dev.patrickgold.florisboard.ime.core

import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryAssets
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private const val LocalizationExtensionDir = "src/main/assets/ime/keyboard/org.florisboard.localization"

private val PresetJson = Json { ignoreUnknownKeys = true }

private val Dutch = FlorisLocale.from("nl", "NL")
private val English = FlorisLocale.from("en", "US")

private fun localizationExtension(): JsonObject {
    return PresetJson.parseToJsonElement(File("$LocalizationExtensionDir/extension.json").readText()).jsonObject
}

private fun bundledPresets(): List<SubtypePreset> {
    return PresetJson.decodeFromJsonElement(
        ListSerializer(SubtypePreset.serializer()),
        localizationExtension().getValue("subtypePresets"),
    )
}

class SubtypePresetTest : FunSpec({
    test("the bundled nl-NL preset types Dutch with English as an additional language") {
        val preset = bundledPresets().single { it.locale.languageTag() == "nl-NL" }

        preset.secondaryLocales shouldBe listOf(English)
        preset.currencySet shouldBe ExtensionComponentName("org.florisboard.currencysets", "euro")
        preset.preferred.characters shouldBe ExtensionComponentName("org.florisboard.layouts", "qwerty")

        val subtype = preset.toSubtype()
        subtype.primaryLocale shouldBe Dutch
        subtype.secondaryLocales shouldBe listOf(English)
        subtype.locales() shouldBe listOf(Dutch, English)
        // Both languages have an n-gram model, so the n-gram scorer serves this subtype with both.
        LatinDictionaryAssets.ngramLanguages(subtype.locales().map { it.language }) shouldBe listOf("nl", "en")
    }

    test("presets without secondaryLocales still parse and stay single-language") {
        val raw = """
            [{
              "languageTag": "de-DE",
              "composer": "org.florisboard.composers:appender",
              "currencySet": "org.florisboard.currencysets:euro",
              "popupMapping": "org.florisboard.localization:de",
              "preferred": { "characters": "org.florisboard.layouts:qwertz" }
            }]
        """.trimIndent()
        val preset = PresetJson.decodeFromString(ListSerializer(SubtypePreset.serializer()), raw).single()

        preset.secondaryLocales.shouldBeEmpty()
        preset.toSubtype().secondaryLocales.shouldBeEmpty()
    }

    test("every other bundled preset keeps an empty secondary language list") {
        bundledPresets()
            .filter { it.locale.language != "nl" }
            .forEach { it.secondaryLocales.shouldBeEmpty() }
    }

    test("toSubtype drops additional languages that repeat the primary language") {
        val preset = SubtypePreset(
            locale = Dutch,
            secondaryLocales = listOf(FlorisLocale.from("nl", "BE"), English, FlorisLocale.from("en", "GB")),
            composer = ExtensionComponentName("org.florisboard.composers", "appender"),
            currencySet = ExtensionComponentName("org.florisboard.currencysets", "euro"),
            preferred = SubtypeLayoutMap(),
        )
        preset.toSubtype().secondaryLocales shouldBe listOf(English)
    }

    test("a subtype with additional languages survives a JSON round trip") {
        val subtype = bundledPresets().single { it.locale.languageTag() == "nl-NL" }.toSubtype()
        val decoded = SubtypeJsonConfig.decodeFromString(
            Subtype.serializer(),
            SubtypeJsonConfig.encodeToString(Subtype.serializer(), subtype),
        )
        decoded.secondaryLocales shouldBe listOf(English)
        decoded.equalsExcludingId(subtype) shouldBe true
    }
})

class SubtypeSecondaryLocalesTest : FunSpec({
    val german = FlorisLocale.from("de", "DE")
    val french = FlorisLocale.from("fr", "FR")

    test("normalize keeps the order and drops the primary language, repeats and blank locales") {
        SubtypeSecondaryLocales.normalize(
            primary = Dutch,
            secondary = listOf(English, FlorisLocale.from("nl", "BE"), FlorisLocale.from("en", "GB"), FlorisLocale.ROOT,
                german),
            dictionaryLanguages = setOf("en", "nl", "de"),
        ) shouldBe listOf(English, german)
    }

    test("languages without a dictionary are never kept, because the engine would use English for them") {
        SubtypeSecondaryLocales.normalize(primary = English, secondary = listOf(german, Dutch, french)) shouldBe
            listOf(Dutch)
        SubtypeSecondaryLocales.isSupported(german) shouldBe false
        SubtypeSecondaryLocales.isSupported(FlorisLocale.from("nl", "BE")) shouldBe true
        SubtypeSecondaryLocales.add(English, emptyList(), german).shouldBeEmpty()
    }

    test("add appends a new language once and remove takes it out again") {
        val added = SubtypeSecondaryLocales.add(Dutch, emptyList(), English)
        added shouldBe listOf(English)
        SubtypeSecondaryLocales.add(Dutch, added, FlorisLocale.from("en", "GB")) shouldBe listOf(English)
        SubtypeSecondaryLocales.add(Dutch, added, Dutch).shouldBe(listOf(English))
        SubtypeSecondaryLocales.remove(added, English).shouldBeEmpty()
    }

    test("suggestions offer every dictionary language not used yet, in the regional variant the user has") {
        SubtypeSecondaryLocales.suggestions(
            primary = german,
            current = emptyList(),
            systemLocales = listOf(german, FlorisLocale.from("en", "GB")),
            configuredLocales = listOf(french),
        ) shouldBe listOf(FlorisLocale.from("en", "GB"), Dutch)

        SubtypeSecondaryLocales.suggestions(
            primary = Dutch,
            current = emptyList(),
            systemLocales = listOf(Dutch, german),
            configuredLocales = listOf(french),
        ) shouldBe listOf(English)

        SubtypeSecondaryLocales.suggestions(
            primary = Dutch,
            current = listOf(English),
            systemLocales = listOf(FlorisLocale.from("en", "GB")),
            configuredLocales = emptyList(),
        ).shouldBeEmpty()
    }

    test("only the Latin provider uses additional languages") {
        SubtypeSecondaryLocales.appliesTo(LatinLanguageProvider.ProviderId) shouldBe true
        SubtypeSecondaryLocales.appliesTo(HanShapeBasedLanguageProvider.ProviderId) shouldBe false
    }
})

class LatinDictionaryAssetsTest : FunSpec({
    test("every listed n-gram model is bundled and every bundled model is listed") {
        val listed = LatinDictionaryAssets.NgramModels.values.map { File("src/main/assets/$it") }
        listed.forEach { it.isFile shouldBe true }
        val bundled = File("src/main/assets/ime/dict/ngram").listFiles { file -> file.extension == "bin" }
            .orEmpty()
            .map { it.name }
            .sorted()
        bundled shouldBe listed.map { it.name }.sorted()
        LatinDictionaryAssets.NgramModels.keys.all { it in LatinDictionaryAssets.Languages } shouldBe true
    }

    test("a subtype uses the n-gram scorer only when every language has a model") {
        LatinDictionaryAssets.ngramLanguages(listOf("nl", "en")) shouldBe listOf("nl", "en")
        LatinDictionaryAssets.ngramLanguages(listOf("EN", "en")) shouldBe listOf("en")
        LatinDictionaryAssets.ngramLanguages(listOf("nl", "de")) shouldBe null
        LatinDictionaryAssets.ngramLanguages(emptyList()) shouldBe null
    }
})
