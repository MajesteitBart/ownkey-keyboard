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
 * Correctly spelled words that are often written for another word: homophones such as "then" and "than", and in
 * Dutch the d/dt/t verb endings ("word" and "wordt"). The words before the cursor decide which one fits; these
 * alternatives only reorder suggestions and are never auto-committed.
 */
internal object ConfusionSets {
    private val English = groups(
        listOf("then", "than"),
        listOf("your", "you're"),
        listOf("its", "it's"),
        listOf("there", "their", "they're"),
        listOf("to", "too"),
        listOf("were", "we're", "where"),
        listOf("whose", "who's"),
        listOf("lose", "loose"),
        listOf("of", "off"),
        listOf("weather", "whether"),
        listOf("quite", "quiet"),
        listOf("know", "no", "now"),
        listOf("knew", "new"),
        listOf("hear", "here"),
        listOf("peace", "piece"),
        listOf("advice", "advise"),
        listOf("breath", "breathe"),
        listOf("accept", "except"),
        listOf("affect", "effect"),
        listOf("thing", "think"),
        listOf("form", "from"),
        listOf("wont", "won't"),
        listOf("cant", "can't"),
        listOf("lets", "let's"),
        listOf("ill", "i'll"),
        listOf("id", "i'd"),
        listOf("well", "we'll"),
        listOf("past", "passed"),
        listOf("our", "are"),
        listOf("by", "buy"),
        listOf("see", "sea"),
        listOf("fair", "fare"),
        listOf("meat", "meet", "met"),
        listOf("would", "wood"),
        listOf("feel", "fell"),
        listOf("though", "thought", "through"),
        listOf("does", "dose"),
        listOf("great", "grate"),
        listOf("week", "weak"),
        listOf("whole", "hole"),
        listOf("right", "write"),
    )

    private val Dutch = groups(
        listOf("als", "dan"),
        listOf("jou", "jouw"),
        listOf("u", "uw"),
        listOf("en", "een"),
    )

    /**
     * Other words that [word] is often confused with in [language], limited to words [isKnown] accepts. Dutch also
     * gets the verb ending pairs d/dt ("vind", "vindt") and final d/t ("gebeurd", "gebeurt").
     */
    fun alternatives(language: String, word: String, isKnown: (String) -> Boolean): List<String> {
        val listed = when (language) {
            "en" -> English[word]
            "nl" -> Dutch[word]
            else -> null
        }.orEmpty()
        val endings = if (language == "nl") dutchVerbEndings(word) else emptyList()
        return (listed + endings).filter { it != word && isKnown(it) }.distinct()
    }

    private fun dutchVerbEndings(word: String): List<String> {
        if (word.length < 3) return emptyList()
        return when {
            word.endsWith("dt") -> listOf(word.dropLast(1), word.dropLast(2) + "t")
            word.endsWith("d") -> listOf(word + "t", word.dropLast(1) + "t")
            word.endsWith("t") -> listOf(word.dropLast(1) + "d", word.dropLast(1) + "dt")
            else -> emptyList()
        }
    }

    private fun groups(vararg groups: List<String>): Map<String, Set<String>> {
        val result = HashMap<String, MutableSet<String>>()
        for (group in groups) {
            for (member in group) result.getOrPut(member) { mutableSetOf() }.addAll(group - member)
        }
        return result
    }
}
