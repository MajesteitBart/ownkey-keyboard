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

package org.florisboard.lib.prediction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.exp

class SpatialModelTest : FunSpec({
    val qwerty = SpatialModel.qwerty()

    test("key-code channel is a normalised distribution with a floor") {
        for (k in 'a'..'z') {
            val sum = ('a'..'z').sumOf { t -> exp(qwerty.logChannel(t, k)) }
            sum shouldBe (1.0 plusOrMinus 1e-5)
            for (t in 'a'..'z') exp(qwerty.logChannel(t, k)) shouldBeGreaterThan 0.99e-3
        }
    }

    test("neighbours are likelier than distant keys and the intended key is likeliest") {
        qwerty.logChannel('w', 'e') shouldBeGreaterThan qwerty.logChannel('p', 'e')
        qwerty.logChannel('d', 'e') shouldBeGreaterThan qwerty.logChannel('m', 'e')
        qwerty.logChannel('e', 'e') shouldBeGreaterThan qwerty.logChannel('w', 'e')
        exp(qwerty.logChannel('e', 'e')) shouldBeGreaterThan 0.7
    }

    test("touch likelihood prefers the nearest key and stays normalised") {
        val out = FloatArray(26)
        val g = qwerty.centre('g')
        qwerty.touchCosts(TouchPoint(g.x + 0.1, g.y - 0.05), out)
        out.indices.minByOrNull { out[it] } shouldBe ('g' - 'a')
        out.sumOf { exp(-it.toDouble()) } shouldBe (1.0 plusOrMinus 1e-4)
        // Halfway between g and h both keys are about equally likely.
        val h = qwerty.centre('h')
        qwerty.touchCosts(TouchPoint((g.x + h.x) / 2, g.y), out)
        (out['g' - 'a'] - out['h' - 'a']).toDouble() shouldBe (0.0 plusOrMinus 1e-4)
        qwerty.nearestKey(TouchPoint(h.x + 0.3, h.y)) shouldBe 'h'
    }

    test("measured centres are converted to pitch units") {
        val cal = TestModels.repoFile("tools/kbbench/calib/ownkey.json")
        val px = 142.7
        val py = 199.0
        val centres = mapOf(
            'q' to (78.5 to 2255.0), 'w' to (221.0 to 2255.0), 'e' to (364.0 to 2255.0), 'r' to (505.5 to 2255.0),
            't' to (649.0 to 2255.0), 'y' to (792.0 to 2255.0), 'u' to (934.0 to 2255.0), 'i' to (1076.5 to 2255.0),
            'o' to (1218.5 to 2255.0), 'p' to (1361.5 to 2255.0), 'a' to (143.0 to 2454.0), 's' to (288.0 to 2454.0),
            'd' to (432.5 to 2454.0), 'f' to (576.5 to 2454.0), 'g' to (720.0 to 2454.0), 'h' to (864.5 to 2454.0),
            'j' to (1008.0 to 2454.0), 'k' to (1152.0 to 2454.0), 'l' to (1296.5 to 2454.0), 'z' to (297.0 to 2653.0),
            'x' to (438.0 to 2653.0), 'c' to (579.0 to 2653.0), 'v' to (719.5 to 2653.0), 'b' to (861.0 to 2653.0),
            'n' to (1001.5 to 2653.0), 'm' to (1142.5 to 2653.0),
        )
        cal.exists() shouldBe true
        val m = SpatialModel.fromKeyCentres(centres, px, py)
        (m.centre('w').x - m.centre('q').x) shouldBe (0.9986 plusOrMinus 1e-3)
        (m.centre('a').y - m.centre('q').y) shouldBe (1.0 plusOrMinus 1e-9)
        m.logChannel('e', 'e') shouldBeGreaterThan m.logChannel('w', 'e')
        m.logChannel('x', 'z') shouldBeLessThan 0.0
    }
})
