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

import dev.patrickgold.florisboard.lib.io.DefaultJsonConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class RewritePromptPreset(
    val id: String,
    val name: String,
    val instruction: String,
)

object RewritePromptPresets {
    private val json = Json(DefaultJsonConfig) {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val defaults = listOf(
        RewritePromptPreset(
            id = "improve",
            name = "Improve writing",
            instruction = "Rewrite the text to improve clarity, flow, and word choice. Preserve the original meaning.",
        ),
        RewritePromptPreset(
            id = "grammar",
            name = "Fix grammar",
            instruction = "Fix spelling, grammar, and punctuation mistakes without changing the meaning or tone.",
        ),
        RewritePromptPreset(
            id = "shorter",
            name = "Make shorter",
            instruction = "Rewrite the text to be significantly shorter while keeping the essential meaning.",
        ),
        RewritePromptPreset(
            id = "business",
            name = "Business",
            instruction = "Rewrite the text in a professional business tone suitable for a client, colleague, or stakeholder.",
        ),
        RewritePromptPreset(
            id = "casual",
            name = "More casual",
            instruction = "Rewrite the text in a relaxed, friendly, casual tone.",
        ),
        RewritePromptPreset(
            id = "rewrite_dutch",
            name = "Rewrite in Dutch",
            instruction = "Rewrite the text in natural Dutch. Preserve the original meaning and return only Dutch text.",
        ),
    )

    val defaultJson: String = encode(defaults)

    fun decode(value: String): List<RewritePromptPreset> {
        val decoded = runCatching {
            json.decodeFromString(ListSerializer(RewritePromptPreset.serializer()), value)
        }.getOrDefault(defaults)
        val validPrompts = decoded.filter { it.name.isNotBlank() && it.instruction.isNotBlank() }
        return when {
            validPrompts.map { it.id } in legacyDefaultIdSets -> defaults
            else -> validPrompts.ifEmpty { defaults }
        }
    }

    fun encode(value: List<RewritePromptPreset>): String {
        return json.encodeToString(ListSerializer(RewritePromptPreset.serializer()), value)
    }

    fun newCustom(index: Int): RewritePromptPreset {
        return RewritePromptPreset(
            id = "custom_${System.currentTimeMillis()}",
            name = "Custom ${index + 1}",
            instruction = "Rewrite the text in my preferred voice.",
        )
    }

    private val legacyDefaultIdSets = listOf(
        listOf("clean", "formal", "business"),
        listOf("clean", "business", "rewrite_dutch", "rewrite_english"),
    )
}
