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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

enum class VoiceActionGestureOutcome {
    DICTATION,
    VOICE_REWRITE,
}

/**
 * Pure tap/hold arbiter for the sticky voice quick action.
 *
 * The pointer pipeline feeds it real timestamps and dispatches whatever it returns. Exactly one
 * outcome may leave a single gesture: a released tap below the configured hold threshold means
 * ordinary dictation, a hold that reaches the threshold means voice rewrite and makes the following
 * release inert, and a cancellation before recognition means neither. The threshold is supplied by
 * the caller from the platform's configured long-press timeout, which already carries the user's
 * accessibility touch-and-hold delay, so no product duration is baked in here.
 */
class VoiceActionGestureArbiter(
    private val longPressTimeoutMs: Long,
) {
    private var downAtMs: Long? = null
    private var holdRecognized = false
    private var dispatched = false
    private var cancelled = false

    /** True once the hold threshold produced a voice-rewrite outcome for the active gesture. */
    val isHoldRecognized: Boolean get() = holdRecognized

    /** True while a pointer gesture is being arbitrated. */
    val isTracking: Boolean get() = downAtMs != null

    fun down(atMs: Long): Boolean {
        if (downAtMs != null) return false
        downAtMs = atMs
        holdRecognized = false
        dispatched = false
        cancelled = false
        return true
    }

    fun advanceTo(atMs: Long): VoiceActionGestureOutcome? {
        val startedAtMs = downAtMs ?: return null
        if (cancelled || dispatched) return null
        if (atMs - startedAtMs < longPressTimeoutMs) return null
        holdRecognized = true
        dispatched = true
        return VoiceActionGestureOutcome.VOICE_REWRITE
    }

    fun up(atMs: Long): VoiceActionGestureOutcome? {
        val startedAtMs = downAtMs ?: return null
        val holdOutcome = advanceTo(atMs)
        val outcome = when {
            holdOutcome != null -> holdOutcome
            cancelled || dispatched -> null
            atMs - startedAtMs < longPressTimeoutMs -> {
                dispatched = true
                VoiceActionGestureOutcome.DICTATION
            }
            else -> null
        }
        endGesture()
        return outcome
    }

    /**
     * A pointer cancellation. It still resolves a hold that had already crossed the threshold, so a
     * slide-off after recognition cannot fall through to ordinary dictation.
     */
    fun cancel(atMs: Long): VoiceActionGestureOutcome? {
        if (downAtMs == null) return null
        val holdOutcome = advanceTo(atMs)
        if (holdOutcome == null) cancelled = true
        endGesture()
        return holdOutcome
    }

    /**
     * Explicit TalkBack/custom-action entry. It never depends on a synthesized touch hold and is
     * ignored only while a pointer gesture has already produced an outcome.
     */
    fun accessibilityVoiceRewrite(): VoiceActionGestureOutcome? {
        if (isTracking && dispatched) return null
        return VoiceActionGestureOutcome.VOICE_REWRITE
    }

    fun reset() {
        endGesture()
    }

    private fun endGesture() {
        downAtMs = null
        holdRecognized = false
        dispatched = false
        cancelled = false
    }
}
