/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudVocabularyHintsTest : FunSpec({
    test("automatic hints follow the HTTP URL host instead of fragment or authority lookalikes") {
        listOf(
            "https://evil.example#@api.mistral.ai/path",
            "https://evil.example#@api.openai.com/path",
            "api.mistral.ai/path",
            "ftp://api.mistral.ai/path",
            "https://api.mistral.ai:bad/path",
            "https://api.mistral.ai.evil.example/path",
            "https://api.mistral.ai@evil.example/path",
        ).forEach { endpoint ->
            CloudVocabularyHints.field(endpoint, CloudVocabularyMode.AUTO) shouldBe CloudVocabularyField.NONE
        }
    }

    val mistral = "https://api.mistral.ai/v1/audio/transcriptions"
    val openai = "https://api.openai.com/v1/audio/transcriptions"
    val custom = "https://asr.example.org/v1/audio/transcriptions"

    test("automatic mode only uses documented fields and sends nothing to unknown endpoints") {
        CloudVocabularyHints.field(mistral, CloudVocabularyMode.AUTO) shouldBe CloudVocabularyField.CONTEXT_BIAS
        CloudVocabularyHints.field("HTTPS://user@API.MISTRAL.AI:443/v1/audio/transcriptions?x=1", CloudVocabularyMode.AUTO) shouldBe
            CloudVocabularyField.CONTEXT_BIAS
        CloudVocabularyHints.field(openai, CloudVocabularyMode.AUTO) shouldBe CloudVocabularyField.PROMPT
        CloudVocabularyHints.field(custom, CloudVocabularyMode.AUTO) shouldBe CloudVocabularyField.NONE
        CloudVocabularyHints.field("https://openrouter.ai/api/v1/audio/transcriptions", CloudVocabularyMode.AUTO) shouldBe
            CloudVocabularyField.NONE
        CloudVocabularyHints.field("", CloudVocabularyMode.AUTO) shouldBe CloudVocabularyField.NONE
    }

    test("explicit modes override the endpoint") {
        CloudVocabularyHints.field(custom, CloudVocabularyMode.PROMPT) shouldBe CloudVocabularyField.PROMPT
        CloudVocabularyHints.field(mistral, CloudVocabularyMode.OFF) shouldBe CloudVocabularyField.NONE
        CloudVocabularyMode.fromPreference("prompt") shouldBe CloudVocabularyMode.PROMPT
        CloudVocabularyMode.fromPreference(" OFF ") shouldBe CloudVocabularyMode.OFF
        CloudVocabularyMode.fromPreference("garbage") shouldBe CloudVocabularyMode.AUTO
        CloudVocabularyHints.promptText(listOf("Ownkey", "Bart")) shouldBe "Ownkey, Bart"
    }

    test("one request is bounded by term count and bytes in saved order, without touching the saved list") {
        val many = (1..250).map { "Term$it" }
        val byCount = CloudVocabularyHints.bound(many)
        byCount.included shouldBe CloudVocabularyHints.MAX_TERMS
        byCount.dropped shouldBe 50
        byCount.terms.first() shouldBe "Term1"
        val byBytes = CloudVocabularyHints.bound(listOf("Zoë", "Müller", "Ångström"), maxBytes = 12)
        // "Zoë" is 4 bytes and ", Müller" is 9 bytes, so the second term already overflows.
        byBytes.terms shouldBe listOf("Zoë")
        byBytes.dropped shouldBe 2
        CloudVocabularyHints.bound(listOf(" ", "Ownkey", "")).terms shouldBe listOf("Ownkey")
        many.size shouldBe 250
    }

    context("request fields") {
        val recording = AudioRecording(byteArrayOf(1, 2, 3), 16000, 1, 500L, "audio/mp4", "clip.m4a")
        fun client(endpoint: String, field: CloudVocabularyField, vocabulary: List<String>) = VoxtralRelayTranscriptionClient(
            apiKeyProvider = { "key" },
            endpointUrlProvider = { endpoint },
            vocabularyProvider = { vocabulary },
            vocabularyFieldProvider = { field },
        )

        test("vocabulary is trimmed and carried with the resolved field") {
            val fields = client(mistral, CloudVocabularyField.CONTEXT_BIAS, listOf("Ownkey", " Bart ", "")).prepareRequestFields(recording).getOrThrow()
            fields.vocabulary shouldBe listOf("Ownkey", "Bart")
            fields.vocabularyField shouldBe CloudVocabularyField.CONTEXT_BIAS
            fields.model shouldBe VoxtralRelayTranscriptionClient.DefaultModel
        }

        test("no field means no vocabulary, and an empty vocabulary means no field") {
            client(custom, CloudVocabularyField.NONE, listOf("Ownkey")).prepareRequestFields(recording).getOrThrow().let { fields ->
                fields.vocabulary shouldBe emptyList()
                fields.vocabularyField shouldBe CloudVocabularyField.NONE
            }
            client(openai, CloudVocabularyField.PROMPT, listOf("  ")).prepareRequestFields(recording).getOrThrow().let { fields ->
                fields.vocabulary shouldBe emptyList()
                fields.vocabularyField shouldBe CloudVocabularyField.NONE
            }
        }
    }
})
