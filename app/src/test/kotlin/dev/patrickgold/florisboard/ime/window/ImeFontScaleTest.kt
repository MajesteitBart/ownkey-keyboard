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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeFontScaleTest : FunSpec({
    test("the keyboard follows the system font scale up to the cap") {
        imeFontScale(0.85f) shouldBe 0.85f
        imeFontScale(1f) shouldBe 1f
        imeFontScale(1.15f) shouldBe 1.15f
        imeFontScale(ImeMaxFontScale) shouldBe ImeMaxFontScale
    }

    test("larger system font scales stop at the cap") {
        imeFontScale(1.5f) shouldBe ImeMaxFontScale
        imeFontScale(2f) shouldBe ImeMaxFontScale
    }
})
