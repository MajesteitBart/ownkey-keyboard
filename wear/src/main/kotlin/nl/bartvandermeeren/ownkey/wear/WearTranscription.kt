package nl.bartvandermeeren.ownkey.wear

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AudioRecording(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
)

data class RecordingSession(
    val recorder: MediaRecorder,
    val outputFile: File,
)

fun startRecording(context: Context): RecordingSession? {
    val outputFile = runCatching {
        File.createTempFile("ownkey_wear_", ".m4a", context.cacheDir ?: context.filesDir)
    }.getOrNull() ?: return null

    val recorder = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    } catch (_: Exception) {
        outputFile.delete()
        return null
    }

    return try {
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(64_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        RecordingSession(recorder = recorder, outputFile = outputFile)
    } catch (_: Exception) {
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
        outputFile.delete()
        null
    }
}

fun stopRecording(session: RecordingSession): Result<AudioRecording> {
    return runCatching {
        val recorder = session.recorder
        val outputFile = session.outputFile

        try {
            recorder.stop()
        } finally {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }

        val bytes = outputFile.readBytes()
        outputFile.delete()

        if (bytes.isEmpty()) {
            throw IllegalStateException("Geen audio opgenomen")
        }

        AudioRecording(
            bytes = bytes,
            mimeType = "audio/mp4",
            fileName = "recording.m4a",
        )
    }
}

class VoxtralWearClient(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 90_000,
    private val maxRetries: Int = 3,
) {
    companion object {
        const val DefaultEndpointUrl = "https://api.mistral.ai/v1/audio/transcriptions"
        const val DefaultModel = "voxtral-mini-latest"
    }

    suspend fun transcribe(
        apiKey: String,
        endpointUrl: String,
        model: String,
        languageHint: String,
        recording: AudioRecording,
    ): Result<String> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("API key ontbreekt"))
        }
        if (recording.bytes.isEmpty()) {
            return Result.failure(IllegalStateException("Geen audio opgenomen"))
        }

        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxRetries) {
            attempt += 1
            val result = runCatching {
                performRequest(
                    apiKey = apiKey,
                    endpointUrl = endpointUrl,
                    model = model,
                    languageHint = languageHint,
                    recording = recording,
                )
            }
            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull() ?: IllegalStateException("Onbekende transcriptiefout")
            lastError = error
            if (!shouldRetry(error) || attempt >= maxRetries) {
                return Result.failure(error)
            }

            delay(600L * attempt)
        }

        return Result.failure(lastError ?: IllegalStateException("Transcriptie mislukt"))
    }

    private fun performRequest(
        apiKey: String,
        endpointUrl: String,
        model: String,
        languageHint: String,
        recording: AudioRecording,
    ): String {
        val boundary = "----OwnkeyWear${System.currentTimeMillis()}"
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { out ->
                out.writeMultipartField(boundary, "model", model)
                if (languageHint.isNotBlank()) {
                    out.writeMultipartField(boundary, "language", languageHint)
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
                val details = responseBody.trim().take(300)
                throw VoxtralHttpException(
                    statusCode = statusCode,
                    message = "HTTP $statusCode. ${if (details.isNotBlank()) details else "Geen foutdetails"}",
                )
            }

            val transcript = extractTranscript(responseBody)
            if (transcript.isBlank()) {
                throw IllegalStateException("Transcript is leeg")
            }
            return transcript
        } finally {
            connection.disconnect()
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
            is IOException -> true
            is VoxtralHttpException -> {
                error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599
            }

            else -> false
        }
    }

    private class VoxtralHttpException(
        val statusCode: Int,
        message: String,
    ) : IllegalStateException(message)
}

class WearSettingsStore(context: Context) {
    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "ownkey_wear_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("ownkey_wear_fallback", Context.MODE_PRIVATE)
    }

    fun getApiKey(): String = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(value: String) {
        prefs.edit().putString("api_key", value).apply()
    }

    fun getEndpointUrl(): String = prefs.getString("endpoint_url", VoxtralWearClient.DefaultEndpointUrl).orEmpty()
    fun setEndpointUrl(value: String) {
        prefs.edit().putString("endpoint_url", value).apply()
    }

    fun getModel(): String = prefs.getString("model", VoxtralWearClient.DefaultModel).orEmpty()
    fun setModel(value: String) {
        prefs.edit().putString("model", value).apply()
    }

    fun getLanguageHint(): String = prefs.getString("language_hint", "").orEmpty()
    fun setLanguageHint(value: String) {
        prefs.edit().putString("language_hint", value).apply()
    }
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
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.contentOrNull
}

private fun JsonObject.optObject(key: String): JsonObject? {
    return this[key] as? JsonObject
}
