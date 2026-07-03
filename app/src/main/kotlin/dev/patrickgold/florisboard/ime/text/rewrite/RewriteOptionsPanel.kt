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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.llmRewriteManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import java.util.Locale

private val PanelEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val PanelMotionMillis = 220
private val PanelGap = 8.dp
private val CardShape = RoundedCornerShape(10.dp)

/**
 * AI rewrite panel following the Liquid Glass mockup: a 2-column options grid as the never-moving
 * base layer, with the generating chip, result sheet, and done confirmation floating above it.
 */
@Composable
fun RewriteOptionsPanel(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val rewriteManager by context.llmRewriteManager()
    val uiState by rewriteManager.uiStateFlow.collectAsState()
    val promptsJson by prefs.voxtral.rewritePrompts.collectAsState()
    val prompts = RewritePromptPresets.decode(promptsJson)
    val step = uiState.step

    // Reset the flow whenever the panel is dismissed by any external path (sparkle toggle,
    // field change, panel swap), so reopening always starts at the options grid.
    DisposableEffect(Unit) {
        onDispose { rewriteManager.onPanelDismissed() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .padding(PanelGap),
    ) {
        // Base layer: the options grid. Dims (but never resizes) while an overlay is on top;
        // the chosen card stays fully lit.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            prompts.chunked(2).forEach { rowPrompts ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PanelGap),
                ) {
                    rowPrompts.forEach { prompt ->
                        val isChosen = prompt.id == uiState.activePrompt?.id
                        val cardAlpha by animateFloatAsState(
                            targetValue = when {
                                step == LlmRewriteManager.RewriteStep.OPTIONS || isChosen -> 1f
                                else -> 0.3f
                            },
                            animationSpec = tween(PanelMotionMillis, easing = PanelEasing),
                            label = "rewriteCardAlpha",
                        )
                        RewriteOptionCard(
                            prompt = prompt,
                            enabled = step == LlmRewriteManager.RewriteStep.OPTIONS,
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
    }
}

@Composable
private fun RewriteOverlay(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PanelMotionMillis, easing = PanelEasing)) +
            slideInVertically(tween(PanelMotionMillis, easing = PanelEasing)) { it / 16 },
        exit = fadeOut(tween(PanelMotionMillis / 2)),
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
    modifier: Modifier = Modifier,
    chosen: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = OwnkeyBrand.Glass.Accent,
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
        Surface(
            modifier = Modifier.clickable(onClick = onCancel),
            color = OwnkeyBrand.Glass.CancelCapsule,
            contentColor = OwnkeyBrand.Glass.InkSoft,
            shape = CircleShape,
        ) {
            Text(
                text = "Cancel",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp,
            )
        }
    }
}

private fun generatingLabel(promptName: String): String {
    if (promptName.isBlank()) return "Rewriting…"
    val lowered = promptName.lowercase(Locale.getDefault())
    return if (lowered.startsWith("rewrite")) {
        // e.g. "Rewrite in Dutch" -> "Rewriting in Dutch…"
        "Rewriting" + promptName.removePrefix("Rewrite") + "…"
    } else {
        "Rewriting — $lowered…"
    }
}

@Composable
private fun ResultOverlay(
    promptName: String,
    resultText: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onInsert: () -> Unit,
) {
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
                    color = OwnkeyBrand.Glass.Accent,
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
                    .clickable(onClick = onBack),
                color = OwnkeyBrand.Glass.Sheet,
                contentColor = OwnkeyBrand.Glass.InkSoft,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back to options",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onRetry),
                color = OwnkeyBrand.Glass.Sheet,
                contentColor = OwnkeyBrand.Glass.InkSoft,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "Try again", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Surface(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .clickable(onClick = onInsert),
                color = OwnkeyBrand.Glass.Accent,
                contentColor = OwnkeyBrand.Glass.Ink,
                shape = CardShape,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "Insert", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DoneOverlay() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val checkScale = remember { Animatable(0.4f) }
        LaunchedEffect(Unit) {
            checkScale.animateTo(1f, animationSpec = tween(350, easing = PanelEasing))
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(checkScale.value)
                .background(OwnkeyBrand.Glass.Accent, CircleShape),
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
            text = "Inserted",
            color = OwnkeyBrand.Glass.InkSoft,
            fontSize = 15.sp,
        )
    }
}
