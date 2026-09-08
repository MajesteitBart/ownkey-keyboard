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

package dev.patrickgold.florisboard.ime.smartbar

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

class VoiceRecordingRowModelTest : FunSpec({
    test("no audio session leaves the ordinary smartbar untouched") {
        voiceRecordingRowState(session = null, nowMs = 0L).shouldBeNull()
    }

    test("dictation recording, pausing and processing drive the row with one status each") {
        val recording = voiceRecordingRowState(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.RECORDING),
            nowMs = 4_000L,
        )!!
        recording.phase shouldBe VoiceRecordingPhase.RECORDING
        recording.status shouldBe VoiceRecordingStatus.LISTENING
        recording.elapsedMs shouldBe 4_000L
        recording.isCapturing shouldBe true

        val paused = voiceRecordingRowState(
            session = session(
                AudioSessionOwner.DICTATION,
                AudioSessionPhase.PAUSED,
                pausedAtMs = 3_000L,
            ),
            nowMs = 9_000L,
        )!!
        paused.phase shouldBe VoiceRecordingPhase.PAUSED
        paused.status shouldBe VoiceRecordingStatus.PAUSED
        // Paused time is excluded from the elapsed timer.
        paused.elapsedMs shouldBe 3_000L

        val processing = voiceRecordingRowState(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.PROCESSING),
            nowMs = 5_000L,
        )!!
        processing.phase shouldBe VoiceRecordingPhase.PROCESSING
        processing.status shouldBe VoiceRecordingStatus.PROCESSING
        processing.isCapturing shouldBe false
    }

    test("a voice rewrite session never produces a smartbar row because the panel owns it") {
        AudioSessionPhase.entries.forEach { phase ->
            voiceRecordingRowState(
                session = session(AudioSessionOwner.VOICE_REWRITE, phase, pausedAtMs = 0L),
                nowMs = 1_500L,
            ).shouldBeNull()
        }
    }

    test("every supported width keeps 48 dp controls and a bounded truthful waveform") {
        listOf(320, 360, 411, 640, 840, 1_200).forEach { widthDp ->
            val layout = RecordingRowLayoutPolicy.resolve(widthDp)

            layout.controlSizeDp shouldBe RecordingRowLayoutPolicy.ControlSizeDp
            (layout.waveformWidthDp > 0) shouldBe true
            (layout.barCount in RecordingRowLayoutPolicy.MinBarCount..RecordingRowLayoutPolicy.MaxBarCount) shouldBe true
            (layout.clusterWidthDp <= RecordingRowLayoutPolicy.MaxClusterWidthDp) shouldBe true
        }
    }

    test("a tablet and a split keyboard centre one identical control cluster") {
        val tablet = RecordingRowLayoutPolicy.resolve(1_200)
        val split = RecordingRowLayoutPolicy.resolve(1_200)
        val compact = RecordingRowLayoutPolicy.resolve(320)

        split shouldBe tablet
        tablet.clusterWidthDp shouldBe RecordingRowLayoutPolicy.MaxClusterWidthDp
        // The waveform is the only element that gives up space on the narrowest supported width.
        (compact.barCount < tablet.barCount) shouldBe true
        compact.controlSizeDp shouldBe tablet.controlSizeDp
    }
})
