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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AutocorrectLevelTest : FunSpec({
    test("every level survives a round trip through the stored prefs") {
        AutocorrectLevel.entries.forEach { level ->
            AutocorrectStrength.entries.forEach { stored ->
                AutocorrectLevel.of(level.toPrefs(currentStrength = stored)) shouldBe level
            }
        }
    }

    test("every stored combination maps to one level and back unchanged") {
        listOf(true, false).forEach { enabled ->
            AutocorrectStrength.entries.forEach { strength ->
                val prefs = AutocorrectPrefs(enabled, strength)
                AutocorrectLevel.of(prefs).toPrefs(currentStrength = strength) shouldBe prefs
            }
        }
    }

    test("autocorrect off reads as Off whatever the strength") {
        AutocorrectStrength.entries.forEach { strength ->
            AutocorrectLevel.of(AutocorrectPrefs(enabled = false, strength = strength)) shouldBe AutocorrectLevel.OFF
        }
    }

    test("Off keeps the stored strength so turning autocorrect back on restores it") {
        AutocorrectLevel.OFF.toPrefs(currentStrength = AutocorrectStrength.STRONG) shouldBe
            AutocorrectPrefs(enabled = false, strength = AutocorrectStrength.STRONG)
    }

    test("the other levels turn autocorrect on at their own strength") {
        AutocorrectLevel.GENTLE.toPrefs(AutocorrectStrength.STRONG) shouldBe
            AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.GENTLE)
        AutocorrectLevel.NORMAL.toPrefs(AutocorrectStrength.GENTLE) shouldBe
            AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.NORMAL)
        AutocorrectLevel.STRONG.toPrefs(AutocorrectStrength.NORMAL) shouldBe
            AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.STRONG)
    }

    test("the defaults read as Normal") {
        AutocorrectLevel.of(AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.NORMAL)) shouldBe
            AutocorrectLevel.NORMAL
    }
})
