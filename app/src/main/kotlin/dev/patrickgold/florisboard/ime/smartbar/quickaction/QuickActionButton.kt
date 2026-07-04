/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import dev.patrickgold.compose.tooltip.PlainTooltip
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.computeImageVector
import dev.patrickgold.florisboard.ime.keyboard.computeLabel
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralDictationManager
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.delay
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText
import kotlin.math.PI
import kotlin.math.sin

enum class QuickActionBarType {
    INTERACTIVE_BUTTON,
    INTERACTIVE_TILE,
    EDITOR_TILE;
}

internal const val QuickActionButtonAspectRatio = 1.1f
internal const val SecondaryQuickActionButtonAspectRatio = 0.72f

internal val QuickActionButtonIconSize = 24.dp
internal val SecondaryQuickActionButtonIconSize = 26.dp

@Composable
fun QuickActionButton(
    action: QuickAction,
    evaluator: ComputingEvaluator,
    modifier: Modifier = Modifier,
    type: QuickActionBarType = QuickActionBarType.INTERACTIVE_BUTTON,
    aspectRatio: Float = QuickActionButtonAspectRatio,
    fillContainer: Boolean = false,
    iconSize: Dp = QuickActionButtonIconSize,
) {
    val context = LocalContext.current
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val localIndication = LocalIndication.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val isEnabled = type == QuickActionBarType.EDITOR_TILE || evaluator.evaluateEnabled(action.keyData())
    val elementName = when (type) {
        QuickActionBarType.INTERACTIVE_BUTTON -> FlorisImeUi.SmartbarActionKey
        QuickActionBarType.INTERACTIVE_TILE -> FlorisImeUi.SmartbarActionTile
        QuickActionBarType.EDITOR_TILE -> FlorisImeUi.SmartbarActionsEditorTile
    }.elementName
    val attributes = mapOf(FlorisImeUi.Attr.Code to action.keyData().code)
    val selector = when {
        isPressed -> SnyggSelector.PRESSED
        !isEnabled -> SnyggSelector.DISABLED
        else -> null
    }
    val voxtralDictationManager by context.voxtralDictationManager()
    val dictationState by voxtralDictationManager.stateFlow.collectAsState()
    val isVoiceInputAction = action is QuickAction.InsertKey &&
        action.data.code == KeyCode.VOICE_INPUT

    // Need to manually cancel an action if this composable suddenly leaves the composition to prevent the key from
    // being stuck in the pressed state
    DisposableEffect(action, isEnabled) {
        onDispose {
            if (action is QuickAction.InsertKey) {
                action.onPointerCancel(context)
            }
        }
    }

    fun Modifier.quickActionInput(): Modifier {
        return indication(interactionSource, localIndication)
            .pointerInput(action, isEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (isEnabled && type != QuickActionBarType.EDITOR_TILE) {
                        val press = PressInteraction.Press(down.position)
                        inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                        interactionSource.tryEmit(press)
                        action.onPointerDown(context)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            action.onPointerUp(context)
                        } else {
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                            action.onPointerCancel(context)
                        }
                    }
                }
            }
    }

    if (type == QuickActionBarType.INTERACTIVE_BUTTON && fillContainer) {
        PlainTooltip(action.computeTooltip(evaluator), enabled = true) {
            Box(
                modifier = modifier.quickActionInput(),
                contentAlignment = Alignment.Center,
            ) {
                when (action) {
                    is QuickAction.InsertKey -> {
                        val (imageVector, label) = remember(action, evaluator) {
                            evaluator.computeImageVector(action.data) to evaluator.computeLabel(action.data)
                        }
                        if (imageVector != null) {
                            val prefs by FlorisPreferenceStore
                            val toolbarIconColor by prefs.theme.toolbarIconColor.collectAsState()
                            Icon(
                                modifier = Modifier.requiredSize(iconSize),
                                imageVector = imageVector,
                                contentDescription = null,
                                tint = toolbarIconColor.takeOrElse { OwnkeyBrand.Glass.InkSoft },
                            )
                        } else if (label != null) {
                            SnyggText(
                                elementName = "$elementName-text",
                                attributes = attributes,
                                selector = selector,
                                text = label,
                            )
                        }
                    }

                    is QuickAction.InsertText -> {
                        SnyggText(
                            elementName = "$elementName-text",
                            attributes = attributes,
                            selector = selector,
                            text = action.data.firstOrNull().toString().ifBlank { "?" },
                        )
                    }
                }
            }
        }
        return
    }

    PlainTooltip(action.computeTooltip(evaluator), enabled = type == QuickActionBarType.INTERACTIVE_BUTTON) {
        SnyggBox(
            elementName = elementName,
            attributes = attributes,
            selector = selector,
            modifier = modifier,
            clickAndSemanticsModifier = (if (type == QuickActionBarType.INTERACTIVE_TILE) {
                Modifier
                    .fillMaxWidth()
                    .height(108.dp)
            } else if (fillContainer) {
                Modifier
            } else {
                Modifier.aspectRatio(aspectRatio)
            })
                .quickActionInput(),
            contentAlignment = Alignment.Center,
        ) {
            val foreground: @Composable () -> Unit = {
                when (action) {
                    is QuickAction.InsertKey -> {
                        val (imageVector, label) = remember(action, evaluator) {
                            evaluator.computeImageVector(action.data) to evaluator.computeLabel(action.data)
                        }
                        if (imageVector != null) {
                            if (type == QuickActionBarType.INTERACTIVE_BUTTON) {
                                Icon(
                                    modifier = Modifier.requiredSize(iconSize),
                                    imageVector = imageVector,
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                )
                            } else {
                                SnyggBox(
                                    elementName = "$elementName-icon",
                                    attributes = attributes,
                                    selector = selector,
                                ) {
                                    SnyggIcon(imageVector = imageVector)
                                }
                            }
                        } else if (label != null) {
                            SnyggText(
                                elementName = "$elementName-text",
                                attributes = attributes,
                                selector = selector,
                                text = label,
                            )
                        }
                    }

                    is QuickAction.InsertText -> {
                        SnyggText(
                            elementName = "$elementName-text",
                            attributes = attributes,
                            selector = selector,
                            text = action.data.firstOrNull().toString().ifBlank { "?" },
                        )
                    }
                }
            }

            if (isVoiceInputAction && type == QuickActionBarType.INTERACTIVE_BUTTON) {
                DictationMicPill(
                    dictationState = dictationState,
                    isPressed = isPressed,
                    isEnabled = isEnabled,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    foreground()

                    // Render additional info if this is a tile
                    if (type != QuickActionBarType.INTERACTIVE_BUTTON) {
                        SnyggText(
                            elementName = "$elementName-text",
                            attributes = attributes,
                            selector = selector,
                            text = action.computeDisplayName(evaluator = evaluator),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The voice input pill following the Liquid Glass dictation spec. Each dictation state maps to a
 * distinct visual:
 * - idle: glass gray circle with a static mic icon
 * - listening: solid accent blue with four animated waveform bars and an expanding halo ring
 * - paused ("silent"): dim blue with flat bars and a slow breathing glow
 * - transcribing: back to glass with a thin accent arc spinning around the pill
 * - success (transcript inserted): solid green with a check icon, shown briefly, then back to idle
 * - error: red tint with a mic-off icon
 */
@Composable
private fun DictationMicPill(
    dictationState: VoxtralDictationManager.DictationState,
    isPressed: Boolean,
    isEnabled: Boolean,
) {
    val pillSize = (FlorisImeSizing.smartbarHeight - 8.dp).coerceAtLeast(34.dp)

    // The manager has no explicit success state; a transcription which returns to idle means the
    // transcript was inserted. Flash the success visual for a moment on that transition.
    var showSuccess by remember { mutableStateOf(false) }
    var lastState by remember { mutableStateOf(dictationState) }
    LaunchedEffect(dictationState) {
        val cameFromTranscribing = lastState == VoxtralDictationManager.DictationState.TRANSCRIBING
        lastState = dictationState
        if (cameFromTranscribing && dictationState == VoxtralDictationManager.DictationState.IDLE) {
            showSuccess = true
            delay(800)
            showSuccess = false
        } else {
            showSuccess = false
        }
    }

    val isListening = dictationState == VoxtralDictationManager.DictationState.LISTENING
    val isSilent = dictationState == VoxtralDictationManager.DictationState.PAUSED
    val isProcessing = dictationState == VoxtralDictationManager.DictationState.TRANSCRIBING
    val isError = dictationState == VoxtralDictationManager.DictationState.ERROR && !showSuccess

    val backgroundColor by animateColorAsState(
        targetValue = when {
            showSuccess -> OwnkeyBrand.Glass.Success
            isListening -> OwnkeyBrand.Glass.Accent
            isSilent -> OwnkeyBrand.Glass.Accent.copy(alpha = 0.30f)
            isError -> OwnkeyBrand.Glass.Danger.copy(alpha = 0.22f)
            isPressed -> OwnkeyBrand.Glass.KeyPressed
            else -> OwnkeyBrand.Glass.Key
        },
        label = "micPillBackground",
    )

    val successScale = remember { Animatable(1f) }
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            successScale.snapTo(0.8f)
            successScale.animateTo(1f, animationSpec = tween(350))
        }
    }

    Box(
        modifier = Modifier.size(pillSize + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isListening) {
            val haloTransition = rememberInfiniteTransition(label = "micHalo")
            val haloProgress by haloTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "micHaloProgress",
            )
            Box(
                modifier = Modifier
                    .size(pillSize + 8.dp)
                    .scale(0.85f + 0.30f * haloProgress)
                    .alpha((1f - haloProgress) * 0.35f)
                    .border(width = 2.dp, color = OwnkeyBrand.Glass.Accent, shape = CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(pillSize)
                .scale(if (showSuccess) successScale.value else if (isPressed) 0.96f else 1f)
                .alpha(if (isEnabled) 1f else 0.45f)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isListening -> DictationWaveformBars(pillSize = pillSize, animated = true)
                isSilent -> DictationWaveformBars(pillSize = pillSize, animated = false)
                showSuccess -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(pillSize * 0.48f),
                )
                isError -> Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = OwnkeyBrand.Glass.Danger,
                    modifier = Modifier.size(pillSize * 0.46f),
                )
                else -> Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                    contentDescription = null,
                    tint = OwnkeyBrand.Glass.InkSoft,
                    modifier = Modifier.size(pillSize * 0.46f),
                )
            }
        }
        if (isProcessing) {
            val spinTransition = rememberInfiniteTransition(label = "micSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "micSpinAngle",
            )
            Canvas(modifier = Modifier.size(pillSize + 8.dp)) {
                drawArc(
                    color = OwnkeyBrand.Glass.Accent,
                    startAngle = spinAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun DictationWaveformBars(
    pillSize: androidx.compose.ui.unit.Dp,
    animated: Boolean,
) {
    val barMaxHeight = pillSize * 0.42f
    val barWidth = 3.5.dp
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "micBars")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "micBarsPhase",
        ).value
    } else {
        0f
    }
    Row(
        modifier = Modifier.height(barMaxHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            val fraction = if (animated) {
                val wave = sin(2.0 * PI * (phase - index * 0.166)).toFloat()
                0.25f + 0.35f * (1f + wave)
            } else {
                0.16f
            }
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(barMaxHeight * fraction.coerceIn(0.12f, 0.95f))
                    .alpha(if (animated) 1f else 0.7f)
                    .background(Color.White, RoundedCornerShape(2.dp)),
            )
        }
    }
}
