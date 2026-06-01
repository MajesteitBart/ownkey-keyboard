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

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL

class LlmRewriteClient(
    private val apiKeyProvider: () -> String,
    private val endpointUrlProvider: () -> String,
    private val modelProvider: () -> String,
    private val providerIdProvider: () -> String = { "" },
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
    private val maxRetryAttempts: Int = 2,
    private val retryDelayMs: Long = 700,
) {
    companion object {
        const val DefaultEndpointUrl = "https://api.openai.com/v1/responses"
        const val DefaultModel = "gpt-5.5"
    }

    private data class PreparedRequest(
        val apiKey: String,
        val endpointUrl: String,
        val model: String,
        val providerId: String,
        val prompt: RewritePromptPreset,
        val input: String,
    )

    suspend fun rewrite(input: String, prompt: RewritePromptPreset): Result<String> {
        val preparedRequest = prepareRequest(input, prompt).getOrElse { error ->
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

            val error = result.exceptionOrNull() ?: IllegalStateException("Unknown LLM rewrite error")
            lastError = error
            if (!shouldRetry(error) || attempt >= maxRetryAttempts) {
                return Result.failure(error)
            }
            delay(retryDelayMs * attempt)
        }

        return Result.failure(lastError ?: IllegalStateException("Unknown LLM rewrite error"))
    }

    private fun prepareRequest(input: String, prompt: RewritePromptPreset): Result<PreparedRequest> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("No LLM API key configured."))
        }

        val rawEndpointUrl = endpointUrlProvider().trim()
        val rawProviderId = providerIdProvider().trim()
        val defaultProviderId = LlmRewriteProviders.OpenAiResponses
        val providerId = when {
            rawProviderId.isBlank() -> LlmRewriteProviders.inferFromEndpoint(rawEndpointUrl)
            rawProviderId == defaultProviderId &&
                rawEndpointUrl.isNotBlank() &&
                rawEndpointUrl != LlmRewriteProviders.byId(defaultProviderId).endpointUrl -> {
                LlmRewriteProviders.inferFromEndpoint(rawEndpointUrl)
            }

            else -> rawProviderId
        }
        val providerPreset = LlmRewriteProviders.byId(providerId)

        val endpointUrl = rawEndpointUrl.ifBlank { providerPreset.endpointUrl.ifBlank { DefaultEndpointUrl } }
        if (!endpointUrl.startsWith("https://") && !endpointUrl.startsWith("http://")) {
            return Result.failure(IllegalStateException("LLM endpoint URL must start with https:// or http://"))
        }

        val model = modelProvider().trim().ifBlank { providerPreset.defaultModel.ifBlank { DefaultModel } }
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return Result.failure(IllegalStateException("Select or type text before rewriting."))
        }

        return Result.success(
            PreparedRequest(
                apiKey = apiKey,
                endpointUrl = endpointUrl,
                model = model,
                providerId = providerPreset.id,
                prompt = prompt,
                input = trimmedInput,
            ),
        )
    }

    private fun executeRequest(request: PreparedRequest): String {
        val connection = (URL(request.endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            when (request.providerId) {
                LlmRewriteProviders.Anthropic -> {
                    setRequestProperty("x-api-key", request.apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }

                LlmRewriteProviders.OpenRouter -> {
                    setRequestProperty("Authorization", "Bearer ${request.apiKey}")
                    setRequestProperty("X-Title", "Ownkey Keyboard")
                }

                else -> {
                    setRequestProperty("Authorization", "Bearer ${request.apiKey}")
                }
            }
        }

        try {
            val payload = buildPayload(request).toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }

            val statusCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            if (statusCode !in 200..299) {
                val message = responseBody.trim().take(300)
                throw LlmRewriteHttpException(
                    statusCode = statusCode,
                    message = "LLM rewrite request failed (HTTP $statusCode). ${if (message.isNotBlank()) message else "No response body."}",
                )
            }

            val rewrittenText = extractRewrite(responseBody).trim()
            if (rewrittenText.isBlank()) {
                throw IllegalStateException("LLM returned an empty rewrite.")
            }
            return rewrittenText
        } finally {
            connection.disconnect()
        }
    }

    private fun buildPayload(request: PreparedRequest): JsonObject {
        return when (request.providerId) {
            LlmRewriteProviders.OpenAiResponses -> buildOpenAiResponsesPayload(request)
            LlmRewriteProviders.Anthropic -> buildAnthropicPayload(request)
            else -> buildChatCompletionsPayload(request)
        }
    }

    private fun buildOpenAiResponsesPayload(request: PreparedRequest): JsonObject {
        return buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("instructions", JsonPrimitive(rewriteSystemInstruction))
            put("store", JsonPrimitive(false))
            put("input", JsonPrimitive(rewriteUserInstruction(request)))
        }
    }

    private fun buildAnthropicPayload(request: PreparedRequest): JsonObject {
        return buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("max_tokens", JsonPrimitive(1024))
            put("temperature", JsonPrimitive(0.35))
            put("system", JsonPrimitive(rewriteSystemInstruction))
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(rewriteUserInstruction(request)))
                    },
                )
            })
        }
    }

    private fun buildChatCompletionsPayload(request: PreparedRequest): JsonObject {
        return buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("temperature", JsonPrimitive(0.35))
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(rewriteSystemInstruction))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(rewriteUserInstruction(request)))
                    },
                )
            })
        }
    }

    private val rewriteSystemInstruction =
        "You rewrite user-provided text. Return only the rewritten text without explanation."

    private fun rewriteUserInstruction(request: PreparedRequest): String {
        return "Rewrite instruction:\n${request.prompt.instruction}\n\nText:\n${request.input}"
    }

    private fun extractRewrite(responseBody: String): String {
        val root = Json.parseToJsonElement(responseBody).jsonObject

        val choices = root["choices"] as? JsonArray
        val firstChoice = choices?.firstOrNull()?.jsonObject
        val message = firstChoice?.get("message")?.jsonObject
        val messageContent = message?.optString("content")
        if (!messageContent.isNullOrBlank()) {
            return messageContent
        }

        val anthropicContent = (root["content"] as? JsonArray)
            ?.firstNotNullOfOrNull { contentPart ->
                contentPart.jsonObject.optString("text")
            }
        if (!anthropicContent.isNullOrBlank()) {
            return anthropicContent
        }

        val responseOutput = (root["output"] as? JsonArray)
            ?.firstNotNullOfOrNull { outputItem ->
                val content = outputItem.jsonObject["content"] as? JsonArray
                content?.firstNotNullOfOrNull { contentPart ->
                    contentPart.jsonObject.optString("text")
                }
            }
        if (!responseOutput.isNullOrBlank()) {
            return responseOutput
        }

        return root.optString("output_text")
            ?: root.optString("text")
            ?: ""
    }

    private fun shouldRetry(error: Throwable): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is LlmRewriteHttpException -> {
                error.statusCode == 408 ||
                    error.statusCode == 429 ||
                    error.statusCode in 500..599
            }

            else -> false
        }
    }

    private class LlmRewriteHttpException(
        val statusCode: Int,
        message: String,
    ) : IllegalStateException(message)
}

private fun JsonObject.optString(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.contentOrNull
}
