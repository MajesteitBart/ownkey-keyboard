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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class KeyboardStateAutocorrectFlagTest : FunSpec({
    test("autocorrect flag round-trips") {
        val state = KeyboardState.new()
        state.isAutocorrectEnabled.shouldBeFalse()
        state.isAutocorrectEnabled = true
        state.isAutocorrectEnabled.shouldBeTrue()
        state.isAutocorrectEnabled = false
        state.isAutocorrectEnabled.shouldBeFalse()
    }

    test("autocorrect flag does not touch neighboring state") {
        val state = KeyboardState.new()
        state.isIncognitoMode = true
        state.isActionsEditorVisible = true
        state.keyboardMode = KeyboardMode.SYMBOLS
        state.isAutocorrectEnabled = true
        state.isIncognitoMode.shouldBeTrue()
        state.isActionsEditorVisible.shouldBeTrue()
        state.isActionsOverflowVisible.shouldBeFalse()
        state.isComposingEnabled.shouldBeFalse()
        state.keyboardMode shouldBe KeyboardMode.SYMBOLS
        state.isAutocorrectEnabled = false
        state.isIncognitoMode.shouldBeTrue()
        state.isActionsEditorVisible.shouldBeTrue()
    }

    test("flag bit is not shared with any other flag or region") {
        val others = listOf(
            KeyboardState.M_KEYBOARD_MODE shl KeyboardState.O_KEYBOARD_MODE,
            KeyboardState.M_KEY_VARIATION shl KeyboardState.O_KEY_VARIATION,
            KeyboardState.M_INPUT_SHIFT_STATE shl KeyboardState.O_INPUT_SHIFT_STATE,
            KeyboardState.M_IME_UI_MODE shl KeyboardState.O_IME_UI_MODE,
            KeyboardState.F_IS_SELECTION_MODE,
            KeyboardState.F_IS_MANUAL_SELECTION_MODE,
            KeyboardState.F_IS_MANUAL_SELECTION_MODE_START,
            KeyboardState.F_IS_MANUAL_SELECTION_MODE_END,
            KeyboardState.F_IS_INCOGNITO_MODE,
            KeyboardState.F_IS_ACTIONS_OVERFLOW_VISIBLE,
            KeyboardState.F_IS_ACTIONS_EDITOR_VISIBLE,
            KeyboardState.F_IS_COMPOSING_ENABLED,
            KeyboardState.F_IS_CHAR_HALF_WIDTH,
            KeyboardState.F_IS_KANA_KATA,
            KeyboardState.F_IS_KANA_SMALL,
            KeyboardState.F_IS_RTL_LAYOUT_DIRECTION,
            KeyboardState.F_IS_SUBTYPE_SELECTION_VISIBLE,
            KeyboardState.F_DEBUG_SHOW_DRAG_AND_DROP_HELPERS,
        )
        others.forEach { mask -> (mask and KeyboardState.F_IS_AUTOCORRECT_ENABLED) shouldBe 0uL }
    }
})
