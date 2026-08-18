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

import android.os.SystemClock
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.computeImageVector
import dev.patrickgold.florisboard.ime.keyboard.computeLabel
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackPhase
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.rewrite.CloudAiAvailability
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEntryOrigin
import dev.patrickgold.florisboard.ime.text.rewrite.stringResId
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import dev.patrickgold.florisboard.voiceRewriteUiController
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText

enum class QuickActionBarType {
    INTERACTIVE_BUTTON,
    INTERACTIVE_TILE,
    EDITOR_TILE;
}

/**
 * Only the interactive dictation key is arbitrated between tap and hold. Every other quick action,
 * and the editor tile in the action editor, keeps the untouched key down/up pipeline.
 */
internal fun quickActionUsesVoiceGesture(action: QuickAction, type: QuickActionBarType): Boolean =
    action is QuickAction.InsertKey &&
        action.data.code == KeyCode.VOICE_INPUT &&
        type != QuickActionBarType.EDITOR_TILE

/** Swallows the remainder of a gesture whose outcome is already decided. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    var event: PointerEvent
    do {
        event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
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
    val coroutineScope = rememberCoroutineScope()
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
    val isVoiceInputAction = action is QuickAction.InsertKey &&
        action.data.code == KeyCode.VOICE_INPUT
    val usesVoiceGesture = quickActionUsesVoiceGesture(action, type)

    // The platform long-press timeout already carries the user's accessibility touch-and-hold delay,
    // so hold recognition never uses a product-fixed duration.
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val gestureArbiter = remember(longPressTimeoutMs) { VoiceActionGestureArbiter(longPressTimeoutMs) }
    val voiceRewriteUiController = remember(context) { context.voiceRewriteUiController() }
    val dictationClickLabel = stringRes(R.string.voice_rewrite__action_start_dictation)
    val voiceRewriteLabel = stringRes(R.string.voice_rewrite__action_voice_rewrite)

    // An incognito or secure editor session disables every cloud AI action. The key stays visible
    // and answers with the specific reason instead of becoming an inert or silently failing control.
    val cloudAiAvailability = if (usesVoiceGesture) {
        voiceRewriteUiController.value.availability.collectAsState().value
    } else {
        CloudAiAvailability.Available
    }
    val aiUnavailableReason = (cloudAiAvailability as? CloudAiAvailability.Unavailable)?.reason
    val aiUnavailableText = aiUnavailableReason?.let { stringRes(it.stringResId()) }

    fun dispatchVoiceOutcome(outcome: VoiceActionGestureOutcome) {
        if (aiUnavailableText != null) {
            coroutineScope.launch { context.showShortToast(aiUnavailableText) }
            return
        }
        when (outcome) {
            VoiceActionGestureOutcome.DICTATION -> {
                action.onPointerDown(context)
                action.onPointerUp(context)
            }
            VoiceActionGestureOutcome.VOICE_REWRITE -> {
                inputFeedbackController.keyLongPress(action.keyData())
                voiceRewriteUiController.value.begin(VoiceRewriteEntryOrigin.DICTATION_KEY)
            }
        }
    }

    // Need to manually cancel an action if this composable suddenly leaves the composition to prevent the key from
    // being stuck in the pressed state
    DisposableEffect(action, isEnabled) {
        onDispose {
            gestureArbiter.reset()
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

    /**
     * Voice-only pointer pipeline. Unlike [quickActionInput] it does not send the key down before
     * the gesture resolves, because ordinary dictation must start on a released tap while a
     * recognized hold has to own the gesture and make the following release inert.
     */
    fun Modifier.voiceQuickActionInput(): Modifier {
        return indication(interactionSource, localIndication)
            .pointerInput(action, isEnabled, longPressTimeoutMs, cloudAiAvailability) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    if (!isEnabled) return@awaitEachGesture
                    val press = PressInteraction.Press(down.position)
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    interactionSource.tryEmit(press)
                    gestureArbiter.reset()
                    gestureArbiter.down(SystemClock.uptimeMillis())

                    var released: PointerInputChange? = null
                    var holdTimedOut = false
                    try {
                        withTimeout(longPressTimeoutMs) {
                            released = waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        holdTimedOut = true
                    }

                    when {
                        holdTimedOut -> {
                            gestureArbiter.advanceTo(SystemClock.uptimeMillis())
                                ?.let(::dispatchVoiceOutcome)
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            // Swallow the rest of the gesture so the release cannot also dictate.
                            consumeUntilUp()
                            gestureArbiter.reset()
                        }
                        released != null -> {
                            released?.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            gestureArbiter.up(SystemClock.uptimeMillis())?.let(::dispatchVoiceOutcome)
                        }
                        else -> {
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                            gestureArbiter.cancel(SystemClock.uptimeMillis())
                                ?.let(::dispatchVoiceOutcome)
                        }
                    }
                }
            }
    }

    /**
     * TalkBack never has to discover or synthesize a hold: the click starts ordinary dictation and a
     * long-click plus an explicit `Voice rewrite` custom action both start voice rewrite.
     */
    val voiceActionDescription = if (usesVoiceGesture) {
        val displayName = action.computeDisplayName(evaluator = evaluator)
        if (aiUnavailableText == null) displayName else "$displayName. $aiUnavailableText"
    } else {
        ""
    }

    fun Modifier.voiceActionSemantics(): Modifier = this.semantics(mergeDescendants = true) {
        role = Role.Button
        contentDescription = voiceActionDescription
        onClick(label = dictationClickLabel) {
            dispatchVoiceOutcome(VoiceActionGestureOutcome.DICTATION)
            true
        }
        onLongClick(label = voiceRewriteLabel) {
            gestureArbiter.accessibilityVoiceRewrite()?.let(::dispatchVoiceOutcome)
            true
        }
        customActions = listOf(
            CustomAccessibilityAction(voiceRewriteLabel) {
                gestureArbiter.accessibilityVoiceRewrite()?.let(::dispatchVoiceOutcome)
                true
            },
        )
    }

    fun Modifier.actionInput(): Modifier = if (usesVoiceGesture) {
        voiceQuickActionInput().voiceActionSemantics()
    } else {
        quickActionInput()
    }

    if (type == QuickActionBarType.INTERACTIVE_BUTTON && fillContainer) {
        PlainTooltip(action.computeTooltip(evaluator), enabled = true) {
            Box(
                modifier = modifier.actionInput(),
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
                .actionInput(),
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
                    isPressed = isPressed,
                    isEnabled = isEnabled && aiUnavailableReason == null,
                    isAiUnavailable = aiUnavailableReason != null,
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
    isPressed: Boolean,
    isEnabled: Boolean,
    isAiUnavailable: Boolean = false,
) {
    val context = LocalContext.current
    val voxtralDictationManager by context.voxtralDictationManager()
    val feedbackState by voxtralDictationManager.feedbackStateFlow.collectAsState()
    val feedbackPhase = feedbackState.phase
    val pillSize = (FlorisImeSizing.smartbarHeight - 8.dp).coerceAtLeast(34.dp)
    val accentColor = ownkeyAccentColor()

    val showSuccess = feedbackPhase == VoiceActionFeedbackPhase.SUCCESS
    val isListening = feedbackPhase == VoiceActionFeedbackPhase.RECORDING
    val isSilent = feedbackPhase == VoiceActionFeedbackPhase.PAUSED
    val isProcessing = feedbackPhase == VoiceActionFeedbackPhase.PROCESSING
    val isError = feedbackPhase == VoiceActionFeedbackPhase.ERROR

    // Reduced motion removes the decorative halo, the interpolated colour transition, the success
    // scale, and the travelling processing arc. Every state keeps its icon, colour, and semantics.
    val reducedMotion = rememberReducedMotion()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            showSuccess -> OwnkeyBrand.Glass.Success
            isListening -> OwnkeyBrand.SignalOrange
            isSilent -> OwnkeyBrand.SignalOrange.copy(alpha = 0.55f)
            isError -> OwnkeyBrand.Glass.Danger.copy(alpha = 0.22f)
            isPressed -> OwnkeyBrand.Glass.KeyPressed
            else -> OwnkeyBrand.Glass.Key
        },
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 300),
        label = "micPillBackground",
    )

    val successScale = remember { Animatable(1f) }
    LaunchedEffect(showSuccess, reducedMotion) {
        if (showSuccess && !reducedMotion) {
            successScale.snapTo(0.8f)
            successScale.animateTo(1f, animationSpec = tween(350))
        } else {
            successScale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier.size(pillSize + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isListening && !reducedMotion) {
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
                    .border(width = 2.dp, color = accentColor, shape = CircleShape),
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
                // The action shows what tapping it does. Microphone-level feedback belongs to the
                // measured centre waveform in the recording row, never inside this button.
                isListening || isSilent -> Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(pillSize * 0.46f),
                )
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
                // Visibly disabled rather than hidden, so the reason stays discoverable.
                isAiUnavailable -> Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = OwnkeyBrand.Glass.InkSoft,
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
            // Under reduced motion the arc stays put: it still marks the processing state, but it
            // no longer travels around the button.
            val spinAngle = if (reducedMotion) {
                -90f
            } else {
                val spinTransition = rememberInfiniteTransition(label = "micSpin")
                spinTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "micSpinAngle",
                ).value
            }
            Canvas(modifier = Modifier.size(pillSize + 8.dp)) {
                drawArc(
                    color = accentColor,
                    startAngle = spinAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
