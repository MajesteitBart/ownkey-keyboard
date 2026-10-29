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

/**
 * Counts taps on the version in About. Seven taps in a row, each within [MaxGapMs] of the previous one,
 * turn on developer tools.
 */
data class DevtoolsUnlock(
    val taps: Int = 0,
    val lastTapAtMs: Long? = null,
) {
    /** The state after a tap at [nowMs]. A slow tap starts a new series. */
    fun tap(nowMs: Long): DevtoolsUnlock {
        val continues = lastTapAtMs != null && nowMs - lastTapAtMs in 0..MaxGapMs
        val next = if (continues) taps + 1 else 1
        return DevtoolsUnlock(taps = next, lastTapAtMs = nowMs)
    }

    /** True exactly on the tap that completes the series. */
    val unlocksNow: Boolean get() = taps == TapsToUnlock

    companion object {
        const val TapsToUnlock = 7
        const val MaxGapMs = 1_000L
    }
}
