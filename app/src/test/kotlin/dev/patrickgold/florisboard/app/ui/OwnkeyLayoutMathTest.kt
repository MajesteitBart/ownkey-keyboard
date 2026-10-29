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

package dev.patrickgold.florisboard.app.ui

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.florisboard.lib.compose.OwnkeyGapArrangement
import org.florisboard.lib.compose.arrangeWithGapsBetweenNonEmpty

class OwnkeyLayoutMathTest : FunSpec({
    context("gaps between non-empty rows") {
        test("adds the gap only between rows that have a size") {
            val sizes = intArrayOf(10, 0, 20, 0, 30)
            val positions = IntArray(sizes.size)
            val extent = arrangeWithGapsBetweenNonEmpty(sizes, gapPx = 2, outPositions = positions)
            positions.toList() shouldBe listOf(0, 10, 12, 32, 34)
            extent shouldBe 64
        }
        test("leading and trailing empty rows add no gap") {
            val sizes = intArrayOf(0, 0, 40, 0)
            val positions = IntArray(sizes.size)
            val extent = arrangeWithGapsBetweenNonEmpty(sizes, gapPx = 2, outPositions = positions)
            positions.toList() shouldBe listOf(0, 0, 0, 40)
            extent shouldBe 40
        }
        test("all empty rows use no space") {
            val sizes = intArrayOf(0, 0)
            val positions = IntArray(sizes.size)
            arrangeWithGapsBetweenNonEmpty(sizes, gapPx = 2, outPositions = positions) shouldBe 0
        }
        test("the arrangement records the extent it used") {
            val arrangement = OwnkeyGapArrangement(2.dp)
            val sizes = intArrayOf(56, 0, 56)
            val positions = IntArray(sizes.size)
            with(arrangement) { Density(density = 3f).arrange(totalSize = 200, sizes = sizes, outPositions = positions) }
            positions.toList() shouldBe listOf(0, 56, 62)
            arrangement.usedExtentPx shouldBe 118
        }
    }

    context("row value width") {
        test("a short title leaves the value its full width") {
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 400, valueWidth = 300) shouldBe 300
        }
        test("the value shrinks before the title wraps, down to 30% of the line") {
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 560, valueWidth = 500) shouldBe 400
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 900, valueWidth = 500) shouldBe 300
        }
        test("next to a short title the value uses what the title leaves, so it isn't cut short") {
            // "Previous language" next to "Swipe up" on a 360dp phone.
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 100, valueWidth = 800) shouldBe 800
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 100, valueWidth = 1200) shouldBe 860
        }
        test("a narrow value keeps its own width next to a long title") {
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 990, valueWidth = 120) shouldBe 120
        }
        test("no value takes no space") {
            rowValueWidth(lineWidth = 1000, gap = 40, titleWidth = 300, valueWidth = 0) shouldBe 0
        }
        test("a long slider value next to a long title leaves the title most of the line") {
            // "Limit clipboard suggestions to" next to "Items copied within the last 60 s".
            val line = 900
            val gap = 48
            val value = rowValueWidth(lineWidth = line, gap = gap, titleWidth = 820, valueWidth = 870)
            value shouldBe 270
            (line - gap - value) shouldBe 582
        }
    }

    context("stacked value") {
        test("the value shares the title's line below 150% font scale") {
            stacksRowValue(1.0f) shouldBe false
            stacksRowValue(1.3f) shouldBe false
            stacksRowValue(1.49f) shouldBe false
        }
        test("the value moves under the title from 150% font scale") {
            stacksRowValue(1.5f) shouldBe true
            stacksRowValue(2.0f) shouldBe true
        }
    }

    context("slider steps") {
        test("snaps to the nearest step inside the range") {
            snapSliderValue(7.4f, min = 0f, max = 10f, step = 1f) shouldBe (7f plusOrMinus 0.0001f)
            snapSliderValue(0.26f, min = 0f, max = 1f, step = 0.1f) shouldBe (0.3f plusOrMinus 0.0001f)
            snapSliderValue(12f, min = 0f, max = 10f, step = 1f) shouldBe (10f plusOrMinus 0.0001f)
            snapSliderValue(55f, min = 50f, max = 150f, step = 10f) shouldBe (60f plusOrMinus 0.0001f)
        }
        test("counts intermediate stops for M3") {
            sliderSteps(min = 1f, max = 100f, step = 1f) shouldBe 98
            sliderSteps(min = 0f, max = 1f, step = 0.25f) shouldBe 3
            sliderSteps(min = 0f, max = 1f, step = 1f) shouldBe 0
        }
        test("a value with an optional unit stays in the value slot") {
            for (label in listOf("75%", "60 s", "100 ms", "1,000 items", "Off", "1.5x")) {
                isPhraseValueLabel(label) shouldBe false
            }
        }
        test("a phrase goes under the title") {
            isPhraseValueLabel("Items copied within the last 60 s") shouldBe true
            isPhraseValueLabel("After 5 minutes") shouldBe true
            isPhraseValueLabel("Very low") shouldBe true
        }
    }

    context("dialog buttons") {
        val gap = 4
        test("all three share a row when they fit") {
            dialogButtonRows(listOf(80, 70, 90), maxWidth = 300, gap = gap) shouldBe listOf(listOf(0, 1, 2))
        }
        test("the neutral button moves above the others when they don't fit") {
            dialogButtonRows(listOf(150, 70, 90), maxWidth = 300, gap = gap) shouldBe
                listOf(listOf(0), listOf(1, 2))
        }
        test("confirm sits above dismiss when even those two don't fit") {
            dialogButtonRows(listOf(150, 160, 170), maxWidth = 300, gap = gap) shouldBe
                listOf(listOf(0), listOf(2), listOf(1))
            dialogButtonRows(listOf(-1, 160, 170), maxWidth = 300, gap = gap) shouldBe listOf(listOf(2), listOf(1))
        }
        test("missing buttons take no room") {
            dialogButtonRows(listOf(-1, 70, 90), maxWidth = 300, gap = gap) shouldBe listOf(listOf(1, 2))
            dialogButtonRows(listOf(120, -1, -1), maxWidth = 100, gap = gap) shouldBe listOf(listOf(0))
        }
    }
})
