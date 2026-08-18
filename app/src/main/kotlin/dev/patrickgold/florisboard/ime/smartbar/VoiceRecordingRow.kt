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
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteTargetScope
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes
import java.util.Locale

/** Callbacks the shared row needs; the smartbar binds them to the mode that owns the recorder. */
interface VoiceRecordingRowActions {
    fun onPauseOrResume()
    fun onCancel()
    fun onStop()
}

/**
 * Center content of the shared recording row.
 *
 * From leading to trailing edge it carries the active dot and elapsed timer, the flexible centred
 * measured-amplitude waveform, then pause/resume and cancel. The waveform owns audio feedback; the
 * trailing stop action lives in the sticky slot and never renders bars.
 */
@Composable
fun VoiceRecordingRowContent(
    state: VoiceRecordingRowState,
    levels: AudioLevelHistoryState,
    actions: VoiceRecordingRowActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val layout = RecordingRowLayoutPolicy.resolve(availableWidth.value.toInt())
        val compact = availableWidth < 320.dp

        if (state.phase == VoiceRecordingPhase.PROCESSING) {
            // The meter is replaced by labelled progress, and cancel stays available in the row
            // because the trailing action is no longer a primary control while processing.
            ProcessingStatus(
                state = state,
                actions = actions,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = RecordingRowLayoutPolicy.MaxClusterWidthDp.dp)
                    .align(Alignment.Center),
            )
            return@BoxWithConstraints
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = RecordingRowLayoutPolicy.MaxClusterWidthDp.dp)
                .align(Alignment.Center)
                .padding(horizontal = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        ) {
            // The status and target scope are announced once per state change. They are never
            // merged into the timer, whose description changes on every tick.
            VoiceRecordingStatusAnnouncement(state)
            RecordingElapsed(
                state = state,
                modifier = Modifier.width(if (compact) 56.dp else 62.dp),
            )
            SmartbarDivider()
            if (availableWidth >= 420.dp) {
                VoiceRecordingRowStatus(
                    state = state,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                SmartbarDivider()
            }
            MeasuredLevelWaveform(
                levels = levels.levels,
                barCount = layout.barCount,
                paused = state.phase == VoiceRecordingPhase.PAUSED,
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 24.dp else 28.dp),
            )
            SmartbarDivider()
            RecordingIconButton(
                size = RecordingRowLayoutPolicy.ControlSizeDp.dp,
                iconSize = 20.dp,
                onClick = actions::onPauseOrResume,
                contentDescription = if (state.phase == VoiceRecordingPhase.PAUSED) {
                    state.resumeContentDescription()
                } else {
                    state.pauseContentDescription()
                },
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
                contentDescription = state.cancelContentDescription(),
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
 * Trailing action in the sticky dictation-key slot. While recording or paused it is an orange
 * `Stop recording` button with a solid square; during processing it shows the existing progress
 * treatment and stops being a primary action, because cancel lives in the row instead.
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
            Box(
                modifier = Modifier.size(buttonSize),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(buttonSize * 0.52f),
                    strokeWidth = 2.5.dp,
                    color = OwnkeyBrand.SignalOrange,
                    trackColor = OwnkeyBrand.Bone.copy(alpha = 0.08f),
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
                contentDescription = state.stopContentDescription(),
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
    val elapsedText = state.remainingSeconds
        ?.let { remaining -> "-${remaining}s" }
        ?: state.elapsedMs.formatElapsedMs()
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
            color = if (state.remainingSeconds != null) OwnkeyBrand.WarningYellow else OwnkeyBrand.Ash,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProcessingStatus(
    state: VoiceRecordingRowState,
    actions: VoiceRecordingRowActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
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
        RecordingIconButton(
            size = RecordingRowLayoutPolicy.ControlSizeDp.dp,
            iconSize = 19.dp,
            onClick = actions::onCancel,
            contentDescription = state.cancelContentDescription(),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = OwnkeyBrand.Bone.copy(alpha = 0.9f),
            )
        }
    }
}

/**
 * Visible status text and target scope for the active row. Shown only where the row has room for it
 * without pushing an interactive control below its 48 dp target.
 */
@Composable
private fun VoiceRecordingRowStatus(
    state: VoiceRecordingRowState,
    modifier: Modifier = Modifier,
) {
    Text(
        text = state.statusAndScopeText(),
        modifier = modifier.clearAndSetSemantics { },
        color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Screen-reader equivalent of the waveform, which itself stays out of the accessibility tree.
 *
 * It carries the mode status and rewrite target scope as a polite live region so every state change
 * is announced exactly once, while elapsed time and per-sample level updates stay silent.
 */
@Composable
private fun VoiceRecordingStatusAnnouncement(state: VoiceRecordingRowState) {
    val announcement = state.statusAndScopeText()
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
private fun VoiceRecordingRowState.statusAndScopeText(): String {
    val statusText = status.label()
    val scopeText = targetScope?.let { scope ->
        when (scope.scope) {
            VoiceRewriteTargetScope.SELECTION -> pluralsRes(
                R.plurals.voice_rewrite__scope_selection,
                scope.characterCount,
                "count" to scope.characterCount,
            )
            VoiceRewriteTargetScope.WHOLE_FIELD -> pluralsRes(
                R.plurals.voice_rewrite__scope_whole_field,
                scope.characterCount,
                "count" to scope.characterCount,
            )
        }
    }
    return if (scopeText == null) statusText else "$statusText · $scopeText"
}

@Composable
private fun VoiceRecordingStatus.label(): String = stringRes(
    when (this) {
        VoiceRecordingStatus.LISTENING -> R.string.voice_rewrite__state_listening
        VoiceRecordingStatus.SPEAK_AN_EDIT -> R.string.voice_rewrite__state_speak_an_edit
        VoiceRecordingStatus.PAUSED -> R.string.voice_rewrite__state_paused
        VoiceRecordingStatus.PROCESSING -> R.string.voice_recording__processing
        VoiceRecordingStatus.UNDERSTANDING_INSTRUCTION -> R.string.voice_rewrite__state_understanding
        VoiceRecordingStatus.REWRITING -> R.string.voice_rewrite__state_rewriting
    },
)

@Composable
private fun VoiceRecordingRowState.pauseContentDescription(): String = stringRes(
    if (mode == VoiceRecordingMode.VOICE_REWRITE) {
        R.string.voice_recording__pause_rewrite
    } else {
        R.string.voice_recording__pause_dictation
    },
)

@Composable
private fun VoiceRecordingRowState.resumeContentDescription(): String = stringRes(
    if (mode == VoiceRecordingMode.VOICE_REWRITE) {
        R.string.voice_recording__resume_rewrite
    } else {
        R.string.voice_recording__resume_dictation
    },
)

@Composable
private fun VoiceRecordingRowState.cancelContentDescription(): String = stringRes(
    if (mode == VoiceRecordingMode.VOICE_REWRITE) {
        R.string.voice_recording__cancel_rewrite
    } else {
        R.string.voice_recording__cancel_dictation
    },
)

@Composable
private fun VoiceRecordingRowState.stopContentDescription(): String = stringRes(
    if (mode == VoiceRecordingMode.VOICE_REWRITE) {
        R.string.voice_recording__stop_rewrite
    } else {
        R.string.voice_recording__stop_dictation
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

/**
 * Draws the bounded recent-level history produced by the shared reducer.
 *
 * Every bar height is a measured sample; there is no clock-driven animation, no interpolation and
 * no travelling motion, so reduced motion needs no separate rendering path and silence settles at
 * the reducer's baseline. The meter is excluded from the accessibility tree because the row's
 * status text already carries the equivalent information without per-sample chatter.
 */
@Composable
private fun MeasuredLevelWaveform(
    levels: List<Float>,
    barCount: Int,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .alpha(if (paused) 0.34f else 1f)
            .clearAndSetSemantics { },
    ) {
        if (levels.isEmpty() || barCount <= 0) return@Canvas
        val color = OwnkeyBrand.SignalOrange
        val stroke = 3.4.dp.toPx()
        val centerY = size.height / 2f
        val gap = size.width / (barCount + 1)
        // Compact widths show fewer bars by dropping the oldest samples, never by faking a profile.
        val visible = levels.takeLast(barCount)
        visible.forEachIndexed { index, level ->
            val x = gap * (index + 1)
            val halfHeight = (size.height * level.coerceIn(0f, 1f) / 2f).coerceAtLeast(stroke)
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

private fun Long.formatElapsedMs(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
