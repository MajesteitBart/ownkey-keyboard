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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.lib.FlorisLocale
import java.util.Locale

/**
 * The languages that ship with their own dictionary for the Latin suggestion provider.
 *
 * Every language here has a built word list (`ime/dict/latin/<lang>.txt`, see [LatinLanguageProvider]) and a binary
 * trigram model for the n-gram scorer (`ime/dict/ngram/<lang>.bin`, `tools/lm/FORMAT.md`). Any other language falls
 * back to the legacy English list, so settings only offer these languages where the choice changes suggestions, such
 * as the additional languages of a subtype.
 */
object LatinDictionaryAssets {
    /**
     * @property language Lower-case ISO 639 language code, as compared by the provider.
     * @property defaultLocale The locale to offer for this language when no regional variant is known.
     * @property ngramModelPath Asset path of the trigram model, stored uncompressed so it can be memory-mapped.
     */
    data class Dictionary(
        val language: String,
        val defaultLocale: FlorisLocale,
        val ngramModelPath: String?,
    )

    val Dictionaries: List<Dictionary> = listOf(
        Dictionary("en", FlorisLocale.from("en", "US"), "ime/dict/ngram/en.bin"),
        Dictionary("nl", FlorisLocale.from("nl", "NL"), "ime/dict/ngram/nl.bin"),
    )

    /** N-gram model asset path by language code, for the languages that have one. */
    val NgramModels: Map<String, String> = Dictionaries
        .mapNotNull { dictionary -> dictionary.ngramModelPath?.let { dictionary.language to it } }
        .toMap()

    /** Language codes that have a dictionary. */
    val Languages: Set<String> = Dictionaries.mapTo(linkedSetOf()) { it.language }

    /** Normalizes a language code the same way the provider does before it looks up a dictionary. */
    fun normalizeLanguage(language: String): String = language.trim().lowercase(Locale.ROOT)

    /**
     * The distinct languages of [localeLanguages] in order (primary first), normalized, if every one of them has an
     * n-gram model; otherwise null.
     */
    fun ngramLanguages(localeLanguages: List<String>, available: Set<String> = NgramModels.keys): List<String>? {
        val languages = localeLanguages.map { normalizeLanguage(it) }.distinct()
        if (languages.isEmpty() || languages.any { it !in available }) return null
        return languages
    }
}
