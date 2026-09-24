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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VoiceOnlyBarTest : FunSpec({
    context("voice only is allowed") {
        test("in ordinary text fields") {
            voiceOnlyAllowed(InputAttributes.Type.TEXT, isSecureField = false, isIncognito = false) shouldBe true
            voiceOnlyAllowed(InputAttributes.Type.NULL, isSecureField = false, isIncognito = false) shouldBe true
        }

        test("never in secure or incognito fields") {
            voiceOnlyAllowed(InputAttributes.Type.TEXT, isSecureField = true, isIncognito = false) shouldBe false
            voiceOnlyAllowed(InputAttributes.Type.TEXT, isSecureField = false, isIncognito = true) shouldBe false
        }

        listOf(InputAttributes.Type.NUMBER, InputAttributes.Type.PHONE, InputAttributes.Type.DATETIME).forEach { type ->
            test("never in $type fields, which keep their keypad") {
                voiceOnlyAllowed(type, isSecureField = false, isIncognito = false) shouldBe false
            }
        }
    }

    context("the dragged bar stays on screen") {
        val area = IntSize(1000, 2000)
        val bar = IntSize(200, 56)
        val edge = 8f
        val bottom = 16f

        test("the default position is bottom center") {
            clampVoiceBarOffset(Offset.Zero, area, bar, edge, bottom) shouldBe Offset.Zero
        }

        test("it never moves below its default position") {
            clampVoiceBarOffset(Offset(0f, 500f), area, bar, edge, bottom) shouldBe Offset.Zero
        }

        test("it stops at the side and top edges") {
            // Centered at x = 400, so it may move 392 px either way; its top may reach y = 8.
            clampVoiceBarOffset(Offset(-5000f, -5000f), area, bar, edge, bottom) shouldBe Offset(-392f, -1920f)
            clampVoiceBarOffset(Offset(5000f, 0f), area, bar, edge, bottom) shouldBe Offset(392f, 0f)
        }

        test("a rotation to a smaller screen pulls a saved offset back inside") {
            val landscape = IntSize(600, 400)
            clampVoiceBarOffset(Offset(350f, -1500f), landscape, bar, edge, bottom) shouldBe Offset(192f, -320f)
        }
    }
})
