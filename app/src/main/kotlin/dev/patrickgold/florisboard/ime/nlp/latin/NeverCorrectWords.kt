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

import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class NeverCorrectWordEntry(
    val word: String,
    val language: String?,
)

@Serializable
data class NeverCorrectWords(
    val entries: List<NeverCorrectWordEntry>,
) {
    fun contains(normalizedWord: String, languages: Set<String>): Boolean {
        val canonicalWord = canonicalizeWord(normalizedWord)
        if (canonicalWord.isBlank()) return false
        val normalizedLanguages = languages.mapNotNullTo(linkedSetOf()) { language ->
            canonicalizeLanguage(language)
        }
        return entries.any { entry ->
            entry.word == canonicalWord && (
                entry.language == null ||
                    entry.language in normalizedLanguages
                )
        }
    }

    fun suppress(normalizedWord: String, language: String?): NeverCorrectWords {
        val canonicalWord = canonicalizeWord(normalizedWord)
        if (canonicalWord.isBlank()) return this
        val canonicalLanguage = canonicalizeLanguage(language)
        val updatedEntries = buildList {
            add(NeverCorrectWordEntry(word = canonicalWord, language = canonicalLanguage))
            entries
                .asSequence()
                .filterNot { entry -> entry.word == canonicalWord && entry.language == canonicalLanguage }
                .take(MaxEntries - 1)
                .forEach(::add)
        }
        return copy(entries = updatedEntries)
    }

    fun remove(normalizedWord: String, language: String?): NeverCorrectWords {
        val canonicalWord = canonicalizeWord(normalizedWord)
        val canonicalLanguage = canonicalizeLanguage(language)
        return copy(entries = entries.filterNot { entry ->
            entry.word == canonicalWord && entry.language == canonicalLanguage
        })
    }

    object Serializer : PreferenceSerializer<NeverCorrectWords> {
        override fun serialize(value: NeverCorrectWords): String {
            return Json.encodeToString(value)
        }

        override fun deserialize(value: String): NeverCorrectWords {
            return try {
                Json.decodeFromString(value)
            } catch (e: Exception) {
                flogError { "Failed to deserialize NeverCorrectWords: $e" }
                Empty
            }
        }
    }

    companion object {
        val Empty = NeverCorrectWords(entries = emptyList())
        const val MaxEntries = 256

        private fun canonicalizeWord(word: String): String {
            return word.trim().replace('’', '\'')
        }

        private fun canonicalizeLanguage(language: String?): String? {
            return language
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.lowercase(Locale.ROOT)
        }
    }
}

object NeverCorrectWordsHelper {
    private val guard = Mutex(locked = false)

    suspend fun suppressWord(
        prefs: FlorisPreferenceModel,
        normalizedWord: String,
        language: String?,
    ): Boolean = guard.withLock {
        val current = prefs.dictionary.neverCorrectWordsData.get()
        val updated = current.suppress(normalizedWord = normalizedWord, language = language)
        if (updated == current) {
            false
        } else {
            prefs.dictionary.neverCorrectWordsData.set(updated)
            true
        }
    }

    fun isBlocked(
        prefs: FlorisPreferenceModel,
        normalizedWord: String,
        languages: Set<String>,
    ): Boolean {
        return prefs.dictionary.neverCorrectWordsData.get().contains(
            normalizedWord = normalizedWord,
            languages = languages,
        )
    }
}
