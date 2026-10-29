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

/** Stored values behind the single "Autocorrect" choice. */
data class AutocorrectPrefs(
    val enabled: Boolean,
    val strength: AutocorrectStrength,
)

/**
 * The one Autocorrect list in Typing. It merges `correction__high_certainty_autocorrect_enabled` and
 * `correction__autocorrect_strength`: Off turns autocorrect off, the other levels turn it on at that
 * strength.
 */
enum class AutocorrectLevel {
    OFF,
    GENTLE,
    NORMAL,
    STRONG;

    /**
     * The pref values for this level. Off keeps [currentStrength], so turning autocorrect back on later
     * restores the strength the user had.
     */
    fun toPrefs(currentStrength: AutocorrectStrength): AutocorrectPrefs = when (this) {
        OFF -> AutocorrectPrefs(enabled = false, strength = currentStrength)
        GENTLE -> AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.GENTLE)
        NORMAL -> AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.NORMAL)
        STRONG -> AutocorrectPrefs(enabled = true, strength = AutocorrectStrength.STRONG)
    }

    companion object {
        fun of(prefs: AutocorrectPrefs): AutocorrectLevel = when {
            !prefs.enabled -> OFF
            else -> when (prefs.strength) {
                AutocorrectStrength.GENTLE -> GENTLE
                AutocorrectStrength.NORMAL -> NORMAL
                AutocorrectStrength.STRONG -> STRONG
            }
        }
    }
}
