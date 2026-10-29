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

package dev.patrickgold.florisboard.app.settings.voxtral

import dev.patrickgold.florisboard.ime.text.dictation.offline.LocalModelState
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AiStatusTest : FunSpec({
    fun dictation(
        preference: String,
        hasCloudKey: Boolean? = true,
        endpoint: String = "",
        orukeet: OrukeetAvailability = OrukeetAvailability.READY,
        debug: Boolean = false,
    ) = AiStatus.dictation(preference, hasCloudKey, endpoint, orukeet, debug)

    fun rewrite(providerId: String, endpoint: String = "", model: String = "", hasKey: Boolean? = true) =
        AiStatus.rewrite(LlmRewriteProviders.resolve(providerId, endpoint, model), hasKey)

    test("on-device dictation reports whether its model can dictate") {
        dictation("orukeet", orukeet = OrukeetAvailability.READY) shouldBe DictationStatus.OnDevice
        dictation("orukeet", orukeet = OrukeetAvailability.MISSING) shouldBe DictationStatus.OnDeviceNeedsDownload
        dictation("orukeet", orukeet = OrukeetAvailability.NOT_ACTIVE) shouldBe DictationStatus.NotSetUp
        dictation("orukeet", orukeet = OrukeetAvailability.UNSUPPORTED) shouldBe DictationStatus.NotSetUp
    }

    test("cloud dictation names the provider, or the host of a server it doesn't know") {
        dictation("cloud") shouldBe DictationStatus.Cloud("Mistral")
        dictation("cloud", endpoint = "  ") shouldBe DictationStatus.Cloud("Mistral")
        dictation("cloud", endpoint = "https://api.openai.com/v1/audio/transcriptions") shouldBe
            DictationStatus.Cloud("OpenAI")
        dictation("cloud", endpoint = "http://asr.example.com:8000/v1/audio/transcriptions") shouldBe
            DictationStatus.Cloud("asr.example.com")
        dictation("cloud", endpoint = "https://") shouldBe DictationStatus.Cloud(null)
    }

    test("cloud dictation asks for a key before it questions the server address") {
        dictation("cloud", hasCloudKey = false) shouldBe DictationStatus.CloudNeedsKey
        dictation("cloud", hasCloudKey = false, endpoint = "asr.example.com/v1") shouldBe DictationStatus.CloudNeedsKey
        dictation("cloud", endpoint = "asr.example.com/v1") shouldBe DictationStatus.CloudCheckServerAddress
        dictation("cloud", endpoint = "ftp://asr.example.com/v1") shouldBe DictationStatus.CloudCheckServerAddress
    }

    test("system voice input, demo dictation and unknown choices") {
        dictation("external") shouldBe DictationStatus.SystemVoiceInput
        dictation("", hasCloudKey = false, debug = false) shouldBe DictationStatus.SystemVoiceInput
        dictation("", hasCloudKey = false, debug = true) shouldBe DictationStatus.Demo
        dictation("", hasCloudKey = true) shouldBe DictationStatus.Cloud("Mistral")
        dictation("unavailable") shouldBe DictationStatus.NotSetUp
        dictation("something-else") shouldBe DictationStatus.NotSetUp
    }

    test("dictation waits only for the checks its choice depends on") {
        dictation("cloud", hasCloudKey = null) shouldBe null
        dictation("", hasCloudKey = null) shouldBe null
        dictation("orukeet", orukeet = OrukeetAvailability.CHECKING) shouldBe null
        dictation("orukeet", hasCloudKey = null, orukeet = OrukeetAvailability.READY) shouldBe DictationStatus.OnDevice
        dictation("external", hasCloudKey = null) shouldBe DictationStatus.SystemVoiceInput
        dictation("cloud", hasCloudKey = true, orukeet = OrukeetAvailability.CHECKING) shouldBe
            DictationStatus.Cloud("Mistral")
    }

    test("rewrite names the provider once it has a key and a usable server") {
        rewrite(LlmRewriteProviders.Default) shouldBe RewriteStatus.Ready("OpenRouter")
        rewrite(LlmRewriteProviders.OpenAiResponses) shouldBe RewriteStatus.Ready("OpenAI")
        rewrite(LlmRewriteProviders.OpenAiChatCompletions) shouldBe RewriteStatus.Ready("OpenAI")
        rewrite(LlmRewriteProviders.Anthropic) shouldBe RewriteStatus.Ready("Anthropic")
        rewrite(LlmRewriteProviders.Mistral) shouldBe RewriteStatus.Ready("Mistral")
        rewrite(LlmRewriteProviders.Custom, "https://llm.example.com/v1/chat/completions", "local-model") shouldBe
            RewriteStatus.Ready("llm.example.com")
    }

    test("rewrite asks for a key first, then for a complete server") {
        rewrite(LlmRewriteProviders.Default, hasKey = false) shouldBe RewriteStatus.NeedsKey
        rewrite(LlmRewriteProviders.Custom, hasKey = false) shouldBe RewriteStatus.NeedsKey
        rewrite(LlmRewriteProviders.Custom) shouldBe RewriteStatus.NeedsSetup
        rewrite(LlmRewriteProviders.Custom, "https://llm.example.com/v1/chat/completions") shouldBe
            RewriteStatus.NeedsSetup
        rewrite(LlmRewriteProviders.Custom, "llm.example.com/v1", "local-model") shouldBe RewriteStatus.NeedsSetup
        rewrite(LlmRewriteProviders.Mistral, "api.mistral.ai/v1/chat/completions") shouldBe RewriteStatus.NeedsSetup
    }

    test("rewrite waits for the key check") {
        rewrite(LlmRewriteProviders.Default, hasKey = null) shouldBe null
    }

    test("only values that need setup carry the warning tone") {
        val warnings = listOf(
            DictationStatus.OnDeviceNeedsDownload,
            DictationStatus.CloudNeedsKey,
            DictationStatus.CloudCheckServerAddress,
            DictationStatus.NotSetUp,
            RewriteStatus.NeedsKey,
            RewriteStatus.NeedsSetup,
        )
        val normal = listOf(
            DictationStatus.OnDevice,
            DictationStatus.Cloud("Mistral"),
            DictationStatus.Cloud(null),
            DictationStatus.SystemVoiceInput,
            DictationStatus.Demo,
            RewriteStatus.Ready("OpenRouter"),
            RewriteStatus.Ready(null),
        )
        warnings.forEach { it.tone shouldBe AiStatusTone.Warning }
        normal.forEach { it.tone shouldBe AiStatusTone.Normal }
    }

    test("the combined status keeps each line independent") {
        AiStatus.of(
            dictationPreference = "external",
            hasCloudKey = null,
            cloudEndpointUrl = "",
            orukeet = OrukeetAvailability.CHECKING,
            rewriteProvider = LlmRewriteProviders.resolve(LlmRewriteProviders.Default, "", ""),
            hasRewriteKey = null,
            debug = false,
        ) shouldBe AiStatus(dictation = DictationStatus.SystemVoiceInput, rewrite = null)
        AiStatus.of(
            dictationPreference = "cloud",
            hasCloudKey = false,
            cloudEndpointUrl = "",
            orukeet = OrukeetAvailability.MISSING,
            rewriteProvider = LlmRewriteProviders.resolve(LlmRewriteProviders.Anthropic, "", ""),
            hasRewriteKey = true,
            debug = false,
        ) shouldBe AiStatus(dictation = DictationStatus.CloudNeedsKey, rewrite = RewriteStatus.Ready("Anthropic"))
    }

    test("Orukeet availability follows the model state") {
        val idle = LocalModelState(phase = ModelPhase.IDLE)
        OrukeetAvailability.of(compatible = false, idle.copy(currentId = "m")) shouldBe OrukeetAvailability.UNSUPPORTED
        OrukeetAvailability.of(compatible = true, LocalModelState()) shouldBe OrukeetAvailability.CHECKING
        OrukeetAvailability.of(compatible = true, idle.copy(currentId = "m", installed = setOf("m"))) shouldBe
            OrukeetAvailability.READY
        OrukeetAvailability.of(compatible = true, idle.copy(installed = setOf("m"))) shouldBe
            OrukeetAvailability.NOT_ACTIVE
        OrukeetAvailability.of(compatible = true, idle) shouldBe OrukeetAvailability.MISSING
        OrukeetAvailability.of(compatible = true, idle.copy(transferPhase = ModelPhase.DOWNLOADING)) shouldBe
            OrukeetAvailability.MISSING
    }
})
