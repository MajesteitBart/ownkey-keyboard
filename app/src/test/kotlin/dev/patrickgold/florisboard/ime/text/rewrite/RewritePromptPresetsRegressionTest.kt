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

/**
 * The pinned voice card is additive: adding it must not rename, reorder, drop, or otherwise change
 * saved rewrite voices, which the hub still renders below it in a two-column grid.
 */
class RewritePromptPresetsRegressionTest : FunSpec({
    test("saved voices keep their identity, name, instruction and order through a round trip") {
        val saved = listOf(
            RewritePromptPreset("custom_1", "My voice", "Rewrite in my voice."),
            RewritePromptPreset("custom_2", "Second voice", "Rewrite tersely."),
            RewritePromptPreset("improve", "Improve writing", "Improve the text."),
        )

        val decoded = RewritePromptPresets.decode(RewritePromptPresets.encode(saved))

        decoded shouldContainExactly saved
        decoded.map { it.name } shouldContainExactly listOf("My voice", "Second voice", "Improve writing")
    }

    test("the shipped default voices match the approved settings without duplicate Plainspoken voices") {
        RewritePromptPresets.decode(RewritePromptPresets.defaultJson).map { it.id } shouldContainExactly
            listOf("improve", "grammar", "shorter", "rewrite_dutch", "plainspoken")
    }

    listOf(
        listOf("clean", "formal", "business"),
        listOf("clean", "business", "rewrite_dutch", "rewrite_english"),
    ).forEachIndexed { index, ids ->
        test("edited legacy voice list $index keeps its names instructions and order") {
            val saved = ids.mapIndexed { position, id ->
                RewritePromptPreset(id, "Edited voice $position", "First line.\nSecond line $position.")
            }

            RewritePromptPresets.decode(RewritePromptPresets.encode(saved)) shouldContainExactly saved
        }
    }

    test("an odd number of voices still fills whole grid rows without dropping the last voice") {
        val odd = RewritePromptPresets.defaults.take(5)

        val rows = odd.chunked(2)

        rows.size shouldBe 3
        rows.flatten() shouldContainExactly odd
        rows.last().size shouldBe 1
    }
})
