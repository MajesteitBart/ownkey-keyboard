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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
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
            .background(Color(0xFF202124))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isRewriting) "Rewriting" else "Rewrite voice",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isRewriting) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = { rewriteManager.closeOptions() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close rewrite voices",
                    tint = Color.White,
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            prompts.forEach { prompt ->
                RewriteVoiceBadge(
                    prompt = prompt,
                    enabled = !isRewriting,
                    onClick = { rewriteManager.rewriteWith(prompt) },
                )
            }
        }

        Text(
            text = "Edit voices in Settings > AI",
            color = Color(0xFFC8C8CC),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RewriteVoiceBadge(
    prompt: RewritePromptPreset,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .widthIn(min = 102.dp, max = 164.dp)
            .heightIn(min = 46.dp)
            .border(
                width = 1.dp,
                color = if (enabled) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) Color(0xFF25272D) else Color(0xFF25272D).copy(alpha = 0.44f),
        contentColor = Color.White,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.04f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFFF56C1E) else Color.White.copy(alpha = 0.38f),
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = prompt.name,
                modifier = Modifier.weight(1f),
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.46f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp,
                maxLines = 2,
            )
        }
    }
}
