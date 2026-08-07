/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TranscriptionLanguageHintsTest : FunSpec({
    test("dictation resolves explicit unset and Auto values across subtype switches") {
        TranscriptionLanguageHints.resolve(
            TranscriptionPurpose.DICTATION,
            storedLanguageHint = "nl",
            activeSubtypeLanguageTag = "en-US",
        ) shouldBe "nl"

        listOf("en-US", "nl-NL", "de-DE").forEach { subtype ->
            TranscriptionLanguageHints.resolve(
                TranscriptionPurpose.DICTATION,
                storedLanguageHint = "",
                activeSubtypeLanguageTag = subtype,
            ) shouldBe subtype
        }

        TranscriptionLanguageHints.resolve(
            TranscriptionPurpose.DICTATION,
            storedLanguageHint = TranscriptionLanguageHints.AUTO,
            activeSubtypeLanguageTag = "nl-NL",
        ) shouldBe null
        TranscriptionLanguageHints.resolve(
            TranscriptionPurpose.DICTATION,
            storedLanguageHint = "",
            activeSubtypeLanguageTag = null,
        ) shouldBe null
    }

    test("voice rewrite request fields omit language for every setting and subtype") {
        val settings = listOf("", TranscriptionLanguageHints.AUTO, "nl", "en-US")
        val subtypes = listOf(null, "nl-NL", "en-US")
        settings.forEach { setting ->
            subtypes.forEach { subtype ->
                val resolved = TranscriptionLanguageHints.resolve(
                    purpose = TranscriptionPurpose.VOICE_REWRITE_INSTRUCTION,
                    storedLanguageHint = setting,
                    activeSubtypeLanguageTag = subtype,
                )
                val fields = requestFields(resolved)
                fields.language shouldBe null
            }
        }
    }

    test("dictation language is asserted on the prepared request without network access") {
        requestFields("nl-NL").language shouldBe "nl-NL"
        requestFields(null).language shouldBe null
    }
})

private fun requestFields(language: String?): VoxtralRelayTranscriptionClient.RequestFields {
    val client = VoxtralRelayTranscriptionClient(
        apiKeyProvider = { "test-key" },
        endpointUrlProvider = { "https://example.invalid/transcriptions" },
        modelProvider = { "test-model" },
        languageHintProvider = { language },
        maxRetryAttempts = 1,
    )
    return client.prepareRequestFields(testLanguageRecording()).getOrThrow()
}

private fun testLanguageRecording() = AudioRecording(
    bytes = byteArrayOf(1),
    sampleRateHz = 16_000,
    channelCount = 1,
    durationMs = 250,
    mimeType = "audio/test",
    fileName = "recording.test",
)
