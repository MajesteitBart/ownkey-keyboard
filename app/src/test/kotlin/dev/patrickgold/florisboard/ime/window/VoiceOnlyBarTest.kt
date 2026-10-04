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
import androidx.compose.ui.unit.dp
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

    context("the bar fits its window") {
        test("a typical phone shows every button and the full status column") {
            voiceBarLayout(maxBarWidth = 395.dp, languageAvailable = true) shouldBe VoiceBarLayout(true, true, 120.dp)
        }

        test("a small screen narrows the status column so every button still fits") {
            // 12 dp padding, a 44 dp mic, three 48 dp touch areas and four 4 dp gaps leave 88 dp of a 304 dp bar.
            voiceBarLayout(maxBarWidth = 304.dp, languageAvailable = true) shouldBe VoiceBarLayout(true, true, 88.dp)
        }

        test("a narrow window drops the language button before the status gets unreadable") {
            voiceBarLayout(maxBarWidth = 260.dp, languageAvailable = true) shouldBe VoiceBarLayout(true, false, 96.dp)
        }

        test("a very narrow window keeps only the mic and the keyboard button") {
            voiceBarLayout(maxBarWidth = 200.dp, languageAvailable = true) shouldBe VoiceBarLayout(false, false, 88.dp)
        }

        test("the bar never grows past its window") {
            val layout = voiceBarLayout(maxBarWidth = 150.dp, languageAvailable = true)
            layout shouldBe VoiceBarLayout(false, false, 38.dp)
        }
    }

    context("the language button switches language, not layout") {
        val layouts = listOf("en-qwerty", "en-dvorak", "nl-qwerty", "de-qwertz")
        val language = { layout: String -> layout.substringBefore('-') }

        test("it skips other layouts of the same language") {
            nextLanguage(layouts, "en-qwerty", language) shouldBe "nl-qwerty"
            nextLanguage(layouts, "en-dvorak", language) shouldBe "nl-qwerty"
        }

        test("it wraps around to the first language") {
            nextLanguage(layouts, "de-qwertz", language) shouldBe "en-qwerty"
        }

        test("with only one language there is nothing to switch to") {
            nextLanguage(listOf("en-qwerty", "en-dvorak"), "en-qwerty", language) shouldBe null
            nextLanguage(listOf("en-qwerty"), "en-qwerty", language) shouldBe null
        }

        test("an active layout missing from the list offers nothing rather than a guess") {
            nextLanguage(layouts, "fr-azerty", language) shouldBe null
        }
    }
})
