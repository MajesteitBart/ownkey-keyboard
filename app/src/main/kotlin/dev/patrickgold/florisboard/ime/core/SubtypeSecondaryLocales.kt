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

import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryAssets
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale

/**
 * Helpers for the secondary ("additional") languages of a [Subtype], which enable multilingual typing: the Latin
 * suggestion engine scores all languages of a subtype at once, so the user never has to switch keyboards.
 *
 * Only languages with a bundled dictionary ([LatinDictionaryAssets]) are kept. The engine falls back to the legacy
 * English model for any other language, so such an entry would not add the language but add a second English model
 * that splits the language weights and lowers autocorrect confidence.
 *
 * All functions are pure and keep the order chosen by the user. The `dictionaryLanguages` parameters exist for tests.
 */
object SubtypeSecondaryLocales {
    /**
     * Returns true if additional languages have an effect for a subtype with this suggestion provider. Only the
     * Latin engine reads them.
     */
    fun appliesTo(suggestionProviderId: String): Boolean {
        return suggestionProviderId == LatinLanguageProvider.ProviderId
    }

    /**
     * Returns true if [locale] can be an additional language: the Latin engine has a dictionary for it.
     */
    fun isSupported(
        locale: FlorisLocale,
        dictionaryLanguages: Set<String> = LatinDictionaryAssets.Languages,
    ): Boolean {
        return LatinDictionaryAssets.normalizeLanguage(locale.language) in dictionaryLanguages
    }

    /**
     * Returns [secondary] without locales that share the language of [primary], without repeated languages (the
     * first occurrence wins) and without languages that have no dictionary. The engine works per language, so a
     * second locale of the same language would add nothing.
     */
    fun normalize(
        primary: FlorisLocale,
        secondary: List<FlorisLocale>,
        dictionaryLanguages: Set<String> = LatinDictionaryAssets.Languages,
    ): List<FlorisLocale> {
        val seenLanguages = mutableSetOf(LatinDictionaryAssets.normalizeLanguage(primary.language))
        return secondary.filter { locale ->
            isSupported(locale, dictionaryLanguages) &&
                seenLanguages.add(LatinDictionaryAssets.normalizeLanguage(locale.language))
        }
    }

    /**
     * Adds [locale] at the end of [current], then normalizes the result for [primary]. A locale without a dictionary
     * is not added.
     */
    fun add(
        primary: FlorisLocale,
        current: List<FlorisLocale>,
        locale: FlorisLocale,
        dictionaryLanguages: Set<String> = LatinDictionaryAssets.Languages,
    ): List<FlorisLocale> {
        return normalize(primary, current + locale, dictionaryLanguages)
    }

    /**
     * Removes [locale] from [current].
     */
    fun remove(current: List<FlorisLocale>, locale: FlorisLocale): List<FlorisLocale> {
        return current.filter { it != locale }
    }

    /**
     * Returns the languages to offer when the user adds an additional language: every language with a dictionary
     * that [primary] and [current] do not cover yet. The regional variant comes from [systemLocales], then from the
     * languages of other configured subtypes ([configuredLocales]), then from [defaultLocales].
     */
    fun suggestions(
        primary: FlorisLocale,
        current: List<FlorisLocale>,
        systemLocales: List<FlorisLocale>,
        configuredLocales: List<FlorisLocale>,
        dictionaryLanguages: Set<String> = LatinDictionaryAssets.Languages,
        defaultLocales: List<FlorisLocale> = LatinDictionaryAssets.Dictionaries.map { it.defaultLocale },
    ): List<FlorisLocale> {
        val takenLanguages = current.mapTo(mutableSetOf()) { LatinDictionaryAssets.normalizeLanguage(it.language) }
        val pool = systemLocales + configuredLocales + defaultLocales
        return normalize(primary, pool, dictionaryLanguages).filter {
            LatinDictionaryAssets.normalizeLanguage(it.language) !in takenLanguages
        }
    }
}
