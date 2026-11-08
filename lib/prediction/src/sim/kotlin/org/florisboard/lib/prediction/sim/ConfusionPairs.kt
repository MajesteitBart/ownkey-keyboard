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

package org.florisboard.lib.prediction.sim

import org.florisboard.lib.prediction.TextFolding
import java.io.File

/**
 * Confusion pairs for `--confusion-sets`, mirroring the app's `ConfusionSets`: listed groups of correctly spelled words
 * that are written for each other ("then", "than"), plus the Dutch verb endings d/dt and final d/t ("vind", "vindt",
 * "gebeurd", "gebeurt"; not dt with t).
 */
class ConfusionPairs(private val groups: Map<String, Set<String>>) {
    fun arePartners(typed: String, replacement: String): Boolean {
        val a = TextFolding.normalizeWord(typed)
        val b = TextFolding.normalizeWord(replacement)
        if (a == b) return false
        if (groups[a]?.contains(b) == true) return true
        return b in dutchVerbEndings(a)
    }

    private fun dutchVerbEndings(word: String): List<String> {
        if (word.length < 3) return emptyList()
        return when {
            word.endsWith("dt") -> listOf(word.dropLast(1))
            word.endsWith("d") -> listOf(word + "t", word.dropLast(1) + "t")
            word.endsWith("t") -> listOf(word.dropLast(1) + "d")
            else -> emptyList()
        }
    }

    companion object {
        /** One group per line, words separated by whitespace; '#' starts a comment. */
        fun load(file: File): ConfusionPairs {
            val groups = HashMap<String, MutableSet<String>>()
            for (line in file.readLines()) {
                val words = line.substringBefore('#').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .map { TextFolding.normalizeWord(it) }
                for (w in words) groups.getOrPut(w) { HashSet() }.addAll(words - w)
            }
            return ConfusionPairs(groups)
        }
    }
}
