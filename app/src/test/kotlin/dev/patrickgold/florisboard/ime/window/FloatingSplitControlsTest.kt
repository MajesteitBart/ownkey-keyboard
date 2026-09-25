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

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FloatingSplitControlsTest : FunSpec({
    test("actions keep 48dp when the panel has room") {
        splitActionSize(400.dp, count = 4) shouldBe 48.dp
        splitActionSize(180.dp, count = 3) shouldBe 48.dp
    }

    test("four actions shrink to fit a 180dp panel at the minimum keyboard width") {
        // A 600dp screen at the 80% minimum gives a 480dp keyboard; the default 25% gap leaves 180dp panels.
        splitActionSize(180.dp, count = 4) shouldBe 45.dp
    }
})
