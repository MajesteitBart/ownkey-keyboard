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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OwnkeyToastOverlayTest : FunSpec({
    val root = DpRect(0.dp, 0.dp, 400.dp, 900.dp)

    test("a toast sits just above the voice-only bar in its default spot") {
        val bar = DpRect(72.dp, 820.dp, 328.dp, 876.dp)
        toastPlacementAroundBar(bar, root) shouldBe ToastPlacement(belowBar = false, offset = 88.dp)
    }

    test("a toast moves below the bar when the bar was dragged to the top") {
        val bar = DpRect(72.dp, 8.dp, 328.dp, 64.dp)
        toastPlacementAroundBar(bar, root) shouldBe ToastPlacement(belowBar = true, offset = 72.dp)
    }

    test("the switch happens where a toast above would no longer fit") {
        toastPlacementAroundBar(DpRect(0.dp, 95.dp, 256.dp, 151.dp), root).belowBar shouldBe true
        toastPlacementAroundBar(DpRect(0.dp, 96.dp, 256.dp, 152.dp), root).belowBar shouldBe false
    }
})
