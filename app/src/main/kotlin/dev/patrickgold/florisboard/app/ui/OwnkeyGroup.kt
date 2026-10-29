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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluator
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.PreferenceModel
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefEnabled
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefVisible
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import org.florisboard.lib.compose.LocalOwnkeyInCard
import org.florisboard.lib.compose.OwnkeyGapArrangement
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ProvideOwnkeyCardContext
import org.florisboard.lib.compose.trimToArrangedExtent

/**
 * A grouped card: optional header above, rows in a card clipped to 24dp with 2dp black gaps between
 * visible rows, optional footer below, and the 24dp gap to the next group. Children paint `surface`,
 * which is Card here, so untouched jetpref rows fit in. When every child is hidden the whole group,
 * header included, takes no space.
 */
@Composable
fun OwnkeyGroup(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    selectableGroup: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    OwnkeyGroupLayout(
        title = title,
        footer = footer,
        modifier = modifier,
        selectableGroup = selectableGroup,
        content = content,
    )
}

/**
 * Drop-in for jetpref's `PreferenceGroup` with the same parameters, order and defaults. [icon] and
 * [iconSpaceReserved] are accepted and ignored for drawing. A blank [title] draws no header.
 */
@Composable
fun <T : PreferenceModel> PreferenceUiScope<T>.PreferenceGroup(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
    footer: String? = null,
    content: PreferenceUiContent<T>,
) {
    if (LocalIsPrefVisible.current && visibleIf(PreferenceDataEvaluatorScope)) {
        val isEnabled = LocalIsPrefEnabled.current && enabledIf(PreferenceDataEvaluatorScope)
        val prefs = this.prefs
        CompositionLocalProvider(
            LocalIconSpaceReserved provides iconSpaceReserved,
            LocalIsPrefEnabled provides isEnabled,
            LocalIsPrefVisible provides true,
        ) {
            OwnkeyGroupLayout(
                title = title.takeIf { it.isNotBlank() },
                footer = footer,
                modifier = modifier,
                selectableGroup = false,
            ) {
                content(PreferenceUiScope(prefs = prefs, columnScope = this))
            }
        }
    }
}

@Composable
internal fun OwnkeyGroupLayout(
    title: String?,
    footer: String?,
    modifier: Modifier,
    selectableGroup: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val arrangement = remember { OwnkeyGapArrangement(OwnkeyTokens.RowGap) }
    val selectableModifier = if (selectableGroup) Modifier.selectableGroup() else Modifier

    if (LocalOwnkeyInCard.current) {
        // A group inside a card stays flat: its header becomes a sub-header row of the parent card.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .trimToArrangedExtent(arrangement)
                .then(selectableModifier),
            verticalArrangement = arrangement,
        ) {
            if (title != null) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                        .semantics { heading() },
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = OwnkeyTokens.Secondary,
                )
            }
            content()
        }
        return
    }

    Layout(
        contents = listOf(
            {
                if (title != null) {
                    Text(
                        modifier = Modifier
                            .padding(
                                start = OwnkeyTokens.RowPaddingHorizontal,
                                end = OwnkeyTokens.RowPaddingHorizontal,
                                bottom = OwnkeyTokens.GroupHeaderBottom,
                            )
                            .semantics { heading() },
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = OwnkeyTokens.Secondary,
                    )
                }
            },
            {
                ProvideOwnkeyCardContext {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OwnkeyTokens.CardShape)
                            .trimToArrangedExtent(arrangement)
                            .then(selectableModifier),
                        verticalArrangement = arrangement,
                        content = content,
                    )
                }
            },
            {
                if (footer != null) {
                    Text(
                        modifier = Modifier.padding(
                            start = OwnkeyTokens.RowPaddingHorizontal,
                            end = OwnkeyTokens.RowPaddingHorizontal,
                            top = 8.dp,
                        ),
                        text = footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnkeyTokens.Secondary,
                    )
                }
            },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (headerMeasurables, cardMeasurables, footerMeasurables), constraints ->
        val childConstraints = constraints.copy(minHeight = 0)
        val card = cardMeasurables.firstOrNull()?.measure(childConstraints)
        if (card == null || card.height == 0) {
            return@Layout layout(constraints.minWidth, constraints.minHeight) { }
        }
        val header = headerMeasurables.firstOrNull()?.measure(childConstraints)
        val footer = footerMeasurables.firstOrNull()?.measure(childConstraints)
        val gap = OwnkeyTokens.GroupGap.roundToPx()
        val width = maxOf(card.width, header?.width ?: 0, footer?.width ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = ((header?.height ?: 0) + card.height + (footer?.height ?: 0) + gap)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            if (header != null) {
                header.placeRelative(0, y)
                y += header.height
            }
            card.placeRelative(0, y)
            y += card.height
            footer?.placeRelative(0, y)
        }
    }
}
