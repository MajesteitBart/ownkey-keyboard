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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.material.ui.JetPrefTextFieldAppearance
import dev.patrickgold.jetpref.material.ui.JetPrefTextFieldDefaults

/** Visual role of a pill button. */
enum class OwnkeyPillStyle {
    /** White pill with black text. One per screen area. */
    Primary,
    /** Raised pill with white text. */
    Secondary,
    /** Danger text, no fill. */
    Destructive,
    /** Label text in medium weight, no fill. Used for links too. */
    Plain,
}

object OwnkeyPillDefaults {
    val Height = OwnkeyTokens.ButtonHeight
    val IconSize = 18.dp
    val IconSpacing = 8.dp

    fun contentPadding(style: OwnkeyPillStyle): PaddingValues = when (style) {
        OwnkeyPillStyle.Primary, OwnkeyPillStyle.Secondary -> PaddingValues(horizontal = 24.dp)
        OwnkeyPillStyle.Destructive, OwnkeyPillStyle.Plain -> PaddingValues(horizontal = 16.dp)
    }

    @Composable
    fun colors(style: OwnkeyPillStyle): ButtonColors {
        val disabledLabel = OwnkeyTokens.Label.copy(alpha = OwnkeyTokens.DisabledAlpha)
        return when (style) {
            OwnkeyPillStyle.Primary -> ButtonDefaults.buttonColors(
                containerColor = OwnkeyTokens.Label,
                contentColor = OwnkeyTokens.Black,
                disabledContainerColor = OwnkeyTokens.Raised,
                disabledContentColor = disabledLabel,
            )
            OwnkeyPillStyle.Secondary -> ButtonDefaults.buttonColors(
                containerColor = OwnkeyTokens.Raised,
                contentColor = OwnkeyTokens.Label,
                disabledContainerColor = OwnkeyTokens.Raised.copy(alpha = OwnkeyTokens.DisabledAlpha),
                disabledContentColor = disabledLabel,
            )
            OwnkeyPillStyle.Destructive -> ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = OwnkeyTokens.Danger,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = OwnkeyTokens.Danger.copy(alpha = OwnkeyTokens.DisabledAlpha),
            )
            OwnkeyPillStyle.Plain -> ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = OwnkeyTokens.Label,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = disabledLabel,
            )
        }
    }
}

/**
 * Shared body of every Ownkey pill button: 52dp tall, fully rounded, no border, no elevation. The app's
 * `OwnkeyButton` and the lib button restyles render through this.
 */
@Composable
fun OwnkeyPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OwnkeyPillStyle = OwnkeyPillStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    colors: ButtonColors = OwnkeyPillDefaults.colors(style),
    contentPadding: PaddingValues = OwnkeyPillDefaults.contentPadding(style),
    shape: Shape = OwnkeyTokens.PillShape,
) {
    val textStyle = when (style) {
        OwnkeyPillStyle.Primary, OwnkeyPillStyle.Secondary -> MaterialTheme.typography.labelLarge
        OwnkeyPillStyle.Destructive, OwnkeyPillStyle.Plain ->
            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = OwnkeyPillDefaults.Height)
            .ownkeyFocusRing(shape),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = null,
        border = null,
        contentPadding = contentPadding,
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(OwnkeyPillDefaults.IconSize),
                imageVector = icon,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(OwnkeyPillDefaults.IconSpacing))
        }
        Text(
            text = label,
            style = textStyle,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tone of a notice or status dot. The tone must also be clear from the text. */
enum class OwnkeyNoticeTone {
    Info,
    Success,
    Warning,
    Error,
}

fun OwnkeyNoticeTone.color(): Color = when (this) {
    OwnkeyNoticeTone.Info -> OwnkeyTokens.Secondary
    OwnkeyNoticeTone.Success -> OwnkeyTokens.Success
    OwnkeyNoticeTone.Warning -> OwnkeyTokens.Warning
    OwnkeyNoticeTone.Error -> OwnkeyTokens.Danger
}

/** Decorative 8dp dot. Callers put the meaning in text. */
@Composable
fun OwnkeyDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Shared body of the Ownkey notice: a Card row with a tone dot, text and an optional Plain action.
 * Inside a grouped card it paints Card without its own corners.
 */
@Composable
fun OwnkeyNoticeBody(
    text: String,
    modifier: Modifier = Modifier,
    tone: OwnkeyNoticeTone = OwnkeyNoticeTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val inCard = LocalOwnkeyInCard.current
    val action = if (actionLabel != null && onAction != null) actionLabel to onAction else null
    val hasAction = action != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (inCard) Modifier else Modifier.clip(OwnkeyTokens.CardShape))
            .background(OwnkeyTokens.Card)
            .then(
                if (onClick != null) {
                    Modifier
                        .ownkeyFocusRing(if (inCard) RectangleShape else OwnkeyTokens.CardShape, inset = true)
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .heightIn(min = OwnkeyTokens.RowMinHeight)
            .padding(start = OwnkeyTokens.RowPaddingHorizontal, end = if (hasAction) 4.dp else OwnkeyTokens.RowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OwnkeyDot(color = tone.color())
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = OwnkeyTokens.RowPaddingVertical),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OwnkeyTokens.Label,
        )
        if (action != null) {
            Spacer(modifier = Modifier.width(4.dp))
            OwnkeyPillButton(
                label = action.first,
                onClick = action.second,
                style = OwnkeyPillStyle.Plain,
                contentPadding = PaddingValues(horizontal = 12.dp),
            )
        }
    }
}

