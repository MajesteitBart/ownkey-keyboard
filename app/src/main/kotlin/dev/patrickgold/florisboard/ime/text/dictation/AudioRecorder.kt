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

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Minimal abstraction around microphone recording for dictation.
 */
interface AudioRecorder {
    /**
     * Starts recording audio.
     *
     * @return `true` if recording started, `false` if already started or unavailable.
     */
    fun start(): Boolean

    /**
     * Stops recording and returns captured audio.
     */
    fun stopAndRead(): Result<AudioRecording>

    /**
     * Cancels an in-progress recording without producing output.
     */
    fun cancel()
}

data class AudioRecording(
    val bytes: ByteArray,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationMs: Long,
    val mimeType: String,
    val fileName: String,
)

/**
 * Recorder used in MVP mock mode.
 */
class NoOpAudioRecorder(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AudioRecorder {
    private var startedAtMs: Long? = null

    override fun start(): Boolean {
        if (startedAtMs != null) return false
        startedAtMs = nowMs()
        return true
    }

    override fun stopAndRead(): Result<AudioRecording> {
        val startedAt = startedAtMs ?: return Result.failure(
            IllegalStateException("No active recording session"),
        )
        startedAtMs = null
        val durationMs = (nowMs() - startedAt).coerceAtLeast(0L)
        return Result.success(
            AudioRecording(
                bytes = ByteArray(0),
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = durationMs,
                mimeType = "audio/wav",
                fileName = "recording.wav",
            ),
        )
    }

    override fun cancel() {
        startedAtMs = null
    }
}

/**
 * Basic recorder implementation for real Voxtral requests.
 *
 * Records AAC audio into a temporary M4A file and returns file bytes after stop.
 */
class MediaRecorderAudioRecorder(
    private val context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long? = null

    override fun start(): Boolean {
        if (startedAtMs != null) return false

        val file = runCatching {
            File.createTempFile("voxtral_dictation_", ".m4a", context.cacheDir ?: context.filesDir)
        }.getOrElse {
            return false
        }

        val localRecorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (error: Exception) {
            file.delete()
            return false
        }

        return try {
            localRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioChannels(1)
                setAudioEncodingBitRate(64_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = localRecorder
            outputFile = file
            startedAtMs = nowMs()
            true
        } catch (error: Exception) {
            runCatching { localRecorder.reset() }
            runCatching { localRecorder.release() }
            file.delete()
            false
        }
    }

    override fun stopAndRead(): Result<AudioRecording> {
        val localRecorder = recorder ?: return Result.failure(
            IllegalStateException("No active recording session"),
        )
        val file = outputFile ?: return Result.failure(
            IllegalStateException("No active recording file"),
        )
        val startedAt = startedAtMs ?: nowMs()

        val stopResult = runCatching { localRecorder.stop() }
        runCatching { localRecorder.reset() }
        runCatching { localRecorder.release() }

        recorder = null
        outputFile = null
        startedAtMs = null

        stopResult.exceptionOrNull()?.let { error ->
            file.delete()
            return Result.failure(IllegalStateException("Recording was too short or unavailable.", error))
        }

        val durationMs = (nowMs() - startedAt).coerceAtLeast(0L)
        return runCatching {
            val bytes = file.readBytes()
            file.delete()
            AudioRecording(
                bytes = bytes,
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = durationMs,
                mimeType = "audio/mp4",
                fileName = "recording.m4a",
            )
        }
    }

    override fun cancel() {
        val localRecorder = recorder
        val file = outputFile

        recorder = null
        outputFile = null
        startedAtMs = null

        if (localRecorder != null) {
            runCatching { localRecorder.stop() }
            runCatching { localRecorder.reset() }
            runCatching { localRecorder.release() }
        }
        file?.delete()
    }
}
