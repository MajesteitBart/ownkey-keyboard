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

/**
 * Maps a configured transcription endpoint onto a provider label users can recognise.
 *
 * Only the host is inspected and only known hosts produce a name; anything else is reported as
 * unknown so the disclosure falls back to resource-backed `Custom endpoint` copy. Neither the path,
 * the query, nor the full URL is ever surfaced or logged.
 */
object TranscriptionProviderNaming {
    private val knownHostLabels = mapOf(
        "api.mistral.ai" to "Mistral",
        "api.openai.com" to "OpenAI",
        "openrouter.ai" to "OpenRouter",
        "api.groq.com" to "Groq",
        "api.deepgram.com" to "Deepgram",
    )

    fun knownLabel(endpointUrl: String): String? {
        val host = hostOf(endpointUrl) ?: return null
        return knownHostLabels[host]
    }

    private fun hostOf(endpointUrl: String): String? {
        val trimmed = endpointUrl.trim()
        if (trimmed.isEmpty()) return null
        val withoutScheme = trimmed.substringAfter("://", missingDelimiterValue = trimmed)
        val authority = withoutScheme.substringBefore('/').substringBefore('?')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.ifEmpty { null }
    }
}
