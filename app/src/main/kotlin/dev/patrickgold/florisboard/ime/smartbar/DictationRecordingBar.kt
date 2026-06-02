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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButtonAspectRatio
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralDictationManager
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sqrt

@Composable
fun DictationRecordingBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DictationSmartbarContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        DictationSmartbarAction()
    }
}

@Composable
fun DictationSmartbarContent(
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
        modifier = modifier.fillMaxSize(),
    ) {
        val compact = maxWidth < 270.dp
        if (isTranscribing) {
            ProcessingStatus(
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 2.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            ) {
                RecordingElapsed(
                    elapsedText = elapsedText,
                    paused = isPaused,
                    modifier = Modifier.width(if (compact) 62.dp else 76.dp),
                )
                SmartbarDivider()
                AudioLevelWaveform(
                    level = audioLevel,
                    paused = isPaused,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compact) 24.dp else 28.dp),
                )
                SmartbarDivider()
                RecordingIconButton(
                    size = if (compact) 30.dp else 34.dp,
                    iconSize = 19.dp,
                    onClick = { voxtralDictationManager.togglePauseResume() },
                    contentDescription = if (isPaused) "Resume dictation" else "Pause dictation",
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = OwnkeyBrand.Bone,
                    )
                }
                RecordingIconButton(
                    size = if (compact) 30.dp else 34.dp,
                    iconSize = 18.dp,
                    onClick = { voxtralDictationManager.cancelDictation() },
                    contentDescription = "Cancel dictation",
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = OwnkeyBrand.Bone.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
fun DictationSmartbarAction(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val voxtralDictationManager by context.voxtralDictationManager()
    val dictationState by voxtralDictationManager.stateFlow.collectAsState()
    val isTranscribing = dictationState == VoxtralDictationManager.DictationState.TRANSCRIBING

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(FlorisImeSizing.smartbarHeight * QuickActionButtonAspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        val buttonSize = (maxHeight - 8.dp).coerceAtLeast(36.dp)
        if (isTranscribing) {
            CircleControlButton(
                size = buttonSize,
                iconSize = 22.dp,
                background = OwnkeyBrand.Action,
                border = OwnkeyBrand.Bone.copy(alpha = 0.07f),
                onClick = { voxtralDictationManager.cancelDictation() },
                contentDescription = "Cancel dictation",
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = OwnkeyBrand.Bone,
                )
            }
        } else {
            CircleControlButton(
                size = buttonSize,
                iconSize = 22.dp,
                background = OwnkeyBrand.SignalOrange,
                border = OwnkeyBrand.SignalAmber.copy(alpha = 0.34f),
                onClick = { voxtralDictationManager.stopAndInsertTranscript() },
                contentDescription = "Stop and transcribe",
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
    elapsedText: String,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun ProcessingStatus(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.5.dp,
            color = OwnkeyBrand.SignalOrange,
            trackColor = OwnkeyBrand.Bone.copy(alpha = 0.08f),
        )
        Text(
            text = "Processing",
            color = OwnkeyBrand.Bone.copy(alpha = 0.86f),
            fontSize = 14.sp,
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
            .background(Color.Transparent)
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
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
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
        val stroke = 3.4.dp.toPx()
        val centerY = size.height / 2f
        val bars = 18
        val gap = size.width / (bars + 1)
        val levelScale = sqrt(displayedLevel.coerceIn(0f, 1f))
        val profile = floatArrayOf(
            0.34f, 0.55f, 0.72f, 0.95f, 0.66f, 0.48f, 0.82f, 1.00f, 0.58f,
            0.46f, 0.70f, 0.88f, 0.62f, 0.40f, 0.76f, 0.52f, 0.92f, 0.60f,
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
