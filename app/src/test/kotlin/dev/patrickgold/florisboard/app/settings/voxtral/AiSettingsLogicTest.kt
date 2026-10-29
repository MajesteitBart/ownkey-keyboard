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

import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyMode
import dev.patrickgold.florisboard.ime.text.dictation.offline.DownloadWaitReason
import dev.patrickgold.florisboard.ime.text.dictation.offline.LocalModelState
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPreset
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.ownkey.offline.DownloadProgress
import org.ownkey.offline.LocalAsrFailure
import org.ownkey.offline.RuntimeState

class AiSettingsLogicTest : FunSpec({
    val openRouter = LlmRewriteProviders.byId(LlmRewriteProviders.OpenRouter)
    val custom = LlmRewriteProviders.byId(LlmRewriteProviders.Custom)

    context("rewrite model value") {
        test("a blank model or the provider default shows Default") {
            AiSettings.rewriteModelValue(openRouter, "") shouldBe RewriteModelValue.Default
            AiSettings.rewriteModelValue(openRouter, "  ") shouldBe RewriteModelValue.Default
            AiSettings.rewriteModelValue(openRouter, " ${openRouter.defaultModel} ") shouldBe RewriteModelValue.Default
        }

        test("any other model shows its id") {
            AiSettings.rewriteModelValue(openRouter, "openai/gpt-5.5") shouldBe RewriteModelValue.Named("openai/gpt-5.5")
        }

        test("a custom server has no default, so a blank model is not set") {
            AiSettings.rewriteModelValue(custom, "") shouldBe RewriteModelValue.NotSet
            AiSettings.rewriteModelValue(custom, "llama") shouldBe RewriteModelValue.Named("llama")
        }

        test("the dialog starts empty for the default and stores the default for an empty field") {
            AiSettings.rewriteModelDraft(openRouter, openRouter.defaultModel) shouldBe ""
            AiSettings.rewriteModelDraft(openRouter, "openai/gpt-5.5") shouldBe "openai/gpt-5.5"
            AiSettings.rewriteModelToStore(openRouter, " ") shouldBe openRouter.defaultModel
            AiSettings.rewriteModelToStore(openRouter, " other ") shouldBe "other"
            AiSettings.rewriteModelToStore(custom, " ") shouldBe ""
        }
    }

    test("the cloud provider is a known name, else the server's host, with Mistral for a blank address") {
        cloudProviderName("") shouldBe "Mistral"
        cloudProviderName("https://api.openai.com/v1/audio/transcriptions") shouldBe "OpenAI"
        cloudProviderName("http://asr.example.com:8000/v1") shouldBe "asr.example.com"
        cloudProviderName("https://") shouldBe null
        isUsableCloudEndpoint(" ") shouldBe true
        isUsableCloudEndpoint("asr.example.com/v1") shouldBe false
    }

    test("the language value follows the stored hint") {
        AiSettings.languageValue("") shouldBe DictationLanguageValue.KeyboardLanguage
        AiSettings.languageValue("auto") shouldBe DictationLanguageValue.Automatic
        AiSettings.languageValue("AUTO") shouldBe DictationLanguageValue.Automatic
        AiSettings.languageValue(" nl ") shouldBe DictationLanguageValue.Specific("nl")
    }

    context("cloud dictation footer") {
        test("names the host the audio goes to, with the default server for a blank address") {
            AiSettings.cloudFooter("", CloudVocabularyMode.OFF, 3) shouldBe CloudDictationFooter("api.mistral.ai", false)
            AiSettings.cloudFooter("https://asr.example.com/v1/audio", CloudVocabularyMode.OFF, 0) shouldBe
                CloudDictationFooter("asr.example.com", false)
        }

        test("mentions dictionary words only when hints actually carry them") {
            AiSettings.cloudFooter("", CloudVocabularyMode.AUTO, 2).sendsDictionaryWords shouldBe true
            AiSettings.cloudFooter("", CloudVocabularyMode.AUTO, 0).sendsDictionaryWords shouldBe false
            AiSettings.cloudFooter("", CloudVocabularyMode.OFF, 2).sendsDictionaryWords shouldBe false
            // Automatic sends nothing to a server it doesn't know; Send as prompt always does.
            AiSettings.cloudFooter("https://asr.example.com/v1", CloudVocabularyMode.AUTO, 2).sendsDictionaryWords shouldBe false
            AiSettings.cloudFooter("https://asr.example.com/v1", CloudVocabularyMode.PROMPT, 2).sendsDictionaryWords shouldBe true
        }

        test("has no host for an address without one") {
            AiSettings.cloudFooter("not a url", CloudVocabularyMode.OFF, 0).host shouldBe null
        }
    }

    test("rewrite text goes to the request's host, or the provider's name") {
        AiSettings.rewriteHost(LlmRewriteProviders.resolve(LlmRewriteProviders.OpenRouter, "", "")) shouldBe "openrouter.ai"
        AiSettings.rewriteHost(LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "https://llm.example.com/v1/chat", "m")) shouldBe
            "llm.example.com"
        AiSettings.rewriteHost(LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "", "m")) shouldBe null
    }

    context("server address fields") {
        test("defaults show as hints and an empty field stores the default") {
            val default = VoxtralRelayTranscriptionClient.DefaultEndpointUrl
            AiSettings.draftOf(default, default) shouldBe ""
            AiSettings.draftOf(" https://asr.example.com ", default) shouldBe "https://asr.example.com"
            AiSettings.storedValueOf("  ", default) shouldBe default
            AiSettings.storedValueOf(" https://asr.example.com ", default) shouldBe "https://asr.example.com"
        }

        test("only http and https addresses can be saved") {
            AiSettings.isAcceptableServerAddress("", emptyAllowed = true) shouldBe true
            AiSettings.isAcceptableServerAddress("", emptyAllowed = false) shouldBe false
            AiSettings.isAcceptableServerAddress("https://asr.example.com", emptyAllowed = false) shouldBe true
            AiSettings.isAcceptableServerAddress("http://localhost:8080/v1", emptyAllowed = false) shouldBe true
            AiSettings.isAcceptableServerAddress("asr.example.com/v1", emptyAllowed = true) shouldBe false
        }

        test("an address that is still being typed blocks Save without an error") {
            AiSettings.showsServerAddressError("h", emptyAllowed = true) shouldBe false
            AiSettings.showsServerAddressError("https:/", emptyAllowed = true) shouldBe false
            AiSettings.showsServerAddressError("HTTP", emptyAllowed = true) shouldBe false
            AiSettings.showsServerAddressError("asr.example.com", emptyAllowed = true) shouldBe true
            AiSettings.showsServerAddressError("ftp://x", emptyAllowed = true) shouldBe true
            AiSettings.showsServerAddressError("https://x", emptyAllowed = true) shouldBe false
        }
    }

    context("API key dialog") {
        test("a failed replacement keeps the dialog open even though the old key is still stored") {
            AiSettings.apiKeySaved(storeSucceeded = false, hasKeyAfter = true) shouldBe
                ApiKeyChange(hasKey = true, succeeded = false)
        }

        test("a save only succeeds when the store call worked and a key is stored") {
            AiSettings.apiKeySaved(storeSucceeded = true, hasKeyAfter = true) shouldBe
                ApiKeyChange(hasKey = true, succeeded = true)
            AiSettings.apiKeySaved(storeSucceeded = true, hasKeyAfter = false) shouldBe
                ApiKeyChange(hasKey = false, succeeded = false)
            AiSettings.apiKeySaved(storeSucceeded = false, hasKeyAfter = false) shouldBe
                ApiKeyChange(hasKey = false, succeeded = false)
        }

        test("a removal succeeds only when no key is left") {
            AiSettings.apiKeyRemoved(hasKeyAfter = false) shouldBe ApiKeyChange(hasKey = false, succeeded = true)
            AiSettings.apiKeyRemoved(hasKeyAfter = true) shouldBe ApiKeyChange(hasKey = true, succeeded = false)
        }
    }

    test("download percent") {
        AiSettings.downloadPercent(0, 0) shouldBe null
        AiSettings.downloadPercent(336, 672) shouldBe 50
        AiSettings.downloadPercent(800, 672) shouldBe 100
    }

    context("on-device model") {
        val catalog = "model-b"
        fun value(
            state: LocalModelState,
            compatible: Boolean = true,
            active: Boolean = false,
            runtime: RuntimeState = RuntimeState.UNLOADED,
            waitReason: DownloadWaitReason = DownloadWaitReason.STARTING,
        ) = AiSettings.orukeetModelValue(compatible, state, waitReason, active, runtime, catalog)

        fun action(state: LocalModelState, active: Boolean = false, error: LocalAsrFailure? = null, compatible: Boolean = true) =
            AiSettings.orukeetNextAction(compatible, state, active, error, catalog)

        val idle = LocalModelState(phase = ModelPhase.IDLE)
        val installed = idle.copy(installed = setOf(catalog), hasStoredData = true)
        val inUse = installed.copy(currentId = catalog)
        val oldInUse = idle.copy(installed = setOf("model-a"), currentId = "model-a", hasStoredData = true)

        test("status values follow the model's phase") {
            value(LocalModelState()) shouldBe OrukeetModelValue.CHECKING
            value(idle, compatible = false) shouldBe OrukeetModelValue.UNSUPPORTED
            value(idle) shouldBe OrukeetModelValue.NOT_DOWNLOADED
            value(idle.copy(phase = ModelPhase.ACTIVATING)) shouldBe OrukeetModelValue.LOADING
            value(idle.copy(phase = ModelPhase.REMOVING)) shouldBe OrukeetModelValue.DELETING
            value(installed) shouldBe OrukeetModelValue.DOWNLOADED
            value(inUse, active = true) shouldBe OrukeetModelValue.READY
            value(inUse, active = true, runtime = RuntimeState.LOADING) shouldBe OrukeetModelValue.LOADING
            value(inUse, active = true, runtime = RuntimeState.TRANSCRIBING) shouldBe OrukeetModelValue.IN_USE
            value(oldInUse, active = true) shouldBe OrukeetModelValue.UPDATE_AVAILABLE
        }

        test("transfers report downloading, verifying or why they wait") {
            val downloading = idle.copy(transferPhase = ModelPhase.DOWNLOADING, progress = DownloadProgress(1, 2))
            value(downloading) shouldBe OrukeetModelValue.DOWNLOADING
            value(idle.copy(transferPhase = ModelPhase.VERIFYING)) shouldBe OrukeetModelValue.VERIFYING
            val waiting = idle.copy(transferPhase = ModelPhase.WAITING_FOR_NETWORK)
            value(waiting, waitReason = DownloadWaitReason.WIFI_REQUIRED) shouldBe OrukeetModelValue.WAITING_FOR_WIFI
            value(waiting, waitReason = DownloadWaitReason.NO_INTERNET) shouldBe OrukeetModelValue.WAITING_FOR_INTERNET
            value(waiting, waitReason = DownloadWaitReason.STARTING) shouldBe OrukeetModelValue.WAITING_TO_START
        }

        test("one primary action at a time") {
            action(idle) shouldBe OrukeetNextAction.DOWNLOAD
            action(idle, error = LocalAsrFailure.DOWNLOAD) shouldBe OrukeetNextAction.RETRY_DOWNLOAD
            action(oldInUse, active = true) shouldBe OrukeetNextAction.DOWNLOAD_UPDATE
            action(installed) shouldBe OrukeetNextAction.USE
            // Chosen as the route but never activated: the downloaded model only has to be put to use.
            action(installed, active = true) shouldBe OrukeetNextAction.USE
            action(installed.copy(currentId = "model-a"), active = true) shouldBe OrukeetNextAction.APPLY_UPDATE
            action(inUse, active = true) shouldBe null
        }

        test("no primary action while the model is busy or on a phone that can't run Orukeet") {
            action(idle.copy(transferPhase = ModelPhase.DOWNLOADING)) shouldBe null
            action(idle, compatible = false) shouldBe null
            // The start-up check hasn't read the store yet, so Download would be offered for a model that exists.
            action(LocalModelState()) shouldBe null
            action(installed.copy(phase = ModelPhase.ACTIVATING), active = true) shouldBe null
            action(installed.copy(phase = ModelPhase.REMOVING)) shouldBe null
        }
    }

    context("rewrite styles") {
        val a = RewritePromptPreset("a", "Fix grammar", "Fix it.")
        val b = RewritePromptPreset("b", "Shorter", "Make it short.")

        test("the summary is the first non-empty line") {
            RewriteStyles.summary("Fix spelling. Keep tone.\n\nMore") shouldBe "Fix spelling. Keep tone."
            RewriteStyles.summary("\n  Rewrite in Dutch  \nignored") shouldBe "Rewrite in Dutch"
            RewriteStyles.summary("") shouldBe ""
            val plainspoken = RewritePromptPresets.defaults.first { it.id == "plainspoken" }
            RewriteStyles.summary(plainspoken.instruction) shouldStartWith "Rewrite in plain, direct, human language. Preserve"
        }

        test("the last style can't be deleted") {
            RewriteStyles.canDelete(listOf(a)) shouldBe false
            RewriteStyles.delete(listOf(a), "a") shouldContainExactly listOf(a)
            RewriteStyles.canDelete(listOf(a, b)) shouldBe true
            RewriteStyles.delete(listOf(a, b), "a") shouldContainExactly listOf(b)
        }

        test("saving replaces a style in place or adds a new one at the end, trimmed") {
            RewriteStyles.save(listOf(a, b), a.copy(name = " Grammar ")) shouldContainExactly listOf(a.copy(name = "Grammar"), b)
            val added = RewritePromptPreset("c", "Style 1", " Be kind. ")
            RewriteStyles.save(listOf(a, b), added) shouldContainExactly listOf(a, b, added.copy(instruction = "Be kind."))
        }

        test("a style needs a name and an instruction") {
            RewriteStyles.canSave("Name", "Do it.") shouldBe true
            RewriteStyles.canSave(" ", "Do it.") shouldBe false
            RewriteStyles.canSave("Name", "  ") shouldBe false
        }

        test("new styles are numbered after the highest Style N, with a unique id and no instruction") {
            RewriteStyles.newStyle(RewritePromptPresets.defaults).name shouldBe "Style 1"
            val numbered = listOf(a, RewritePromptPreset("x", "Style 2", "i"), RewritePromptPreset("y", "Style 7", "i"))
            val created = RewriteStyles.newStyle(numbered)
            created.name shouldBe "Style 8"
            created.instruction shouldBe ""
            created.id shouldStartWith "custom_"
            RewritePromptPresets.newCustom(4).name shouldBe "Style 5"
        }
    }
})
