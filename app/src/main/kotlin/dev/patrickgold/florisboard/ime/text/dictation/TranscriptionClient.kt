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

import kotlinx.coroutines.delay

/**
 * Abstraction over the component that turns audio into transcript text.
 */
interface TranscriptionClient {
    /**
     * @return A successful transcript result or a failure with the underlying error.
     */
    suspend fun transcribe(recording: AudioRecording): Result<String>
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
 * Placeholder for upcoming real Voxtral integration.
 *
 * TODO(voxtral):
 *  - Upload audio bytes to backend relay.
 *  - Relay service should call Mistral Voxtral Mini and return transcript.
 *  - Add auth, retry, timeout, and structured error mapping.
 */
class VoxtralRelayTranscriptionClient : TranscriptionClient {
    override suspend fun transcribe(recording: AudioRecording): Result<String> {
        return Result.failure(
            NotImplementedError("Voxtral relay transcription is not implemented in MVP."),
        )
    }
}
