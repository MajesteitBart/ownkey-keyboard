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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButtonAspectRatio
import org.florisboard.lib.compose.stringRes

/**
 * Smartbar centre while the AI rewrite panel is open.
 *
 * Suggestions are hidden because the panel owns the editor's next action; a quiet title keeps the
 * row from reading as an empty gap without adding a second status owner.
 */
@Composable
fun RewritePanelSmartbarTitle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(R.string.rewrite_panel__smartbar_title),
            color = OwnkeyBrand.Bone.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The single dismissal affordance for the open panel, in the slot the dictation key normally uses.
 * Closing disposes the panel, which cancels any voice-rewrite session and releases the recorder.
 */
@Composable
fun RewritePanelCloseAction(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringRes(R.string.rewrite_panel__action_close)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(FlorisImeSizing.smartbarHeight * QuickActionButtonAspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        val buttonSize = (maxHeight - 8.dp).coerceIn(40.dp, 56.dp)
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .size(buttonSize)
                .clip(CircleShape)
                .background(OwnkeyBrand.Glass.Key)
                .border(1.dp, OwnkeyBrand.Bone.copy(alpha = 0.12f), CircleShape)
                .clickable(onClickLabel = label, onClick = onClose)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = OwnkeyBrand.Bone.copy(alpha = 0.9f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
