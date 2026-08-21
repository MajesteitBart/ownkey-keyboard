package nl.bartvandermeeren.ownkey.wear

import android.os.Trace

/** Fixed, content-free spans for profiling the Wear companion on the watch itself. */
internal object WearBatteryTrace {
    const val Recording = "Ownkey.wear.voice.recording"
    const val TranscriptionRequest = "Ownkey.wear.ai.transcription.request"

    fun beginRecording(cookie: Int) {
        runCatching { Trace.beginAsyncSection(Recording, cookie) }
    }

    fun endRecording(cookie: Int) {
        runCatching { Trace.endAsyncSection(Recording, cookie) }
    }

    fun beginRequest() {
        runCatching { Trace.beginSection(TranscriptionRequest) }
    }

    fun endRequest() {
        runCatching { Trace.endSection() }
    }
}
