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

package dev.patrickgold.florisboard.ime.nlp.personal

import android.content.Context
import dev.patrickgold.florisboard.lib.devtools.flogError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Result entry of a next-word prediction query against the personal n-gram model.
 */
data class PersonalPrediction(
    val word: String,
    val score: Int,
)

/**
 * Pure in-memory bigram/trigram model learned from the user's own typing. All words are expected to be
 * normalized (lowercased, trimmed) by the caller. This class is not thread-safe by itself; synchronization
 * is handled by [PersonalNgramStore].
 */
class PersonalNgramModel(
    private val maxBigrams: Int = MAX_BIGRAMS_DEFAULT,
    private val maxTrigrams: Int = MAX_TRIGRAMS_DEFAULT,
) {
    companion object {
        const val MAX_BIGRAMS_DEFAULT = 6000
        const val MAX_TRIGRAMS_DEFAULT = 4000
        private const val KEY_SEPARATOR = " "
        private const val EVICTION_HEADROOM_FACTOR = 1.2
    }

    private val bigrams = HashMap<String, Int>()
    private val trigrams = HashMap<String, Int>()

    val bigramCount: Int get() = bigrams.size
    val trigramCount: Int get() = trigrams.size

    /**
     * Records the word sequence ending at the most recently completed word. Only the final bigram and
     * trigram of [tokens] are recorded, so this should be called exactly once per completed word.
     */
    fun learn(tokens: List<String>) {
        if (tokens.size < 2) return
        val word = tokens.last()
        val prev1 = tokens[tokens.size - 2]
        if (!isLearnableWord(word) || !isLearnableWord(prev1)) return

        bigrams.merge(prev1 + KEY_SEPARATOR + word, 1, Int::plus)
        evictIfNecessary(bigrams, maxBigrams)

        val prev2 = tokens.getOrNull(tokens.size - 3)
        if (prev2 != null && isLearnableWord(prev2)) {
            trigrams.merge(prev2 + KEY_SEPARATOR + prev1 + KEY_SEPARATOR + word, 1, Int::plus)
            evictIfNecessary(trigrams, maxTrigrams)
        }
    }

    /**
     * Predicts likely next words given the preceding one or two words. Trigram continuations are weighted
     * considerably higher than plain bigram continuations.
     */
    fun predictNext(prev2: String?, prev1: String, limit: Int): List<PersonalPrediction> {
        if (limit <= 0 || prev1.isBlank()) return emptyList()
        val scores = HashMap<String, Int>()
        val bigramPrefix = prev1 + KEY_SEPARATOR
        for ((key, count) in bigrams) {
            if (key.startsWith(bigramPrefix)) {
                val word = key.substring(bigramPrefix.length)
                scores.merge(word, count, Int::plus)
            }
        }
        if (prev2 != null && prev2.isNotBlank()) {
            val trigramPrefix = prev2 + KEY_SEPARATOR + prev1 + KEY_SEPARATOR
            for ((key, count) in trigrams) {
                if (key.startsWith(trigramPrefix)) {
                    val word = key.substring(trigramPrefix.length)
                    scores.merge(word, count * 3, Int::plus)
                }
            }
        }
        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { PersonalPrediction(it.key, it.value) }
    }

    /**
     * Returns a score in [0.0, 1.0] expressing how strongly [word] has followed [prev1] in the user's
     * own typing. Used to context-boost current-word completion/correction candidates.
     */
    fun continuationScore(prev1: String, word: String): Double {
        if (prev1.isBlank() || word.isBlank()) return 0.0
        val count = bigrams[prev1 + KEY_SEPARATOR + word] ?: return 0.0
        return count.toDouble() / (2.0 + count.toDouble())
    }

    fun snapshotBigrams(): Map<String, Int> = HashMap(bigrams)
    fun snapshotTrigrams(): Map<String, Int> = HashMap(trigrams)

    fun restore(bigramData: Map<String, Int>, trigramData: Map<String, Int>) {
        bigrams.clear()
        trigrams.clear()
        bigrams.putAll(bigramData)
        trigrams.putAll(trigramData)
        evictIfNecessary(bigrams, maxBigrams)
        evictIfNecessary(trigrams, maxTrigrams)
    }

    fun clear() {
        bigrams.clear()
        trigrams.clear()
    }

    private fun isLearnableWord(word: String): Boolean {
        if (word.isEmpty() || word.length > 24) return false
        return word.all { it.isLetter() || it == '\'' || it == '-' }
    }

    private fun evictIfNecessary(map: HashMap<String, Int>, maxSize: Int) {
        if (map.size <= (maxSize * EVICTION_HEADROOM_FACTOR).toInt()) return
        val keep = map.entries
            .sortedByDescending { it.value }
            .take(maxSize)
        val retained = keep.associate { it.key to it.value }
        map.clear()
        map.putAll(retained)
    }
}

