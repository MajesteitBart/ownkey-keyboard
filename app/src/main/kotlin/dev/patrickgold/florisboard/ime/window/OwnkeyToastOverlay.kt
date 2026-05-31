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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.florisboard.lib.android.OwnkeyToastBus
import org.florisboard.lib.android.OwnkeyToastMessage

@Composable
fun BoxScope.OwnkeyToastOverlay() {
    val windowController = LocalWindowController.current
    val isWindowShown by windowController.isWindowShown.collectAsState()
    var message by remember { mutableStateOf<OwnkeyToastMessage?>(null) }

    DisposableEffect(isWindowShown) {
        if (isWindowShown) {
            val registration = OwnkeyToastBus.registerImeHost()
            onDispose { registration.close() }
        } else {
            message = null
            onDispose { }
        }
    }

    LaunchedEffect(Unit) {
        OwnkeyToastBus.messages.collect { incoming ->
            message = incoming
        }
    }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        delay(current.durationMillis)
        if (message?.id == current.id) {
            message = null
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(100f)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        val current = message ?: return@AnimatedVisibility
        OwnkeyToastBanner(
            message = current.text,
            onDismiss = { message = null },
        )
    }
}

@Composable
private fun OwnkeyToastBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
            )
            .background(
                color = ownkeyToastBackground(message),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
    ) {
        Text(
            text = message,
            color = Color.Black,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clickable(onClick = onDismiss),
        ) {
            Text(
                text = "x",
                color = Color.Black,
                fontSize = 20.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

private fun ownkeyToastBackground(text: String): Color {
    val normalized = text.lowercase()
    return when {
        listOf("success", "successfully", "saved", "copied", "inserted", "gelukt", "opgeslagen", "gekopieerd")
            .any { it in normalized } -> Color(0xFF3EDB83)
        listOf("error", "failed", "failure", "unable", "missing", "could not", "invalid", "fout", "mislukt")
            .any { it in normalized } -> Color(0xFFFF7A7A)
        else -> Color(0xFFF7F7F7)
    }
}
