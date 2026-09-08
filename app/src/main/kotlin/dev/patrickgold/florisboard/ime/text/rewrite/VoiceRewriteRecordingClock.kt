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

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionOwner
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionPhase
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionState

/** The spoken instruction is capped; ordinary dictation stays open-ended. */
const val VOICE_REWRITE_MAX_RECORDING_MS = 30_000L

/** The countdown becomes visible only in the last seconds so it does not compete with the timer. */
const val VOICE_REWRITE_REMAINING_VISIBLE_AFTER_MS = 25_000L

/**
 * Elapsed time and, near the cap, the remaining seconds for the panel's recording body.
 *
 * Derived from the single audio session so the panel never keeps a second clock. Both values change
 * every tick and are therefore never used as screen-reader announcements or transition keys.
 */
data class VoiceRewriteRecordingClock(
    val elapsedMs: Long,
    val remainingSeconds: Int?,
) {
    companion object {
        val Idle = VoiceRewriteRecordingClock(elapsedMs = 0L, remainingSeconds = null)
    }
}

fun voiceRewriteRecordingClock(
    session: AudioSessionState?,
    nowMs: Long,
    maxRecordingDurationMs: Long = VOICE_REWRITE_MAX_RECORDING_MS,
    remainingVisibleAfterMs: Long = VOICE_REWRITE_REMAINING_VISIBLE_AFTER_MS,
): VoiceRewriteRecordingClock {
    if (session == null || session.owner != AudioSessionOwner.VOICE_REWRITE) {
        return VoiceRewriteRecordingClock.Idle
    }
    val elapsedMs = session.elapsedMs(nowMs)
    val remainingSeconds = when {
        session.phase == AudioSessionPhase.PROCESSING -> null
        elapsedMs < remainingVisibleAfterMs -> null
        else -> {
            val remainingMs = (maxRecordingDurationMs - elapsedMs).coerceAtLeast(0L)
            ((remainingMs + 999L) / 1_000L).toInt()
        }
    }
    return VoiceRewriteRecordingClock(elapsedMs = elapsedMs, remainingSeconds = remainingSeconds)
}
