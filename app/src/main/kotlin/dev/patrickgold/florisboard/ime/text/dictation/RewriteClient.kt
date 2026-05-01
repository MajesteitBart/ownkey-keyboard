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

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Abstraction over post-dictation text cleanup.
 *
 * Implementations must not log request text, prompts, responses, or credentials.
 */
interface RewriteClient {
    suspend fun rewrite(request: RewriteRequest): Result<String>
}

data class RewriteRequest(
    val text: String,
    val languageTag: String? = null,
)

object DictationRewritePrompt {
    fun systemPrompt(languageTag: String?): String {
        val languageInstruction = languageInstruction(languageTag)
        return buildString {
            append("Clean up a voice dictation transcript. ")
            append("Remove stutters, repeated words, false starts, and obvious grammar issues. ")
            append("Preserve the user's meaning, facts, tone, names, numbers, links, and formatting. ")
            append(languageInstruction)
            append(" Return only the cleaned text, with no explanation.")
        }
    }

    private fun languageInstruction(languageTag: String?): String {
        val normalizedTag = languageTag?.trim().orEmpty()
        if (normalizedTag.isBlank()) {
            return "Keep the output in the same language as the input."
        }

        val displayLanguage = Locale.forLanguageTag(normalizedTag).getDisplayLanguage(Locale.ENGLISH)
        return if (displayLanguage.isBlank()) {
            "Write the output in language tag $normalizedTag when that matches the input."
        } else {
            "Write the output in $displayLanguage ($normalizedTag) when that matches the input."
        }
    }
}

/**
 * Mistral chat-completions rewrite provider using the same user-provided API key pattern as Voxtral transcription.
 */
class MistralChatRewriteClient(
    private val apiKeyProvider: () -> String,
    private val endpointUrlProvider: () -> String = { DefaultEndpointUrl },
    private val modelProvider: () -> String = { DefaultModel },
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRetryAttempts: Int = 2,
    private val retryDelayMs: Long = 500,
) : RewriteClient {
    companion object {
        const val DefaultEndpointUrl = "https://api.mistral.ai/v1/chat/completions"
        const val DefaultModel = "ministral-3b-latest"
        private const val MaxInputChars = 4_000
    }

    private data class PreparedRequest(
        val apiKey: String,
        val endpointUrl: String,
        val requestBody: String,
    )

    override suspend fun rewrite(request: RewriteRequest): Result<String> {
        val preparedRequest = prepareRequest(request).getOrElse { error ->
            return Result.failure(error)
        }

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < maxRetryAttempts) {
            attempt += 1
            val result = runCatching {
                executeRequest(preparedRequest)
            }
            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Unknown dictation cleanup error")
            lastError = error

            if (!shouldRetry(error) || attempt >= maxRetryAttempts) {
                return Result.failure(error)
            }

            delay(retryDelayMs * attempt)
        }

        return Result.failure(lastError ?: IllegalStateException("Unknown dictation cleanup error"))
    }

    private fun prepareRequest(request: RewriteRequest): Result<PreparedRequest> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("No Voxtral API key configured."))
        }

        val text = request.text.trim()
        if (text.isBlank()) {
            return Result.failure(IllegalStateException("No dictation text to clean up."))
        }
        if (text.length > MaxInputChars) {
            return Result.failure(IllegalStateException("Dictation text is too long to clean up."))
        }

        val endpointUrl = endpointUrlProvider().trim().ifBlank { DefaultEndpointUrl }
        if (!endpointUrl.startsWith("https://") && !endpointUrl.startsWith("http://")) {
            return Result.failure(IllegalStateException("Post-processing endpoint URL must start with https:// or http://"))
        }

        return Result.success(
            PreparedRequest(
                apiKey = apiKey,
                endpointUrl = endpointUrl,
                requestBody = buildRequestBody(
                    model = modelProvider().trim().ifBlank { DefaultModel },
                    request = request.copy(text = text),
                ),
            ),
        )
    }

    internal fun buildRequestBody(model: String, request: RewriteRequest): String {
        return buildJsonObject {
            put("model", JsonPrimitive(model))
            put("temperature", JsonPrimitive(0.1))
            put("messages", buildJsonArray {
                add(
                    chatMessage(
                        role = "system",
                        content = DictationRewritePrompt.systemPrompt(request.languageTag),
                    ),
                )
                add(
                    chatMessage(
                        role = "user",
                        content = request.text,
                    ),
                )
            })
        }.toString()
    }

    private fun chatMessage(role: String, content: String): JsonObject {
        return buildJsonObject {
            put("role", JsonPrimitive(role))
            put("content", JsonPrimitive(content))
        }
    }

    private fun executeRequest(preparedRequest: PreparedRequest): String {
        val connection = (URL(preparedRequest.endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Authorization", "Bearer ${preparedRequest.apiKey}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(preparedRequest.requestBody)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            if (statusCode !in 200..299) {
                throw RewriteHttpException(
                    statusCode = statusCode,
                    message = "Dictation cleanup request failed (HTTP $statusCode).",
                )
            }

            val rewrittenText = extractRewrittenText(responseBody)
            if (rewrittenText.isBlank()) {
                throw IllegalStateException("Dictation cleanup returned empty text.")
            }

            return rewrittenText
        } finally {
            connection.disconnect()
        }
    }

    private fun extractRewrittenText(responseBody: String): String {
        val root = Json.parseToJsonElement(responseBody).jsonObject
        val choices = root["choices"] as? JsonArray ?: return ""
        val firstChoice = choices.firstOrNull()?.jsonObject ?: return ""
        return firstChoice.optObject("message")?.optString("content")
            ?: firstChoice.optString("text")
            ?: ""
    }

    private fun shouldRetry(error: Throwable): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is RewriteHttpException -> {
                error.statusCode == 408 ||
                    error.statusCode == 429 ||
                    error.statusCode in 500..599
            }

            else -> false
        }
    }

    private class RewriteHttpException(
        val statusCode: Int,
        message: String,
    ) : IllegalStateException(message)
}

private fun JsonObject.optString(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.optObject(key: String): JsonObject? {
    return this[key] as? JsonObject
}
