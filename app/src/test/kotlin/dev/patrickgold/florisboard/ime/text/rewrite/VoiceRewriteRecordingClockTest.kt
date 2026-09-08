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

import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionOwner
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionPhase
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun session(
    owner: AudioSessionOwner,
    phase: AudioSessionPhase,
    startedAtMs: Long = 0L,
    pausedAtMs: Long? = null,
    pausedDurationMs: Long = 0L,
) = AudioSessionState(
    sessionId = 1L,
    owner = owner,
    mode = AudioSessionMode.CONFIGURED_PROVIDER,
    phase = phase,
    startedAtMs = startedAtMs,
    pausedAtMs = pausedAtMs,
    pausedDurationMs = pausedDurationMs,
)

class VoiceRewriteRecordingClockTest : FunSpec({
    test("no session or a dictation session leaves the rewrite clock idle") {
        voiceRewriteRecordingClock(session = null, nowMs = 5_000L) shouldBe VoiceRewriteRecordingClock.Idle
        voiceRewriteRecordingClock(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.RECORDING),
            nowMs = 5_000L,
        ) shouldBe VoiceRewriteRecordingClock.Idle
    }

    test("elapsed time excludes paused time") {
        val clock = voiceRewriteRecordingClock(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.PAUSED, pausedAtMs = 3_000L),
            nowMs = 9_000L,
        )
        clock.elapsedMs shouldBe 3_000L
        clock.remainingSeconds.shouldBeNull()
    }

    test("the capped instruction shows remaining time only in the last five seconds") {
        fun remainingAt(elapsedMs: Long) = voiceRewriteRecordingClock(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.RECORDING),
            nowMs = elapsedMs,
        ).remainingSeconds

        remainingAt(24_999L).shouldBeNull()
        remainingAt(25_000L) shouldBe 5
        remainingAt(27_500L) shouldBe 3
        remainingAt(30_000L) shouldBe 0
        remainingAt(31_000L) shouldBe 0

        voiceRewriteRecordingClock(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.PAUSED, pausedAtMs = 27_000L),
            nowMs = 60_000L,
        ).remainingSeconds shouldBe 3
    }

    test("processing shows no countdown because the recorder has already stopped") {
        voiceRewriteRecordingClock(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.PROCESSING),
            nowMs = 29_000L,
        ).remainingSeconds.shouldBeNull()
    }
})
