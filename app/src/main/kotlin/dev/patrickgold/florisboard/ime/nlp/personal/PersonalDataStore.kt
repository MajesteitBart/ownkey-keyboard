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
import java.util.Locale

/**
 * Stores user-specific data tokens (currently e-mail addresses) which the user has typed, so they can be
 * offered as suggestions instead of having to be typed out in full every time. All data is kept in a local
 * JSON file in the app's private storage and never leaves the device.
 */
class PersonalDataStore(context: Context) {
    companion object {
        private const val FILE_NAME = "personal_data_v1.json"
        private const val SAVE_DEBOUNCE_MS = 3000L
        private const val MAX_EMAILS = 24
        private const val MAX_EMAIL_LENGTH = 64
    }

    @Serializable
    private data class UsageEntry(
        val count: Int = 1,
        val lastUsedAt: Long = 0L,
    )

    @Serializable
    private data class PersonalDataFile(
        val version: Int = 1,
        val emails: Map<String, UsageEntry> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(PersonalLearningFiles.dir(context), FILE_NAME)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val emails = HashMap<String, UsageEntry>()
    private var isLoaded = false
    private var pendingSaveJob: Job? = null

    suspend fun recordEmail(address: String) {
        val normalized = normalizeEmail(address) ?: return
        mutex.withLock {
            ensureLoadedLocked()
            val existing = emails[normalized]
            emails[normalized] = UsageEntry(
                count = (existing?.count ?: 0) + 1,
                lastUsedAt = System.currentTimeMillis(),
            )
            evictIfNecessaryLocked()
            scheduleSaveLocked()
        }
    }

    /**
     * Returns known e-mail addresses ordered by relevance (usage count, then recency). If [prefix] is
     * non-blank, only addresses whose local part or full address starts with it (ignoring case) are returned.
     */
    suspend fun emailsByRelevance(prefix: String = "", limit: Int = 3): List<String> {
        if (limit <= 0) return emptyList()
        val normalizedPrefix = prefix.trim().lowercase(Locale.ROOT)
        mutex.withLock {
            ensureLoadedLocked()
            return emails.entries
                .filter { (address, _) ->
                    normalizedPrefix.isEmpty() || address.startsWith(normalizedPrefix)
                }
                .sortedWith(
                    compareByDescending<Map.Entry<String, UsageEntry>> { it.value.count }
                        .thenByDescending { it.value.lastUsedAt }
                )
                .take(limit)
                .map { it.key }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            emails.clear()
            isLoaded = true
            pendingSaveJob?.cancel()
            pendingSaveJob = null
            try {
                file.delete()
            } catch (e: Exception) {
                flogError { "Failed deleting personal data: $e" }
            }
        }
    }

    private fun normalizeEmail(address: String): String? {
        val normalized = address.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty() || normalized.length > MAX_EMAIL_LENGTH) return null
        if (!normalized.contains('@') || !normalized.substringAfter('@').contains('.')) return null
        return normalized
    }

    private fun evictIfNecessaryLocked() {
        if (emails.size <= MAX_EMAILS) return
        val keep = emails.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, UsageEntry>> { it.value.count }
                    .thenByDescending { it.value.lastUsedAt }
            )
            .take(MAX_EMAILS)
        val retained = keep.associate { it.key to it.value }
        emails.clear()
        emails.putAll(retained)
    }

    private fun ensureLoadedLocked() {
        if (isLoaded) return
        isLoaded = true
        try {
            if (!file.exists()) return
            val data = json.decodeFromString<PersonalDataFile>(file.readText())
            emails.putAll(data.emails)
        } catch (e: Exception) {
            flogError { "Failed loading personal data: $e" }
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
            PersonalDataFile(emails = HashMap(emails))
        }
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(PersonalDataFile.serializer(), data))
        } catch (e: Exception) {
            flogError { "Failed saving personal data: $e" }
        }
    }
}
