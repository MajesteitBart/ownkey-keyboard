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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.OwnkeyDot
import org.florisboard.lib.compose.OwnkeyNoticeTone
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.color
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.compose.withTabularNumbers

/** Tone of a notice or status dot; the meaning must also be in the text. */
typealias OwnkeyTone = OwnkeyNoticeTone

private const val StackedValueFontScale = 1.5f
private val RowFocusShape = RoundedCornerShape(20.dp)
private val ChevronSize = 20.dp
private val SelectionMarkSize = 24.dp

private const val ValueMinWidthFraction = 0.3f
private val TitleValueGap = 16.dp

/**
 * Width the value gets on a row's first line. The title has priority: the value takes what the title
 * leaves, so next to a short title it can use most of the line, and never less than 30% (or its own
 * width when smaller), so a long title wraps only when the value can't shrink further.
 */
internal fun rowValueWidth(lineWidth: Int, gap: Int, titleWidth: Int, valueWidth: Int): Int {
    if (valueWidth <= 0 || lineWidth <= 0) return 0
    val floor = minOf(valueWidth, (lineWidth * ValueMinWidthFraction).toInt())
    val leftover = lineWidth - gap - titleWidth
    return minOf(valueWidth, maxOf(leftover, floor)).coerceAtLeast(0)
}

/** Title and right-aligned value on one line, value on the title's first baseline. */
@Composable
private fun TitleValueLine(
    title: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    Layout(content = { title(); value() }) { measurables, constraints ->
        val titleMeasurable = measurables[0]
        val valueMeasurable = measurables[1]
        val lineWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            titleMeasurable.maxIntrinsicWidth(Constraints.Infinity) +
                valueMeasurable.maxIntrinsicWidth(Constraints.Infinity)
        }
        val gap = TitleValueGap.roundToPx()
        val valueWidth = rowValueWidth(
            lineWidth = lineWidth,
            gap = gap,
            titleWidth = titleMeasurable.maxIntrinsicWidth(Constraints.Infinity),
            valueWidth = valueMeasurable.maxIntrinsicWidth(Constraints.Infinity),
        )
        val titleWidth = (lineWidth - if (valueWidth > 0) gap + valueWidth else 0).coerceAtLeast(0)
        val titlePlaceable = titleMeasurable.measure(Constraints(maxWidth = titleWidth))
        val valuePlaceable = valueMeasurable.measure(Constraints(maxWidth = valueWidth))
        val titleBaseline = titlePlaceable[FirstBaseline]
        val valueBaseline = valuePlaceable[FirstBaseline]
        val valueY = if (titleBaseline != AlignmentLine.Unspecified && valueBaseline != AlignmentLine.Unspecified) {
            (titleBaseline - valueBaseline).coerceAtLeast(0)
        } else {
            0
        }
        val height = maxOf(titlePlaceable.height, valueY + valuePlaceable.height)
        layout(lineWidth, height) {
            titlePlaceable.placeRelative(0, 0)
            valuePlaceable.placeRelative(lineWidth - valuePlaceable.width, valueY)
        }
    }
}

/** From this font scale on, a row's value moves under its title instead of sharing the line. */
internal fun stacksRowValue(fontScale: Float): Boolean = fontScale >= StackedValueFontScale

/**
 * A title with an optional value, used by every row and the slider header. Below 150% font scale they
 * share a line where the title has priority and the value gets what it leaves, ellipsized; from 150% on
 * the value sits on its own line under the title.
 */
