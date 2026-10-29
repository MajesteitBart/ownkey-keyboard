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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.theme.rememberKeyboardPalette
import kotlinx.coroutines.delay
import org.florisboard.lib.android.OwnkeyToastBus
import org.florisboard.lib.android.OwnkeyToastMessage
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

/** Room above the voice-only bar that a toast needs; with less, it goes below the bar. */
private val ToastRoomAboveBar = 96.dp

/** Where a toast sits around the voice-only bar; [offset] is from the root's bottom, or from its top when [belowBar]. */
internal data class ToastPlacement(val belowBar: Boolean, val offset: Dp)

/**
 * Keeps a toast next to the voice-only bar without covering the bar's own status: just above the bar, or
 * just below it when the bar was dragged so high that a toast above would leave the screen.
 */
internal fun toastPlacementAroundBar(bar: DpRect, root: DpRect): ToastPlacement =
    if (bar.top - root.top < ToastRoomAboveBar) {
        ToastPlacement(belowBar = true, offset = bar.bottom - root.top + 8.dp)
    } else {
        ToastPlacement(belowBar = false, offset = (root.bottom - bar.top + 8.dp).coerceAtLeast(14.dp))
    }

@Composable
fun BoxScope.OwnkeyToastOverlay() {
    val windowController = LocalWindowController.current
    val isWindowShown by windowController.isWindowShown.collectAsState()
    val isVoiceOnly by windowController.isVoiceOnlyActive.collectAsState()
    val rootInsets by windowController.activeRootInsets.collectAsState()
    val windowInsets by windowController.activeWindowInsets.collectAsState()
    var message by remember { mutableStateOf<OwnkeyToastMessage?>(null) }
    val voiceBar = windowInsets?.boundsDp?.takeIf { isVoiceOnly }
    val placement = voiceBar?.let { toastPlacementAroundBar(it, rootInsets.boundsDp) }
    val toastAlignment = if (placement?.belowBar == true) Alignment.TopCenter else Alignment.BottomCenter
    val toastPadding = when {
        placement == null -> PaddingValues(horizontal = 18.dp, vertical = 14.dp)
        placement.belowBar -> PaddingValues(start = 18.dp, end = 18.dp, top = placement.offset)
        else -> PaddingValues(start = 18.dp, end = 18.dp, bottom = placement.offset)
    }

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
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(OwnkeyBrand.MotionFastMillis)) +
            slideInVertically(animationSpec = androidx.compose.animation.core.tween(OwnkeyBrand.MotionFastMillis)) { it / 2 },
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(OwnkeyBrand.MotionFastMillis)) +
            slideOutVertically(animationSpec = androidx.compose.animation.core.tween(OwnkeyBrand.MotionFastMillis)) { it / 2 },
        modifier = Modifier
            .align(toastAlignment)
            .zIndex(100f)
            // Over the full keyboard, stay above the system's hide and switch-keyboard buttons.
            .then(if (placement == null) Modifier.safeDrawingPadding() else Modifier)
            .padding(toastPadding),
    ) {
        val current = message ?: return@AnimatedVisibility
        OwnkeyToastBanner(
            message = current.text,
            // Around the voice-only bar the app owns every touch, so the toast only times out.
            onDismiss = if (voiceBar == null) ({ message = null }) else null,
        )
    }
}

@Composable
private fun OwnkeyToastBanner(
    message: String,
    onDismiss: (() -> Unit)?,
) {
    // The popup fill with label text, like every floating surface on the keyboard. On a light keyboard the
    // toast inverts to near-black so it still reads as floating; nothing is lighter than the white keys.
    val palette = rememberKeyboardPalette()
    val fill = if (palette.isLight) palette.pill else palette.popup
    val ink = if (palette.isLight) palette.onPill else palette.popupInk
    val tone = remember(message) { ownkeyToastTone(message) }
    val shape = RoundedCornerShape(16.dp)
    // The 48dp dismiss target sets the row height, so the row itself needs little vertical padding.
    val verticalPadding = if (onDismiss != null) 4.dp else 10.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = fill, shape = shape)
            .padding(start = 16.dp, top = verticalPadding, end = 0.dp, bottom = verticalPadding),
    ) {
        tone.dotColor()?.let { dot ->
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .background(color = dot, shape = CircleShape),
            )
        }
        Text(
            text = message,
            color = ink,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = if (onDismiss != null) 0.dp else 16.dp),
        )
        if (onDismiss != null) {
            val dismissLabel = stringRes(R.string.ime__toast__dismiss)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = dismissLabel, onClick = onDismiss)
                    .semantics { contentDescription = dismissLabel },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** How a toast message reads. The dot carries the tone; the words always say it too. */
internal enum class OwnkeyToastTone { NEUTRAL, SUCCESS, WARNING, ERROR }

private fun OwnkeyToastTone.dotColor(): Color? = when (this) {
    OwnkeyToastTone.NEUTRAL -> null
    OwnkeyToastTone.SUCCESS -> OwnkeyTokens.Success
    OwnkeyToastTone.WARNING -> OwnkeyTokens.Warning
    OwnkeyToastTone.ERROR -> OwnkeyTokens.Danger
}

internal fun ownkeyToastTone(text: String): OwnkeyToastTone {
    val normalized = text.lowercase()
    return when {
        listOf("success", "successfully", "saved", "copied", "inserted", "gelukt", "opgeslagen", "gekopieerd")
            .any { it in normalized } -> OwnkeyToastTone.SUCCESS
        listOf("warning", "permission", "paused", "let op", "machtiging", "toestemming")
            .any { it in normalized } -> OwnkeyToastTone.WARNING
        listOf("error", "failed", "failure", "unable", "missing", "could not", "invalid", "fout", "mislukt")
            .any { it in normalized } -> OwnkeyToastTone.ERROR
        else -> OwnkeyToastTone.NEUTRAL
    }
}
