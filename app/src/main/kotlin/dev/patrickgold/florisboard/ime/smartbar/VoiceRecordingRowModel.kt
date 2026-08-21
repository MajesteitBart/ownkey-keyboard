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

import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionOwner
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionPhase
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionState
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteMessage
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteScopeLabel
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteSurface
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteUiModel

/**
 * Pure presentation contract for the shared first-action-row recording surface.
 *
 * Ordinary dictation and voice rewrite render the same row with the same control placement; only
 * the status wording and the rewrite target label differ. Deriving it from one immutable state means
 * the two modes cannot disagree about recording, processing, or error.
 */
enum class VoiceRecordingMode {
    DICTATION,
    VOICE_REWRITE,
}

enum class VoiceRecordingPhase {
    RECORDING,
    PAUSED,
    PROCESSING,
}

enum class VoiceRecordingStatus {
    LISTENING,
    SPEAK_AN_EDIT,
    PAUSED,
    PROCESSING,
    UNDERSTANDING_INSTRUCTION,
    REWRITING,
}

data class VoiceRecordingRowState(
    val mode: VoiceRecordingMode,
    val phase: VoiceRecordingPhase,
    val status: VoiceRecordingStatus,
    val elapsedMs: Long,
    val targetScope: VoiceRewriteScopeLabel? = null,
    /** Non-null once the recording cap is close enough that the remaining time becomes visible. */
    val remainingSeconds: Int? = null,
) {
    val isCapturing: Boolean
        get() = phase == VoiceRecordingPhase.RECORDING || phase == VoiceRecordingPhase.PAUSED
}

data class RecordingRowLayout(
    val clusterWidthDp: Int,
    val controlSizeDp: Int,
    val waveformWidthDp: Int,
    val barCount: Int,
)

/**
 * Width policy approved by the T-001 probe.
 *
 * Interactive controls keep their 48 dp targets at every supported width and the cluster is capped
 * so a tablet or split keyboard centres one control group instead of stranding actions at opposite
 * screen edges. The waveform is the only element that flexes: it reduces its bar count rather than
 * pushing a control off the row or moving back into the stop button.
 */
object RecordingRowLayoutPolicy {
    const val MaxClusterWidthDp = 840
    const val ControlSizeDp = 48
    const val MinBarCount = 6
    const val MaxBarCount = 18

    private const val TrailingActionWidthDp = 53
    private const val TimerWidthDp = 62
    private const val GapsAndDividersDp = 26
    private const val DpPerBar = 8

    fun resolve(availableWidthDp: Int): RecordingRowLayout {
        val clusterWidthDp = availableWidthDp.coerceAtMost(MaxClusterWidthDp).coerceAtLeast(0)
        val waveformWidthDp = (
            clusterWidthDp - TrailingActionWidthDp - TimerWidthDp -
                ControlSizeDp * 2 - GapsAndDividersDp
            ).coerceAtLeast(1)
        val barCount = (waveformWidthDp / DpPerBar).coerceIn(MinBarCount, MaxBarCount)
        return RecordingRowLayout(
            clusterWidthDp = clusterWidthDp,
            controlSizeDp = ControlSizeDp,
            waveformWidthDp = waveformWidthDp,
            barCount = barCount,
        )
    }
}

const val VOICE_RECORDING_MAX_DURATION_MS = 30_000L
const val VOICE_RECORDING_REMAINING_VISIBLE_AFTER_MS = 25_000L

/**
 * Derives the shared row from the single audio session plus the voice-rewrite surface.
 *
 * The active audio session is authoritative while the recorder is held. Voice rewrite keeps the row
 * afterwards for its transcribing and rewriting states so the trailing action stays in its
 * processing treatment instead of snapping back to an idle mic mid-flow.
 */
