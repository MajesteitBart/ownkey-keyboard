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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import androidx.compose.ui.geometry.Rect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class VoiceActionHintStateTest : FunSpec({
    test("a press shows the hint without a linger timer until the gesture resolves") {
        val state = VoiceActionHintState()

        state.request.shouldBeNull()
        state.show()
        val request = state.request.shouldNotBeNull()
        request.lingerMillis.shouldBeNull()
    }

    test("a tap keeps the hint for the linger window and then lets it expire") {
        val state = VoiceActionHintState()
        state.show()
        val shown = state.request.shouldNotBeNull()

        state.linger()
        val lingering = state.request.shouldNotBeNull()
        lingering.id shouldBe shown.id
        lingering.lingerMillis shouldBe VoiceActionHintLingerMillis
        // Repeating the release cannot restart the timer.
        state.linger()
        state.request shouldBe lingering

        state.expire(shown.id)
        state.request.shouldBeNull()
    }

    test("a recognized hold or a cancellation hides the hint at once") {
        val state = VoiceActionHintState()

        state.show()
        state.hide()
        state.request.shouldBeNull()
        // Lingering after a hide is a no-op: there is nothing left to keep on screen.
        state.linger()
        state.request.shouldBeNull()
    }

    test("a newer press keeps its own hint when an older linger timer elapses") {
        val state = VoiceActionHintState()
        state.show()
        val first = state.request.shouldNotBeNull()
        state.linger()

        state.show()
        val second = state.request.shouldNotBeNull()
        second.id shouldNotBe first.id
        second.lingerMillis.shouldBeNull()

        state.expire(first.id)
        state.request shouldBe second
    }

    test("disposing an interrupted press clears its hint") {
        val state = VoiceActionHintState()
        state.show()
        state.cancelPress(state.request.shouldNotBeNull().id)
        state.request.shouldBeNull()
    }

    test("disposing a released tap preserves the hint until its timer expires") {
        val state = VoiceActionHintState()
        state.show()
        val id = state.request.shouldNotBeNull().id
        state.linger()
        state.cancelPress(id)
        state.request.shouldNotBeNull().lingerMillis shouldBe VoiceActionHintLingerMillis
        state.expire(id)
        state.request.shouldBeNull()
    }

    test("cancelling an older press does not hide a newer press") {
        val state = VoiceActionHintState()
        state.show()
        val oldId = state.request.shouldNotBeNull().id
        state.show()
        val current = state.request.shouldNotBeNull()
        state.cancelPress(oldId)
        state.cancelPress(null)
        state.request shouldBe current
    }

    test("the anchor only changes when the key actually moved") {
        val state = VoiceActionHintState()
        val bounds = Rect(left = 10f, top = 20f, right = 60f, bottom = 70f)

        state.anchorBounds.shouldBeNull()
        state.updateAnchor(bounds)
        state.anchorBounds shouldBe bounds
        state.updateAnchor(Rect(left = 10f, top = 20f, right = 60f, bottom = 70f))
        state.anchorBounds shouldBe bounds
        state.updateAnchor(bounds.translate(5f, 0f))
        state.anchorBounds shouldBe bounds.translate(5f, 0f)
    }
})
