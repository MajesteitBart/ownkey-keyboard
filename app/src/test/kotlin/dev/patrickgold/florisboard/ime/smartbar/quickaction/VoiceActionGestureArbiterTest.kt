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

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class VoiceActionGestureArbiterTest : FunSpec({
    test("a released tap below the configured timeout dispatches ordinary dictation exactly once") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0) shouldBe true
        arbiter.advanceTo(atMs = 399).shouldBeNull()
        arbiter.up(atMs = 399) shouldBe VoiceActionGestureOutcome.DICTATION
        arbiter.up(atMs = 399).shouldBeNull()
    }

    test("a hold that reaches the timeout dispatches voice rewrite and makes the release inert") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.advanceTo(atMs = 399).shouldBeNull()
        arbiter.advanceTo(atMs = 400) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
        arbiter.isHoldRecognized shouldBe true
        arbiter.advanceTo(atMs = 500).shouldBeNull()
        arbiter.up(atMs = 900).shouldBeNull()
    }

    test("an unresolved hold released after the timeout still dispatches voice rewrite only") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.up(atMs = 700) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
    }

    test("hold recognition follows the configured accessibility touch-and-hold delay") {
        val slow = VoiceActionGestureArbiter(longPressTimeoutMs = 1_500)

        slow.down(atMs = 0)
        slow.advanceTo(atMs = 1_499).shouldBeNull()
        slow.advanceTo(atMs = 1_500) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE

        val fast = VoiceActionGestureArbiter(longPressTimeoutMs = 200)
        fast.down(atMs = 0)
        fast.advanceTo(atMs = 200) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
    }

    test("a slide or cancellation before recognition starts neither mode") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.cancel(atMs = 250).shouldBeNull()
        arbiter.advanceTo(atMs = 500).shouldBeNull()
        arbiter.up(atMs = 600).shouldBeNull()
    }

    test("a cancellation after the timeout resolves as voice rewrite, never as dictation") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.cancel(atMs = 450) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
        arbiter.up(atMs = 460).shouldBeNull()
    }

    test("a second down inside one gesture cannot start a second arbitration") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0) shouldBe true
        arbiter.down(atMs = 10) shouldBe false
        arbiter.up(atMs = 100) shouldBe VoiceActionGestureOutcome.DICTATION
        arbiter.down(atMs = 200) shouldBe true
        arbiter.up(atMs = 250) shouldBe VoiceActionGestureOutcome.DICTATION
    }

    test("disposal clears the press so the next gesture starts clean") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.reset()
        arbiter.isTracking shouldBe false
        arbiter.up(atMs = 100).shouldBeNull()
        arbiter.down(atMs = 500) shouldBe true
        arbiter.up(atMs = 600) shouldBe VoiceActionGestureOutcome.DICTATION
    }

    test("the accessibility action starts voice rewrite without any synthesized hold") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.accessibilityVoiceRewrite() shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
        arbiter.isTracking shouldBe false
    }

    test("the accessibility action cannot double-dispatch on top of a resolved gesture") {
        val arbiter = VoiceActionGestureArbiter(longPressTimeoutMs = 400)

        arbiter.down(atMs = 0)
        arbiter.advanceTo(atMs = 400) shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
        arbiter.accessibilityVoiceRewrite().shouldBeNull()
        arbiter.reset()
        arbiter.accessibilityVoiceRewrite() shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
    }

    test("only the interactive dictation key is arbitrated; other quick actions keep key down/up") {
        val voiceKey = QuickAction.InsertKey(TextKeyData(code = KeyCode.VOICE_INPUT))
        val rewriteKey = QuickAction.InsertKey(TextKeyData(code = KeyCode.AI_REWRITE))
        val undoKey = QuickAction.InsertKey(TextKeyData(code = KeyCode.UNDO))
        val insertText = QuickAction.InsertText("hi")

        quickActionUsesVoiceGesture(voiceKey, QuickActionBarType.INTERACTIVE_BUTTON) shouldBe true
        quickActionUsesVoiceGesture(voiceKey, QuickActionBarType.INTERACTIVE_TILE) shouldBe true
        quickActionUsesVoiceGesture(voiceKey, QuickActionBarType.EDITOR_TILE) shouldBe false
        quickActionUsesVoiceGesture(rewriteKey, QuickActionBarType.INTERACTIVE_BUTTON) shouldBe false
        quickActionUsesVoiceGesture(undoKey, QuickActionBarType.INTERACTIVE_BUTTON) shouldBe false
        quickActionUsesVoiceGesture(insertText, QuickActionBarType.INTERACTIVE_BUTTON) shouldBe false
    }
})
