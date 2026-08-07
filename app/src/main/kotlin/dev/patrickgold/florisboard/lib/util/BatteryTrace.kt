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

package dev.patrickgold.florisboard.lib.util

import android.os.Build
import android.os.Trace

/**
 * Fixed, content-free trace labels used to correlate Ownkey work with system power traces.
 *
 * Never add editor text, prompt text, endpoint URLs, provider responses, API keys, audio metadata,
 * or content-derived values to these labels. They are local system-trace events, not telemetry.
 */
object OwnkeyBatteryTraceLabels {
    const val VoiceDictationRecording = "Ownkey.voice.dictation.recording"
    const val VoiceRewriteRecording = "Ownkey.voice.rewrite.recording"
    const val VoiceDictationProcessing = "Ownkey.voice.dictation.processing"
    const val VoiceRewriteProcessing = "Ownkey.voice.rewrite.processing"
    const val TranscriptionRequest = "Ownkey.ai.transcription.request"
    const val RewriteRequest = "Ownkey.ai.rewrite.request"
}

/** Small injectable boundary so lifecycle and cancellation trace balance can be unit tested. */
interface BatteryTraceSink {
    fun beginSection(label: String)
    fun endSection()
    fun beginAsyncSection(label: String, cookie: Int)
    fun endAsyncSection(label: String, cookie: Int)
}

object NoOpBatteryTraceSink : BatteryTraceSink {
    override fun beginSection(label: String) = Unit
    override fun endSection() = Unit
    override fun beginAsyncSection(label: String, cookie: Int) = Unit
    override fun endAsyncSection(label: String, cookie: Int) = Unit
}

/**
 * Android trace sink for profileable debug, beta, benchmark, and release builds.
 *
 * Tracing must never be able to break ordinary typing, so platform calls are best-effort. Async
 * sections require API 29; synchronous provider sections work across Ownkey's supported API range.
 */
object AndroidBatteryTraceSink : BatteryTraceSink {
    override fun beginSection(label: String) {
        runCatching { Trace.beginSection(label) }
    }

    override fun endSection() {
        runCatching { Trace.endSection() }
    }

    override fun beginAsyncSection(label: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Trace.beginAsyncSection(label, cookie) }
        }
    }

    override fun endAsyncSection(label: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Trace.endAsyncSection(label, cookie) }
        }
    }
}