/**
 * Context for content painted on a Card: `surface` becomes Card so untouched M3/jetpref list items paint
 * the card colour, and [LocalOwnkeyInCard] tells nested cards and notices to stay flat.
 */
@Composable
fun ProvideOwnkeyCardContext(content: @Composable () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val cardScheme = remember(colorScheme) { colorScheme.copy(surface = OwnkeyTokens.Card) }
    MaterialTheme(
        colorScheme = cardScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalOwnkeyInCard provides true, content = content)
    }
}

object OwnkeyTextFieldDefaults {
    /**
     * Ownkey field look for the remaining `JetPrefTextField`/`JetPrefDropdown` users: Raised fill,
     * 16dp corners, no indicator line, Accent cursor. Labels inside the field use dimmed Label because
     * Secondary is too weak on Raised.
     */
    @Composable
    fun jetPrefAppearance(): JetPrefTextFieldAppearance {
        val accent = MaterialTheme.colorScheme.primary
        val label = OwnkeyTokens.Label
        val dimLabel = OwnkeyTokens.Label.copy(alpha = 0.7f)
        val disabled = OwnkeyTokens.DisabledAlpha
        return JetPrefTextFieldDefaults.filled(
            shape = OwnkeyTokens.FieldShape,
            colors = TextFieldDefaults.colors(
                focusedTextColor = label,
                unfocusedTextColor = label,
                disabledTextColor = label.copy(alpha = disabled),
                errorTextColor = label,
                focusedContainerColor = OwnkeyTokens.Raised,
                unfocusedContainerColor = OwnkeyTokens.Raised,
                disabledContainerColor = OwnkeyTokens.Raised.copy(alpha = disabled),
                errorContainerColor = OwnkeyTokens.Raised,
                cursorColor = accent,
                errorCursorColor = OwnkeyTokens.Danger,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedLeadingIconColor = dimLabel,
                unfocusedLeadingIconColor = dimLabel,
                focusedTrailingIconColor = dimLabel,
                unfocusedTrailingIconColor = dimLabel,
                errorTrailingIconColor = OwnkeyTokens.Danger,
                focusedLabelColor = dimLabel,
                unfocusedLabelColor = dimLabel,
                disabledLabelColor = label.copy(alpha = disabled),
                errorLabelColor = OwnkeyTokens.Danger,
                focusedPlaceholderColor = label.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = label.copy(alpha = 0.6f),
                focusedSupportingTextColor = OwnkeyTokens.Secondary,
                unfocusedSupportingTextColor = OwnkeyTokens.Secondary,
                errorSupportingTextColor = OwnkeyTokens.Danger,
            ),
        )
    }
}
