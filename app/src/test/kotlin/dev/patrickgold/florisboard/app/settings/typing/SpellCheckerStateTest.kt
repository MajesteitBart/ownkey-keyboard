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

package dev.patrickgold.florisboard.app.settings.typing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpellCheckerStateTest : FunSpec({
    test("a phone that never touched the spell checker switch has it on") {
        isSystemSpellCheckerEnabled(null) shouldBe true
        isSystemSpellCheckerEnabled("") shouldBe true
    }

    test("only an explicit 0 turns the spell checker off") {
        isSystemSpellCheckerEnabled("1") shouldBe true
        isSystemSpellCheckerEnabled("0") shouldBe false
        isSystemSpellCheckerEnabled(" 0 ") shouldBe false
    }
})
