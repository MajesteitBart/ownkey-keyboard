/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import dev.patrickgold.florisboard.ime.text.network.withCancellableHttpConnection
import dev.patrickgold.florisboard.lib.util.OwnkeyBatteryTraceLabels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Abstraction over the component that turns audio into transcript text.
 */
interface TranscriptionClient {
    /**
     * @return A successful transcript result or a failure with the underlying error.
     */
    suspend fun transcribe(recording: AudioRecording): Result<String>
}

enum class TranscriptionPurpose {
    DICTATION,
    VOICE_REWRITE_INSTRUCTION,
}

/**
 * The three reachable states of the dictation language setting.
 *
 * They are distinct on purpose: an empty stored value means "follow the keyboard language", which is
 * the default, while `Auto` is an explicit choice that sends no hint and lets the provider detect
 * the language. Neither affects rewrite instructions or the language a rewrite is returned in.
 */
enum class TranscriptionLanguageMode {
    FOLLOW_KEYBOARD,
    AUTO,
    EXPLICIT,
}

object TranscriptionLanguageHints {
    /** Stored value used by the explicit Auto setting; blank remains the legacy/unset value. */
    const val AUTO = "auto"

    private const val DEFAULT_EXPLICIT_LANGUAGE = "en"

    fun modeOf(storedLanguageHint: String): TranscriptionLanguageMode {
        val stored = storedLanguageHint.trim()
        return when {
            stored.isEmpty() -> TranscriptionLanguageMode.FOLLOW_KEYBOARD
            stored.equals(AUTO, ignoreCase = true) -> TranscriptionLanguageMode.AUTO
            else -> TranscriptionLanguageMode.EXPLICIT
        }
    }

    fun storedValueFor(mode: TranscriptionLanguageMode, explicitLanguage: String): String = when (mode) {
        TranscriptionLanguageMode.FOLLOW_KEYBOARD -> ""
        TranscriptionLanguageMode.AUTO -> AUTO
        TranscriptionLanguageMode.EXPLICIT -> explicitLanguage.trim()
    }

    /**
     * Resolves a radio-button selection into a persistable value.
     *
     * The current stored value cannot be reused blindly when selecting [TranscriptionLanguageMode.EXPLICIT]:
     * blank and [AUTO] are sentinels for the other two modes, so persisting either would make the UI
     * immediately jump back and keep the explicit-language field unreachable. Prefer an existing
     * explicit value, then the active keyboard language, with English only as a defensive fallback
     * for the settings screen's no-subtype edge case.
     */
    fun storedValueForSelection(
        mode: TranscriptionLanguageMode,
        currentStoredLanguageHint: String,
        activeSubtypeLanguageTag: String?,
    ): String = when (mode) {
        TranscriptionLanguageMode.FOLLOW_KEYBOARD,
        TranscriptionLanguageMode.AUTO,
        -> storedValueFor(mode, currentStoredLanguageHint)

        TranscriptionLanguageMode.EXPLICIT -> listOfNotNull(
            currentStoredLanguageHint.trim().takeIf(::isExplicitLanguage),
            activeSubtypeLanguageTag?.trim()?.takeIf(::isExplicitLanguage),
        ).firstOrNull() ?: DEFAULT_EXPLICIT_LANGUAGE
    }

    private fun isExplicitLanguage(value: String): Boolean =
        value.isNotEmpty() && !value.equals(AUTO, ignoreCase = true)

