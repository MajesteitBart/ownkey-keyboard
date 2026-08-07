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
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEntryOrigin
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteMessage
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteScopeLabel
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteSurface
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteTargetScope
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteUiModel
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

private fun hubModel() = VoiceRewriteUiModel(
    surface = VoiceRewriteSurface.HUB,
    origin = VoiceRewriteEntryOrigin.DICTATION_KEY,
)

private fun voiceRewriteModel(
    surface: VoiceRewriteSurface,
    statusMessage: VoiceRewriteMessage? = null,
    scope: VoiceRewriteScopeLabel? = null,
) = VoiceRewriteUiModel(
    surface = surface,
    origin = VoiceRewriteEntryOrigin.DICTATION_KEY,
    statusMessage = statusMessage,
    scopeLabel = scope,
)

class VoiceRecordingRowModelTest : FunSpec({
    test("no audio session and an idle rewrite hub leave the ordinary smartbar untouched") {
        voiceRecordingRowState(
            session = null,
            voiceRewrite = hubModel(),
            nowMs = 0L,
        ).shouldBeNull()
    }

    test("dictation recording, pausing and processing drive the same row with listening wording") {
        val recording = voiceRecordingRowState(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.RECORDING),
            voiceRewrite = hubModel(),
            nowMs = 4_000L,
        )!!
        recording.mode shouldBe VoiceRecordingMode.DICTATION
        recording.phase shouldBe VoiceRecordingPhase.RECORDING
        recording.status shouldBe VoiceRecordingStatus.LISTENING
        recording.elapsedMs shouldBe 4_000L
        recording.targetScope.shouldBeNull()
        recording.isCapturing shouldBe true

        val paused = voiceRecordingRowState(
            session = session(
                AudioSessionOwner.DICTATION,
                AudioSessionPhase.PAUSED,
                pausedAtMs = 3_000L,
            ),
            voiceRewrite = hubModel(),
            nowMs = 9_000L,
        )!!
        paused.phase shouldBe VoiceRecordingPhase.PAUSED
        paused.status shouldBe VoiceRecordingStatus.PAUSED
        // Paused time is excluded from the elapsed timer.
        paused.elapsedMs shouldBe 3_000L
        paused.remainingSeconds.shouldBeNull()

        val processing = voiceRecordingRowState(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.PROCESSING),
            voiceRewrite = hubModel(),
            nowMs = 5_000L,
        )!!
        processing.phase shouldBe VoiceRecordingPhase.PROCESSING
        processing.status shouldBe VoiceRecordingStatus.PROCESSING
        processing.isCapturing shouldBe false
    }

    test("voice rewrite recording uses the speak-an-edit wording and carries its target scope") {
        val scope = VoiceRewriteScopeLabel(VoiceRewriteTargetScope.WHOLE_FIELD, 184)
        val state = voiceRecordingRowState(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.RECORDING),
            voiceRewrite = voiceRewriteModel(VoiceRewriteSurface.RECORDING, scope = scope),
            nowMs = 1_500L,
        )!!

        state.mode shouldBe VoiceRecordingMode.VOICE_REWRITE
        state.status shouldBe VoiceRecordingStatus.SPEAK_AN_EDIT
        state.targetScope shouldBe scope
    }

    test("the rewrite target label never leaks into an ordinary dictation row") {
        val state = voiceRecordingRowState(
            session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.RECORDING),
            voiceRewrite = voiceRewriteModel(
                VoiceRewriteSurface.RECORDING,
                scope = VoiceRewriteScopeLabel(VoiceRewriteTargetScope.SELECTION, 12),
            ),
            nowMs = 0L,
        )!!

        state.targetScope.shouldBeNull()
    }

    test("voice rewrite keeps the row through transcription and rewriting after the lease ends") {
        val transcribing = voiceRecordingRowState(
            session = null,
            voiceRewrite = voiceRewriteModel(
                VoiceRewriteSurface.PROCESSING,
                statusMessage = VoiceRewriteMessage.UNDERSTANDING_INSTRUCTION,
            ),
            nowMs = 0L,
        )!!
        transcribing.phase shouldBe VoiceRecordingPhase.PROCESSING
        transcribing.status shouldBe VoiceRecordingStatus.UNDERSTANDING_INSTRUCTION

        val rewriting = voiceRecordingRowState(
            session = null,
            voiceRewrite = voiceRewriteModel(
                VoiceRewriteSurface.PROCESSING,
                statusMessage = VoiceRewriteMessage.REWRITING_SELECTED_TEXT,
            ),
            nowMs = 0L,
        )!!
        rewriting.status shouldBe VoiceRecordingStatus.REWRITING
    }

    test("cancellation, disposal and terminal states drop the row for both modes") {
        listOf(
            VoiceRewriteSurface.HUB,
            VoiceRewriteSurface.RESULT,
            VoiceRewriteSurface.RECOVERY,
            VoiceRewriteSurface.SUCCESS,
        ).forEach { surface ->
            voiceRecordingRowState(
                session = null,
                voiceRewrite = voiceRewriteModel(surface),
                nowMs = 0L,
            ).shouldBeNull()
        }
    }

    test("the capped voice instruction shows remaining time only in the last five seconds") {
        fun remainingAt(elapsedMs: Long) = voiceRecordingRowState(
            session = session(AudioSessionOwner.VOICE_REWRITE, AudioSessionPhase.RECORDING),
            voiceRewrite = voiceRewriteModel(VoiceRewriteSurface.RECORDING),
            nowMs = elapsedMs,
        )!!.remainingSeconds

        remainingAt(24_999L).shouldBeNull()
        remainingAt(25_000L) shouldBe 5
        remainingAt(27_500L) shouldBe 3
        remainingAt(30_000L) shouldBe 0
        remainingAt(31_000L) shouldBe 0
    }

    test("ordinary dictation is never shown a deadline it does not enforce") {
        listOf(0L, 25_000L, 31_000L).forEach { elapsedMs ->
            voiceRecordingRowState(
                session = session(AudioSessionOwner.DICTATION, AudioSessionPhase.RECORDING),
                voiceRewrite = hubModel(),
                nowMs = elapsedMs,
            )!!.remainingSeconds.shouldBeNull()
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
