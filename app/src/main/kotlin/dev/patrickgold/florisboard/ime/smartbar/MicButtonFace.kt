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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackPhase
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion

/** What the dictation button shows. Tapping it always does what the face suggests. */
enum class MicFaceState {
    IDLE,
    LISTENING,
    PAUSED,
    TRANSCRIBING,
    SUCCESS,
    ERROR,
    UNAVAILABLE,
}

/**
 * The idle look. The voice-only bar leads with a solid orange button, because dictation is all it does; the
 * keyboard's mic key stays quiet next to the other toolbar actions.
 */
enum class MicIdleStyle {
    SOLID,
    QUIET,
}

/** Maps the shared dictation feedback onto the button. An editor without AI shows the unavailable face. */
fun micFaceState(phase: VoiceActionFeedbackPhase, aiUnavailable: Boolean): MicFaceState = when {
    aiUnavailable -> MicFaceState.UNAVAILABLE
    else -> when (phase) {
        VoiceActionFeedbackPhase.IDLE -> MicFaceState.IDLE
        VoiceActionFeedbackPhase.RECORDING -> MicFaceState.LISTENING
        VoiceActionFeedbackPhase.PAUSED -> MicFaceState.PAUSED
        VoiceActionFeedbackPhase.PROCESSING -> MicFaceState.TRANSCRIBING
        VoiceActionFeedbackPhase.SUCCESS -> MicFaceState.SUCCESS
        VoiceActionFeedbackPhase.ERROR -> MicFaceState.ERROR
    }
}

// Proportions of the Ownkey mic state sheet, relative to the button diameter. Glyph sizes cover the
// drawable's 24 unit box, whose 20 unit Heroicons glyph sits 2 units in from each edge.
private const val MicGlyph = 0.552f
private const val StopGlyph = 0.432f
private const val StatusGlyph = 0.504f
private const val ListeningRingRadius = 0.42f
private const val ListeningRingStroke = 0.06f
private const val TrackRadius = 0.41f
private const val TrackStroke = 0.05f

/**
 * The face of the dictation button, shared by the keyboard's mic key and the voice-only bar so both always
 * show the same state the same way. It draws no touch handling; the caller owns input and semantics.
 */
@Composable
fun MicButtonFace(
    state: MicFaceState,
    idleStyle: MicIdleStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val background = when (state) {
        MicFaceState.IDLE -> if (idleStyle == MicIdleStyle.SOLID) OwnkeyBrand.Ember else OwnkeyBrand.Coal
        MicFaceState.LISTENING -> OwnkeyBrand.Ember
        MicFaceState.PAUSED -> OwnkeyBrand.Ember.copy(alpha = 0.55f)
        MicFaceState.SUCCESS -> OwnkeyBrand.Glass.Success
        MicFaceState.TRANSCRIBING,
        MicFaceState.ERROR,
        MicFaceState.UNAVAILABLE,
        -> OwnkeyBrand.Coal
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            MicFaceState.LISTENING -> Canvas(Modifier.size(size)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = this.size.minDimension * ListeningRingRadius,
                    style = Stroke(width = this.size.minDimension * ListeningRingStroke),
                )
            }
            MicFaceState.TRANSCRIBING -> TranscribingRing(size)
            else -> Unit
        }
        val (glyph, tint, glyphSize) = when (state) {
            MicFaceState.IDLE -> Triple(
                R.drawable.ic_hero_microphone,
                if (idleStyle == MicIdleStyle.SOLID) Color.White else OwnkeyBrand.Ember,
                MicGlyph,
            )
            MicFaceState.LISTENING, MicFaceState.PAUSED -> Triple(R.drawable.ic_hero_stop, Color.White, StopGlyph)
            MicFaceState.SUCCESS -> Triple(R.drawable.ic_hero_check, Color.White, StatusGlyph)
            MicFaceState.ERROR -> Triple(R.drawable.ic_hero_exclamation_circle, OwnkeyBrand.Bone, StatusGlyph)
            MicFaceState.UNAVAILABLE -> Triple(R.drawable.ic_hero_no_symbol, OwnkeyBrand.Stone, StatusGlyph)
            // The ring is the whole face while the transcript is on its way.
            MicFaceState.TRANSCRIBING -> return@Box
        }
        Icon(
            imageVector = ImageVector.vectorResource(id = glyph),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * glyphSize),
        )
    }
}

@Composable
private fun TranscribingRing(size: Dp) {
    // Under reduced motion the arc still marks transcribing, but it stays put.
    val angle = if (rememberReducedMotion()) {
        -90f
    } else {
        rememberInfiniteTransition(label = "micTranscribing").animateFloat(
            initialValue = -90f,
            targetValue = 270f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900, easing = LinearEasing)),
            label = "micTranscribingAngle",
        ).value
    }
    Canvas(Modifier.size(size)) {
        val diameter = this.size.minDimension
        val radius = diameter * TrackRadius
        val stroke = diameter * TrackStroke
        drawCircle(color = Color.White.copy(alpha = 0.1f), radius = radius, style = Stroke(width = stroke))
        drawArc(
            color = OwnkeyBrand.Ember,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
