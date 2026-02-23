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

/**
 * Minimal abstraction around microphone recording for dictation.
 *
 * TODO(voxtral): Replace [NoOpAudioRecorder] with a real recorder implementation
 * (MediaRecorder/AudioRecord) once runtime permission UX and format choices are finalized.
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
)

/**
 * Recorder used in MVP mock mode.
 *
 * It intentionally captures no microphone audio to keep this MVP safe and testable
 * without RECORD_AUDIO permission.
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
            ),
        )
    }

    override fun cancel() {
        startedAtMs = null
    }
}
