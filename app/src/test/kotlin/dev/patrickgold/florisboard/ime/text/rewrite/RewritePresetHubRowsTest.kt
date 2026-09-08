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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class RewritePresetHubRowsTest : FunSpec({
    test("the six defaults split into one quality/length row and one tone/language row") {
        val rows = RewritePromptPresets.hubRows(RewritePromptPresets.defaults)

        rows.map { it.group } shouldContainExactly listOf(RewritePresetGroup.QUALITY, RewritePresetGroup.TONE)
        rows[0].prompts.map { it.id } shouldContainExactly listOf("improve", "grammar", "shorter")
        rows[1].prompts.map { it.id } shouldContainExactly listOf("business", "casual", "rewrite_dutch")
    }

    test("every default preset is placed exactly once and nothing is dropped") {
        val placed = RewritePromptPresets.hubRows(RewritePromptPresets.defaults).flatMap { it.prompts }

        placed.map { it.id }.sorted() shouldContainExactly RewritePromptPresets.defaults.map { it.id }.sorted()
    }

    test("user-defined presets keep their own rows and wrap instead of squeezing into one line") {
        val custom = (1..4).map { RewritePromptPresets.newCustom(it).copy(id = "custom_$it") }
        val rows = RewritePromptPresets.hubRows(RewritePromptPresets.defaults + custom)

        rows.map { it.group } shouldContainExactly listOf(
            RewritePresetGroup.QUALITY,
            RewritePresetGroup.TONE,
            RewritePresetGroup.CUSTOM,
            RewritePresetGroup.CUSTOM,
        )
        rows[2].prompts.size shouldBe RewritePromptPresets.HubColumns
        rows[3].prompts.size shouldBe 1
        rows.all { it.prompts.size <= RewritePromptPresets.HubColumns } shouldBe true
    }

    test("translation presets count as tone and language however they are named") {
        val translate = RewritePromptPreset("translate_fr", "Translate to French", "Translate the text to French.")
        val rows = RewritePromptPresets.hubRows(listOf(translate))

        rows.single().group shouldBe RewritePresetGroup.TONE
    }
})
