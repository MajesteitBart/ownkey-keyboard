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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.OwnkeyPillButton
import org.florisboard.lib.compose.OwnkeyPillStyle
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ProvideOwnkeyCardContext

private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp

/**
 * Marks a separate window's content root so Compose test tags are visible as resource ids (Maestro
 * `id:`). Dialogs and sheets don't inherit this from the activity root.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.ownkeyTestTagsAsResourceId(): Modifier = this.semantics { testTagsAsResourceId = true }

/**
 * Ownkey alert dialog: Card, 28dp corners, headlineSmall title, bodyMedium text, pill buttons aligned
 * to the end. Confirm is Primary, or Destructive with [destructive]; dismiss is Plain; the optional
 * neutral button sits at the start, or on its own line above the others when they don't fit side by
 * side. Buttons are tagged `dialog-confirm`, `dialog-dismiss` and `dialog-neutral`. Mirrors the
 * parameter names of `JetPrefAlertDialog` where they overlap: [onDismiss] runs for the dismiss button,
 * [onOutsideDismissal] for back and outside taps.
 *
 * The body scrolls by default. Pass `scrollModifier = Modifier` for lazy content that scrolls itself,
 * and [contentPadding] to change the body's inset. [trailingIconTitle] sits at the end of the title.
 */
@Composable
fun OwnkeyDialog(
    title: String,
    onDismiss: () -> Unit = { },
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = { },
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String? = null,
    neutralLabel: String? = null,
    onNeutral: () -> Unit = { },
    neutralStyle: OwnkeyButtonStyle = OwnkeyButtonStyle.Plain,
    neutralEnabled: Boolean = true,
    allowOutsideDismissal: Boolean = true,
    onOutsideDismissal: () -> Unit = onDismiss,
    properties: DialogProperties = DialogProperties(),
    scrollModifier: Modifier = Modifier.verticalScroll(rememberScrollState()),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp),
    trailingIconTitle: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = { },
) {
    Dialog(
        onDismissRequest = { if (allowOutsideDismissal) onOutsideDismissal() },
        properties = properties,
    ) {
        ProvideOwnkeyCardContext {
            CompositionLocalProvider(LocalContentColor provides OwnkeyTokens.Label) {
                Column(
                    modifier = modifier
                        .ownkeyTestTagsAsResourceId()
                        .widthIn(min = DialogMinWidth, max = DialogMaxWidth)
                        .clip(OwnkeyTokens.DialogShape)
                        .background(OwnkeyTokens.Card)
                        .padding(top = 24.dp, bottom = 16.dp),
                ) {
                    val titleText: @Composable (Modifier) -> Unit = { titleModifier ->
                        Text(
                            modifier = titleModifier.semantics { heading() },
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = OwnkeyTokens.Label,
                        )
                    }
                    if (trailingIconTitle == null) {
                        titleText(Modifier.padding(horizontal = 24.dp))
                    } else {
                        Row(
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            titleText(Modifier.weight(1f))
                            trailingIconTitle()
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .padding(contentPadding)
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .then(scrollModifier),
                    ) {
                        if (text != null) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OwnkeyTokens.Secondary,
                            )
                        }
                        content()
                    }
                    val neutralButton: (@Composable () -> Unit)? = if (!neutralLabel.isNullOrBlank()) {
                        {
                            OwnkeyPillButton(
                                modifier = Modifier.testTag("dialog-neutral"),
                                label = neutralLabel,
                                onClick = onNeutral,
                                style = neutralStyle,
                                enabled = neutralEnabled,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            )
                        }
                    } else {
                        null
                    }
                    val dismissButton: (@Composable () -> Unit)? = if (!dismissLabel.isNullOrBlank()) {
                        {
                            OwnkeyPillButton(
                                modifier = Modifier.testTag("dialog-dismiss"),
                                label = dismissLabel,
                                onClick = onDismiss,
                                style = OwnkeyPillStyle.Plain,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            )
                        }
                    } else {
                        null
                    }
                    val confirmButton: (@Composable () -> Unit)? = if (!confirmLabel.isNullOrBlank()) {
                        {
                            OwnkeyPillButton(
                                modifier = Modifier.testTag("dialog-confirm"),
                                label = confirmLabel,
                                onClick = onConfirm,
                                style = if (destructive) OwnkeyPillStyle.Destructive else OwnkeyPillStyle.Primary,
                                enabled = confirmEnabled,
                                contentPadding = if (destructive) {
                                    PaddingValues(horizontal = 12.dp)
                                } else {
                                    PaddingValues(horizontal = 20.dp)
                                },
                            )
                        }
                    } else {
                        null
                    }
                    if (neutralButton != null || dismissButton != null || confirmButton != null) {
                        OwnkeyDialogButtons(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 16.dp),
                            neutral = neutralButton,
                            dismiss = dismissButton,
                            confirm = confirmButton,
                        )
                    }
                }
            }
        }
    }
}

