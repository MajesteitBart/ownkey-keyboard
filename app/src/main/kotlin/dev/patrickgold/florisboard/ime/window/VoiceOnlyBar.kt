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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.audioLevelHistorySampler
import dev.patrickgold.florisboard.audioSessionCoordinator
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.smartbar.MeasuredLevelWaveform
import dev.patrickgold.florisboard.ime.smartbar.VoiceRecordingPhase
import dev.patrickgold.florisboard.ime.smartbar.label
import dev.patrickgold.florisboard.ime.smartbar.voiceRecordingRowState
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackPhase
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * If the voice-only bar may replace the keyboard for an editor. Secure and incognito fields never take
 * dictation, and numeric, phone, and date fields are faster to fill in with their own keypad.
 */
fun voiceOnlyAllowed(type: InputAttributes.Type, isSecureField: Boolean, isIncognito: Boolean): Boolean =
    !isSecureField && !isIncognito && type !in VoiceOnlyKeypadTypes

private val VoiceOnlyKeypadTypes = setOf(
    InputAttributes.Type.NUMBER,
    InputAttributes.Type.PHONE,
    InputAttributes.Type.DATETIME,
)

private val BarHeight = 56.dp
private val BarBottomMargin = 16.dp
private val BarEdgeMargin = 8.dp
private val MicSize = 44.dp
private val KeyboardButtonSize = 40.dp
private val StatusWidth = 136.dp

/**
 * Keeps a drag offset inside the area: the bar may move anywhere above its default bottom-center spot,
 * but never below it or past an edge. All values are in pixels; the offset is relative to that spot.
 */
internal fun clampVoiceBarOffset(offset: Offset, area: IntSize, bar: IntSize, edge: Float, bottom: Float): Offset {
    val baseX = (area.width - bar.width) / 2f
    val baseY = area.height - bar.height - bottom
    val minX = edge - baseX
    val maxX = area.width - bar.width - edge - baseX
    val minY = edge - baseY
    return Offset(
        x = offset.x.coerceIn(min(minX, maxX), max(minX, maxX)),
        y = offset.y.coerceIn(min(minY, 0f), 0f),
    )
}

/**
 * The voice-only keyboard: one bar with the dictation button, the live level and status, and a button
 * back to the full keyboard. It reports only its own bounds as the window, so every touch outside the
 * bar reaches the app, and the app is not resized. Drag the bar to move it off app controls.
 */
@Composable
fun BoxScope.VoiceOnlyWindow() {
    val density = LocalDensity.current
    val view = LocalView.current
    val windowController = LocalWindowController.current
    val windowConfig by windowController.activeWindowConfig.collectAsState()
    val windowInsets by windowController.activeWindowInsets.collectAsState()

    LaunchedEffect(windowInsets) {
        // The bar moves inside a full-size view, so ask the IME service to recompute the touch region.
        view.requestLayout()
    }

    val stored = windowConfig.voiceBarOffset
    var dragOffset by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(stored) {
        dragOffset = null
    }
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .matchParentSize()
            .safeDrawingPadding(),
    ) {
        val area = IntSize(constraints.maxWidth, constraints.maxHeight)
        val edgePx = with(density) { BarEdgeMargin.toPx() }
        val bottomPx = with(density) { BarBottomMargin.toPx() }
        val clamp = rememberUpdatedState { offset: Offset ->
            clampVoiceBarOffset(offset, area, barSize, edgePx, bottomPx)
        }
        val offset = clamp.value(dragOffset ?: Offset(stored.x * density.density, stored.y * density.density))
        val currentOffset = rememberUpdatedState(offset)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = ((area.width - barSize.width) / 2f + offset.x).roundToInt(),
                        y = (area.height - barSize.height - bottomPx + offset.y).roundToInt(),
                    )
                }
                // Hidden for the one frame before its size is known, so it never flashes off-center.
                .graphicsLayer { alpha = if (barSize == IntSize.Zero) 0f else 1f }
                .onSizeChanged { barSize = it }
                .onGloballyPositioned { coords ->
                    val boundsPx = coords.boundsInRoot().roundToIntRect()
                    windowController.updateWindowInsets(with(density) { ImeInsets.Window.of(boundsPx) })
                }
                .pointerInput(Unit) {
                    var current = Offset.Zero
                    val save = {
                        windowController.actions.moveVoiceBar(
                            ImeWindowConfig.VoiceBarOffset(x = current.x / density.density, y = current.y / density.density),
                        )
                    }
                    detectDragGestures(
                        onDragStart = { current = currentOffset.value },
                        onDrag = { change, amount ->
                            change.consume()
                            current = clamp.value(current + amount)
                            dragOffset = current
                        },
                        onDragEnd = save,
                        onDragCancel = save,
                    )
                },
        ) {
            VoiceOnlyBar()
        }
    }
}

