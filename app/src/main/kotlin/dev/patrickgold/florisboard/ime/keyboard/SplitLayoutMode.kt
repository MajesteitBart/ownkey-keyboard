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

package dev.patrickgold.florisboard.ime.keyboard

import android.content.res.Configuration

/**
 * User preference controlling when the keyboard renders as a split (left/right half) layout,
 * targeted at large-screen devices such as unfolded foldables and tablets.
 */
enum class SplitLayoutMode {
    NEVER,
    AUTO,
    ALWAYS;
}

object SplitLayout {
    /**
     * Minimum available screen width for [SplitLayoutMode.AUTO] to activate the split layout.
     * 600dp is the conventional breakpoint separating phones from unfolded foldables/tablets.
     */
    const val AutoMinScreenWidthDp = 600

    const val GapPercentMin = 10
    const val GapPercentMax = 45
    const val GapPercentDefault = 25

    /** Keyboard modes which support rendering as a split layout. */
    fun supportsMode(mode: KeyboardMode): Boolean {
        return when (mode) {
            KeyboardMode.CHARACTERS,
            KeyboardMode.SYMBOLS,
            KeyboardMode.SYMBOLS2,
            KeyboardMode.NUMERIC_ADVANCED -> true
            else -> false
        }
    }

    fun isActive(splitMode: SplitLayoutMode, screenWidthDp: Int): Boolean {
        return when (splitMode) {
            SplitLayoutMode.NEVER -> false
            SplitLayoutMode.AUTO -> screenWidthDp >= AutoMinScreenWidthDp
            SplitLayoutMode.ALWAYS -> true
        }
    }

    fun isActive(splitMode: SplitLayoutMode, configuration: Configuration, mode: KeyboardMode): Boolean {
        return supportsMode(mode) && isActive(splitMode, configuration.screenWidthDp)
    }
}