/**
 * Persistent, thread-safe wrapper around per-language [PersonalNgramModel]s. All learned data stays in a
 * local JSON file in the app's private storage and never leaves the device.
 */
class PersonalNgramStore(context: Context) {
    companion object {
        private const val FILE_NAME = "personal_ngrams_v1.json"
        private const val SAVE_DEBOUNCE_MS = 5000L
    }

    @Serializable
    private data class LanguageNgramData(
        val bigrams: Map<String, Int> = emptyMap(),
        val trigrams: Map<String, Int> = emptyMap(),
    )

    @Serializable
    private data class NgramFileData(
        val version: Int = 1,
        val languages: Map<String, LanguageNgramData> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(PersonalLearningFiles.dir(context), FILE_NAME)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val models = HashMap<String, PersonalNgramModel>()
    private var isLoaded = false
    private var pendingSaveJob: Job? = null

    suspend fun learn(language: String, tokens: List<String>) {
        mutex.withLock {
            ensureLoadedLocked()
            modelForLocked(language).learn(tokens)
            scheduleSaveLocked()
        }
    }

    suspend fun predictNext(language: String, prev2: String?, prev1: String, limit: Int): List<PersonalPrediction> {
        mutex.withLock {
            ensureLoadedLocked()
            return modelForLocked(language).predictNext(prev2, prev1, limit)
        }
    }

    suspend fun continuationScore(language: String, prev1: String, word: String): Double {
        mutex.withLock {
            ensureLoadedLocked()
            return modelForLocked(language).continuationScore(prev1, word)
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            models.values.forEach { it.clear() }
            models.clear()
            isLoaded = true
            pendingSaveJob?.cancel()
            pendingSaveJob = null
            try {
                file.delete()
            } catch (e: Exception) {
                flogError { "Failed deleting personal n-gram data: $e" }
            }
        }
    }

    private fun modelForLocked(language: String): PersonalNgramModel {
        return models.getOrPut(language.lowercase()) { PersonalNgramModel() }
    }

    private fun ensureLoadedLocked() {
        if (isLoaded) return
        isLoaded = true
        try {
            if (!file.exists()) return
            val data = json.decodeFromString<NgramFileData>(file.readText())
            for ((language, languageData) in data.languages) {
                modelForLocked(language).restore(languageData.bigrams, languageData.trigrams)
            }
        } catch (e: Exception) {
            flogError { "Failed loading personal n-gram data: $e" }
        }
    }

    private fun scheduleSaveLocked() {
        if (pendingSaveJob?.isActive == true) return
        pendingSaveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val data = mutex.withLock {
            NgramFileData(
                languages = models.mapValues { (_, model) ->
                    LanguageNgramData(
                        bigrams = model.snapshotBigrams(),
                        trigrams = model.snapshotTrigrams(),
                    )
                },
            )
        }
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(NgramFileData.serializer(), data))
        } catch (e: Exception) {
            flogError { "Failed saving personal n-gram data: $e" }
        }
    }
}

internal object PersonalLearningFiles {
    fun dir(context: Context): File {
        return File(context.filesDir, "personal_learning").also { it.mkdirs() }
    }
}
