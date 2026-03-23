package nl.bartvandermeeren.ownkey.wear

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AudioRecording(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
    val recordingUri: String?,
    val durationMs: Long,
)

data class RecordingSession(
    val recorder: MediaRecorder,
    val outputUri: Uri,
    val outputFileName: String,
    val startedAtEpochMs: Long,
    val descriptor: ParcelFileDescriptor,
    val appContext: Context,
    val usesPendingFlag: Boolean,
)

fun startRecording(context: Context): RecordingSession? {
    val appContext = context.applicationContext
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "ownkey_wear_$timestamp.m4a"
    val usePending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_RECORDINGS}/Ownkey")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val outputUri = appContext.contentResolver
        .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: return null

    val descriptor = runCatching {
        appContext.contentResolver.openFileDescriptor(outputUri, "w")
    }.getOrNull()

    if (descriptor == null) {
        appContext.contentResolver.delete(outputUri, null, null)
        return null
    }

    val recorder = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    } catch (_: Exception) {
        runCatching { descriptor.close() }
        appContext.contentResolver.delete(outputUri, null, null)
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
            setOutputFile(descriptor.fileDescriptor)
            prepare()
            start()
        }

        RecordingSession(
            recorder = recorder,
            outputUri = outputUri,
            outputFileName = fileName,
            startedAtEpochMs = System.currentTimeMillis(),
            descriptor = descriptor,
            appContext = appContext,
            usesPendingFlag = usePending,
        )
    } catch (_: Exception) {
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
        runCatching { descriptor.close() }
        appContext.contentResolver.delete(outputUri, null, null)
        null
    }
}

fun stopRecording(session: RecordingSession): Result<AudioRecording> {
    return runCatching {
        stopRecorderInternal(session)
        publishIfPending(session)

        val bytes = session.appContext.contentResolver.openInputStream(session.outputUri)
            ?.use { it.readBytes() }
            ?: ByteArray(0)

        if (bytes.isEmpty()) {
            session.appContext.contentResolver.delete(session.outputUri, null, null)
            throw IllegalStateException("Geen audio opgenomen")
        }

        AudioRecording(
            bytes = bytes,
            mimeType = "audio/mp4",
            fileName = session.outputFileName,
            recordingUri = session.outputUri.toString(),
            durationMs = (System.currentTimeMillis() - session.startedAtEpochMs).coerceAtLeast(0L),
        )
    }
}

fun cancelRecording(session: RecordingSession): Result<Unit> {
    return runCatching {
        stopRecorderInternal(session)
        session.appContext.contentResolver.delete(session.outputUri, null, null)
    }
}

private fun stopRecorderInternal(session: RecordingSession) {
    try {
        session.recorder.stop()
    } catch (_: Exception) {
        // Ignore stop failures for very short recordings.
    } finally {
        runCatching { session.recorder.reset() }
        runCatching { session.recorder.release() }
        runCatching { session.descriptor.close() }
    }
}

private fun publishIfPending(session: RecordingSession) {
    if (!session.usesPendingFlag || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
    }
    session.appContext.contentResolver.update(session.outputUri, values, null, null)
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

@Serializable
data class SavedTranscriptSession(
    val id: String,
    val createdAtEpochMs: Long,
    val providerLabel: String,
    val transcript: String,
    val durationMs: Long,
    val recordingUri: String? = null,
)

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

    fun getSavedSessions(): List<SavedTranscriptSession> {
        val raw = prefs.getString("saved_sessions_json", "[]").orEmpty()
        return runCatching {
            Json.decodeFromString<List<SavedTranscriptSession>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveSession(session: SavedTranscriptSession) {
        val updated = listOf(session) + getSavedSessions().filterNot { it.id == session.id }
        val trimmed = updated.take(120)
        prefs.edit().putString("saved_sessions_json", Json.encodeToString(trimmed)).apply()
    }

    fun removeSession(sessionId: String) {
        val updated = getSavedSessions().filterNot { it.id == sessionId }
        prefs.edit().putString("saved_sessions_json", Json.encodeToString(updated)).apply()
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
