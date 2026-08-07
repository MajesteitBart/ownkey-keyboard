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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import dev.patrickgold.florisboard.llmRewriteManager
import dev.patrickgold.florisboard.voiceRewriteUiController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import java.util.Locale

private val PanelEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val PanelMotionMillis = 220
private val PanelGap = 8.dp
private val CardShape = RoundedCornerShape(10.dp)
private val PresetRowHeight = 56.dp
private val MinTouchTarget = 48.dp
private val ActionRailHeight = 52.dp

/** Expanded layouts centre the hub content instead of stretching it to the screen edges. */
private val HubMaxWidth = 1_200.dp
private val VoiceSurfaceMaxWidth = 840.dp

/** Approved dwell time for the `Text replaced` confirmation before the panel closes itself. */
private const val VoiceSuccessConfirmationMillis = 900L

@Composable
private fun instructionDescription(instruction: String): String =
    stringRes(R.string.voice_rewrite__instruction_label, "instruction" to instruction)

/**
 * AI rewrite panel. The two-column preset grid remains the never-moving base layer under a pinned
 * voice-instruction card, with the generating chip, result sheet, done confirmation, and the
 * voice-rewrite surfaces floating above it.
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
    val prompts = RewritePromptPresets.decode(promptsJson)
    val step = uiState.step

    var hubCard by remember { mutableStateOf(VoiceRewriteHubCardState()) }
    LaunchedEffect(availability, voiceModel.surface) {
        // Provider readiness reads the Keystore-backed secret stores; never on the typing thread.
        hubCard = withContext(Dispatchers.IO) { voiceController.hubCardState() }
    }

    // Reset the flow whenever the panel is dismissed by any external path (sparkle toggle,
    // field change, panel swap), so reopening always starts at the options grid and no voice
    // recorder or provider job outlives the panel.
    DisposableEffect(Unit) {
        onDispose {
            rewriteManager.onPanelDismissed()
            voiceController.invalidate(AudioSessionInvalidation.OWNER_CANCELLED)
        }
    }

    val cardAlphaMotionMillis = if (rememberReducedMotion()) 0 else PanelMotionMillis
    val presetsInteractive = step == LlmRewriteManager.RewriteStep.OPTIONS &&
        voiceModel.isPresetGridInteractive &&
        hubCard.isAvailable

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .padding(PanelGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = HubMaxWidth)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            // The voice card stays pinned so a long custom preset list cannot push the visible
            // route off-screen.
            VoiceInstructionCard(
                state = hubCard,
                dimmed = step != LlmRewriteManager.RewriteStep.OPTIONS || !voiceModel.isHub,
                onClick = { voiceController.begin(VoiceRewriteEntryOrigin.REWRITE_HUB) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                prompts.chunked(2).forEach { rowPrompts ->
                    Row(
                        modifier = Modifier
                            .height(PresetRowHeight)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PanelGap),
                    ) {
                        rowPrompts.forEach { prompt ->
                            val isChosen = prompt.id == uiState.activePrompt?.id
                            val cardAlpha by animateFloatAsState(
                                targetValue = when {
                                    !hubCard.isAvailable -> 0.4f
                                    step == LlmRewriteManager.RewriteStep.OPTIONS || isChosen -> 1f
                                    else -> 0.3f
                                },
                                animationSpec = tween(cardAlphaMotionMillis, easing = PanelEasing),
                                label = "rewriteCardAlpha",
                            )
                            RewriteOptionCard(
                                prompt = prompt,
                                enabled = presetsInteractive,
                                unavailableReason = hubCard.unavailableReason,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .alpha(cardAlpha),
                                chosen = isChosen,
                                onClick = { rewriteManager.rewriteWith(prompt) },
                            )
                        }
                        if (rowPrompts.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        RewriteOverlay(visible = step == LlmRewriteManager.RewriteStep.GENERATING) {
            GeneratingOverlay(
                promptName = uiState.activePrompt?.name.orEmpty(),
                onCancel = { rewriteManager.cancelGeneration() },
            )
        }
        RewriteOverlay(visible = step == LlmRewriteManager.RewriteStep.RESULT) {
            ResultOverlay(
                promptName = uiState.activePrompt?.name.orEmpty(),
                resultText = uiState.resultText.orEmpty(),
                onBack = { rewriteManager.backToOptions() },
                onRetry = { rewriteManager.tryAgain() },
                onInsert = { rewriteManager.insertResult() },
            )
        }
        RewriteOverlay(visible = step == LlmRewriteManager.RewriteStep.DONE) {
            DoneOverlay()
        }

        VoiceRewriteOverlays(
            model = voiceModel,
            controller = voiceController,
        )
    }
}

/**
 * Pinned `Tell Ownkey what to change` action.
 *
 * It states the resolved intent, names the two configured providers so the data path is visible
 * before first use, and — while an incognito or secure session blocks cloud AI — renders visibly
 * disabled with the reason instead of disappearing or failing silently on tap.
 */
