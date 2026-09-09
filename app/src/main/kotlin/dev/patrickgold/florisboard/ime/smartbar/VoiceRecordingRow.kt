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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButtonAspectRatio
import dev.patrickgold.florisboard.ime.text.dictation.AudioLevelHistoryState
import dev.patrickgold.florisboard.ime.text.dictation.StationaryLevelBars
import org.florisboard.lib.compose.stringRes
import java.util.Locale

/** Callbacks for the dictation row; the smartbar binds them to the dictation manager. */
interface VoiceRecordingRowActions {
    fun onPauseOrResume()
    fun onCancel()
    fun onStop()
}

/**
 * Center content of the dictation recording row.
 *
 * From leading to trailing edge it carries the active dot and elapsed timer, the flexible centred
 * measured-amplitude waveform, then pause/resume and cancel. While transcribing it shows the single
 * processing indicator with its label; cancel then lives in the trailing action slot, so the row
 * never shows two spinners or two cancel controls.
 */
@Composable
fun VoiceRecordingRowContent(
    state: VoiceRecordingRowState,
    levels: State<AudioLevelHistoryState>,
    actions: VoiceRecordingRowActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val layout = RecordingRowLayoutPolicy.resolve(availableWidth.value.toInt())
        val compact = availableWidth < 320.dp

        if (state.phase == VoiceRecordingPhase.PROCESSING) {
            ProcessingStatus(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = RecordingRowLayoutPolicy.MaxClusterWidthDp.dp)
                    .align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        // The waveform has a fixed width, so the cluster is centred as a whole instead of letting
        // the meter stretch across whatever a wide row leaves over.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = RecordingRowLayoutPolicy.MaxClusterWidthDp.dp)
                .align(Alignment.Center)
                .padding(horizontal = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = if (compact) 4.dp else 8.dp,
                alignment = Alignment.CenterHorizontally,
            ),
        ) {
            // The status is announced once per state change. It is never merged into the timer,
            // whose description changes on every tick.
            VoiceRecordingStatusAnnouncement(state)
            RecordingElapsed(
                state = state,
                modifier = Modifier.width(if (compact) 56.dp else 62.dp),
            )
            SmartbarDivider()
            if (availableWidth >= 420.dp) {
                Text(
                    text = state.status.label(),
                    modifier = Modifier
                        // The label may use only the space left after the fixed meter and controls.
                        .weight(1f, fill = false)
                        .widthIn(max = 220.dp)
                        .clearAndSetSemantics { },
                    color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SmartbarDivider()
            }
            MeasuredLevelWaveform(
                levels = levels,
                barCount = layout.barCount,
                paused = state.phase == VoiceRecordingPhase.PAUSED,
                modifier = Modifier
                    .width(layout.waveformWidthDp.dp)
                    .height(if (compact) 28.dp else 32.dp),
            )
            SmartbarDivider()
            RecordingIconButton(
                size = RecordingRowLayoutPolicy.ControlSizeDp.dp,
                iconSize = 20.dp,
                onClick = actions::onPauseOrResume,
                contentDescription = stringRes(
                    if (state.phase == VoiceRecordingPhase.PAUSED) {
                        R.string.voice_recording__resume_dictation
                    } else {
                        R.string.voice_recording__pause_dictation
                    },
                ),
            ) {
                Icon(
                    imageVector = if (state.phase == VoiceRecordingPhase.PAUSED) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    },
                    contentDescription = null,
                    tint = OwnkeyBrand.Bone,
                )
            }
            RecordingIconButton(
                size = RecordingRowLayoutPolicy.ControlSizeDp.dp,
                iconSize = 19.dp,
                onClick = actions::onCancel,
                contentDescription = stringRes(R.string.voice_recording__cancel_dictation),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OwnkeyBrand.Bone.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Trailing action in the dictation-key slot. While recording or paused it is the orange
 * `Stop and transcribe` button with a solid square. While transcribing the slot holds the neutral
 * cancel action: the row already carries the one processing spinner, so the slot must not add a
 * second one.
 */
@Composable
fun VoiceRecordingStickyAction(
    state: VoiceRecordingRowState,
    actions: VoiceRecordingRowActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(FlorisImeSizing.smartbarHeight * QuickActionButtonAspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        val buttonSize = (maxHeight - 8.dp).coerceIn(
            RecordingRowLayoutPolicy.ControlSizeDp.dp - 8.dp,
            56.dp,
        )
        if (state.phase == VoiceRecordingPhase.PROCESSING) {
            CircleControlButton(
                size = buttonSize,
                iconSize = 20.dp,
                background = OwnkeyBrand.Glass.Key,
                border = OwnkeyBrand.Bone.copy(alpha = 0.12f),
                onClick = actions::onCancel,
                contentDescription = stringRes(R.string.voice_recording__cancel_dictation),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OwnkeyBrand.Bone.copy(alpha = 0.9f),
                )
            }
        } else {
            CircleControlButton(
                size = buttonSize,
                iconSize = 22.dp,
                background = if (state.phase == VoiceRecordingPhase.PAUSED) {
                    OwnkeyBrand.SignalOrange.copy(alpha = 0.55f)
                } else {
                    OwnkeyBrand.SignalOrange
                },
                border = OwnkeyBrand.SignalAmber.copy(alpha = 0.34f),
                onClick = actions::onStop,
                contentDescription = stringRes(R.string.voice_recording__stop_dictation),
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RecordingElapsed(
    state: VoiceRecordingRowState,
    modifier: Modifier = Modifier,
) {
    val paused = state.phase == VoiceRecordingPhase.PAUSED
    val elapsedText = formatRecordingElapsed(state.elapsedMs)
    val elapsedDescription = stringRes(R.string.voice_recording__elapsed_label, "time" to elapsedText)
    Row(
        modifier = modifier.semantics {
            contentDescription = elapsedDescription
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(
                    color = if (paused) OwnkeyBrand.WarningYellow else OwnkeyBrand.SignalOrange,
                    shape = CircleShape,
                ),
        )
        Text(
            text = elapsedText,
            color = OwnkeyBrand.Ash,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** The single processing indicator for dictation: one spinner, one label, no duplicate control. */
@Composable
private fun ProcessingStatus(
    state: VoiceRecordingRowState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.5.dp,
            color = OwnkeyBrand.SignalOrange,
            trackColor = OwnkeyBrand.Bone.copy(alpha = 0.08f),
        )
        Text(
            text = state.status.label(),
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Screen-reader equivalent of the waveform, which itself stays out of the accessibility tree.
 *
 * It carries the dictation status as a polite live region so every state change is announced
 * exactly once, while elapsed time and per-sample level updates stay silent.
 */
@Composable
private fun VoiceRecordingStatusAnnouncement(state: VoiceRecordingRowState) {
    val announcement = state.status.label()
    Box(
        modifier = Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
    )
}

@Composable
private fun VoiceRecordingStatus.label(): String = stringRes(
    when (this) {
        VoiceRecordingStatus.LISTENING -> R.string.voice_rewrite__state_listening
        VoiceRecordingStatus.PAUSED -> R.string.voice_rewrite__state_paused
        VoiceRecordingStatus.PROCESSING -> R.string.voice_recording__processing
    },
)

@Composable
private fun RecordingIconButton(
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = size, minHeight = size)
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun CircleControlButton(
    size: Dp,
    iconSize: Dp,
    background: Color,
    border: Color,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = RecordingRowLayoutPolicy.ControlSizeDp.dp, minHeight = RecordingRowLayoutPolicy.ControlSizeDp.dp)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable(
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** Tallest bar the meter draws, so a tall host such as the rewrite sheet does not stretch it. */
private val MaxWaveformBarHeight = 40.dp

/**
 * Draws the Ownkey waveform mark breathing with the measured level history.
 *
 * The bars are stationary: [StationaryLevelBars] gives each slot a rest height from the mark and
 * a fixed lag into the reducer's history, so every bar grows and shrinks in place with a real
 * recent sample and nothing scrolls. There is no clock-driven animation and no interpolation; the
 * canvas reads [levels] only in its draw pass, so a published sample redraws this canvas and
 * recomposes nothing, and when the sampler is silent (idle, paused, processing) nothing here runs.
 * Reduced motion therefore needs no separate rendering path. Bars are mirrored around the centre
 * line on a fixed [barPitch] grid and the group is centred in the canvas, so the mark keeps its
 * proportions whether it sits in the smartbar row or the rewrite sheet, and a wide canvas never
 * spreads it thin. The meter is excluded from the accessibility tree because the owning surface's
 * status text already carries the equivalent information without per-sample chatter.
 *
 * Shared by the dictation row and the rewrite panel's recording body so both modes show the same
 * truthful meter without a second recorder or sampler.
 */
@Composable
fun MeasuredLevelWaveform(
    levels: State<AudioLevelHistoryState>,
    barCount: Int,
    paused: Boolean,
    modifier: Modifier = Modifier,
    color: Color = OwnkeyBrand.SignalOrange,
    barWidth: Dp = RecordingRowLayoutPolicy.BarWidthDp.dp,
    barPitch: Dp = RecordingRowLayoutPolicy.BarPitchDp.dp,
) {
    Canvas(
        modifier = modifier
            .alpha(if (paused) 0.34f else 1f)
            .clearAndSetSemantics { },
    ) {
        if (barCount <= 0) return@Canvas
        val history = levels.value
        val heights = StationaryLevelBars.heights(history.levels, barCount, history.baseline)
        val stroke = barWidth.toPx()
        val pitch = barPitch.toPx()
        val centerY = size.height / 2f
        // Round caps add half a stroke at each end, so a full-height bar still fits the canvas.
        val fullHalfHeight = ((minOf(size.height, MaxWaveformBarHeight.toPx()) - stroke) / 2f)
            .coerceAtLeast(stroke * 0.25f)
        val stubHalfHeight = (stroke * 0.25f).coerceAtMost(fullHalfHeight)
        val firstX = (size.width - heights.size * pitch) / 2f + pitch / 2f
        heights.forEachIndexed { index, height ->
            val x = firstX + index * pitch
            val halfHeight = (fullHalfHeight * height).coerceIn(stubHalfHeight, fullHalfHeight)
            drawLine(
                color = color,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SmartbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight(0.52f)
            .background(OwnkeyBrand.Bone.copy(alpha = 0.06f)),
    )
}

/** `mm:ss` for a recording timer; shared by the dictation row and the rewrite panel. */
fun formatRecordingElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
