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

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** One option of [OwnkeyChoiceListRow]. */
data class ChoiceOption<T>(val value: T, val label: String, val summary: String? = null)

/**
 * A row that shows the selected option as its value and opens an [OwnkeySheet] of options, for choices
 * that aren't a single pref (merged controls). Picking an option applies it and closes the sheet.
 * Sheet rows are tagged `sheet-option-<index>`, like [ListPreference]'s.
 */
@Composable
fun <T> OwnkeyChoiceListRow(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var isSheetOpen by remember { mutableStateOf(false) }
    val selectedIndex = options.indexOfFirst { it.value == selected }
    OwnkeyRow(
        title = title,
        modifier = modifier,
        summary = summary,
        value = options.getOrNull(selectedIndex)?.label,
        enabled = enabled,
        onClick = { isSheetOpen = true },
    )
    if (isSheetOpen) {
        OwnkeySheet(
            onDismiss = { isSheetOpen = false },
            title = title,
            initialScrollIndex = selectedIndex.coerceAtLeast(0),
        ) { hide ->
            itemsIndexed(options) { index, option ->
                OwnkeyChoiceRow(
                    modifier = Modifier.testTag("sheet-option-$index"),
                    label = option.label,
                    summary = option.summary,
                    selected = option.value == selected,
                    onClick = {
                        onSelect(option.value)
                        hide()
                    },
                )
            }
        }
    }
}
