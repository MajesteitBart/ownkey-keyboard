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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RewriteOptionsPanel(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val rewriteManager by context.llmRewriteManager()
    val rewriteState by rewriteManager.stateFlow.collectAsState()
    val promptsJson by prefs.voxtral.rewritePrompts.collectAsState()
    val prompts = RewritePromptPresets.decode(promptsJson)
    val isRewriting = rewriteState == LlmRewriteManager.RewriteState.REWRITING

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .background(OwnkeyBrand.Key)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            prompts.forEach { prompt ->
                RewriteActionTile(
                    prompt = prompt,
                    enabled = !isRewriting,
                    onClick = { rewriteManager.rewriteWith(prompt) },
                )
            }
        }

        if (isRewriting) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = OwnkeyBrand.SignalOrange,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun RewriteActionTile(
    prompt: RewritePromptPreset,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val icon = when {
        prompt.id.startsWith("rewrite_") -> Icons.Default.Translate
        else -> Icons.Default.AutoFixHigh
    }
    Surface(
        modifier = Modifier
            .widthIn(min = 210.dp, max = 360.dp)
            .height(100.dp)
            .border(
                width = 1.dp,
                color = OwnkeyBrand.Bone.copy(alpha = if (enabled) 0.04f else 0.02f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        color = OwnkeyBrand.PanelRaised.copy(alpha = if (enabled) 0.88f else 0.42f),
        contentColor = OwnkeyBrand.Bone,
        shadowElevation = 4.dp,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RewriteActionIcon(
                imageVector = icon,
                enabled = enabled,
            )
            Text(
                text = prompt.name,
                modifier = Modifier.weight(1f),
                color = OwnkeyBrand.Bone.copy(alpha = if (enabled) 1f else 0.42f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RewriteActionIcon(
    imageVector: ImageVector,
    enabled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .border(
                width = 1.dp,
                color = OwnkeyBrand.Bone.copy(alpha = if (enabled) 0.07f else 0.03f),
                shape = CircleShape,
            )
            .background(Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = OwnkeyBrand.Bone.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(28.dp),
        )
    }
}
