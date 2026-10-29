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

package dev.patrickgold.florisboard.app.settings.keyboard

import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction

/**
 * Settings that show one choice but store it in several prefs. Each mapping reads the stored prefs into a
 * choice and turns a picked choice into the writes it needs; a null field in the writes means "leave as is".
 */

/** The key next to the space bar. */
enum class SpaceNeighborKey {
    NONE,
    EMOJI,
    LANGUAGE,
    AUTOMATIC,
    SWITCH_KEYBOARD;

    /** What picking this choice writes to `keyboard__utility_key_enabled` and `keyboard__utility_key_action`. */
    data class Writes(val enabled: Boolean, val action: UtilityKeyAction?)

    fun writes(): Writes = when (this) {
        // The stored action stays, so turning the key back on elsewhere restores it.
        NONE -> Writes(enabled = false, action = null)
        EMOJI -> Writes(enabled = true, action = UtilityKeyAction.SWITCH_TO_EMOJIS)
        LANGUAGE -> Writes(enabled = true, action = UtilityKeyAction.SWITCH_LANGUAGE)
        AUTOMATIC -> Writes(enabled = true, action = UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS)
        SWITCH_KEYBOARD -> Writes(enabled = true, action = UtilityKeyAction.SWITCH_KEYBOARD_APP)
    }

    companion object {
        fun of(enabled: Boolean, action: UtilityKeyAction): SpaceNeighborKey = when {
            !enabled -> NONE
            else -> when (action) {
                UtilityKeyAction.DISABLED -> NONE
                UtilityKeyAction.SWITCH_TO_EMOJIS -> EMOJI
                UtilityKeyAction.SWITCH_LANGUAGE -> LANGUAGE
                UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> AUTOMATIC
                UtilityKeyAction.SWITCH_KEYBOARD_APP -> SWITCH_KEYBOARD
            }
        }
    }
}

/**
 * Key sounds or vibration: off, following the phone's touch feedback setting, or always on. The phone's
 * setting only matters for Follow phone, so a stored mode never silently mutes an "on" choice.
 */
enum class FeedbackChoice {
    OFF,
    FOLLOW_PHONE,
    ALWAYS;

    /**
     * What picking this choice writes to `*_enabled`, `*_activation_mode` and `*_feat_key_press`. Turning
     * feedback on also turns on key presses, so a hidden key-press switch can't keep it silent.
     */
    data class Writes(
        val enabled: Boolean,
        val activationMode: InputFeedbackActivationMode?,
        val featKeyPress: Boolean?,
    )

    fun writes(): Writes = when (this) {
        OFF -> Writes(enabled = false, activationMode = null, featKeyPress = null)
        FOLLOW_PHONE -> Writes(
            enabled = true,
            activationMode = InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS,
            featKeyPress = true,
        )
        ALWAYS -> Writes(
            enabled = true,
            activationMode = InputFeedbackActivationMode.IGNORE_SYSTEM_SETTINGS,
            featKeyPress = true,
        )
    }

    companion object {
        fun of(enabled: Boolean, activationMode: InputFeedbackActivationMode): FeedbackChoice = when {
            !enabled -> OFF
            activationMode == InputFeedbackActivationMode.RESPECT_SYSTEM_SETTINGS -> FOLLOW_PHONE
            else -> ALWAYS
        }
    }
}

/** One key label size for both orientations. The slider shows portrait and writes both. */
object KeyLabelSize {
    const val MIN = 50
    const val MAX = 150
    const val STEP = 5

    data class Writes(val portrait: Int, val landscape: Int)

    fun shown(portrait: Int, @Suppress("UNUSED_PARAMETER") landscape: Int): Int = portrait

    fun writes(value: Int): Writes {
        val size = value.coerceIn(MIN, MAX)
        return Writes(portrait = size, landscape = size)
    }
}
