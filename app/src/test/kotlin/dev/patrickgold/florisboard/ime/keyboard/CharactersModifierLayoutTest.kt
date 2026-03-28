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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.lib.io.loadJsonAsset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

class CharactersModifierLayoutTest : FunSpec({
    test("characters modifier layout exposes direct numeric advanced popup on symbols key") {
        val layoutPath = candidateLayoutPaths().firstOrNull(Path::exists)
            ?: error("Unable to find characters modifier layout JSON from module or repo root")
        val layoutJson = layoutPath.readText()
        val layout = loadJsonAsset<LayoutArrangement>(layoutJson).getOrThrow()

        val viewSymbolsKey = layout
            .last()
            .first()
            .let { it as TextKeyData }
        val numericAdvancedPopup = viewSymbolsKey.popup?.main as? TextKeyData

        numericAdvancedPopup.shouldNotBeNull()
        numericAdvancedPopup.code shouldBe KeyCode.VIEW_NUMERIC_ADVANCED
        numericAdvancedPopup.type shouldBe KeyType.SYSTEM_GUI
    }
})

private fun candidateLayoutPaths(): List<Path> {
    return listOf(
        Paths.get(
            "src",
            "main",
            "assets",
            "ime",
            "keyboard",
            "org.florisboard.layouts",
            "layouts",
            "charactersMod",
            "default.json",
        ),
        Paths.get(
            "app",
            "src",
            "main",
            "assets",
            "ime",
            "keyboard",
            "org.florisboard.layouts",
            "layouts",
            "charactersMod",
            "default.json",
        ),
    )
}
