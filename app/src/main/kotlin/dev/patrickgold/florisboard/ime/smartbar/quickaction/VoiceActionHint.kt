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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.stringRes
import kotlin.math.roundToInt

/** How long the hint stays after a tap so it can be read once the finger is gone. */
internal const val VoiceActionHintLingerMillis = 1_400L

/**
 * One visible request for the hold hint. A new [id] per press keeps the linger timer bound to
 * the press that started it; [lingerMillis] is set once the press ends as a tap.
 */
data class VoiceActionHintRequest(
    val id: Long,
    val lingerMillis: Long? = null,
)

/**
 * Pure presentation state for the dictation key's hold hint.
 *
 * The key owns the gesture and reports where it is; the IME root draws the hint above it. Keeping
 * the state here rather than in the key means the hint survives the key being replaced by the
 * recording row's stop button the moment a tap starts dictation.
 */
@Stable
class VoiceActionHintState {
    private var nextId = 0L

    var anchorBounds: Rect? by mutableStateOf(null)
        private set

    var request: VoiceActionHintRequest? by mutableStateOf(null)
        private set

    fun updateAnchor(bounds: Rect) {
        if (anchorBounds != bounds) anchorBounds = bounds
    }

    /** A press started: show the hint until the gesture resolves. */
    fun show() {
        request = VoiceActionHintRequest(id = ++nextId)
    }

    /** The press ended as a tap: keep the hint briefly so it teaches the hold for next time. */
    fun linger() {
        val current = request ?: return
        if (current.lingerMillis == null) {
            request = current.copy(lingerMillis = VoiceActionHintLingerMillis)
        }
    }

    /** The hold was recognized or the gesture was cancelled: the hint has nothing left to teach. */
    fun hide() {
        request = null
    }

    /** Clear an interrupted press without removing a completed tap's linger or a newer press. */
    fun cancelPress(id: Long?) {
        val current = request ?: return
        if (current.id == id && current.lingerMillis == null) request = null
    }

    /** The linger timer for [id] elapsed; a newer press keeps its own hint. */
    fun expire(id: Long) {
        if (request?.id == id) request = null
    }
}

/**
 * Provided by the IME root window. Absent in the settings app's action editor, where the key is
 * a preview tile and never arbitrates a gesture.
 */
val LocalVoiceActionHintState = staticCompositionLocalOf<VoiceActionHintState?> { null }

private val HintGap = 6.dp
private val HintEdgeInset = 8.dp
private val HintShape = RoundedCornerShape(999.dp)

/**
 * Draws the hold hint bubble just above the dictation key, inside the IME's own window.
 *
 * It lives in the root box rather than in a popup window, so it needs no window token, cannot
 * intercept touches meant for the host app (the area above the keyboard is outside the IME's
 * touchable region), and is positioned from the key's reported bounds the way key previews are.
 * It stays out of the accessibility tree: TalkBack already has the explicit voice rewrite action.
 */
@Composable
fun BoxScope.VoiceActionHintOverlay(state: VoiceActionHintState) {
    val request = state.request
    val anchor = state.anchorBounds
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(request?.id, request?.lingerMillis) {
        val current = request ?: return@LaunchedEffect
        val linger = current.lingerMillis ?: return@LaunchedEffect
        delay(linger)
        state.expire(current.id)
    }

    val motionMillis = if (reducedMotion) 0 else OwnkeyBrand.MotionFastMillis
    // The wrapper has no pointer handling, so it never sits between a touch and the keyboard; the
    // bubble itself is placed from the key's last known bounds, which outlive the key's press.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        AnimatedVisibility(
            visible = request != null && anchor != null,
            modifier = Modifier.placeAbove(anchor ?: Rect.Zero),
            enter = fadeIn(animationSpec = tween(motionMillis)) +
                slideInVertically(animationSpec = tween(motionMillis)) { it / 3 },
            exit = fadeOut(animationSpec = tween(motionMillis)) +
                slideOutVertically(animationSpec = tween(motionMillis)) { it / 3 },
        ) {
            VoiceActionHintBubble()
        }
    }
}

/** Centres the content horizontally over [anchor] and sits it just above the anchor's top edge. */
private fun Modifier.placeAbove(anchor: Rect): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val inset = HintEdgeInset.roundToPx()
    val maxX = (constraints.maxWidth - placeable.width - inset).coerceAtLeast(inset)
    val x = (anchor.center.x - placeable.width / 2f).roundToInt().coerceIn(inset, maxX)
    val y = (anchor.top - placeable.height - HintGap.toPx()).roundToInt().coerceAtLeast(0)
    layout(constraints.maxWidth, constraints.maxHeight) {
        // boundsInRoot is already in physical coordinates, including in RTL layouts.
        placeable.place(x, y)
    }
}

@Composable
private fun VoiceActionHintBubble() {
    Box(
        modifier = Modifier
            .shadow(elevation = 6.dp, shape = HintShape, clip = false)
            .background(color = OwnkeyBrand.Glass.Sheet, shape = HintShape)
            .border(1.dp, OwnkeyBrand.Glass.Ink.copy(alpha = 0.1f), HintShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = stringRes(R.string.voice_rewrite__hold_hint),
            color = OwnkeyBrand.Glass.Ink,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