fun voiceRecordingRowState(
    session: AudioSessionState?,
    voiceRewrite: VoiceRewriteUiModel,
    nowMs: Long,
    maxRecordingDurationMs: Long = VOICE_RECORDING_MAX_DURATION_MS,
    remainingVisibleAfterMs: Long = VOICE_RECORDING_REMAINING_VISIBLE_AFTER_MS,
): VoiceRecordingRowState? {
    if (session != null) {
        val mode = when (session.owner) {
            AudioSessionOwner.DICTATION -> VoiceRecordingMode.DICTATION
            AudioSessionOwner.VOICE_REWRITE -> VoiceRecordingMode.VOICE_REWRITE
        }
        val phase = when (session.phase) {
            AudioSessionPhase.RECORDING -> VoiceRecordingPhase.RECORDING
            AudioSessionPhase.PAUSED -> VoiceRecordingPhase.PAUSED
            AudioSessionPhase.PROCESSING -> VoiceRecordingPhase.PROCESSING
        }
        val elapsedMs = session.elapsedMs(nowMs)
        return VoiceRecordingRowState(
            mode = mode,
            phase = phase,
            status = statusFor(mode, phase),
            elapsedMs = elapsedMs,
            targetScope = voiceRewrite.scopeLabel.takeIf { mode == VoiceRecordingMode.VOICE_REWRITE },
            remainingSeconds = remainingSeconds(
                mode = mode,
                phase = phase,
                elapsedMs = elapsedMs,
                maxRecordingDurationMs = maxRecordingDurationMs,
                remainingVisibleAfterMs = remainingVisibleAfterMs,
            ),
        )
    }
    if (voiceRewrite.surface != VoiceRewriteSurface.PROCESSING) return null
    return VoiceRecordingRowState(
        mode = VoiceRecordingMode.VOICE_REWRITE,
        phase = VoiceRecordingPhase.PROCESSING,
        status = when (voiceRewrite.statusMessage) {
            VoiceRewriteMessage.REWRITING_SELECTED_TEXT -> VoiceRecordingStatus.REWRITING
            else -> VoiceRecordingStatus.UNDERSTANDING_INSTRUCTION
        },
        elapsedMs = 0L,
        targetScope = voiceRewrite.scopeLabel,
    )
}

private fun statusFor(mode: VoiceRecordingMode, phase: VoiceRecordingPhase): VoiceRecordingStatus =
    when (phase) {
        VoiceRecordingPhase.RECORDING -> when (mode) {
            VoiceRecordingMode.DICTATION -> VoiceRecordingStatus.LISTENING
            VoiceRecordingMode.VOICE_REWRITE -> VoiceRecordingStatus.SPEAK_AN_EDIT
        }
        VoiceRecordingPhase.PAUSED -> VoiceRecordingStatus.PAUSED
        VoiceRecordingPhase.PROCESSING -> when (mode) {
            VoiceRecordingMode.DICTATION -> VoiceRecordingStatus.PROCESSING
            VoiceRecordingMode.VOICE_REWRITE -> VoiceRecordingStatus.UNDERSTANDING_INSTRUCTION
        }
    }

/**
 * Only the voice instruction is capped, so only it counts down. Ordinary dictation keeps its
 * existing open-ended recording behaviour and must not be shown a deadline it does not enforce.
 */
private fun remainingSeconds(
    mode: VoiceRecordingMode,
    phase: VoiceRecordingPhase,
    elapsedMs: Long,
    maxRecordingDurationMs: Long,
    remainingVisibleAfterMs: Long,
): Int? {
    if (mode != VoiceRecordingMode.VOICE_REWRITE) return null
    if (phase !in setOf(VoiceRecordingPhase.RECORDING, VoiceRecordingPhase.PAUSED)) return null
    if (elapsedMs < remainingVisibleAfterMs) return null
    val remainingMs = (maxRecordingDurationMs - elapsedMs).coerceAtLeast(0L)
    return ((remainingMs + 999L) / 1_000L).toInt()
}
