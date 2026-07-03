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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SplitLayoutSpec
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

private const val KEYBOARD_WIDTH = 1000f
private const val KEYBOARD_HEIGHT = 240f
private const val GAP_WIDTH = 200f

private fun charKey(code: Int = 97): TextKey {
    return TextKey(TextKeyData(code = code)).also {
        it.flayWidthFactor = 1.0f
        it.flayShrink = 1.0f
        it.flayGrow = 0.0f
    }
}

private fun spaceKey(): TextKey {
    return TextKey(TextKeyData(code = KeyCode.SPACE)).also {
        it.flayWidthFactor = 1.0f
        it.flayShrink = 0.0f
        it.flayGrow = 1.0f
    }
}

private fun keyboardOf(vararg rows: Array<TextKey>): TextKeyboard {
    return TextKeyboard(
        arrangement = arrayOf(*rows),
        mode = KeyboardMode.CHARACTERS,
        extendedPopupMapping = null,
        extendedPopupMappingDefault = null,
    )
}

private fun desiredKey(keyWidth: Float): TextKey {
    return TextKey(TextKeyData.UNSPECIFIED).also {
        it.touchBounds.apply {
            left = 0f
            top = 0f
            right = keyWidth
            bottom = 60f
        }
        it.visibleBounds.applyFrom(it.touchBounds)
    }
}

class TextKeyboardSplitLayoutTest : FunSpec({
    test("non-split layout fills the full keyboard width") {
        val row = Array(10) { charKey() }
        val keyboard = keyboardOf(row)

        keyboard.layout(KEYBOARD_WIDTH, KEYBOARD_HEIGHT, desiredKey(KEYBOARD_WIDTH / 10f), false, null)

        row.first().touchBounds.left shouldBe 0f
        row.last().touchBounds.right shouldBe KEYBOARD_WIDTH
        row[4].touchBounds.right shouldBe (500f plusOrMinus 0.01f)
        row[5].touchBounds.left shouldBe (500f plusOrMinus 0.01f)
    }

    test("split layout leaves a middle gap between the two key groups") {
        val row = Array(10) { charKey() }
        val keyboard = keyboardOf(row)
        val segmentKeyWidth = (KEYBOARD_WIDTH - GAP_WIDTH) / 10f

        keyboard.layout(
            KEYBOARD_WIDTH, KEYBOARD_HEIGHT, desiredKey(segmentKeyWidth), false,
            SplitLayoutSpec(gapWidth = GAP_WIDTH),
        )

        // Left half: keys 0..4 within [0, 400]
        row.first().touchBounds.left shouldBe 0f
        row[4].touchBounds.right shouldBe (400f plusOrMinus 0.01f)
        // Right half: keys 5..9 within [600, 1000]
        row[5].touchBounds.left shouldBe (600f plusOrMinus 0.01f)
        row.last().touchBounds.right shouldBe (1000f plusOrMinus 0.01f)
        // No key may reach into the middle gap
        for (key in row) {
            val inGap = key.touchBounds.left >= 400.01f && key.touchBounds.left < 599.99f
            inGap shouldBe false
        }
    }

    test("split layout divides bottom row between duplicated space keys") {
        // Emulates the arrangement produced by LayoutManager when split is active:
        // [sym] [,] [space] | [space] [.] [enter]
        val row = arrayOf(
            charKey(code = KeyCode.VIEW_SYMBOLS),
            charKey(code = 44),
            spaceKey(),
            spaceKey(),
            charKey(code = 46),
            charKey(code = KeyCode.ENTER),
        )
        val keyboard = keyboardOf(row)
        val segmentKeyWidth = (KEYBOARD_WIDTH - GAP_WIDTH) / 10f

        keyboard.layout(
            KEYBOARD_WIDTH, KEYBOARD_HEIGHT, desiredKey(segmentKeyWidth), false,
            SplitLayoutSpec(gapWidth = GAP_WIDTH),
        )

        // Left space bar grows to the end of the left half, right space bar starts the right half.
        row[2].touchBounds.right shouldBe (400f plusOrMinus 0.01f)
        row[3].touchBounds.left shouldBe (600f plusOrMinus 0.01f)
        row.first().touchBounds.left shouldBe 0f
        row.last().touchBounds.right shouldBe (1000f plusOrMinus 0.01f)
    }

    test("split layout keeps touch bounds non-overlapping and ordered") {
        val row = Array(9) { charKey() }
        val keyboard = keyboardOf(row)
        val segmentKeyWidth = (KEYBOARD_WIDTH - GAP_WIDTH) / 10f

        keyboard.layout(
            KEYBOARD_WIDTH, KEYBOARD_HEIGHT, desiredKey(segmentKeyWidth), false,
            SplitLayoutSpec(gapWidth = GAP_WIDTH),
        )

        for (i in 0 until row.size - 1) {
            (row[i].touchBounds.right <= row[i + 1].touchBounds.left + 0.01f) shouldBe true
        }
    }
})