@Composable
private fun VoiceInstructionCard(
    state: VoiceRewriteHubCardState,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = ownkeyAccentColor()
    val unavailableText = state.unavailableReason?.text()
    val title = stringRes(R.string.voice_rewrite__card_title)
    val supportingText = when {
        unavailableText != null -> unavailableText
        state.selectionCharacterCount != null -> stringRes(
            R.string.voice_rewrite__card_summary_selection,
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
            .alpha(if (dimmed || !state.isAvailable) 0.45f else 1f)
            .clickable(enabled = state.isAvailable, onClickLabel = title, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(title, supportingText, providerText)
                    .joinToString(separator = ". ")
            },
        color = OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shadowElevation = 2.dp,
        shape = CardShape,
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

/** Voice-specific surfaces layered over the stable preset grid. */
@Composable
private fun VoiceRewriteOverlays(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.DISCLOSURE) {
        val disclosure = model.disclosure
        if (disclosure != null) {
            VoiceDisclosureOverlay(
                disclosure = disclosure,
                onContinue = { controller.acknowledgeDisclosure() },
                onOpenSettings = { controller.openAiSettings() },
                onBack = { controller.back() },
            )
        }
    }
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.RECOVERY) {
        VoiceRecoveryOverlay(model = model, controller = controller)
    }
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.TARGETING) {
        VoiceTargetingOverlay(model = model, onCancel = { controller.cancel() })
    }
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.PROCESSING) {
        VoiceProcessingOverlay(model = model, onCancel = { controller.cancel() })
    }
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.RESULT) {
        VoiceResultOverlay(model = model, controller = controller)
    }
    RewriteOverlay(visible = model.surface == VoiceRewriteSurface.SUCCESS) {
        VoiceSuccessOverlay(onFinished = { controller.finishAfterReplacement() })
    }
}

/**
 * `Understanding instruction…` and `Rewriting selected text…`.
 *
 * The recognized instruction is shown here so the user can see what was heard, and it is never
 * committed into the host editor. Cancel remains available and aborts the pending request.
 */
@Composable
private fun VoiceProcessingOverlay(
    model: VoiceRewriteUiModel,
    onCancel: () -> Unit,
) {
    val message = model.statusMessage ?: return
    VoiceSurfaceSheet {
        model.recognizedInstruction?.let { instruction ->
            InstructionChip(instruction = instruction, onRecordAgain = null)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp,
                color = ownkeyAccentColor(),
                trackColor = OwnkeyBrand.Glass.Ink.copy(alpha = 0.12f),
            )
            Text(
                text = message.text(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 15.sp,
            )
        }
        VoiceActionRow(
            secondary = emptyList(),
            primary = VoiceRewriteAction.CANCEL to onCancel,
        )
    }
}

/**
 * Mandatory review before mutation.
 *
 * The captured source text is never rendered in the keyboard. When target verification fails the
 * generated result stays visible and the rail changes to `Copy result` and `Close`, so nothing is
 * written into an uncertain target.
 */
