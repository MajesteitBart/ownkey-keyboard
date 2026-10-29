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

package dev.patrickgold.florisboard.app.settings.keyboard

import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyboardChoicesTest : FunSpec({
    /** Applies the writes to stored values the way the screen does: null fields stay as stored. */
    fun storeSpaceNeighbor(choice: SpaceNeighborKey, stored: Pair<Boolean, UtilityKeyAction>): Pair<Boolean, UtilityKeyAction> {
        val writes = choice.writes()
        return writes.enabled to (writes.action ?: stored.second)
    }

    data class Feedback(val enabled: Boolean, val mode: InputFeedbackActivationMode, val featKeyPress: Boolean)

    fun storeFeedback(choice: FeedbackChoice, stored: Feedback): Feedback {
        val writes = choice.writes()
        return Feedback(
            enabled = writes.enabled,
            mode = writes.activationMode ?: stored.mode,
            featKeyPress = writes.featKeyPress ?: stored.featKeyPress,
        )
    }

    test("every key-next-to-space choice reads back as itself from any stored state") {
        val storedStates = UtilityKeyAction.entries.flatMap { action -> listOf(true to action, false to action) }
        for (choice in SpaceNeighborKey.entries) {
            for (stored in storedStates) {
                val (enabled, action) = storeSpaceNeighbor(choice, stored)
                SpaceNeighborKey.of(enabled, action) shouldBe choice
            }
        }
    }

    test("key next to space maps each choice to its utility key action") {
        SpaceNeighborKey.EMOJI.writes() shouldBe SpaceNeighborKey.Writes(true, UtilityKeyAction.SWITCH_TO_EMOJIS)
        SpaceNeighborKey.LANGUAGE.writes() shouldBe SpaceNeighborKey.Writes(true, UtilityKeyAction.SWITCH_LANGUAGE)
        SpaceNeighborKey.AUTOMATIC.writes() shouldBe
            SpaceNeighborKey.Writes(true, UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS)
        SpaceNeighborKey.SWITCH_KEYBOARD.writes() shouldBe
            SpaceNeighborKey.Writes(true, UtilityKeyAction.SWITCH_KEYBOARD_APP)
    }

    test("key next to space reads a switched-off or disabled utility key as none and keeps the stored action") {
        SpaceNeighborKey.of(false, UtilityKeyAction.SWITCH_TO_EMOJIS) shouldBe SpaceNeighborKey.NONE
        SpaceNeighborKey.of(true, UtilityKeyAction.DISABLED) shouldBe SpaceNeighborKey.NONE
        storeSpaceNeighbor(SpaceNeighborKey.NONE, true to UtilityKeyAction.SWITCH_LANGUAGE) shouldBe
            (false to UtilityKeyAction.SWITCH_LANGUAGE)
    }

    test("the default utility key reads as automatic") {
        SpaceNeighborKey.of(true, UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS) shouldBe SpaceNeighborKey.AUTOMATIC
    }

    test("every key sounds and vibration choice reads back as itself from any stored state") {
        val storedStates = listOf(true, false).flatMap { enabled ->
            InputFeedbackActivationMode.entries.flatMap { mode ->
                listOf(true, false).map { feat -> Feedback(enabled, mode, feat) }
            }
        }
        for (choice in FeedbackChoice.entries) {
            for (stored in storedStates) {
                val result = storeFeedback(choice, stored)
                FeedbackChoice.of(result.enabled, result.mode) shouldBe choice
            }
        }
    }

    test("turning key sounds or vibration on also turns on key presses") {
        val muted = Feedback(enabled = false, mode = InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS, featKeyPress = false)
        storeFeedback(FeedbackChoice.FOLLOW_PHONE, muted) shouldBe
            Feedback(true, InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS, true)
        storeFeedback(FeedbackChoice.ALWAYS, muted) shouldBe
            Feedback(true, InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS, true)
        storeFeedback(FeedbackChoice.OFF, muted.copy(enabled = true)) shouldBe muted
    }

    test("the default feedback prefs read as follow phone for sounds and off for vibration") {
        FeedbackChoice.of(true, InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS) shouldBe FeedbackChoice.FOLLOW_PHONE
        FeedbackChoice.of(false, InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS) shouldBe FeedbackChoice.OFF
    }

    test("key label size shows portrait and writes the same size to both orientations") {
        KeyLabelSize.shown(portrait = 120, landscape = 90) shouldBe 120
        KeyLabelSize.writes(120) shouldBe KeyLabelSize.Writes(portrait = 120, landscape = 120)
        val written = KeyLabelSize.writes(85)
        KeyLabelSize.shown(written.portrait, written.landscape) shouldBe 85
    }

    test("key label size stays inside the slider range") {
        KeyLabelSize.writes(10) shouldBe KeyLabelSize.Writes(KeyLabelSize.MIN, KeyLabelSize.MIN)
        KeyLabelSize.writes(400) shouldBe KeyLabelSize.Writes(KeyLabelSize.MAX, KeyLabelSize.MAX)
    }
})
