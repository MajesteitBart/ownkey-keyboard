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

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RewriteClientTest : FunSpec({
    test("builds a chat completion request for voice cleanup") {
        val client = MistralChatRewriteClient(
            apiKeyProvider = { "test-key" },
        )
        val body = Json.parseToJsonElement(
            client.buildRequestBody(
                model = "ministral-3b-latest",
                request = RewriteRequest(
                    text = "I think I think we should meet tomorrow",
                    languageTag = "nl-NL",
                ),
            ),
        ).jsonObject

        body["model"]?.jsonPrimitive?.content shouldBe "ministral-3b-latest"
        val messages = body["messages"]?.jsonArray.shouldNotBeNull()
        messages shouldHaveSize 2
        messages[0].jsonObject["role"]?.jsonPrimitive?.content shouldBe "system"
        messages[0].jsonObject["content"]?.jsonPrimitive?.content.shouldNotBeNull() shouldContain "Dutch (nl-NL)"
        messages[1].jsonObject["role"]?.jsonPrimitive?.content shouldBe "user"
        messages[1].jsonObject["content"]?.jsonPrimitive?.content shouldBe
            "I think I think we should meet tomorrow"
    }

    test("uses same-language instruction when no active subtype language is available") {
        DictationRewritePrompt.systemPrompt(languageTag = null) shouldContain
            "Keep the output in the same language as the input."
    }
})
