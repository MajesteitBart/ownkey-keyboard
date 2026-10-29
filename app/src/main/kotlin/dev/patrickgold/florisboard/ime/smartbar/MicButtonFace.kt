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

import androidx.annotation.StringRes
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
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.theme.rememberKeyboardAccent
import dev.patrickgold.florisboard.lib.util.rememberReducedMotion
import org.florisboard.lib.compose.onAccentFor
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

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
 * The idle look. The keyboard's mic key and the voice-only bar lead with a solid accent button; the quiet style
 * (a key-coloured well with an accent glyph) is for secondary spots such as the floating split controls.
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

/** What the dictation button does when activated, for its accessibility name and click label. */
@StringRes
fun micActionLabelRes(state: MicFaceState): Int = when (state) {
    MicFaceState.LISTENING, MicFaceState.PAUSED -> R.string.ime__mic__stop_dictation
    MicFaceState.TRANSCRIBING -> R.string.voice_recording__processing
    else -> R.string.voice_rewrite__action_start_dictation
}

/** The state TalkBack reads after the name, or null when the name says it all. */
@StringRes
fun micStateLabelRes(state: MicFaceState): Int? = when (state) {
    MicFaceState.LISTENING -> R.string.ime__mic__recording
    MicFaceState.PAUSED -> R.string.ime__mic__paused
    else -> null
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

/** The check on the green success face, picked like any glyph on a coloured fill. */
private val SuccessGlyph = onAccentFor(OwnkeyBrand.Glass.Success)

/**
 * The face of the dictation button, shared by the keyboard's mic key and the voice-only bar so both always
 * show the same state the same way. It draws no touch handling; the caller owns input and semantics.
 *
 * Colours come from the keyboard theme: the effective accent ([rememberKeyboardAccent]) for the solid face,
 * the key fill for the quiet well, and the key label and toolbar icon colours for the error and unavailable
 * glyphs, so every face stays legible on Signal Bone too.
 */
@Composable
fun MicButtonFace(
    state: MicFaceState,
    idleStyle: MicIdleStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val accent = rememberKeyboardAccent()
    val keyStyle = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val well = keyStyle.background(default = OwnkeyBrand.Coal)
    val label = keyStyle.foreground(default = OwnkeyBrand.Bone)
    val quietIcon = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionKey.elementName)
        .foreground(default = OwnkeyBrand.Stone)
    val background = when (state) {
        MicFaceState.IDLE -> if (idleStyle == MicIdleStyle.SOLID) accent.color else well
        MicFaceState.LISTENING -> accent.color
        MicFaceState.PAUSED -> accent.color.copy(alpha = 0.55f)
        MicFaceState.SUCCESS -> OwnkeyBrand.Glass.Success
        MicFaceState.TRANSCRIBING,
        MicFaceState.ERROR,
        MicFaceState.UNAVAILABLE,
        -> well
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
                    color = accent.onColor.copy(alpha = 0.45f),
                    radius = this.size.minDimension * ListeningRingRadius,
                    style = Stroke(width = this.size.minDimension * ListeningRingStroke),
                )
            }
            MicFaceState.TRANSCRIBING -> TranscribingRing(size, track = label, arc = accent.color)
            else -> Unit
        }
        val (glyph, tint, glyphSize) = when (state) {
            MicFaceState.IDLE -> Triple(
                R.drawable.ic_hero_microphone,
                if (idleStyle == MicIdleStyle.SOLID) accent.onColor else accent.color,
                MicGlyph,
            )
            MicFaceState.LISTENING, MicFaceState.PAUSED -> Triple(R.drawable.ic_hero_stop, accent.onColor, StopGlyph)
            MicFaceState.SUCCESS -> Triple(R.drawable.ic_hero_check, SuccessGlyph, StatusGlyph)
            MicFaceState.ERROR -> Triple(R.drawable.ic_hero_exclamation_circle, label, StatusGlyph)
            MicFaceState.UNAVAILABLE -> Triple(R.drawable.ic_hero_no_symbol, quietIcon, StatusGlyph)
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
private fun TranscribingRing(size: Dp, track: Color, arc: Color) {
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
        drawCircle(color = track.copy(alpha = 0.1f), radius = radius, style = Stroke(width = stroke))
        drawArc(
            color = arc,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
