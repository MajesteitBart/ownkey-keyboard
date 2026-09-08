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

package dev.patrickgold.florisboard.ime.text.rewrite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import dev.patrickgold.florisboard.audioLevelHistorySampler
import dev.patrickgold.florisboard.audioSessionCoordinator
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.MeasuredLevelWaveform
import dev.patrickgold.florisboard.ime.smartbar.formatRecordingElapsed
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionPhase
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import dev.patrickgold.florisboard.llmRewriteManager
import dev.patrickgold.florisboard.voiceRewriteUiController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes
import java.util.Locale

private val PanelEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val BodyFadeMillis = 160
private val PanelGap = 8.dp
private val CardShape = RoundedCornerShape(10.dp)
private val PresetRowHeight = 50.dp
private val MinTouchTarget = 48.dp
private val ActionRailHeight = 52.dp
private val HeaderControlSize = MinTouchTarget

/** Expanded layouts centre the content instead of stretching it to the screen edges. */
private val HubMaxWidth = 1_200.dp
private val SheetMaxWidth = 840.dp

/** Dwell time of the `Text replaced` confirmation before the voice flow closes itself. */
private const val VoiceReplacedConfirmationMillis = 1_200L

/**
 * AI rewrite panel.
 *
 * The panel is one stable frame with exactly one body, selected by [rewritePanelBody]. The hub
 * (voice card and preset rows) is rendered only while nothing is in progress; every session state
 * replaces it entirely, so no dimmed hub, second spinner, or competing control set is ever visible.
 */
