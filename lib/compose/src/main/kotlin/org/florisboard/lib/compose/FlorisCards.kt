/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FlorisCardDefaults {
    val IconRequiredSize = 24.dp
    val IconSpacing = 12.dp

    val ContentPadding = PaddingValues(
        start = 0.dp,
        end = OwnkeyTokens.RowPaddingHorizontal,
        top = OwnkeyTokens.RowPaddingVertical,
        bottom = OwnkeyTokens.RowPaddingVertical,
    )
}

object BoxDefaults {
    val OutlinedBoxShape = OwnkeyTokens.CardShape

    val ContentPadding = PaddingValues(all = 0.dp)
}

@Composable
fun FlorisSimpleCard(
    modifier: Modifier = Modifier,
    text: String,
    secondaryText: String? = null,
    backgroundColor: Color = OwnkeyTokens.Card,
    contentColor: Color = OwnkeyTokens.Label,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = if (LocalOwnkeyInCard.current) RectangleShape else OwnkeyTokens.CardShape
    Card(
        onClick = onClick ?: { },
        enabled = onClick != null,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.ownkeyFocusRing(shape, inset = true) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            contentColor = contentColor,
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor,
            disabledContentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = OwnkeyTokens.RowMinHeight)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (icon == null) OwnkeyTokens.RowPaddingHorizontal else 0.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (secondaryText != null) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OwnkeyTokens.Secondary,
                    )
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FlorisErrorCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    OwnkeyNoticeBody(text = text, modifier = modifier, tone = OwnkeyNoticeTone.Error, onClick = onClick)
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FlorisWarningCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    OwnkeyNoticeBody(text = text, modifier = modifier, tone = OwnkeyNoticeTone.Warning, onClick = onClick)
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FlorisInfoCard(
    text: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    contentPadding: PaddingValues = FlorisCardDefaults.ContentPadding,
    onClick: (() -> Unit)? = null,
) {
    OwnkeyNoticeBody(text = text, modifier = modifier, tone = OwnkeyNoticeTone.Info, onClick = onClick)
}

/**
 * Formerly an outlined box with a notched title; now a Card with its title above, like a settings group.
 * [borderWidth] and [borderColor] are accepted and ignored because Ownkey draws no outlines.
 */
@Composable
fun FlorisOutlinedBox(
    modifier: Modifier = Modifier,
    title: String,
    onTitleClick: (() -> Unit)? = null,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    FlorisOutlinedBox(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onTitleClick = onTitleClick,
        subtitle = if (subtitle != null) {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            null
        },
        onSubtitleClick = onSubtitleClick,
        borderWidth = borderWidth,
        borderColor = borderColor,
        shape = shape,
        contentPadding = contentPadding,
        content = content,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun FlorisOutlinedBox(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    shape: Shape = BoxDefaults.OutlinedBoxShape,
    contentPadding: PaddingValues = BoxDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inCard = LocalOwnkeyInCard.current
    Column(modifier = modifier) {
        if (title != null) {
            Row(
                modifier = Modifier
                    .padding(
                        start = OwnkeyTokens.RowPaddingHorizontal,
                        end = OwnkeyTokens.RowPaddingHorizontal,
                        bottom = if (subtitle != null) 2.dp else OwnkeyTokens.GroupHeaderBottom,
                    )
                    .semantics { heading() }
                    .then(if (onTitleClick != null) Modifier.rippleClickable { onTitleClick() } else Modifier),
            ) {
                CompositionLocalProvider(LocalContentColor provides OwnkeyTokens.Secondary) {
                    title()
                }
            }
        }
        if (subtitle != null) {
            Row(
                modifier = Modifier
                    .padding(
                        start = OwnkeyTokens.RowPaddingHorizontal,
                        end = OwnkeyTokens.RowPaddingHorizontal,
                        bottom = OwnkeyTokens.GroupHeaderBottom,
                    )
                    .then(if (onSubtitleClick != null) Modifier.rippleClickable { onSubtitleClick() } else Modifier),
            ) {
                CompositionLocalProvider(LocalContentColor provides OwnkeyTokens.Secondary) {
                    subtitle()
                }
            }
        }
        ProvideOwnkeyCardContext {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (inCard) RectangleShape else shape)
                    .background(OwnkeyTokens.Card)
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

fun Modifier.defaultFlorisOutlinedBox(): Modifier = composed {
    if (LocalOwnkeyScreenInset.current) {
        this
            .fillMaxWidth()
            .padding(bottom = OwnkeyTokens.GroupGap)
    } else {
        this
            .fillMaxWidth()
            .padding(horizontal = OwnkeyTokens.ScreenInset, vertical = OwnkeyTokens.GroupGap / 2)
    }
}
