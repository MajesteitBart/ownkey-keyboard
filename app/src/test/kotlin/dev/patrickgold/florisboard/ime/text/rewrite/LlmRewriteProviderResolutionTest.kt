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

package dev.patrickgold.florisboard.ime.text.rewrite

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LlmRewriteProviderResolutionTest : FunSpec({
    test("an older install with only a Mistral endpoint saved resolves to Mistral, not the default provider") {
        val resolved = LlmRewriteProviders.resolve(
            providerId = LlmRewriteProviders.Default,
            endpointUrl = "https://api.mistral.ai/v1/chat/completions",
            model = "mistral-small-latest",
        )
        resolved.preset.id shouldBe LlmRewriteProviders.Mistral
        resolved.preset.providerName shouldBe "Mistral"
    }

    test("blank fields of a built-in provider show the defaults a request would use") {
        val resolved = LlmRewriteProviders.resolve(providerId = LlmRewriteProviders.Anthropic, endpointUrl = "", model = " ")
        resolved.endpointUrl shouldBe "https://api.anthropic.com/v1/messages"
        resolved.model shouldBe "claude-sonnet-4-5-20250929"
        resolved.isComplete shouldBe true
    }

    test("a custom endpoint gets no defaults and stays incomplete until both fields are set") {
        LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "https://custom.invalid/rewrite", "").isComplete shouldBe false
        LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "", "custom-model").isComplete shouldBe false
        val complete = LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, " https://custom.invalid/rewrite ", "custom-model")
        complete.isComplete shouldBe true
        complete.endpointUrl shouldBe "https://custom.invalid/rewrite"
    }

    test("an endpoint without http or https is complete but can't be used") {
        val resolved = LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "api.example.com/v1/chat", "custom-model")
        resolved.isComplete shouldBe true
        resolved.hasHttpEndpoint shouldBe false
        LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "https://api.example.com/v1/chat", "m").hasHttpEndpoint shouldBe true
        LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "http://localhost:8080/v1/chat", "m").hasHttpEndpoint shouldBe true
        LlmRewriteProviders.resolve(LlmRewriteProviders.Custom, "HTTPS://api.example.com/v1/chat", "m").hasHttpEndpoint shouldBe true
    }

    test("nothing saved resolves to the default provider and model") {
        val resolved = LlmRewriteProviders.resolve(providerId = "", endpointUrl = "", model = "")
        resolved.preset.id shouldBe LlmRewriteProviders.Default
        resolved.model shouldBe LlmRewriteProviders.DefaultModel
    }
})
