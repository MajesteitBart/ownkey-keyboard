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
            id = "clean",
            name = "Clean up",
            instruction = "Rewrite the text so it is clear, concise, and grammatically correct. Preserve the original meaning.",
        ),
        RewritePromptPreset(
            id = "formal",
            name = "Formal",
            instruction = "Rewrite the text in a more formal tone. Keep it natural and avoid sounding stiff.",
        ),
        RewritePromptPreset(
            id = "business",
            name = "Business",
            instruction = "Rewrite the text in a professional business tone suitable for a client, colleague, or stakeholder.",
        ),
    )

    val defaultJson: String = encode(defaults)

    fun decode(value: String): List<RewritePromptPreset> {
        val decoded = runCatching {
            json.decodeFromString(ListSerializer(RewritePromptPreset.serializer()), value)
        }.getOrDefault(defaults)
        return decoded.filter { it.name.isNotBlank() && it.instruction.isNotBlank() }.ifEmpty { defaults }
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
}
