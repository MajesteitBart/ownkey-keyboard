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

package dev.patrickgold.florisboard.app.settings.about

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun tapsAt(vararg times: Long): List<DevtoolsUnlock> {
    var state = DevtoolsUnlock()
    return times.map { state = state.tap(it); state }
}

class DevtoolsUnlockTest : FunSpec({
    test("seven quick taps unlock on the seventh tap only") {
        val states = tapsAt(0, 300, 600, 900, 1200, 1500, 1800, 2100)
        states.map { it.unlocksNow } shouldBe listOf(false, false, false, false, false, false, true, false)
    }

    test("a slow tap starts a new series") {
        val states = tapsAt(0, 300, 600, 2_000, 2_300, 2_600, 2_900, 3_200, 3_500)
        states.map { it.taps } shouldBe listOf(1, 2, 3, 1, 2, 3, 4, 5, 6)
        states.none { it.unlocksNow } shouldBe true
    }

    test("a gap of exactly the limit still counts") {
        tapsAt(0, DevtoolsUnlock.MaxGapMs).last().taps shouldBe 2
        tapsAt(0, DevtoolsUnlock.MaxGapMs + 1).last().taps shouldBe 1
    }

    test("a clock that jumps back starts over") {
        tapsAt(1_000, 500).last().taps shouldBe 1
    }
})