@Composable
private fun VoiceOnlyBar() {
    val context = LocalContext.current
    val inputFeedbackController = LocalInputFeedbackController.current
    val windowController = LocalWindowController.current
    val audioSessionCoordinator by context.audioSessionCoordinator()
    val audioLevelHistorySampler by context.audioLevelHistorySampler()
    val dictationManager by context.voxtralDictationManager()

    // The session publishes every level sample; the bar shows no timer, so it only follows phase changes.
    val recordingState by remember(audioSessionCoordinator) {
        audioSessionCoordinator.state.map { voiceRecordingRowState(it, nowMs = 0L) }.distinctUntilChanged()
    }.collectAsState(initial = voiceRecordingRowState(audioSessionCoordinator.state.value, nowMs = 0L))
    val recording = recordingState
    val feedback by dictationManager.feedbackStateFlow.collectAsState()
    // Read only in the waveform's draw pass, so level samples never recompose the bar.
    val levels = audioLevelHistorySampler.state.collectAsState()
    val phase = recording?.phase

    val windowTheme = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val keyTheme = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val background = windowTheme.background(default = OwnkeyBrand.Panel)
    val foreground = windowTheme.foreground(default = OwnkeyBrand.Bone)
    val keyBackground = keyTheme.background(default = OwnkeyBrand.Action)
    val keyForeground = keyTheme.foreground(default = OwnkeyBrand.Bone)
    val barShape = CircleShape

    val status = when {
        recording != null -> recording.label()
        feedback.phase == VoiceActionFeedbackPhase.SUCCESS -> stringRes(R.string.voice_only__inserted)
        feedback.phase == VoiceActionFeedbackPhase.ERROR -> stringRes(R.string.voice_only__try_again)
        else -> stringRes(R.string.voice_only__tap_to_speak)
    }

    Row(
        modifier = Modifier
            .height(BarHeight)
            .shadow(elevation = 8.dp, shape = barShape)
            .background(background, barShape)
            .border(1.dp, foreground.copy(alpha = 0.08f), barShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VoiceOnlyMicButton(
            phase = phase,
            feedbackPhase = feedback.phase,
            idleBackground = keyBackground,
            idleForeground = keyForeground,
            onClick = {
                inputFeedbackController.keyPress()
                if (phase == VoiceRecordingPhase.PROCESSING) {
                    dictationManager.cancelDictation()
                } else {
                    FlorisImeService.handleVoiceInputAction()
                }
            },
        )
        // A fixed width keeps both buttons in place while the status text changes under the finger.
        Column(
            modifier = Modifier.width(StatusWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeasuredLevelWaveform(
                levels = levels,
                barCount = 9,
                paused = phase != VoiceRecordingPhase.RECORDING,
                modifier = Modifier
                    .width(54.dp)
                    .height(18.dp),
                barWidth = 3.dp,
                barPitch = 6.dp,
            )
            Text(
                text = status,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = foreground.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val showKeyboard = stringRes(R.string.voice_only__show_keyboard)
        Box(
            modifier = Modifier
                .size(KeyboardButtonSize)
                .clip(CircleShape)
                .background(keyBackground)
                .clickable(onClickLabel = showKeyboard) {
                    inputFeedbackController.keyPress()
                    // A running recording keeps going; the full keyboard's row offers pause and cancel.
                    windowController.actions.toggleVoiceOnly()
                }
                .semantics { contentDescription = showKeyboard },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_keyboard),
                contentDescription = null,
                tint = keyForeground.copy(alpha = 0.86f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun VoiceOnlyMicButton(
    phase: VoiceRecordingPhase?,
    feedbackPhase: VoiceActionFeedbackPhase,
    idleBackground: Color,
    idleForeground: Color,
    onClick: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val success = phase == null && feedbackPhase == VoiceActionFeedbackPhase.SUCCESS
    val error = phase == null && feedbackPhase == VoiceActionFeedbackPhase.ERROR
    val background = when {
        phase == VoiceRecordingPhase.RECORDING -> OwnkeyBrand.SignalOrange
        phase == VoiceRecordingPhase.PAUSED -> OwnkeyBrand.SignalOrange.copy(alpha = 0.55f)
        success -> OwnkeyBrand.Glass.Success
        error -> OwnkeyBrand.Glass.Danger.copy(alpha = 0.22f)
        else -> idleBackground
    }
    val label = stringRes(
        when (phase) {
            VoiceRecordingPhase.RECORDING, VoiceRecordingPhase.PAUSED -> R.string.voice_recording__stop_dictation
            VoiceRecordingPhase.PROCESSING -> R.string.voice_recording__cancel_dictation
            null -> R.string.voice_rewrite__action_start_dictation
        },
    )

    Box(
        modifier = Modifier
            .size(MicSize)
            .drawBehind {
                if (phase == VoiceRecordingPhase.RECORDING) {
                    // A small halo around the button only; the bar's own shadow stays neutral.
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.6f to OwnkeyBrand.SignalOrange.copy(alpha = 0.3f),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.minDimension * 0.8f,
                        ),
                        radius = size.minDimension * 0.8f,
                    )
                }
            }
            .clip(CircleShape)
            .background(background)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            phase == VoiceRecordingPhase.PROCESSING -> {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_x_mark),
                    contentDescription = null,
                    tint = idleForeground.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp),
                )
                ProcessingArc(reducedMotion)
            }
            success -> Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            error -> Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_exclamation_circle),
                contentDescription = null,
                tint = OwnkeyBrand.Glass.Danger,
                modifier = Modifier.size(20.dp),
            )
            else -> Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_microphone),
                contentDescription = null,
                tint = if (phase == null) idleForeground.copy(alpha = 0.86f) else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ProcessingArc(reducedMotion: Boolean) {
    // Under reduced motion the arc still marks processing, but it stays put.
    val angle = if (reducedMotion) {
        -90f
    } else {
        rememberInfiniteTransition(label = "voiceOnlySpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
            label = "voiceOnlySpinAngle",
        ).value
    }
    Canvas(modifier = Modifier.size(MicSize)) {
        val stroke = 2.dp.toPx()
        drawArc(
            color = OwnkeyBrand.SignalOrange,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