    fun resolve(
        purpose: TranscriptionPurpose,
        storedLanguageHint: String,
        activeSubtypeLanguageTag: String?,
    ): String? {
        if (purpose == TranscriptionPurpose.VOICE_REWRITE_INSTRUCTION) return null
        val stored = storedLanguageHint.trim()
        return when {
            stored.equals(AUTO, ignoreCase = true) -> null
            stored.isNotEmpty() -> stored
            else -> activeSubtypeLanguageTag?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * Safe default used by MVP.
 */
class MockTranscriptionClient(
    private val mockTranscriptProvider: () -> String = {
        "This is a Voxtral Mini mock transcript."
    },
) : TranscriptionClient {
    override suspend fun transcribe(recording: AudioRecording): Result<String> {
        delay(200)
        return Result.success(mockTranscriptProvider())
    }
}

/**
 * Direct client for Voxtral transcription API.
 *
 * NOTE:
 * - This sends the user-entered API key directly from device to Mistral API.
 * - For production hardening, move this behind a backend relay.
 */
class VoxtralRelayTranscriptionClient(
    private val apiKeyProvider: () -> String,
    private val endpointUrlProvider: () -> String = { DefaultEndpointUrl },
    private val modelProvider: () -> String = { DefaultModel },
    private val languageHintProvider: () -> String? = { null },
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
    private val maxRetryAttempts: Int = 2,
    private val retryDelayMs: Long = 700,
) : TranscriptionClient {
    companion object {
        const val DefaultEndpointUrl = "https://api.mistral.ai/v1/audio/transcriptions"
        const val DefaultModel = "voxtral-mini-latest"
    }

    private data class PreparedRequest(
        val apiKey: String,
        val endpointUrl: String,
        val model: String,
        val languageHint: String?,
    )

    internal data class RequestFields(
        val model: String,
        val language: String?,
    )

    override suspend fun transcribe(recording: AudioRecording): Result<String> {
        val preparedRequest = prepareRequest(recording).getOrElse { error ->
            return Result.failure(error)
        }

        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < maxRetryAttempts) {
            attempt += 1
            val result = try {
                Result.success(executeRequest(preparedRequest, recording))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Unknown Voxtral transcription error")
            lastError = error

            if (!shouldRetry(error) || attempt >= maxRetryAttempts) {
                return Result.failure(error)
            }

            delay(retryDelayMs * attempt)
        }

        return Result.failure(lastError ?: IllegalStateException("Unknown Voxtral transcription error"))
    }

    internal fun prepareRequestFields(recording: AudioRecording): Result<RequestFields> =
        prepareRequest(recording).map { request ->
            RequestFields(model = request.model, language = request.languageHint)
        }

    private fun prepareRequest(recording: AudioRecording): Result<PreparedRequest> {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("No Voxtral API key configured."))
        }
        if (recording.bytes.isEmpty()) {
            return Result.failure(IllegalStateException("No audio was captured."))
        }

        val endpointUrl = endpointUrlProvider().trim().ifBlank { DefaultEndpointUrl }
        if (!endpointUrl.startsWith("https://") && !endpointUrl.startsWith("http://")) {
            return Result.failure(IllegalStateException("Endpoint URL must start with https:// or http://"))
        }

        val model = modelProvider().trim().ifBlank { DefaultModel }
        val languageHint = languageHintProvider()?.trim()?.takeIf { it.isNotEmpty() }

        return Result.success(
            PreparedRequest(
                apiKey = apiKey,
                endpointUrl = endpointUrl,
                model = model,
                languageHint = languageHint,
            ),
        )
    }

    private suspend fun executeRequest(preparedRequest: PreparedRequest, recording: AudioRecording): String {
        val boundary = "----VoxtralBoundary${System.currentTimeMillis()}"
        return withCancellableHttpConnection(
            traceLabel = OwnkeyBatteryTraceLabels.TranscriptionRequest,
            openConnection = {
                (URL(preparedRequest.endpointUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    setRequestProperty("Authorization", "Bearer ${preparedRequest.apiKey}")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }
            },
        ) { connection ->
            DataOutputStream(connection.outputStream).use { out ->
                out.writeMultipartField(boundary, "model", preparedRequest.model)
                if (!preparedRequest.languageHint.isNullOrBlank()) {
                    out.writeMultipartField(boundary, "language", preparedRequest.languageHint)
                }
                out.writeMultipartFile(
                    boundary = boundary,
                    fieldName = "file",
                    fileName = recording.fileName,
                    mimeType = recording.mimeType,
                    bytes = recording.bytes,
                )
                out.writeBytes("--$boundary--\r\n")
                out.flush()
            }

            val statusCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            if (statusCode !in 200..299) {
                val message = responseBody.trim().take(300)
                throw VoxtralHttpException(
                    statusCode = statusCode,
                    message = "Voxtral API request failed (HTTP $statusCode). ${if (message.isNotBlank()) message else "No response body."}",
                )
            }

            val transcript = extractTranscript(responseBody)
            if (transcript.isBlank()) {
                throw IllegalStateException("Voxtral API returned an empty transcript.")
            }

            transcript
        }
    }

    private fun extractTranscript(responseBody: String): String {
        val root = Json.parseToJsonElement(responseBody).jsonObject

        return root.optString("text")
            ?: root.optString("transcript")
            ?: root.optObject("data")?.optString("text")
            ?: root.optObject("result")?.optString("text")
            ?: ""
    }

    private fun shouldRetry(error: Throwable): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is VoxtralHttpException -> {
                error.statusCode == 408 ||
                    error.statusCode == 429 ||
                    error.statusCode in 500..599
            }

            else -> false
        }
    }

    private class VoxtralHttpException(
        val statusCode: Int,
        message: String,
    ) : IllegalStateException(message)
}

private fun DataOutputStream.writeMultipartField(
    boundary: String,
    fieldName: String,
    value: String,
) {
    writeBytes("--$boundary\r\n")
    writeBytes("Content-Disposition: form-data; name=\"$fieldName\"\r\n\r\n")
    writeBytes(value)
    writeBytes("\r\n")
}

private fun DataOutputStream.writeMultipartFile(
    boundary: String,
    fieldName: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
) {
    writeBytes("--$boundary\r\n")
    writeBytes("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n")
    writeBytes("Content-Type: $mimeType\r\n\r\n")
    write(bytes)
    writeBytes("\r\n")
}

private fun JsonObject.optString(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.contentOrNull
}

private fun JsonObject.optObject(key: String): JsonObject? {
    return this[key] as? JsonObject
}
