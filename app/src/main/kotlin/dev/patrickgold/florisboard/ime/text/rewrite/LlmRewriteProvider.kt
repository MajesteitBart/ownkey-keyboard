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

import androidx.annotation.StringRes
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.util.NetworkUtils

data class LlmRewriteProviderPreset(
    val id: String,
    /** Name in the settings provider choice. */
    @param:StringRes val labelRes: Int,
    /** One-line description in the settings provider choice. */
    @param:StringRes val summaryRes: Int,
    val endpointUrl: String,
    val defaultModel: String,
    /**
     * Provider identity without the API variant, for the keyboard's compact summaries and the
     * data-sharing disclosure. Both OpenAI presets send data to OpenAI; the protocol difference
     * stays a configuration detail in settings.
     */
    val providerName: String,
    val isCustom: Boolean = false,
)

/** The provider, endpoint and model a rewrite request actually uses, after legacy inference and defaults. */
data class ResolvedRewriteProvider(
    val preset: LlmRewriteProviderPreset,
    val endpointUrl: String,
    val model: String,
) {
    /** A custom endpoint has no defaults, so it can only send requests once both fields are filled in. */
    val isComplete: Boolean get() = endpointUrl.isNotBlank() && model.isNotBlank()

    /** Requests only go to http(s) URLs, so an endpoint typed without a scheme can't be used. */
    val hasHttpEndpoint: Boolean get() = NetworkUtils.hasHttpScheme(endpointUrl)
}

object LlmRewriteProviders {
    const val OpenAiResponses = "openai_responses"
    const val OpenAiChatCompletions = "openai_chat_completions"
    const val Anthropic = "anthropic_messages"
    const val Mistral = "mistral_chat_completions"
    const val OpenRouter = "openrouter_chat_completions"
    const val Custom = "custom"
    const val Default = OpenRouter
    const val DefaultEndpointUrl = "https://openrouter.ai/api/v1/chat/completions"
    const val DefaultModel = "meta/muse-spark-1.1"

    val presets = listOf(
        LlmRewriteProviderPreset(
            id = OpenAiResponses,
            labelRes = R.string.settings__ai__rewrite_provider__openai,
            summaryRes = R.string.settings__ai__rewrite_provider__openai_summary,
            endpointUrl = "https://api.openai.com/v1/responses",
            defaultModel = "gpt-5.5",
            providerName = "OpenAI",
        ),
        LlmRewriteProviderPreset(
            id = OpenAiChatCompletions,
            labelRes = R.string.settings__ai__rewrite_provider__openai_chat,
            summaryRes = R.string.settings__ai__rewrite_provider__openai_chat_summary,
            endpointUrl = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-5.4-mini",
            providerName = "OpenAI",
        ),
        LlmRewriteProviderPreset(
            id = Anthropic,
            labelRes = R.string.settings__ai__rewrite_provider__anthropic,
            summaryRes = R.string.settings__ai__rewrite_provider__anthropic_summary,
            endpointUrl = "https://api.anthropic.com/v1/messages",
            defaultModel = "claude-sonnet-4-5-20250929",
            providerName = "Anthropic",
        ),
        LlmRewriteProviderPreset(
            id = Mistral,
            labelRes = R.string.settings__ai__rewrite_provider__mistral,
            summaryRes = R.string.settings__ai__rewrite_provider__mistral_summary,
            endpointUrl = "https://api.mistral.ai/v1/chat/completions",
            defaultModel = "mistral-small-latest",
            providerName = "Mistral",
        ),
        LlmRewriteProviderPreset(
            id = OpenRouter,
            labelRes = R.string.settings__ai__rewrite_provider__openrouter,
            summaryRes = R.string.settings__ai__rewrite_provider__openrouter_summary,
            endpointUrl = DefaultEndpointUrl,
            defaultModel = DefaultModel,
            providerName = "OpenRouter",
        ),
        LlmRewriteProviderPreset(
            id = Custom,
            labelRes = R.string.settings__ai__rewrite_provider__other,
            summaryRes = R.string.settings__ai__rewrite_provider__other_summary,
            endpointUrl = "",
            defaultModel = "",
            isCustom = true,
            providerName = "Custom endpoint",
        ),
    )

    fun byId(id: String): LlmRewriteProviderPreset {
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }

    /**
     * Resolves the stored rewrite settings the way a request uses them, so settings can show what is actually
     * used. Blank fields of a built-in provider fall back to its defaults; a custom endpoint gets none.
     */
    fun resolve(providerId: String, endpointUrl: String, model: String): ResolvedRewriteProvider {
        val rawEndpointUrl = endpointUrl.trim()
        val rawProviderId = providerId.trim()
        val resolvedId = when {
            rawProviderId.isBlank() && rawEndpointUrl.isBlank() -> Default
            rawProviderId.isBlank() -> inferFromEndpoint(rawEndpointUrl)
            // Older installs may have saved only the endpoint, with no explicit provider.
            rawProviderId in setOf(OpenAiResponses, Default) &&
                rawEndpointUrl.isNotBlank() &&
                rawEndpointUrl != byId(rawProviderId).endpointUrl -> {
                inferFromEndpoint(rawEndpointUrl)
            }

            else -> rawProviderId
        }
        val preset = byId(resolvedId)
        val rawModel = model.trim()
        if (preset.isCustom) return ResolvedRewriteProvider(preset, rawEndpointUrl, rawModel)
        return ResolvedRewriteProvider(
            preset = preset,
            endpointUrl = rawEndpointUrl.ifBlank { preset.endpointUrl.ifBlank { DefaultEndpointUrl } },
            model = rawModel.ifBlank { preset.defaultModel.ifBlank { DefaultModel } },
        )
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
