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
