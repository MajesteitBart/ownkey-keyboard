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

/**
 * Pure presentation contract for the smartbar recording row.
 *
 * Ordinary dictation is the row's only owner. Voice rewrite presents its own recording, pause, and
 * processing inside the rewrite panel, so the row never has to decide which mode a control belongs
 * to and rewrite controls can never be dispatched to dictation.
 */
enum class VoiceRecordingPhase {
    RECORDING,
    PAUSED,
    PROCESSING,
}

enum class VoiceRecordingStatus {
    LISTENING,
    PAUSED,
    PROCESSING,
}

data class VoiceRecordingRowState(
    val phase: VoiceRecordingPhase,
    val status: VoiceRecordingStatus,
    val elapsedMs: Long,
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

/**
 * Derives the dictation row from the single audio session.
 *
 * A session held by voice rewrite yields no row: that session is presented by the rewrite panel,
 * which is always open while it runs. The row therefore appears only for ordinary dictation and
 * disappears the moment its lease ends, leaving the mic key's own transient success or error
 * treatment as the single follow-up signal.
 */
fun voiceRecordingRowState(
    session: AudioSessionState?,
    nowMs: Long,
): VoiceRecordingRowState? {
    if (session == null || session.owner != AudioSessionOwner.DICTATION) return null
    val phase = when (session.phase) {
        AudioSessionPhase.RECORDING -> VoiceRecordingPhase.RECORDING
        AudioSessionPhase.PAUSED -> VoiceRecordingPhase.PAUSED
        AudioSessionPhase.PROCESSING -> VoiceRecordingPhase.PROCESSING
    }
    return VoiceRecordingRowState(
        phase = phase,
        status = when (phase) {
            VoiceRecordingPhase.RECORDING -> VoiceRecordingStatus.LISTENING
            VoiceRecordingPhase.PAUSED -> VoiceRecordingStatus.PAUSED
            VoiceRecordingPhase.PROCESSING -> VoiceRecordingStatus.PROCESSING
        },
        elapsedMs = session.elapsedMs(nowMs),
    )
}