@Composable
internal fun OwnkeyTitleValue(
    title: String,
    value: String?,
    contentAlpha: Float,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = titleStyle.withTabularNumbers(),
    valueColor: Color = OwnkeyTokens.Secondary,
) {
    val titleText: @Composable () -> Unit = {
        Text(
            modifier = Modifier.alpha(contentAlpha),
            text = title,
            style = titleStyle,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (value == null) {
        titleText()
    } else if (stacksRowValue(LocalDensity.current.fontScale)) {
        Column {
            titleText()
            Text(
                modifier = Modifier.alpha(contentAlpha),
                text = value,
                style = valueStyle,
                color = valueColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        TitleValueLine(
            title = titleText,
            value = {
                Text(
                    modifier = Modifier.alpha(contentAlpha),
                    text = value,
                    style = valueStyle,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

/**
 * Layout shared by every Ownkey row and the preference drop-ins. [interaction] carries the
 * clickable/toggleable/selectable modifier so the focus ring and ripple cover the whole row.
 */
@Composable
internal fun OwnkeyRowContent(
    title: String,
    modifier: Modifier,
    interaction: Modifier,
    summary: String?,
    value: String?,
    enabled: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    chevron: Boolean,
    titleColor: Color = Color.Unspecified,
    valueColor: Color = Color.Unspecified,
    note: String? = null,
    summaryMaxLines: Int = Int.MAX_VALUE,
    tabularValue: Boolean = true,
    disabledSummaryAlpha: Float = OwnkeyTokens.DisabledAlpha,
) {
    val contentAlpha = if (enabled) 1f else OwnkeyTokens.DisabledAlpha
    val summaryAlpha = if (enabled) 1f else disabledSummaryAlpha
    val typography = MaterialTheme.typography
    val resolvedTitleColor = titleColor.takeOrElse { MaterialTheme.colorScheme.onSurface }
    val resolvedValueColor = valueColor.takeOrElse { OwnkeyTokens.Secondary }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .ownkeyFocusRing(RowFocusShape, inset = true)
            .then(interaction)
            .heightIn(min = OwnkeyTokens.RowMinHeight)
            .padding(horizontal = OwnkeyTokens.RowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(modifier = Modifier.alpha(contentAlpha), contentAlignment = Alignment.Center) {
                leading()
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = OwnkeyTokens.RowPaddingVertical),
        ) {
            OwnkeyTitleValue(
                title = title,
                value = value,
                contentAlpha = contentAlpha,
                titleColor = resolvedTitleColor,
                valueStyle = if (tabularValue) typography.bodyLarge.withTabularNumbers() else typography.bodyLarge,
                valueColor = resolvedValueColor,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    modifier = Modifier.alpha(summaryAlpha),
                    text = summary,
                    style = typography.bodyMedium,
                    color = OwnkeyTokens.Secondary,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = typography.bodyMedium,
                    color = OwnkeyTokens.Secondary,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
        if (chevron) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                modifier = Modifier
                    .size(ChevronSize)
                    .alpha(contentAlpha),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OwnkeyTokens.Secondary,
            )
        }
    }
}

/**
 * A settings row: title (wraps to two lines), optional summary, right-aligned value, trailing slot and
 * chevron. Pass [chevron] only for rows that open another screen. Paints `surface`, which is Card inside
 * an [OwnkeyGroup]. [summaryMaxLines] caps a summary that previews user text; it ends in an ellipsis.
 * Values use tabular figures; pass `tabularValue = false` for a fixed label such as a version name, where
 * Inter's tabular forms would widen the dots and hyphens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnkeyRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    valueColor: Color = Color.Unspecified,
    note: String? = null,
    summaryMaxLines: Int = Int.MAX_VALUE,
    tabularValue: Boolean = true,
) {
    val interaction = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick ?: { },
        )
    } else {
        Modifier.semantics(mergeDescendants = true) { }
    }
    OwnkeyRowContent(
        title = title,
        modifier = modifier,
        interaction = interaction,
        summary = summary,
        value = value,
        enabled = enabled,
        leading = leading,
        trailing = trailing,
        chevron = chevron,
        titleColor = titleColor,
        valueColor = valueColor,
        note = note,
        summaryMaxLines = summaryMaxLines,
        tabularValue = tabularValue,
    )
}

/**
 * The only switch in the app: unchecked thumb Label on a Raised track, checked track Accent with an
 * onAccent thumb, no border. The thumb keeps full size in both states.
 */
@Composable
fun OwnkeySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val disabled = OwnkeyTokens.DisabledAlpha
    // Disabled colours are flattened over Card so the track never shows the row through it.
    val base = OwnkeyTokens.Card
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = { Box(modifier = Modifier.size(SwitchDefaults.IconSize)) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = onAccent,
            checkedTrackColor = accent,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = Color.Transparent,
            uncheckedThumbColor = OwnkeyTokens.Label,
            uncheckedTrackColor = OwnkeyTokens.Raised,
            uncheckedBorderColor = Color.Transparent,
            uncheckedIconColor = Color.Transparent,
            disabledCheckedThumbColor = onAccent.copy(alpha = disabled).compositeOver(accent.copy(alpha = disabled).compositeOver(base)),
            disabledCheckedTrackColor = accent.copy(alpha = disabled).compositeOver(base),
            disabledCheckedBorderColor = Color.Transparent,
            disabledCheckedIconColor = Color.Transparent,
            disabledUncheckedThumbColor = OwnkeyTokens.Label.copy(alpha = disabled).compositeOver(OwnkeyTokens.Raised.copy(alpha = disabled).compositeOver(base)),
            disabledUncheckedTrackColor = OwnkeyTokens.Raised.copy(alpha = disabled).compositeOver(base),
            disabledUncheckedBorderColor = Color.Transparent,
            disabledUncheckedIconColor = Color.Transparent,
        ),
    )
}

/** A row that toggles as a whole (role Switch) with a trailing [OwnkeySwitch]. */
@Composable
fun OwnkeySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    OwnkeyRowContent(
        title = title,
        modifier = modifier,
        interaction = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        summary = summary,
        value = null,
        enabled = enabled,
        leading = null,
        trailing = { OwnkeySwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        chevron = false,
    )
}

/**
 * One option of a single choice. Shows an accent checkmark when selected. Place a list of these inside
 * a `selectableGroup()` container ([OwnkeyGroup] with `selectableGroup = true`, or [OwnkeySheet]).
 *
 * A disabled choice dims its label and summary to 38%; [note] stays Secondary at full alpha to explain
 * why. With [onDisabledClick] a disabled choice stays tappable to run its fix: it keeps its radio button
 * role and selected state inside the group, says "Unavailable" as its state, and names the fix (the note)
 * as its click action.
 */
@Composable
fun OwnkeyChoiceRow(
    label: String,
    summary: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    note: String? = null,
    onDisabledClick: (() -> Unit)? = null,
) {
    val unavailable = stringRes(R.string.ownkey_ui__choice_unavailable)
    val isSelected = selected
    val interaction = if (!enabled && onDisabledClick != null) {
        Modifier
            .semantics {
                this.selected = isSelected
                stateDescription = unavailable
            }
            .clickable(
                role = Role.RadioButton,
                onClickLabel = note,
                onClick = onDisabledClick,
            )
    } else {
        Modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        )
    }
    val accent = MaterialTheme.colorScheme.primary
    OwnkeyRowContent(
        title = label,
        modifier = modifier,
        interaction = interaction,
        summary = summary,
        value = null,
        enabled = enabled,
        leading = null,
        trailing = {
            if (selected) {
                Icon(
                    modifier = Modifier
                        .size(SelectionMarkSize)
                        .alpha(if (enabled) 1f else OwnkeyTokens.DisabledAlpha),
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                )
            } else {
                Spacer(modifier = Modifier.size(SelectionMarkSize))
            }
        },
        chevron = false,
        note = note,
        // A choice that can still be fixed stays readable: only its title is dimmed, the note says what to do.
        disabledSummaryAlpha = if (note.isNullOrBlank()) OwnkeyTokens.DisabledAlpha else UnavailableChoiceSummaryAlpha,
    )
}

/** Summary alpha of a choice that can't be picked yet but has a fix; 38% is too faint to read what it does. */
private const val UnavailableChoiceSummaryAlpha = 0.6f

/** Decorative 8dp status dot in the tone colour. Always pair it with text that names the state. */
@Composable
fun OwnkeyStatusDot(
    tone: OwnkeyTone,
    modifier: Modifier = Modifier,
) {
    OwnkeyDot(color = tone.color(), modifier = modifier)
}