@Composable
private fun VoiceResultOverlay(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val resultText = model.resultText ?: return
    val canReplace = VoiceRewriteAction.REPLACE in model.actions
    VoiceSurfaceSheet {
        model.statusMessage?.let { message ->
            Text(
                text = message.text(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = OwnkeyBrand.WarningYellow,
                fontSize = 13.sp,
            )
        }
        model.recognizedInstruction?.let { instruction ->
            InstructionChip(
                instruction = instruction,
                onRecordAgain = { controller.recordInstructionAgain() }
                    .takeIf { VoiceRewriteAction.RECORD_AGAIN in model.actions },
            )
        }
        Text(
            text = resultText,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = resultText },
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
        if (canReplace) {
            VoiceActionRow(
                secondary = listOf(
                    VoiceRewriteAction.BACK to { controller.back() },
                    VoiceRewriteAction.TRY_AGAIN to { controller.tryAgain() },
                ),
                primary = VoiceRewriteAction.REPLACE to { controller.replaceResult() },
            )
        } else {
            VoiceActionRow(
                secondary = listOf(VoiceRewriteAction.CLOSE to { controller.close() }),
                primary = VoiceRewriteAction.COPY_RESULT to { controller.copyResult() },
            )
        }
    }
}

/**
 * `Text replaced` confirmation. It closes automatically and deliberately creates no persistent undo
 * control or stored result, instruction, or target history; recovery stays with the host editor's
 * own undo.
 */
@Composable
private fun VoiceSuccessOverlay(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(VoiceSuccessConfirmationMillis)
        onFinished()
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ownkeyAccentColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = OwnkeyBrand.Glass.Ink,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = VoiceRewriteMessage.TEXT_REPLACED.text(),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = OwnkeyBrand.Glass.InkSoft,
            fontSize = 15.sp,
        )
    }
}

/**
 * Recognized instruction in a bounded chip. It is truncated to two lines visually but exposed in
 * full to TalkBack, and its mic action records a replacement instruction against the same target.
 */
