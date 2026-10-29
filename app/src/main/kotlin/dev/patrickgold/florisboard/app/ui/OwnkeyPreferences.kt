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

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluator
import dev.patrickgold.jetpref.datastore.model.PreferenceDataEvaluatorScope
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.ListPreferenceEntry
import dev.patrickgold.jetpref.datastore.ui.LocalDefaultDialogPrefStrings
import dev.patrickgold.jetpref.datastore.ui.LocalIconSpaceReserved
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefEnabled
import dev.patrickgold.jetpref.datastore.ui.LocalIsPrefVisible
import kotlinx.coroutines.launch

/**
 * SPEC §6 hidden-row rule: a setting without a place in the UI shows only while it holds a non-default
 * value, so whoever changed it can put it back. Once it's back at its default the row disappears. Use it
 * in `visibleIf`.
 */
@Composable
fun <V : Any> PreferenceDataEvaluatorScope.isChanged(pref: PreferenceData<V>): Boolean {
    return pref isNotEqualTo pref.default
}

/**
 * Drop-in for jetpref's `Preference` with the same parameters, order and defaults, drawn as an
 * [OwnkeyRow]. [icon] and [iconSpaceReserved] are accepted and ignored. New: [value] is shown
 * right-aligned, and [chevron] marks rows that open another screen. Emits nothing when hidden.
 */
@Composable
fun Preference(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summary: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
    onClick: (() -> Unit)? = null,
    eventModifier: (@Composable () -> Modifier)? = null,
    value: String? = null,
    chevron: Boolean = false,
) {
    require(!(onClick != null && eventModifier != null)) {
        "You cannot provide both an onClick lambda and an eventModifier."
    }
    if (LocalIsPrefVisible.current && visibleIf(PreferenceDataEvaluatorScope)) {
        val isEnabled = LocalIsPrefEnabled.current && enabledIf(PreferenceDataEvaluatorScope)
        CompositionLocalProvider(
            LocalIconSpaceReserved provides iconSpaceReserved,
            LocalIsPrefEnabled provides isEnabled,
            LocalIsPrefVisible provides true,
        ) {
            val interaction = when {
                onClick != null -> Modifier.clickable(
                    enabled = isEnabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                eventModifier != null -> eventModifier()
                else -> Modifier.semantics(mergeDescendants = true) { }
            }
            OwnkeyRowContent(
                title = title,
                modifier = modifier,
                interaction = interaction,
                summary = summary,
                value = value,
                enabled = isEnabled,
                leading = null,
                trailing = trailing,
                chevron = chevron,
            )
        }
    }
}

/**
 * Drop-in for jetpref's `SwitchPreference`: the whole row toggles (role Switch) and the trailing control
 * is an [OwnkeySwitch]. Same parameters, order and defaults.
 */
@Composable
fun SwitchPreference(
    pref: PreferenceData<Boolean>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summary: String? = null,
    summaryOn: String? = null,
    summaryOff: String? = null,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
) {
    val scope = rememberCoroutineScope()
    val prefValue by pref.collectAsState()

    Preference(
        modifier = modifier.testTag("pref-${pref.key}"),
        eventModifier = {
            Modifier.toggleable(
                value = prefValue,
                enabled = LocalIsPrefEnabled.current,
                role = Role.Switch,
                onValueChange = {
                    scope.launch {
                        pref.set(it)
                    }
                },
            )
        },
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = when {
            prefValue && summaryOn != null -> summaryOn
            !prefValue && summaryOff != null -> summaryOff
            else -> summary
        },
        trailing = {
            OwnkeySwitch(
                checked = prefValue,
                onCheckedChange = null,
                enabled = LocalIsPrefEnabled.current,
            )
        },
        enabledIf = enabledIf,
        visibleIf = visibleIf,
    )
}

/**
 * Drop-in for jetpref's `ListPreference`. The row shows the selected label as its value; tapping opens
 * an [OwnkeySheet] of [OwnkeyChoiceRow]s, and picking one applies it and closes the sheet (no OK,
 * Cancel or Default). With [switchPref] the row gets a trailing switch; while it's off the choices are
 * dimmed and disabled, and picking never flips the switch. [dialogStrings] is accepted and ignored.
 * New: an optional [summary] under the title.
 */
@SuppressLint("ModifierParameter")
@Composable
fun <V : Any> ListPreference(
    listPref: PreferenceData<V>,
    switchPref: PreferenceData<Boolean>? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconSpaceReserved: Boolean = LocalIconSpaceReserved.current,
    title: String,
    summarySwitchDisabled: String? = null,
    dialogStrings: DialogPrefStrings = LocalDefaultDialogPrefStrings.current,
    enabledIf: PreferenceDataEvaluator = { true },
    visibleIf: PreferenceDataEvaluator = { true },
    entries: List<ListPreferenceEntry<V>>,
    summary: String? = null,
) {
    val scope = rememberCoroutineScope()
    val listPrefValue by listPref.collectAsState()
    val switchPrefState = switchPref?.collectAsState()
    val isSwitchOn = switchPrefState?.value ?: true
    var isSheetOpen by remember { mutableStateOf(false) }
    val selectedIndex = entries.indexOfFirst { it.key == listPrefValue }
    val selectedLabel = entries.getOrNull(selectedIndex)?.label

    Preference(
        modifier = modifier.testTag("pref-${listPref.key}"),
        icon = icon,
        iconSpaceReserved = iconSpaceReserved,
        title = title,
        summary = summary,
        trailing = if (switchPref != null && switchPrefState != null) {
            {
                val enabled = LocalIsPrefEnabled.current
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 48.dp)
                        .semantics { contentDescription = title }
                        .toggleable(
                            value = switchPrefState.value,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = {
                                scope.launch {
                                    switchPref.set(it)
                                }
                            },
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    OwnkeySwitch(
                        checked = switchPrefState.value,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
                }
            }
        } else {
            null
        },
        enabledIf = enabledIf,
        visibleIf = visibleIf,
        onClick = { isSheetOpen = true },
        value = if (isSwitchOn) selectedLabel else (summarySwitchDisabled ?: selectedLabel),
    )

    if (isSheetOpen) {
        OwnkeySheet(
            title = title,
            onDismiss = { isSheetOpen = false },
            initialScrollIndex = selectedIndex.coerceAtLeast(0),
        ) { hide ->
            itemsIndexed(entries) { index, entry ->
                val isSelected = entry.key == listPrefValue
                val description = entry.description.takeIf { it.isNotBlank() }
                    ?.takeIf { !entry.showDescriptionOnlyIfSelected || isSelected }
                OwnkeyChoiceRow(
                    modifier = Modifier.testTag("sheet-option-$index"),
                    label = entry.label,
                    summary = description,
                    selected = isSelected,
                    enabled = isSwitchOn,
                    onClick = {
                        scope.launch {
                            listPref.set(entry.key)
                        }
                        hide()
                    },
                )
            }
        }
    }
}
