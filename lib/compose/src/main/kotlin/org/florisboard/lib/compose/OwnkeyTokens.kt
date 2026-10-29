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

package org.florisboard.lib.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Design tokens of the Ownkey settings UI. The settings app is always dark; the keyboard reads only
 * [onAccentFor] and [contrastRatio] from here.
 */
object OwnkeyTokens {
    val Black = Color(0xFF000000)
    val Card = Color(0xFF262628)
    val Raised = Color(0xFF38383A)
    val Label = Color(0xFFFFFFFF)
    val Secondary = Color(0xFF98989D)
    val Success = Color(0xFF30D158)
    val Warning = Color(0xFFFFB340)
    val Danger = Color(0xFFFF6961)
    val Outline = Color(0xFF5A5A5E)

    /** Ember, the default accent. Also used when the stored accent is unspecified. */
    val DefaultAccent = Color(0xFFDE5F14)

    const val DisabledAlpha = 0.38f

    val CardShape = RoundedCornerShape(24.dp)
    val FieldShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)
    val DialogShape = RoundedCornerShape(28.dp)
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val SwatchShape = RoundedCornerShape(20.dp)

    val ScreenInset = 16.dp
    val GroupGap = 24.dp
    val RowGap = 2.dp
    val RowMinHeight = 56.dp
    val RowPaddingHorizontal = 16.dp
    val RowPaddingVertical = 14.dp
    val GroupHeaderBottom = 8.dp
    val ButtonHeight = 52.dp
    val MinTouchTarget = 48.dp
    val ContentMaxWidth = 640.dp
    val FocusRingWidth = 2.dp

    /** Font feature for tabular figures (slider values, versions, counts, percentages). */
    const val TabularNumbers = "tnum"
}

/** True inside an Ownkey grouped card, so nested groups and notices don't draw a card in a card. */
val LocalOwnkeyInCard = staticCompositionLocalOf { false }

/** True when the screen scaffold already insets content by [OwnkeyTokens.ScreenInset] on both sides. */
val LocalOwnkeyScreenInset = staticCompositionLocalOf { false }

fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = OwnkeyTokens.TabularNumbers)

/** WCAG 2.x relative luminance of the colour's sRGB channels. Alpha is ignored. */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG 2.x contrast ratio between two colours, from 1.0 to 21.0. Order doesn't matter. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Glyph, thumb and check colour on an accent fill: white when white reaches 3:1 against the accent,
 * otherwise black. Text never sits on an accent fill, so the non-text threshold applies. Serves both
 * M3 `onPrimary` and the keyboard's `--on-primary`.
 */
fun onAccentFor(accent: Color): Color {
    val effective = if (accent.isSpecified) accent else OwnkeyTokens.DefaultAccent
    return if (contrastRatio(Color.White, effective) >= 3.0) Color.White else Color.Black
}

/**
 * Positions children top to bottom with [gapPx] between consecutive children whose size is larger
 * than 0. Zero-size children sit at the current offset and add no gap. Returns the used extent.
 */
fun arrangeWithGapsBetweenNonEmpty(sizes: IntArray, gapPx: Int, outPositions: IntArray): Int {
    var position = 0
    var placedAny = false
    for (index in sizes.indices) {
        val size = sizes[index]
        if (size > 0) {
            if (placedAny) position += gapPx
            outPositions[index] = position
            position += size
            placedAny = true
        } else {
            outPositions[index] = position
        }
    }
    return position
}

/**
 * Vertical arrangement for grouped cards. Declares [gap] as spacing so the column reserves enough room,
 * and records the extent it actually used in [usedExtentPx] so the card can trim the slack left by
 * zero-size children (see [trimToArrangedExtent]).
 */
class OwnkeyGapArrangement(private val gap: Dp) : Arrangement.Vertical {
    override val spacing: Dp = gap

    var usedExtentPx: Int = -1
        private set

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        usedExtentPx = arrangeWithGapsBetweenNonEmpty(sizes, gap.roundToPx(), outPositions)
    }
}

/**
 * Reports the height the [arrangement] used instead of the column's reserved height. Place this before
 * the column's own size modifiers and after any clip, so the clip follows the trimmed bounds.
 */
fun Modifier.trimToArrangedExtent(arrangement: OwnkeyGapArrangement): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val used = arrangement.usedExtentPx
        val height = if (used < 0) placeable.height else used.coerceIn(constraints.minHeight, placeable.height)
        layout(placeable.width, height) {
            placeable.place(0, 0)
        }
    }
)

/**
 * Draws a 2dp Label ring while the element has keyboard or D-pad focus. Touch never focuses clickables,
 * so the ring never shows on touch. Place it before the clickable/focusable modifier. With [inset] the
 * ring is drawn inside the bounds (for rows clipped by a card), otherwise 2dp outside.
 */
fun Modifier.ownkeyFocusRing(
    shape: Shape = OwnkeyTokens.PillShape,
    inset: Boolean = false,
    color: Color = OwnkeyTokens.Label,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = OwnkeyTokens.FocusRingWidth.toPx()
                val offset = if (inset) stroke / 2f + stroke else -(stroke / 2f + stroke)
                val ringSize = Size(size.width - 2 * offset, size.height - 2 * offset)
                if (ringSize.width > 0f && ringSize.height > 0f) {
                    val outline = shape.createOutline(ringSize, layoutDirection, this)
                    translate(offset, offset) {
                        drawOutline(outline, color = color, style = Stroke(width = stroke))
                    }
                }
            }
        }
}