private val DialogButtonGap = 4.dp
private val NoButton: @Composable () -> Unit = { }

/**
 * Rows of dialog buttons, by index into the widths: neutral (0), dismiss (1), confirm (2), with -1 for a
 * missing button. One row when everything fits (neutral at the start, the others at the end). Otherwise
 * the neutral button gets its own row above, and if dismiss and confirm still don't fit together,
 * confirm sits above dismiss. Every stacked row is end-aligned.
 */
internal fun dialogButtonRows(widths: List<Int>, maxWidth: Int, gap: Int): List<List<Int>> {
    fun rowWidth(row: List<Int>) = row.sumOf { widths[it] } + gap * (row.size - 1).coerceAtLeast(0)
    val present = (0..2).filter { widths.getOrElse(it) { -1 } >= 0 }
    if (rowWidth(present) <= maxWidth) return listOf(present)
    val neutral = present.filter { it == 0 }
    val ends = present.filter { it != 0 }
    val endRows = if (rowWidth(ends) <= maxWidth) listOf(ends) else ends.reversed().map { listOf(it) }
    return (if (neutral.isEmpty()) emptyList() else listOf(neutral)) + endRows.filter { it.isNotEmpty() }
}

@Composable
private fun OwnkeyDialogButtons(
    modifier: Modifier,
    neutral: (@Composable () -> Unit)?,
    dismiss: (@Composable () -> Unit)?,
    confirm: (@Composable () -> Unit)?,
) {
    Layout(
        modifier = modifier,
        contents = listOf(neutral ?: NoButton, dismiss ?: NoButton, confirm ?: NoButton),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.firstOrNull()?.measure(loose) }
        val gap = DialogButtonGap.roundToPx()
        val rows = dialogButtonRows(placeables.map { it?.width ?: -1 }, constraints.maxWidth, gap)
        val rowHeights = rows.map { row -> row.maxOf { placeables[it]!!.height } }
        val width = constraints.maxWidth
        val height = (rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0))
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowHeight = rowHeights[rowIndex]
                // A single row keeps the neutral button at the start.
                if (rows.size == 1 && row.firstOrNull() == 0) {
                    val neutralPlaceable = placeables[0]!!
                    neutralPlaceable.placeRelative(0, y + (rowHeight - neutralPlaceable.height) / 2)
                }
                var x = width
                for (index in row.reversed()) {
                    if (rows.size == 1 && index == 0) continue
                    val placeable = placeables[index]!!
                    x -= placeable.width
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x -= gap
                }
                y += rowHeight + gap
            }
        }
    }
}

/**
 * Ownkey bottom sheet: opens fully expanded, Card with 28dp top corners, Raised drag handle, optional
 * headlineSmall title, rows in a LazyColumn that starts scrolled to [initialScrollIndex], bottom insets
 * padded. [content] receives `hide`, which animates the sheet away and then calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnkeySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    initialScrollIndex: Int = 0,
    content: LazyListScope.(hide: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    // Keep two rows above the selected one visible for context.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialScrollIndex - 2).coerceAtLeast(0),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = OwnkeyTokens.SheetShape,
        containerColor = OwnkeyTokens.Card,
        contentColor = OwnkeyTokens.Label,
        tonalElevation = 0.dp,
        scrimColor = OwnkeyTokens.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = OwnkeyTokens.Raised) },
    ) {
        ProvideOwnkeyCardContext {
            Column(modifier = Modifier.ownkeyTestTagsAsResourceId()) {
                if (title != null) {
                    Text(
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                            .semantics { heading() },
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = OwnkeyTokens.Label,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .testTag("ownkey-sheet")
                        .fillMaxWidth()
                        .selectableGroup(),
                    state = listState,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                ) {
                    content(hide)
                }
            }
        }
    }
}
