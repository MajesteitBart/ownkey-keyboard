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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.keyboard.CaseSelector
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.nlp.latin.engine.KeyGeometry
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class KeyboardGeometrySourceTest : FunSpec({
    afterTest {
        KeyboardGeometrySource.update(emptyList())
        TapTrail.clear()
    }

    fun keys(letters: String) = letters.map { TextKey(TextKeyData(code = it.code)) }

    test("a recomposition hands over a new list of the same keys and keeps the tap trail") {
        val qwerty = keys("qwertyuiop")
        KeyboardGeometrySource.update(qwerty)
        "hel".forEach { TapTrail.recordWithoutPosition(it.code) }
        // Shift turning off after the first letter recomposes the layout with the same keys.
        KeyboardGeometrySource.update(qwerty.toList())
        TapTrail.tapsFor("hel").shouldNotBeNull()
        // Another layout has other key objects, and taps recorded on the old one no longer fit.
        KeyboardGeometrySource.update(keys("azertyuiop"))
        TapTrail.tapsFor("hel").shouldBeNull()
    }

    test("the geometry reads the evaluated key codes and counts in key pitches") {
        // Keys 100 px apart and rows 150 px apart; the visible keys are 90 x 130 px, the rest is margin.
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").map { letters ->
            letters.mapIndexed { index, ch ->
                // The last key of each row sits behind a case selector, as some layouts do for letters.
                val data = if (index == letters.lastIndex) {
                    CaseSelector(TextKeyData(code = ch.code), TextKeyData(code = ch.uppercaseChar().code))
                } else {
                    TextKeyData(code = ch.code)
                }
                TextKey(data)
            }.toTypedArray()
        }
        val keyboard = TextKeyboard(rows.toTypedArray(), KeyboardMode.CHARACTERS, null, null)
        val evaluator = object : ComputingEvaluator by DefaultComputingEvaluator {
            override val keyboard = keyboard
        }
        rows.forEachIndexed { row, keys ->
            keys.forEachIndexed { index, key ->
                key.compute(evaluator)
                val left = listOf(0f, 50f, 150f)[row] + index * 100f + 5f
                key.visibleBounds.apply {
                    this.left = left
                    top = row * 150f + 10f
                    right = left + 90f
                    bottom = row * 150f + 140f
                }
            }
        }
        val laidOut = keyboard.keys().asSequence().toList()
        KeyboardGeometrySource.update(laidOut)
        val geometry = KeyboardGeometrySource.current().shouldNotBeNull()
        geometry.size shouldBe 26
        geometry.distance('q', 'w')!! shouldBe (1.0 plusOrMinus 1e-9)
        geometry.distance('o', 'p')!! shouldBe (1.0 plusOrMinus 1e-9)
        for ((a, b) in listOf('f' to 'v', 'q' to 'a', 'k' to 'm')) {
            geometry.distance(a, b)!! shouldBe (KeyGeometry.QwertyPhone.distance(a, b)!! plusOrMinus 1e-9)
        }
    }
})
