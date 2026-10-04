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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import dev.patrickgold.florisboard.ime.smartbar.MicButtonFace
import dev.patrickgold.florisboard.ime.smartbar.MicFaceState
import dev.patrickgold.florisboard.ime.smartbar.MicIdleStyle
import dev.patrickgold.florisboard.ime.smartbar.VoiceRecordingPhase
import dev.patrickgold.florisboard.ime.smartbar.label
import dev.patrickgold.florisboard.ime.smartbar.micFaceState
import dev.patrickgold.florisboard.ime.smartbar.voiceRecordingRowState
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionErrorReason
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackPhase
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.florisboard.voxtralDictationManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale

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
private val BarPadding = 6.dp
private val BarSpacing = 4.dp
private val MicSize = 44.dp
/** Touch area of a round button: Android's 48 dp minimum, around a [ButtonCircleSize] circle. */
private val ButtonSize = 48.dp
private val ButtonCircleSize = 40.dp
private val MinStatusWidth = 72.dp
private val MaxStatusWidth = 120.dp

/** Which optional buttons the bar shows, and how wide its status column is. */
internal data class VoiceBarLayout(val showRewrite: Boolean, val showLanguage: Boolean, val statusWidth: Dp)

/**
 * Fits the bar into [maxBarWidth]. The status column gets [MaxStatusWidth] when there is room and narrows on small
 * screens; below [MinStatusWidth] the language button gives way first, then rewrite, so the mic and the keyboard
 * button always stay usable. The width never follows the status text, so buttons stay put while the text changes.
 */
internal fun voiceBarLayout(maxBarWidth: Dp, languageAvailable: Boolean): VoiceBarLayout {
    fun freeWidth(buttons: Int) = maxBarWidth - (BarPadding * 2 + MicSize + ButtonSize * buttons + BarSpacing * (buttons + 1))
    var showLanguage = languageAvailable
    var showRewrite = true
    fun buttons() = 1 + (if (showRewrite) 1 else 0) + (if (showLanguage) 1 else 0)
    if (freeWidth(buttons()) < MinStatusWidth) showLanguage = false
    if (freeWidth(buttons()) < MinStatusWidth) showRewrite = false
    return VoiceBarLayout(
        showRewrite = showRewrite,
        showLanguage = showLanguage,
        statusWidth = freeWidth(buttons()).coerceIn(0.dp, MaxStatusWidth),
    )
}

/**
 * The next item after [active] whose [language] differs, wrapping around, or null if every item shares it. Layouts
 * of one language, such as English QWERTY and English Dvorak, are skipped: switching between them would not change
 * the language the bar shows or cloud dictation uses.
 */
internal fun <T> nextLanguage(items: List<T>, active: T, language: (T) -> String): T? {
    val start = items.indexOf(active)
    if (start < 0) return null
    val activeLanguage = language(active)
    for (step in 1 until items.size) {
        val candidate = items[(start + step).mod(items.size)]
        if (language(candidate) != activeLanguage) return candidate
    }
    return null
}

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

        val maxBarWidth = maxWidth - BarEdgeMargin * 2
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
            VoiceOnlyBar(maxBarWidth = maxBarWidth)
        }
    }
}

