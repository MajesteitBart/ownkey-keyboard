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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class KeyGeometryTest : FunSpec({
    /**
     * Visible key centers of a phone QWERTY in pixels: keys 100 px apart and rows 150 px apart. The visible keys are
     * smaller (margins), which must not change any distance.
     */
    fun measuredQwerty(gapAfterT: Float = 0f): Map<Char, Pair<Float, Float>> {
        val centers = HashMap<Char, Pair<Float, Float>>()
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { row, letters ->
            val offset = listOf(0f, 50f, 150f)[row]
            letters.forEachIndexed { index, ch ->
                val gap = if (row == 0 && index > 4) gapAfterT else 0f
                centers[ch] = (offset + index * 100f + 50f + gap) to (row * 150f + 75f)
            }
        }
        return centers
    }

    test("measured distances count in key pitches, like the phone layout the costs were tuned on") {
        val measured = KeyGeometry.fromPixels(measuredQwerty()).shouldNotBeNull()
        measured.unit shouldBe (100.0 plusOrMinus 1e-9)
        val geometry = measured.geometry
        geometry.distance('q', 'w')!! shouldBe (1.0 plusOrMinus 1e-9)
        geometry.distance('q', 'a')!! shouldBe (KeyGeometry.QwertyPhone.distance('q', 'a')!! plusOrMinus 1e-9)
        geometry.distance('f', 'v')!! shouldBe (KeyGeometry.QwertyPhone.distance('f', 'v')!! plusOrMinus 1e-9)
    }

    test("a split gap in one row does not change the unit") {
        KeyGeometry.fromPixels(measuredQwerty(gapAfterT = 400f)).shouldNotBeNull().unit shouldBe (100.0 plusOrMinus 1e-9)
    }

    test("too few keys or a single row give no geometry") {
        KeyGeometry.fromPixels(measuredQwerty().filterKeys { it in "qwert" }).shouldBeNull()
        KeyGeometry.fromPixels(measuredQwerty().filterKeys { it in "qwertyuiop" }).shouldBeNull()
    }
})
