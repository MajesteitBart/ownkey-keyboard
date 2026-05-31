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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralDictationManager
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun DictationRecordingBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voxtralDictationManager by context.voxtralDictationManager()
    val dictationState by voxtralDictationManager.stateFlow.collectAsState()
    val recordingSession by voxtralDictationManager.recordingSessionFlow.collectAsState()
    val audioLevel by voxtralDictationManager.audioLevelFlow.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isPaused = dictationState == VoxtralDictationManager.DictationState.PAUSED
    val isTranscribing = dictationState == VoxtralDictationManager.DictationState.TRANSCRIBING
    val elapsedText = recordingSession?.elapsedMs(nowMs)?.formatElapsedMs() ?: "00:00"

    LaunchedEffect(recordingSession, dictationState) {
        while (recordingSession != null && !isTranscribing) {
            nowMs = System.currentTimeMillis()
            delay(250)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight * 2)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val compact = maxWidth < 380.dp
        val panelShape = RoundedCornerShape(18.dp)
        val secondaryButtonSize = if (compact) 34.dp else 38.dp
        val primaryButtonSize = if (compact) 42.dp else 46.dp
        val statusWidth = if (compact) 116.dp else 134.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OwnkeyBrand.PanelRaised.copy(alpha = 0.96f),
                            OwnkeyBrand.Action.copy(alpha = 0.97f),
                        ),
                    ),
                )
                .border(1.dp, OwnkeyBrand.Bone.copy(alpha = 0.07f), panelShape)
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = 10.dp),
        ) {
            if (isTranscribing) {
                TranscribingContent(modifier = Modifier.align(Alignment.Center))
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    DictationStatusRow(
                        isPaused = isPaused,
                        isTranscribing = false,
                        elapsedText = elapsedText,
                        modifier = Modifier.width(statusWidth),
                    )
                    AudioLevelWaveform(
                        level = audioLevel,
                        paused = isPaused,
                        modifier = Modifier
                            .weight(1f)
                            .height(if (compact) 30.dp else 34.dp),
                    )
                    RecordingIconButton(
                        size = secondaryButtonSize,
                        iconSize = 19.dp,
                        onClick = { voxtralDictationManager.togglePauseResume() },
                        contentDescription = if (isPaused) "Resume dictation" else "Pause dictation",
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    RecordingIconButton(
                        size = secondaryButtonSize,
                        iconSize = 18.dp,
                        onClick = { voxtralDictationManager.cancelDictation() },
                        contentDescription = "Cancel dictation",
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFE2E2E5),
                        )
                    }
                    FinishDictationButton(
                        size = primaryButtonSize,
                        compact = compact,
                        onClick = { voxtralDictationManager.stopAndInsertTranscript() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictationStatusRow(
    isPaused: Boolean,
    isTranscribing: Boolean,
    elapsedText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isTranscribing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                    trackColor = OwnkeyBrand.Bone.copy(alpha = 0.15f),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isPaused) OwnkeyBrand.WarningYellow else OwnkeyBrand.SignalOrange,
                )
            }
        }

        Text(
            text = when {
                isTranscribing -> "Transcribing"
                isPaused -> "Paused"
                else -> "Listening"
            },
            color = OwnkeyBrand.Bone,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            modifier = Modifier
                .size(3.dp)
                .background(OwnkeyBrand.Bone.copy(alpha = 0.34f), CircleShape),
        )
        Text(
            text = elapsedText,
            color = OwnkeyBrand.Ash,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TranscribingContent(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.5.dp,
            color = OwnkeyBrand.SignalOrange,
            trackColor = OwnkeyBrand.Bone.copy(alpha = 0.14f),
        )
        Text(
            text = "Preparing transcript",
            color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

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
            .size(size)
            .clip(CircleShape)
            .background(OwnkeyBrand.Bone.copy(alpha = 0.075f))
            .border(1.dp, OwnkeyBrand.Bone.copy(alpha = 0.08f), CircleShape)
            .clickable(
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun FinishDictationButton(
    size: Dp,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFA51C),
                        OwnkeyBrand.SignalOrange,
                    ),
                ),
            )
            .border(1.dp, OwnkeyBrand.SignalAmber.copy(alpha = 0.34f), CircleShape)
            .clickable(
                onClickLabel = "Stop and transcribe",
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WaveformGlyph(compact = compact)
    }
}

@Composable
private fun AudioLevelWaveform(
    level: Float,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayedLevel by animateFloatAsState(
        targetValue = if (paused) 0f else level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "dictationAudioLevel",
    )

    Canvas(
        modifier = modifier
            .alpha(if (paused) 0.34f else 1f),
    ) {
        val color = OwnkeyBrand.SignalOrange
        val stroke = 3.8.dp.toPx()
        val centerY = size.height / 2f
        val bars = 14
        val gap = size.width / (bars + 1)
        val levelScale = sqrt(displayedLevel.coerceIn(0f, 1f))
        val profile = floatArrayOf(
            0.34f, 0.55f, 0.72f, 0.95f, 0.66f, 0.48f, 0.82f,
            1.00f, 0.58f, 0.46f, 0.70f, 0.88f, 0.62f, 0.40f,
        )
        repeat(bars) { index ->
            val x = gap * (index + 1)
            val barScale = 0.14f + levelScale * 0.86f * profile[index]
            val halfHeight = (size.height * barScale / 2f).coerceAtLeast(stroke)
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
private fun WaveformGlyph(compact: Boolean) {
    Canvas(
        modifier = Modifier
            .width(if (compact) 22.dp else 26.dp)
            .height(if (compact) 17.dp else 19.dp),
    ) {
        val stroke = 2.4.dp.toPx()
        val color = Color.White
        val centerY = size.height / 2f
        val bars = floatArrayOf(0.32f, 0.72f, 1f, 0.56f, 0.82f)
        val gap = size.width / (bars.size + 1)
        bars.forEachIndexed { index, scale ->
            val x = gap * (index + 1)
            val halfHeight = size.height * scale / 2f
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

private fun Long.formatElapsedMs(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
