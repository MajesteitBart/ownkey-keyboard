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

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.jetpref.datastore.runtime.DataStoreReader
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JetPref 0.3.0 unescapes newline/carriage-return sequences before escaped backslashes, corrupting
 * JSON stored in a string preference on reload. Equivalent JSON Unicode escapes avoid that bug,
 * including literal backslashes followed by n/r. The result remains readable by older Ownkey builds.
 */
internal fun escapeRewritePromptJsonForStorage(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        if (char == '\\' && index < value.length) {
            when (val escaped = value[index++]) {
                '\\' -> append("\\u005c")
                'n' -> append("\\u000a")
                'r' -> append("\\u000d")
                else -> append('\\').append(escaped)
            }
        } else {
            append(char)
        }
    }
}

/**
 * Recover the original JSON from existing preference files before JetPref can corrupt it. Only the
 * rewrite-voices entry is adapted; malformed entries and all other preferences remain untouched.
 * This reader is shared by application startup and backup restore. It never writes the source file.
 */
class RewritePromptPreferenceReader(private val delegate: DataStoreReader) : DataStoreReader {
    override suspend fun read(): String = entryPattern.replace(delegate.read()) { match ->
        val original = match.value
        val json = runCatching {
            Json.decodeFromString<String>(original.removePrefix(entryPrefix))
        }.getOrNull()
        if (json == null) {
            original
        } else {
            entryPrefix + Json.encodeToString(escapeRewritePromptJsonForStorage(json))
        }
    }

    private companion object {
        const val entryPrefix = "s;voxtral__rewrite_prompts;"
        val entryPattern = Regex("(?m)^${Regex.escape(entryPrefix)}[^\\r\\n]*")
    }
}
