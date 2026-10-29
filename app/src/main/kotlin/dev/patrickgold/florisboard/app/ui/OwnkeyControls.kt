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

package dev.patrickgold.florisboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.florisboard.lib.compose.LocalOwnkeyInCard
import org.florisboard.lib.compose.OwnkeyNoticeBody
import org.florisboard.lib.compose.OwnkeyPillButton
import org.florisboard.lib.compose.OwnkeyPillStyle
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing

/** Visual role of an [OwnkeyButton]: Primary, Secondary, Destructive or Plain. */
typealias OwnkeyButtonStyle = OwnkeyPillStyle

/**
 * 52dp pill button. Primary is white with black text, Secondary is Raised with white text, Destructive
 * is Danger text without fill, Plain is Label text without fill (also for links).
 */
@Composable
fun OwnkeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OwnkeyButtonStyle = OwnkeyButtonStyle.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OwnkeyPillButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        style = style,
        enabled = enabled,
        icon = icon,
    )
}

/**
 * Segmented choice as separate pills with 8dp gaps and no track. The selected pill is white with black
 * text; others are Card, or Raised with [insideCard]. Each segment is a Tab with a selected state and the
 * test tag `segment-<index>`.
 */
@Composable
fun OwnkeySegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    insideCard: Boolean = LocalOwnkeyInCard.current,
    enabled: Boolean = true,
) {
    val unselectedFill = if (insideCard) OwnkeyTokens.Raised else OwnkeyTokens.Card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = OwnkeyTokens.MinTouchTarget)
                    .testTag("segment-$index")
                    .ownkeyFocusRing(OwnkeyTokens.PillShape)
                    .clip(OwnkeyTokens.PillShape)
                    .background(if (selected) OwnkeyTokens.Label else unselectedFill)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .alpha(if (enabled) 1f else OwnkeyTokens.DisabledAlpha),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) OwnkeyTokens.Black else OwnkeyTokens.Label,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Filled text field: Raised fill, 16dp corners, no indicator line, label above (labelMedium in
 * Secondary), Accent cursor, error text in Danger. The label sits inside the field's decoration so
 * TalkBack reads it with the field.
 */
@Composable
fun OwnkeyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color = OwnkeyTokens.Raised,
) {
    val accent = MaterialTheme.colorScheme.primary
    val typography = MaterialTheme.typography
    val contentAlpha = if (enabled) 1f else OwnkeyTokens.DisabledAlpha
    val errorMessage = errorText?.takeIf { isError && it.isNotBlank() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (isError) error(errorMessage ?: "") },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = typography.bodyLarge.copy(color = OwnkeyTokens.Label.copy(alpha = contentAlpha)),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(accent),
        decorationBox = { innerTextField ->
            Column {
                if (label != null) {
                    Text(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                            .alpha(contentAlpha),
                        text = label,
                        style = typography.labelMedium,
                        color = OwnkeyTokens.Secondary,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OwnkeyTokens.ButtonHeight)
                        .clip(OwnkeyTokens.FieldShape)
                        .background(containerColor)
                        .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp),
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = typography.bodyLarge,
                                // Secondary is too weak on Raised; a dimmed Label keeps 4.5:1.
                                color = OwnkeyTokens.Label.copy(alpha = 0.6f * contentAlpha),
                                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (trailing != null) {
                        trailing()
                    }
                }
                when {
                    errorMessage != null -> Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        text = errorMessage,
                        style = typography.bodySmall,
                        color = OwnkeyTokens.Danger,
                    )
                    supportingText != null -> Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        text = supportingText,
                        style = typography.bodySmall,
                        color = OwnkeyTokens.Secondary,
                    )
                }
            }
        },
    )
}

/**
 * A Card row with a decorative tone dot, text and an optional Plain action. Put the tone into words in
 * [text]; the dot only repeats it. Standalone notices keep the 24dp gap to what follows, like groups;
 * inside a group they're a flat row.
 */
@Composable
fun OwnkeyNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: OwnkeyTone = OwnkeyTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val inCard = LocalOwnkeyInCard.current
    OwnkeyNoticeBody(
        text = text,
        modifier = if (inCard) modifier else modifier.padding(bottom = OwnkeyTokens.GroupGap),
        tone = tone,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
