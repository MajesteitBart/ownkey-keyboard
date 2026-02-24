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

package dev.patrickgold.florisboard.ime.text.dictation

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class VoxtralSecretsStore(
    context: Context,
) {
    companion object {
        private const val FILE_NAME = "voxtral-secrets"
        private const val KEY_API_KEY = "voxtral_api_key"
    }

    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(): String {
        return encryptedPrefs.getString(KEY_API_KEY, "").orEmpty()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun setApiKey(apiKey: String) {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isBlank()) {
            encryptedPrefs.edit().remove(KEY_API_KEY).apply()
        } else {
            encryptedPrefs.edit().putString(KEY_API_KEY, normalizedApiKey).apply()
        }
    }

    fun clearApiKey() {
        encryptedPrefs.edit().remove(KEY_API_KEY).apply()
    }
}
