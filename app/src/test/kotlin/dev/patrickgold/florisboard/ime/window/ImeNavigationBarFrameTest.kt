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

class ImeNavigationBarFrameTest : FunSpec({
    // A 2400 px tall keyboard window at 420 dpi: the platform frame is 48dp, 126 px, and the cap 96dp, 252 px.
    val windowBottom = 2400
    val maxOverlap = 252

    test("a frame at the bottom of the window covers its own height") {
        navigationBarFrameOverlap(frameTop = 2274, frameHeight = 126, viewBottom = windowBottom, maxOverlap) shouldBe 126
    }

    test("an empty frame or one below the window covers nothing") {
        navigationBarFrameOverlap(frameTop = 2400, frameHeight = 0, viewBottom = windowBottom, maxOverlap) shouldBe 0
        navigationBarFrameOverlap(frameTop = 2400, frameHeight = 126, viewBottom = windowBottom, maxOverlap) shouldBe 0
        navigationBarFrameOverlap(frameTop = 2500, frameHeight = 126, viewBottom = windowBottom, maxOverlap) shouldBe 0
    }

    test("a frame taller than the cap is ignored, so the keys never leave the screen") {
        navigationBarFrameOverlap(frameTop = 0, frameHeight = 2400, viewBottom = windowBottom, maxOverlap) shouldBe 0
        navigationBarFrameOverlap(frameTop = 2147, frameHeight = 253, viewBottom = windowBottom, maxOverlap) shouldBe 0
        navigationBarFrameOverlap(frameTop = 2148, frameHeight = 252, viewBottom = windowBottom, maxOverlap) shouldBe 252
    }
})