@Composable
private fun VoiceOnlyBar(maxBarWidth: Dp) {
    val context = LocalContext.current
    val inputFeedbackController = LocalInputFeedbackController.current
    val windowController = LocalWindowController.current
    val audioSessionCoordinator by context.audioSessionCoordinator()
    val audioLevelHistorySampler by context.audioLevelHistorySampler()
    val dictationManager by context.voxtralDictationManager()
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()

    // The session publishes every level sample; the bar shows no timer, so it only follows phase changes.
    val recordingState by remember(audioSessionCoordinator) {
        audioSessionCoordinator.state.map { voiceRecordingRowState(it, nowMs = 0L) }.distinctUntilChanged()
    }.collectAsState(initial = voiceRecordingRowState(audioSessionCoordinator.state.value, nowMs = 0L))
    val recording = recordingState
    val feedback by dictationManager.feedbackStateFlow.collectAsState()
    // Read only in the waveform's draw pass, so level samples never recompose the bar.
    val levels = audioLevelHistorySampler.state.collectAsState()
    val phase = recording?.phase
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
    // Rewrite needs the microphone and the editor to itself, and a language switch only applies to the next
    // recording, so both wait until dictation has finished.
    val sessionBusy = recording != null

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
        feedback.phase == VoiceActionFeedbackPhase.ERROR -> stringRes(feedback.errorReason.voiceOnlyLabel())
        else -> stringRes(R.string.voice_only__tap_to_speak)
    }
    // With one keyboard language there is nothing to switch to, so the bar leaves the button out.
    val nextLanguageSubtype = remember(subtypes, activeSubtype) {
        nextLanguage(subtypes, activeSubtype) { it.primaryLocale.language }
    }
    val layout = voiceBarLayout(maxBarWidth, languageAvailable = nextLanguageSubtype != null)

    Row(
        modifier = Modifier
            .height(BarHeight)
            .shadow(elevation = 8.dp, shape = barShape)
            .background(background, barShape)
            .border(1.dp, foreground.copy(alpha = 0.08f), barShape)
            .padding(horizontal = BarPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BarSpacing),
    ) {
        VoiceOnlyMicButton(
            feedbackPhase = feedback.phase,
            onClick = {
                inputFeedbackController.keyPress()
                // While transcribing the face offers no cancel, so a tap only says it is still working;
                // the full keyboard's row keeps an explicit cancel.
                FlorisImeService.handleVoiceInputAction()
            },
        )
        // A fixed width keeps the buttons in place while the status text changes under the finger.
        Column(
            modifier = Modifier.width(layout.statusWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeasuredLevelWaveform(
                levels = levels,
                barCount = 9,
                paused = phase != VoiceRecordingPhase.RECORDING,
                modifier = Modifier
                    .width(54.dp)
                    .height(18.dp),
                color = OwnkeyBrand.Ember,
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
        if (layout.showRewrite) {
            VoiceOnlyButton(
                label = stringRes(R.string.voice_only__rewrite),
                background = keyBackground,
                enabled = !sessionBusy,
                onClick = {
                    inputFeedbackController.keyPress()
                    keyboardManager.openRewriteFromVoiceBar()
                },
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_sparkles),
                    contentDescription = null,
                    tint = keyForeground.copy(alpha = 0.86f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (layout.showLanguage && nextLanguageSubtype != null) {
            val locale = activeSubtype.primaryLocale
            VoiceOnlyButton(
                label = stringRes(
                    R.string.voice_only__keyboard_language,
                    "language" to locale.displayLanguage().ifBlank { locale.language },
                ),
                actionLabel = stringRes(R.string.voice_only__switch_language),
                background = keyBackground,
                enabled = !sessionBusy,
                onClick = {
                    inputFeedbackController.keyPress()
                    subtypeManager.switchToSubtypeById(nextLanguageSubtype.id)
                },
            ) {
                Text(
                    text = locale.language.uppercase(Locale.ROOT),
                    color = keyForeground.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        VoiceOnlyButton(
            label = stringRes(R.string.voice_only__show_keyboard),
            background = keyBackground,
            onClick = {
                inputFeedbackController.keyPress()
                // A running recording keeps going; the full keyboard's row offers pause and cancel.
                windowController.actions.toggleVoiceOnly()
            },
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

/** A round secondary button of the bar. A disabled button stays in place, dimmed, so the bar keeps its shape. */
@Composable
private fun VoiceOnlyButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    actionLabel: String = label,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ButtonCircleSize)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun VoiceOnlyMicButton(
    feedbackPhase: VoiceActionFeedbackPhase,
    onClick: () -> Unit,
) {
    val state = micFaceState(feedbackPhase, aiUnavailable = false)
    val label = stringRes(
        when (state) {
            MicFaceState.LISTENING, MicFaceState.PAUSED -> R.string.voice_recording__stop_dictation
            MicFaceState.TRANSCRIBING -> R.string.voice_recording__processing
            else -> R.string.voice_rewrite__action_start_dictation
        },
    )
    MicButtonFace(
        state = state,
        idleStyle = MicIdleStyle.SOLID,
        size = MicSize,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/** Why the last attempt failed, short enough for the bar's status line. */
private fun VoiceActionErrorReason?.voiceOnlyLabel(): Int = when (this) {
    VoiceActionErrorReason.PROVIDER_CONFIGURATION -> R.string.voice_only__error_no_api_key
    VoiceActionErrorReason.MICROPHONE_PERMISSION -> R.string.voice_only__error_microphone_permission
    VoiceActionErrorReason.AUDIO_SESSION_BUSY,
    VoiceActionErrorReason.RECORDER_UNAVAILABLE,
    -> R.string.voice_only__error_microphone_unavailable
    VoiceActionErrorReason.EMPTY_AUDIO -> R.string.voice_only__error_nothing_heard
    VoiceActionErrorReason.EDITOR_COMMIT -> R.string.voice_only__error_insert
    VoiceActionErrorReason.AI_UNAVAILABLE -> R.string.voice_only__error_ai_unavailable
    VoiceActionErrorReason.RECORDING,
    VoiceActionErrorReason.TRANSCRIPTION,
    VoiceActionErrorReason.TARGET,
    VoiceActionErrorReason.REWRITE,
    null,
    -> R.string.voice_only__try_again
}