@Composable
fun RewriteOptionsPanel(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val rewriteManager by context.llmRewriteManager()
    val voiceController by context.voiceRewriteUiController()
    val uiState by rewriteManager.uiStateFlow.collectAsState()
    val voiceModel by voiceController.uiState.collectAsState()
    val availability by voiceController.availability.collectAsState()
    val promptsJson by prefs.voxtral.rewritePrompts.collectAsState()
    val prompts = remember(promptsJson) { RewritePromptPresets.decode(promptsJson) }
    val body = rewritePanelBody(step = uiState.step, surface = voiceModel.surface)

    // The hub card is resolved whenever the hub is visible and the host editor content changes,
    // so the scope summary is fresh on return, after the user adjusts the selection handles, and
    // when the field's text changes underneath an unchanged range. The editor is observed only
    // while the hub is shown: active states never carry a live content subscription and show the
    // session's own snapshot. Provider readiness reads the Keystore-backed secret stores, never on
    // the typing thread.
    val editorInstance by context.editorInstance()
    val hubEditorContent = if (body == RewritePanelBody.HUB) {
        editorInstance.activeContentFlow.collectAsState().value
    } else {
        null
    }
    var hubCard by remember { mutableStateOf(VoiceRewriteHubCardState()) }
    LaunchedEffect(availability, body, hubEditorContent) {
        if (body == RewritePanelBody.HUB) {
            hubCard = withContext(Dispatchers.IO) { voiceController.hubCardState() }
        }
    }

    // Reset both flows whenever the panel leaves the composition by any path (smartbar close,
    // sparkle toggle, field change, panel swap), so reopening always starts at the hub and no voice
    // recorder or provider job outlives the panel.
    DisposableEffect(Unit) {
        onDispose {
            rewriteManager.onPanelDismissed()
            voiceController.invalidate(AudioSessionInvalidation.OWNER_CANCELLED)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .padding(PanelGap),
    ) {
        PanelBodyTransition(body = body) { visibleBody ->
            when (visibleBody) {
                RewritePanelBody.HUB -> HubBody(
                    prompts = prompts,
                    hubCard = hubCard,
                    onVoice = { voiceController.begin(VoiceRewriteEntryOrigin.REWRITE_HUB) },
                    onPreset = { prompt -> rewriteManager.rewriteWith(prompt) },
                )
                RewritePanelBody.PRESET_GENERATING -> PresetGeneratingBody(
                    promptName = uiState.activePrompt?.name.orEmpty(),
                    onCancel = { rewriteManager.cancelGeneration() },
                )
                RewritePanelBody.PRESET_RESULT -> PresetResultBody(
                    promptName = uiState.activePrompt?.name.orEmpty(),
                    resultText = uiState.resultText.orEmpty(),
                    onClose = { rewriteManager.backToOptions() },
                    onRetry = { rewriteManager.tryAgain() },
                    onInsert = { rewriteManager.insertResult() },
                )
                RewritePanelBody.PRESET_DONE -> ReplacedConfirmation(
                    text = stringRes(R.string.rewrite_panel__state_inserted),
                )
                RewritePanelBody.VOICE_TARGETING -> VoiceTargetingBody(
                    model = voiceModel,
                    onCancel = { voiceController.cancel() },
                )
                RewritePanelBody.VOICE_DISCLOSURE -> VoiceDisclosureBody(
                    model = voiceModel,
                    onContinue = { voiceController.acknowledgeDisclosure() },
                    onOpenSettings = { voiceController.openAiSettings() },
                    onBack = { voiceController.back() },
                )
                RewritePanelBody.VOICE_CAPTURE -> VoiceCaptureBody(
                    model = voiceModel,
                    controller = voiceController,
                )
                RewritePanelBody.VOICE_PROCESSING -> VoiceProcessingBody(
                    model = voiceModel,
                    onCancel = { voiceController.cancel() },
                )
                RewritePanelBody.VOICE_RESULT -> VoiceResultBody(
                    model = voiceModel,
                    controller = voiceController,
                )
                RewritePanelBody.VOICE_RECOVERY -> VoiceRecoveryBody(
                    model = voiceModel,
                    controller = voiceController,
                )
                RewritePanelBody.VOICE_SUCCESS -> VoiceSuccessBody(
                    model = voiceModel,
                    controller = voiceController,
                )
            }
        }
    }
}

/**
 * Renders exactly one body at a time.
 *
 * A body change drops the previous body immediately and fades the new one in over a short, bounded
 * duration, so outgoing content can never accept input or remain an accessibility target during a
 * transition. Only body changes animate; recording levels, timers, and status text updates inside a
 * body do not. Reduced motion replaces the fade with a direct swap.
 */
@Composable
private fun PanelBodyTransition(
    body: RewritePanelBody,
    content: @Composable BoxScope.(RewritePanelBody) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val bodyAlpha = remember { Animatable(1f) }
    LaunchedEffect(body, reducedMotion) {
        if (reducedMotion) {
            bodyAlpha.snapTo(1f)
        } else {
            bodyAlpha.snapTo(0f)
            bodyAlpha.animateTo(1f, animationSpec = tween(BodyFadeMillis, easing = PanelEasing))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = bodyAlpha.value },
    ) {
        key(body) {
            content(body)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hub
// ---------------------------------------------------------------------------------------------

@Composable
private fun BoxScope.HubBody(
    prompts: List<RewritePromptPreset>,
    hubCard: VoiceRewriteHubCardState,
    onVoice: () -> Unit,
    onPreset: (RewritePromptPreset) -> Unit,
) {
    val rows = remember(prompts) { RewritePromptPresets.hubRows(prompts) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = HubMaxWidth)
            .align(Alignment.TopCenter),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        // The voice card stays pinned so a long custom preset list cannot push the visible route
        // off-screen.
        VoiceInstructionCard(state = hubCard, onClick = onVoice)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            var previousGroup: RewritePresetGroup? = null
            rows.forEach { row ->
                if (row.group != previousGroup) {
                    PresetGroupCaption(group = row.group)
                    previousGroup = row.group
                }
                Row(
                    modifier = Modifier
                        .height(PresetRowHeight)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PanelGap),
                ) {
                    row.prompts.forEach { prompt ->
                        RewriteOptionCard(
                            prompt = prompt,
                            enabled = hubCard.isAvailable,
                            unavailableReason = hubCard.unavailableReason,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onPreset(prompt) },
                        )
                    }
                    repeat(RewritePromptPresets.HubColumns - row.prompts.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetGroupCaption(group: RewritePresetGroup) {
    Text(
        text = stringRes(
            when (group) {
                RewritePresetGroup.QUALITY -> R.string.rewrite_panel__group_quality
                RewritePresetGroup.TONE -> R.string.rewrite_panel__group_tone
                RewritePresetGroup.CUSTOM -> R.string.rewrite_panel__group_custom
            },
        ).uppercase(Locale.getDefault()),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        color = OwnkeyBrand.Glass.Hint,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}

/**
 * Pinned `Tell Ownkey what to change` action.
 *
 * It states the resolved scope, names the two configured providers so the data path is visible
 * before first use, and — while an incognito or secure session blocks cloud AI — renders visibly
 * disabled with the reason instead of disappearing or failing silently on tap. It keeps the only
 * icon ring in the hub, so the voice entry is distinct from the text-only preset cards.
 */
@Composable
private fun VoiceInstructionCard(
    state: VoiceRewriteHubCardState,
    onClick: () -> Unit,
) {
    val accentColor = ownkeyAccentColor()
    val unavailableText = state.unavailableReason?.text()
    val title = stringRes(R.string.voice_rewrite__card_title)
    val supportingText = when {
        unavailableText != null -> unavailableText
        state.selectionCharacterCount != null -> pluralsRes(
            R.plurals.voice_rewrite__card_summary_selection,
            state.selectionCharacterCount,
            "count" to state.selectionCharacterCount,
        )
        else -> stringRes(R.string.voice_rewrite__card_summary_no_selection)
    }
    val providerText = if (state.providersConfigured) {
        stringRes(
            R.string.voice_rewrite__card_providers,
            "audio" to state.audioProvider!!.displayName,
            "rewrite" to state.rewriteProvider!!.displayName,
        )
    } else {
        null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (state.isAvailable) 1f else 0.45f)
            .clickable(enabled = state.isAvailable, onClickLabel = title, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(title, supportingText, providerText)
                    .joinToString(separator = ". ")
            },
        color = OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = CardShape,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .border(width = 1.dp, color = accentColor.copy(alpha = 0.5f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = OwnkeyBrand.Glass.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    color = OwnkeyBrand.Glass.InkSoft,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (providerText != null) {
                    Text(
                        text = providerText,
                        color = OwnkeyBrand.Glass.InkSoft.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Text-only preset card. No icon ring, so the voice entry above is the one ringed control. */
@Composable
private fun RewriteOptionCard(
    prompt: RewritePromptPreset,
    enabled: Boolean,
    unavailableReason: CloudAiUnavailableReason?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val reasonText = unavailableReason?.text()
    Surface(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClickLabel = prompt.name, onClick = onClick)
            .semantics {
                // Unavailability is announced, not only rendered as a dimmed card.
                contentDescription = if (reasonText == null) prompt.name else "${prompt.name}. $reasonText"
            },
        color = OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = CardShape,
        border = BorderStroke(1.dp, OwnkeyBrand.Glass.Ink.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = prompt.name,
                color = OwnkeyBrand.Glass.Ink,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Voice rewrite session bodies
// ---------------------------------------------------------------------------------------------

/** `Selecting whole field…` / `Preparing microphone…` with the single neutral cancel. */
@Composable
private fun BoxScope.VoiceTargetingBody(
    model: VoiceRewriteUiModel,
    onCancel: () -> Unit,
) {
    // Every body keeps a label and a way out even if the model carries no message.
    val message = model.statusMessage ?: VoiceRewriteMessage.SELECTING_WHOLE_FIELD
    SessionSheet {
        SheetEyebrow(text = stringRes(R.string.voice_rewrite__sheet_title))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            StatusLine(text = message.text(), spinner = true)
        }
        ActionRail(RailAction(VoiceRewriteAction.CANCEL, onCancel))
    }
}

@Composable
private fun BoxScope.VoiceDisclosureBody(
    model: VoiceRewriteUiModel,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val disclosure = model.disclosure
    SessionSheet {
        Text(
            text = stringRes(R.string.voice_rewrite__disclosure_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            model.scopeLabel?.let { scope ->
                Text(text = scope.text(), color = OwnkeyBrand.Glass.InkSoft, fontSize = 12.sp)
            }
            Text(
                text = stringRes(R.string.voice_rewrite__disclosure_body),
                color = OwnkeyBrand.Glass.InkSoft,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            if (disclosure != null) {
                Text(
                    text = stringRes(
                        R.string.voice_rewrite__disclosure_audio_row,
                        "provider" to disclosure.audioProviderName,
                    ),
                    fontSize = 13.sp,
                )
                Text(
                    text = stringRes(
                        R.string.voice_rewrite__disclosure_text_row,
                        "provider" to disclosure.rewriteProviderName,
                    ),
                    fontSize = 13.sp,
                )
            }
        }
        ActionRail(
            RailAction(VoiceRewriteAction.BACK, onBack),
            RailAction(VoiceRewriteAction.OPEN_AI_SETTINGS, onOpenSettings),
            RailAction(VoiceRewriteAction.CONTINUE, onContinue, emphasis = RailEmphasis.ACCENT),
        )
    }
}

/**
 * Recording and paused, in the panel. The measured waveform is the audio feedback; the timer counts
 * up and, close to the cap, down. Stop begins transcription and never inserts the instruction;
 * Cancel discards it. The neutral stop button stays the most prominent control through its
 * high-contrast fill, trailing placement, and stop-square glyph.
 */
@Composable
private fun BoxScope.VoiceCaptureBody(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val context = LocalContext.current
    val audioSessionCoordinator by context.audioSessionCoordinator()
    val audioLevelHistorySampler by context.audioLevelHistorySampler()
    val audioSession by audioSessionCoordinator.state.collectAsState()
    val levels by audioLevelHistorySampler.state.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(audioSession?.sessionId, audioSession?.phase) {
        nowMs = System.currentTimeMillis()
        while (audioSession?.phase == AudioSessionPhase.RECORDING) {
            nowMs = System.currentTimeMillis()
            delay(250L)
        }
    }
    val clock = voiceRewriteRecordingClock(session = audioSession, nowMs = nowMs)
    val paused = model.surface == VoiceRewriteSurface.PAUSED
    val status = model.statusMessage ?: VoiceRewriteMessage.SPEAK_AN_EDIT

    SessionSheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SheetEyebrow(text = stringRes(R.string.voice_rewrite__sheet_title))
                // Announced once per state change; the timer beside it is never a live region.
                Text(
                    text = status.text(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                model.scopeLabel?.let { scope ->
                    Text(
                        text = scope.text(),
                        color = OwnkeyBrand.Glass.InkSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RecordingTimer(clock = clock, paused = paused)
        }
        // The waveform takes whatever height remains and gives it all up under pressure, so the
        // action rail below can never be pushed out of the sheet.
        MeasuredLevelWaveform(
            levels = levels.levels,
            barCount = levels.levels.size,
            paused = paused,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            color = ownkeyAccentColor(),
        )
        ActionRail(
            RailAction(
                action = VoiceRewriteAction.CANCEL,
                onClick = { controller.cancel() },
                icon = Icons.Default.Close,
                description = stringRes(R.string.voice_recording__cancel_rewrite),
            ),
            if (paused) {
                RailAction(
                    action = VoiceRewriteAction.RESUME,
                    onClick = { controller.resumeRecording() },
                    icon = Icons.Default.PlayArrow,
                    description = stringRes(R.string.voice_recording__resume_rewrite),
                )
            } else {
                RailAction(
                    action = VoiceRewriteAction.PAUSE,
                    onClick = { controller.pauseRecording() },
                    icon = Icons.Default.Pause,
                    description = stringRes(R.string.voice_recording__pause_rewrite),
                )
            },
            RailAction(
                action = VoiceRewriteAction.STOP,
                onClick = { controller.stopRecording() },
                emphasis = RailEmphasis.STRONG,
                icon = Icons.Default.Stop,
                description = stringRes(R.string.voice_recording__stop_rewrite),
            ),
        )
    }
}

@Composable
private fun RecordingTimer(
    clock: VoiceRewriteRecordingClock,
    paused: Boolean,
) {
    val remaining = clock.remainingSeconds
    val timeText = if (remaining != null) "-${remaining}s" else formatRecordingElapsed(clock.elapsedMs)
    val description = stringRes(R.string.voice_recording__elapsed_label, "time" to timeText)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (paused) OwnkeyBrand.WarningYellow else OwnkeyBrand.SignalOrange,
                    shape = CircleShape,
                ),
        )
        Text(
            text = timeText,
            color = if (remaining != null) OwnkeyBrand.WarningYellow else OwnkeyBrand.Glass.InkSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * `Understanding instruction…` then `Rewriting selected text…`: one spinner and one stage label at
 * a time. The recognized instruction appears once it exists and is never committed to the editor.
 */
@Composable
private fun BoxScope.VoiceProcessingBody(
    model: VoiceRewriteUiModel,
    onCancel: () -> Unit,
) {
    val message = model.statusMessage ?: VoiceRewriteMessage.UNDERSTANDING_INSTRUCTION
    SessionSheet {
        SheetEyebrow(text = stringRes(R.string.voice_rewrite__sheet_title))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            StatusLine(text = message.text(), spinner = true)
            model.recognizedInstruction?.let { instruction ->
                HeardInstruction(instruction = instruction, onEditInstruction = null)
            }
        }
        ActionRail(RailAction(VoiceRewriteAction.CANCEL, onCancel))
    }
}

/**
 * Review before mutation. The captured source text is never rendered in the keyboard. Close leaves
 * without touching the editor; Replace is the only accent action and revalidates the target first.
 * When verification fails the result stays readable and the rail offers Copy result only.
 */
@Composable
private fun BoxScope.VoiceResultBody(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val resultText = model.resultText
    val canReplace = VoiceRewriteAction.REPLACE in model.actions
    val closeLabel = VoiceRewriteAction.CLOSE.label()
    val resultLabel = stringRes(R.string.voice_rewrite__result_label)
    var copied by remember { mutableStateOf(false) }

    if (resultText == null) {
        // The session never publishes a result surface without text; if it ever did, the user
        // still gets a labelled state and a way out rather than an empty sheet.
        SessionSheet {
            SheetEyebrow(text = stringRes(R.string.voice_rewrite__sheet_title))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                StatusLine(
                    text = VoiceRewriteMessage.EMPTY_RESULT.text(),
                    spinner = false,
                    icon = Icons.Outlined.ErrorOutline,
                    color = OwnkeyBrand.WarningYellow,
                )
            }
            ActionRail(RailAction(VoiceRewriteAction.CLOSE, { controller.close() }))
        }
        return
    }

    SessionSheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val statusMessage = model.statusMessage
            val instruction = model.recognizedInstruction
            when {
                statusMessage != null -> StatusLine(
                    text = statusMessage.text(),
                    spinner = false,
                    icon = Icons.Outlined.ErrorOutline,
                    color = OwnkeyBrand.WarningYellow,
                    modifier = Modifier.weight(1f),
                )
                instruction != null -> HeardInstruction(
                    instruction = instruction,
                    onEditInstruction = { controller.recordInstructionAgain() }
                        .takeIf { VoiceRewriteAction.RECORD_AGAIN in model.actions },
                    modifier = Modifier.weight(1f),
                )
                else -> Spacer(modifier = Modifier.weight(1f))
            }
            HeaderIconButton(
                icon = Icons.Default.Close,
                contentDescription = closeLabel,
                tint = OwnkeyBrand.Glass.InkSoft,
                onClick = { controller.close() },
            )
        }
        Text(
            text = resultText,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = "$resultLabel. $resultText" },
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
        if (canReplace) {
            ActionRail(
                RailAction(VoiceRewriteAction.TRY_AGAIN, { controller.tryAgain() }),
                RailAction(
                    action = VoiceRewriteAction.REPLACE,
                    onClick = { controller.replaceResult() },
                    emphasis = RailEmphasis.ACCENT,
                ),
            )
        } else {
            ActionRail(
                RailAction(
                    action = VoiceRewriteAction.COPY_RESULT,
                    onClick = { if (controller.copyResult()) copied = true },
                    emphasis = RailEmphasis.ACCENT,
                    label = if (copied) stringRes(R.string.voice_rewrite__state_copied) else null,
                ),
            )
        }
    }
}

/** Specific failure, useful context, and only the recovery actions that are currently valid. */
@Composable
private fun BoxScope.VoiceRecoveryBody(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val message = model.statusMessage ?: VoiceRewriteMessage.REWRITE_FAILED
    SessionSheet {
        SheetEyebrow(text = stringRes(R.string.voice_rewrite__sheet_title))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            StatusLine(
                text = message.text(),
                spinner = false,
                icon = Icons.Outlined.ErrorOutline,
                color = OwnkeyBrand.WarningYellow,
            )
            model.scopeLabel?.let { scope ->
                Text(text = scope.text(), color = OwnkeyBrand.Glass.InkSoft, fontSize = 12.sp)
            }
            model.recognizedInstruction?.let { instruction ->
                HeardInstruction(instruction = instruction, onEditInstruction = null)
            }
        }
        val actions = buildList {
            add(RailAction(VoiceRewriteAction.CLOSE, { controller.close() }))
            if (VoiceRewriteAction.OPEN_AI_SETTINGS in model.actions) {
                add(RailAction(VoiceRewriteAction.OPEN_AI_SETTINGS, { controller.openAiSettings() }))
            }
            if (VoiceRewriteAction.OPEN_INCOGNITO_SETTING in model.actions) {
                add(RailAction(VoiceRewriteAction.OPEN_INCOGNITO_SETTING, { controller.openIncognitoSetting() }))
            }
            if (VoiceRewriteAction.TRY_AGAIN in model.actions) {
                add(RailAction(VoiceRewriteAction.TRY_AGAIN, { controller.tryAgain() }))
            }
            if (VoiceRewriteAction.RECORD_AGAIN in model.actions) {
                add(RailAction(VoiceRewriteAction.RECORD_AGAIN, { controller.recordInstructionAgain() }))
            }
        }
        ActionRail(*actions.toTypedArray())
    }
}

/**
 * `Text replaced` inside the same panel frame. It is shown only after a confirmed replacement,
 * closes itself after a short dwell, and deliberately creates no undo control or stored history.
 * The timer is bound to this confirmation, so it can never close a newer session.
 */
@Composable
private fun BoxScope.VoiceSuccessBody(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val confirmationId = model.announcementId
    LaunchedEffect(confirmationId) {
        delay(VoiceReplacedConfirmationMillis)
        controller.finishAfterReplacement(confirmationId)
    }
    ReplacedConfirmation(text = VoiceRewriteMessage.TEXT_REPLACED.text())
}

// ---------------------------------------------------------------------------------------------
// Preset rewrite bodies
// ---------------------------------------------------------------------------------------------

@Composable
private fun BoxScope.PresetGeneratingBody(
    promptName: String,
    onCancel: () -> Unit,
) {
    SessionSheet {
        SheetEyebrow(text = promptName)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            StatusLine(text = generatingLabel(promptName), spinner = true)
        }
        ActionRail(RailAction(VoiceRewriteAction.CANCEL, onCancel))
    }
}

@Composable
private fun generatingLabel(promptName: String): String {
    if (promptName.isBlank()) return stringRes(R.string.rewrite_panel__state_rewriting)
    return stringRes(
        R.string.rewrite_panel__state_rewriting_with_voice,
        "voice" to promptName.lowercase(Locale.getDefault()),
    )
}

@Composable
private fun BoxScope.PresetResultBody(
    promptName: String,
    resultText: String,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onInsert: () -> Unit,
) {
    val backLabel = stringRes(R.string.rewrite_panel__action_back_to_options)
    val resultLabel = stringRes(R.string.voice_rewrite__result_label)
    SessionSheet {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SheetEyebrow(text = promptName)
            }
            HeaderIconButton(
                icon = Icons.Default.Close,
                contentDescription = backLabel,
                tint = OwnkeyBrand.Glass.InkSoft,
                onClick = onClose,
            )
        }
        Text(
            text = resultText,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = "$resultLabel. $resultText" },
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
        ActionRail(
            RailAction(
                action = VoiceRewriteAction.TRY_AGAIN,
                onClick = onRetry,
                label = stringRes(R.string.rewrite_panel__action_try_again),
            ),
            RailAction(
                action = VoiceRewriteAction.REPLACE,
                onClick = onInsert,
                emphasis = RailEmphasis.ACCENT,
                label = stringRes(R.string.rewrite_panel__action_insert),
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

/**
 * Shared success treatment: a high-contrast check on the accent background plus a polite
 * announcement. Used by both the preset and the voice flow, always inside the panel frame.
 */
@Composable
private fun BoxScope.ReplacedConfirmation(text: String) {
    val reducedMotion = rememberReducedMotion()
    val checkScale = remember { Animatable(if (reducedMotion) 1f else 0.6f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            checkScale.snapTo(1f)
        } else {
            checkScale.animateTo(1f, animationSpec = tween(300, easing = PanelEasing))
        }
    }
    val accent = ownkeyAccentColor()
    SessionSheet {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = checkScale.value
                        scaleY = checkScale.value
                    }
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentForeground(accent),
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = OwnkeyBrand.Glass.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** The single sheet every non-hub body renders in; it fills the panel frame and never overlaps it. */
@Composable
private fun BoxScope.SessionSheet(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = SheetMaxWidth)
            .align(Alignment.TopCenter),
        color = OwnkeyBrand.Glass.Sheet,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = CardShape,
        border = BorderStroke(1.dp, OwnkeyBrand.Glass.Ink.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
            content = content,
        )
    }
}

@Composable
private fun SheetEyebrow(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = ownkeyAccentColor(),
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One status indicator: a spinner or an icon, never both, next to a politely announced label. */
@Composable
private fun StatusLine(
    text: String,
    spinner: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = OwnkeyBrand.Glass.Ink,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            spinner -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp,
                color = ownkeyAccentColor(),
                trackColor = OwnkeyBrand.Glass.Ink.copy(alpha = 0.12f),
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = text,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
        )
    }
}

/**
 * Recognized instruction under a `Heard` label. It is shown, never altered: wrapping and the
 * two-line collapse are visual only, the full text is exposed to TalkBack, and a tap expands it
 * without changing what the provider received. The optional mic records a replacement instruction
 * against the same target and says so accessibly.
 */
@Composable
private fun HeardInstruction(
    instruction: String,
    onEditInstruction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val heardDescription = stringRes(R.string.voice_rewrite__instruction_label, "instruction" to instruction)
    val toggleLabel = stringRes(
        if (expanded) R.string.voice_rewrite__heard_show_less else R.string.voice_rewrite__heard_show_full,
    )
    val editLabel = stringRes(R.string.voice_rewrite__action_edit_instruction)
    val editDescription = stringRes(R.string.voice_rewrite__action_edit_instruction_description)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = toggleLabel) { expanded = !expanded }
                .semantics { contentDescription = heardDescription },
        ) {
            Text(
                text = stringRes(R.string.voice_rewrite__heard_label).uppercase(Locale.getDefault()),
                color = OwnkeyBrand.Glass.Hint,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = "“$instruction”",
                color = OwnkeyBrand.Glass.InkSoft,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onEditInstruction != null) {
            HeaderIconButton(
                icon = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                contentDescription = "$editLabel. $editDescription",
                tint = ownkeyAccentColor(),
                onClick = onEditInstruction,
                outlined = true,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    outlined: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(HeaderControlSize)
            .then(
                if (outlined) {
                    Modifier.border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private enum class RailEmphasis {
    /** Cancel, Stop-adjacent, navigation, retry: readable but not the commit action. */
    NEUTRAL,

    /** Stop: neutral in colour, prominent through a high-contrast fill and its glyph. */
    STRONG,

    /** The one committing action of a state, such as Replace. */
    ACCENT,
}

private data class RailAction(
    val action: VoiceRewriteAction,
    val onClick: () -> Unit,
    val emphasis: RailEmphasis = RailEmphasis.NEUTRAL,
    val icon: ImageVector? = null,
    /** Visible label override; the action's own label is used when null. */
    val label: String? = null,
    /** Accessible description override; the visible label is used when null. */
    val description: String? = null,
)

/**
 * Action rail. Every action keeps its 48 dp target and the accent action, when present, is always
 * the trailing one. The rail is at least [ActionRailHeight] tall and grows to the tallest label,
 * so a two-line label at a large font scale is never clipped; the body above yields the space.
 */
@Composable
private fun ActionRail(vararg actions: RailAction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ActionRailHeight)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        actions.forEach { rail ->
            RailButton(
                rail = rail,
                modifier = Modifier.weight(if (rail.emphasis == RailEmphasis.ACCENT) 1.4f else 1f),
            )
        }
    }
}

@Composable
private fun RailButton(
    rail: RailAction,
    modifier: Modifier = Modifier,
) {
    val label = rail.label ?: rail.action.label()
    val description = rail.description ?: label
    val accent = ownkeyAccentColor()
    val (background, foreground) = when (rail.emphasis) {
        RailEmphasis.NEUTRAL -> OwnkeyBrand.Glass.Key to OwnkeyBrand.Glass.Ink
        RailEmphasis.STRONG -> OwnkeyBrand.Glass.Ink to OwnkeyBrand.Glass.Stage
        RailEmphasis.ACCENT -> accent to accentForeground(accent)
    }
    Surface(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .fillMaxHeight()
            .clickable(onClickLabel = description, onClick = rail.onClick)
            .semantics { contentDescription = description },
        color = background,
        contentColor = foreground,
        shape = CardShape,
        border = if (rail.emphasis == RailEmphasis.NEUTRAL) {
            BorderStroke(1.dp, OwnkeyBrand.Glass.Ink.copy(alpha = 0.10f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            rail.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Two lines keep longer labels readable on narrow phones instead of ellipsizing
            // them to a stub; the rail height has room for both.
            Text(
                text = label,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = if (rail.emphasis == RailEmphasis.NEUTRAL) FontWeight.Normal else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Ink on the user-configurable accent. White stays the brand default on dark accents; a light
 * accent (yellow, mint, pale blue) switches to dark ink so Replace and the replaced check remain
 * readable instead of washing out.
 */
private fun accentForeground(accent: Color): Color =
    if (accent.luminance() > 0.4f) OwnkeyBrand.Glass.Stage else OwnkeyBrand.Glass.Ink
