/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import java.net.URL

/** How vocabulary reaches a cloud transcription endpoint. Stored as a preference. */
enum class CloudVocabularyMode(val preference: String) {
    /** Known endpoints with a documented field get it; unknown or custom endpoints get nothing. */
    AUTO("auto"),

    /** Send the words as an OpenAI-style `prompt` field. Only for endpoints known to accept it. */
    PROMPT("prompt"),

    /** Never send vocabulary with cloud audio. Corrections and filler removal still run locally. */
    OFF("off");

    companion object {
        fun fromPreference(value: String): CloudVocabularyMode =
            entries.firstOrNull { it.preference == value.trim().lowercase() } ?: AUTO
    }
}

/** The multipart field that carries vocabulary, if any. */
enum class CloudVocabularyField {
    NONE,

    /** Mistral's documented `context_bias` array, one multipart part per term. */
    CONTEXT_BIAS,

    /** One comma-separated `prompt` part, the OpenAI transcription convention. */
    PROMPT,
}

object CloudVocabularyHints {
    private const val MISTRAL_HOST = "api.mistral.ai"
    private const val OPENAI_HOST = "api.openai.com"

    /**
     * Request-side budget for one upload. Saved entries are never truncated; only the request is,
     * in saved order, and the settings page reports how many words fit. Not a provider limit.
     */
    const val MAX_TERMS = 200
    const val MAX_BYTES = 16 * 1024

    data class Bounded(val terms: List<String>, val dropped: Int) {
        val included: Int get() = terms.size
    }

    fun bound(vocabulary: List<String>, maxTerms: Int = MAX_TERMS, maxBytes: Int = MAX_BYTES): Bounded {
        val terms = ArrayList<String>()
        var bytes = 0
        var dropped = 0
        for (raw in vocabulary) {
            val term = raw.trim()
            if (term.isEmpty()) continue
            if (dropped > 0) {
                dropped++
                continue
            }
            val termBytes = term.toByteArray(Charsets.UTF_8).size + if (terms.isEmpty()) 0 else 2
            if (terms.size >= maxTerms || bytes + termBytes > maxBytes) {
                dropped++
                continue
            }
            terms.add(term)
            bytes += termBytes
        }
        return Bounded(terms, dropped)
    }

    /**
     * OpenAI compatibility alone is not evidence of hint support: an unknown endpoint may reject or
     * silently ignore an extra field, so [CloudVocabularyMode.AUTO] only sends to documented hosts.
     */
    fun field(endpointUrl: String, mode: CloudVocabularyMode): CloudVocabularyField = when (mode) {
        CloudVocabularyMode.OFF -> CloudVocabularyField.NONE
        CloudVocabularyMode.PROMPT -> CloudVocabularyField.PROMPT
        CloudVocabularyMode.AUTO -> when (hostOf(endpointUrl)) {
            MISTRAL_HOST -> CloudVocabularyField.CONTEXT_BIAS
            OPENAI_HOST -> CloudVocabularyField.PROMPT
            else -> CloudVocabularyField.NONE
        }
    }

    fun promptText(vocabulary: List<String>): String = vocabulary.joinToString(", ")

    private fun hostOf(endpointUrl: String): String? {
        // Use the HTTP client's parser: fragments and user-info must never impersonate a host.
        val url = runCatching { URL(endpointUrl.trim()) }.getOrNull() ?: return null
        if (url.protocol != "https") return null
        return url.host.lowercase().ifEmpty { null }
    }
}