@Composable
private fun InstructionChip(
    instruction: String,
    onRecordAgain: (() -> Unit)?,
) {
    val recordAgainLabel = stringRes(R.string.voice_rewrite__action_record_again)
    val instructionDescription = instructionDescription(instruction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = instructionDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "“$instruction”",
            modifier = Modifier.weight(1f),
            color = OwnkeyBrand.Glass.InkSoft,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRecordAgain != null) {
            Box(
                modifier = Modifier
                    .size(MinTouchTarget)
                    .clickable(onClickLabel = recordAgainLabel, onClick = onRecordAgain)
                    .semantics { contentDescription = recordAgainLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                    contentDescription = null,
                    tint = ownkeyAccentColor(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun VoiceDisclosureOverlay(
    disclosure: VoiceRewriteProviderDisclosure,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    VoiceSurfaceSheet {
        Text(
            text = stringRes(R.string.voice_rewrite__disclosure_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringRes(R.string.voice_rewrite__disclosure_body),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            color = OwnkeyBrand.Glass.InkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
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
        VoiceActionRow(
            secondary = listOf(
                VoiceRewriteAction.BACK to onBack,
                VoiceRewriteAction.OPEN_AI_SETTINGS to onOpenSettings,
            ),
            primary = VoiceRewriteAction.CONTINUE to onContinue,
        )
    }
}

@Composable
private fun VoiceRecoveryOverlay(
    model: VoiceRewriteUiModel,
    controller: VoiceRewriteUiController,
) {
    val message = model.statusMessage ?: return
    VoiceSurfaceSheet {
        Text(
            text = message.text(),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        model.scopeLabel?.let { scope ->
            Text(text = scope.text(), color = OwnkeyBrand.Glass.InkSoft, fontSize = 13.sp)
        }
        val secondary = buildList {
            if (VoiceRewriteAction.OPEN_AI_SETTINGS in model.actions) {
                add(VoiceRewriteAction.OPEN_AI_SETTINGS to { controller.openAiSettings() })
            }
            if (VoiceRewriteAction.OPEN_INCOGNITO_SETTING in model.actions) {
                add(VoiceRewriteAction.OPEN_INCOGNITO_SETTING to { controller.openIncognitoSetting() })
            }
            if (VoiceRewriteAction.TRY_AGAIN in model.actions) {
                add(VoiceRewriteAction.TRY_AGAIN to { controller.tryAgain() })
            }
            if (VoiceRewriteAction.RECORD_AGAIN in model.actions) {
                add(VoiceRewriteAction.RECORD_AGAIN to { controller.recordInstructionAgain() })
            }
        }
        VoiceActionRow(
            secondary = secondary,
            primary = VoiceRewriteAction.CLOSE to { controller.close() },
        )
    }
}

@Composable
private fun VoiceTargetingOverlay(
    model: VoiceRewriteUiModel,
    onCancel: () -> Unit,
) {
    val message = model.statusMessage ?: return
    VoiceSurfaceSheet {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp,
                color = ownkeyAccentColor(),
                trackColor = OwnkeyBrand.Glass.Ink.copy(alpha = 0.12f),
            )
            Text(
                text = message.text(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                fontSize = 15.sp,
            )
        }
        VoiceActionRow(
            secondary = emptyList(),
            primary = VoiceRewriteAction.CANCEL to onCancel,
        )
    }
}

/** Bottom sheet shell shared by every voice surface, centred within its maximum width. */
@Composable
private fun VoiceSurfaceSheet(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = VoiceSurfaceMaxWidth),
            color = OwnkeyBrand.Glass.Sheet,
            contentColor = OwnkeyBrand.Glass.Ink,
            shape = CardShape,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * Fixed action rail. Secondary actions keep their 48 dp targets and the primary action is always
 * visible, so a long result body can scroll without stranding the commit action.
 */
@Composable
private fun VoiceActionRow(
    secondary: List<Pair<VoiceRewriteAction, () -> Unit>>,
    primary: Pair<VoiceRewriteAction, () -> Unit>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A fixed rail: the result body above it scrolls, the actions never grow to fill the
            // sheet and never leave the screen.
            .height(ActionRailHeight),
        horizontalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        secondary.forEach { (action, onClick) ->
            VoiceActionButton(
                action = action,
                onClick = onClick,
                modifier = Modifier.weight(1f),
                emphasized = false,
            )
        }
        VoiceActionButton(
            action = primary.first,
            onClick = primary.second,
            modifier = Modifier.weight(1.4f),
            emphasized = true,
        )
    }
}

@Composable
private fun VoiceActionButton(
    action: VoiceRewriteAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean,
) {
    val label = action.label()
    Surface(
        modifier = modifier
            .heightIn(min = MinTouchTarget)
            .fillMaxHeight()
            .clickable(onClickLabel = label, onClick = onClick),
        color = if (emphasized) ownkeyAccentColor() else OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = CardShape,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp),
                fontSize = 15.sp,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RewriteOverlay(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    // Reduced motion drops the slide and cross-fade; the surface still appears and disappears, and
    // every state keeps its visible label.
    val motionMillis = if (rememberReducedMotion()) 0 else PanelMotionMillis
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motionMillis, easing = PanelEasing)) +
            slideInVertically(tween(motionMillis, easing = PanelEasing)) { it / 16 },
        exit = fadeOut(tween(motionMillis / 2)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun RewriteOptionCard(
    prompt: RewritePromptPreset,
    enabled: Boolean,
    unavailableReason: CloudAiUnavailableReason?,
    modifier: Modifier = Modifier,
    chosen: Boolean = false,
    onClick: () -> Unit,
) {
    val reasonText = unavailableReason?.text()
    Surface(
        modifier = modifier
            .clickable(enabled = enabled, onClickLabel = prompt.name, onClick = onClick)
            .semantics {
                // Unavailability is announced, not only rendered as a dimmed card.
                contentDescription = if (reasonText == null) prompt.name else "${prompt.name}. $reasonText"
            },
        color = if (chosen) OwnkeyBrand.Glass.KeyPressed else OwnkeyBrand.Glass.Key,
        contentColor = OwnkeyBrand.Glass.Ink,
        shadowElevation = 2.dp,
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(width = 1.dp, color = OwnkeyBrand.Glass.Ink.copy(alpha = 0.14f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = promptIcon(prompt),
                    contentDescription = null,
                    tint = OwnkeyBrand.Glass.InkSoft,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = prompt.name,
                modifier = Modifier.weight(1f),
                color = OwnkeyBrand.Glass.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun promptIcon(prompt: RewritePromptPreset): ImageVector {
    return when {
        prompt.id.startsWith("rewrite_") || prompt.id.contains("translate") -> Icons.Outlined.Translate
        prompt.id.contains("grammar") || prompt.id.contains("fix") -> Icons.Outlined.CheckCircle
        prompt.id.contains("short") -> Icons.Outlined.UnfoldLess
        prompt.id.contains("business") || prompt.id.contains("formal") -> Icons.Outlined.Work
        prompt.id.contains("casual") -> Icons.Outlined.Chat
        else -> Icons.Outlined.AutoFixHigh
    }
}

@Composable
private fun GeneratingOverlay(
    promptName: String,
    onCancel: () -> Unit,
) {
    val accentColor = ownkeyAccentColor()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = accentColor,
            contentColor = OwnkeyBrand.Glass.Ink,
            shape = CircleShape,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    color = OwnkeyBrand.Glass.Ink,
                    trackColor = OwnkeyBrand.Glass.Ink.copy(alpha = 0.35f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = generatingLabel(promptName),
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val cancelLabel = stringRes(R.string.rewrite_panel__action_cancel)
        Surface(
            modifier = Modifier
                .heightIn(min = MinTouchTarget)
                .clickable(onClickLabel = cancelLabel, onClick = onCancel),
            color = OwnkeyBrand.Glass.CancelCapsule,
            contentColor = OwnkeyBrand.Glass.InkSoft,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = cancelLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                )
            }
        }
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
private fun ResultOverlay(
    promptName: String,
    resultText: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onInsert: () -> Unit,
) {
    val accentColor = ownkeyAccentColor()
    val backLabel = stringRes(R.string.rewrite_panel__action_back_to_options)
    val retryLabel = stringRes(R.string.rewrite_panel__action_try_again)
    val insertLabel = stringRes(R.string.rewrite_panel__action_insert)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            color = OwnkeyBrand.Glass.Sheet,
            contentColor = OwnkeyBrand.Glass.Ink,
            shape = CardShape,
            shadowElevation = 8.dp,
        ) {
            Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    text = promptName.uppercase(Locale.getDefault()),
                    modifier = Modifier.padding(top = 5.dp, end = 14.dp),
                    color = accentColor,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
                Text(
                    text = resultText,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    fontSize = 17.sp,
                    lineHeight = 25.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(PanelGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            Surface(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .clickable(onClickLabel = backLabel, onClick = onBack),
                color = OwnkeyBrand.Glass.Sheet,
                contentColor = OwnkeyBrand.Glass.InkSoft,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = backLabel,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = retryLabel, onClick = onRetry),
                color = OwnkeyBrand.Glass.Sheet,
                contentColor = OwnkeyBrand.Glass.InkSoft,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = retryLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = insertLabel, onClick = onInsert),
                color = accentColor,
                contentColor = OwnkeyBrand.Glass.Ink,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = insertLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneOverlay() {
    val accentColor = ownkeyAccentColor()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val reducedMotion = rememberReducedMotion()
        val checkScale = remember { Animatable(if (reducedMotion) 1f else 0.4f) }
        LaunchedEffect(reducedMotion) {
            if (reducedMotion) {
                checkScale.snapTo(1f)
            } else {
                checkScale.animateTo(1f, animationSpec = tween(350, easing = PanelEasing))
            }
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(checkScale.value)
                .background(accentColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = OwnkeyBrand.Glass.Ink,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringRes(R.string.rewrite_panel__state_inserted),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = OwnkeyBrand.Glass.InkSoft,
            fontSize = 15.sp,
        )
    }
}
