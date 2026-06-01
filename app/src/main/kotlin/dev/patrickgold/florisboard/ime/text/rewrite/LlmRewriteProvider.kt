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

data class LlmRewriteProviderPreset(
    val id: String,
    val label: String,
    val summary: String,
    val endpointUrl: String,
    val defaultModel: String,
    val isCustom: Boolean = false,
)

object LlmRewriteProviders {
    const val OpenAiResponses = "openai_responses"
    const val OpenAiChatCompletions = "openai_chat_completions"
    const val Anthropic = "anthropic_messages"
    const val Mistral = "mistral_chat_completions"
    const val OpenRouter = "openrouter_chat_completions"
    const val Custom = "custom"

    val presets = listOf(
        LlmRewriteProviderPreset(
            id = OpenAiResponses,
            label = "OpenAI Responses",
            summary = "Best fit for GPT-5.5 and newer OpenAI models.",
            endpointUrl = "https://api.openai.com/v1/responses",
            defaultModel = "gpt-5.5",
        ),
        LlmRewriteProviderPreset(
            id = OpenAiChatCompletions,
            label = "OpenAI Chat Completions",
            summary = "Chat-compatible OpenAI endpoint for lower-cost rewrite models.",
            endpointUrl = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-5.4-mini",
        ),
        LlmRewriteProviderPreset(
            id = Anthropic,
            label = "Anthropic",
            summary = "Claude Messages API with native Anthropic headers.",
            endpointUrl = "https://api.anthropic.com/v1/messages",
            defaultModel = "claude-sonnet-4-5-20250929",
        ),
        LlmRewriteProviderPreset(
            id = Mistral,
            label = "Mistral",
            summary = "Mistral chat completions endpoint.",
            endpointUrl = "https://api.mistral.ai/v1/chat/completions",
            defaultModel = "mistral-small-latest",
        ),
        LlmRewriteProviderPreset(
            id = OpenRouter,
            label = "OpenRouter",
            summary = "OpenAI-compatible routing through OpenRouter.",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "openai/gpt-5.4-mini",
        ),
        LlmRewriteProviderPreset(
            id = Custom,
            label = "Other / Custom",
            summary = "Use your own OpenAI-compatible endpoint.",
            endpointUrl = "",
            defaultModel = "",
            isCustom = true,
        ),
    )

    fun byId(id: String): LlmRewriteProviderPreset {
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }

    fun inferFromEndpoint(endpointUrl: String): String {
        val normalized = endpointUrl.lowercase()
        return when {
            normalized.contains("api.openai.com") && normalized.contains("/responses") -> OpenAiResponses
            normalized.contains("api.openai.com") && normalized.contains("/chat/completions") -> OpenAiChatCompletions
            normalized.contains("api.anthropic.com") -> Anthropic
            normalized.contains("api.mistral.ai") -> Mistral
            normalized.contains("openrouter.ai") -> OpenRouter
            else -> Custom
        }
    }
}
