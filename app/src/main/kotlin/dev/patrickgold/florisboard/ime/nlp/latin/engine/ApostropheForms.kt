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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

/**
 * Words people habitually type without their apostrophe, per language. Only forms that are not words themselves are
 * listed: "dont" can only mean "don't", while "cant", "ill", "its" or "well" are real words and stay suggestions.
 * The scorer treats a listed form as a spelling habit, not a typing error, so it costs nothing in the channel model.
 */
internal object ApostropheForms {
    private val English = listOf(
        "aren't", "could've", "couldn't", "didn't", "doesn't", "don't", "hadn't", "hasn't", "haven't", "he's",
        "here's", "i'm", "i've", "isn't", "mustn't", "she's", "should've", "shouldn't", "that's", "there's", "they'd",
        "they'll", "they're", "they've", "wasn't", "we've", "weren't", "what's", "where's", "who's", "would've",
        "wouldn't", "you'd", "you'll", "you're", "you've",
    )

    private val Dutch = listOf("z'n", "m'n")

    private val formsByLanguage: Map<String, Map<String, String>> = mapOf(
        "en" to English.associateBy { it.replace("'", "") },
        "nl" to Dutch.associateBy { it.replace("'", "") },
    )

    /** The apostrophe form of [input] in [language], or null when [input] is not a listed form. */
    fun lookup(language: String, input: String): String? = formsByLanguage[language]?.get(input)

    /** All listed forms of [language], keyed by the form without apostrophe. */
    fun all(language: String): Map<String, String> = formsByLanguage[language].orEmpty()
}
