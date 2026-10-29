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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.DialogSliderPreference
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.Preference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.app.ui.isChanged
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.keyboard.SplitLayout
import dev.patrickgold.florisboard.ime.keyboard.SplitLayoutMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.ExtendedActionsPlacement
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.stringRes

private enum class KeyboardReset { TOOLBAR, WINDOW }

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val scope = rememberCoroutineScope()
        var pendingReset by remember { mutableStateOf<KeyboardReset?>(null) }
        val percent: @Composable (Int) -> String = { stringRes(R.string.unit__percent__symbol, "v" to it) }

        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier.testTag("keyboard-sound-vibration"),
                title = stringRes(R.string.settings__input_feedback__title),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("keyboard-gestures"),
                title = stringRes(R.string.settings__gestures__title),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Gestures) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_layout__label)) {
            SwitchPreference(
                prefs.keyboard.numberRow,
                title = stringRes(R.string.pref__keyboard__number_row__label),
                summary = stringRes(R.string.pref__keyboard__number_row__summary),
            )
            SwitchPreference(
                prefs.keyboard.hintedNumberRowEnabled,
                title = stringRes(R.string.settings__keyboard__number_hints),
                summary = stringRes(R.string.settings__keyboard__number_hints_summary),
                enabledIf = { prefs.keyboard.numberRow.isFalse() },
            )
            ListPreference(
                listPref = prefs.keyboard.hintedNumberRowMode,
                title = stringRes(R.string.settings__keyboard__number_hint_priority),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
                enabledIf = { prefs.keyboard.numberRow.isFalse() },
                visibleIf = { isChanged(prefs.keyboard.hintedNumberRowMode) },
            )
            SwitchPreference(
                prefs.keyboard.hintedSymbolsEnabled,
                title = stringRes(R.string.settings__keyboard__symbol_hints),
                summary = stringRes(R.string.settings__keyboard__symbol_hints_summary),
            )
            ListPreference(
                listPref = prefs.keyboard.hintedSymbolsMode,
                title = stringRes(R.string.settings__keyboard__symbol_hint_priority),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
                visibleIf = { isChanged(prefs.keyboard.hintedSymbolsMode) },
            )

            val utilityKeyEnabled by prefs.keyboard.utilityKeyEnabled.collectAsState()
            val utilityKeyAction by prefs.keyboard.utilityKeyAction.collectAsState()
            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("keyboard-space-neighbor"),
                title = stringRes(R.string.settings__keyboard__space_neighbor),
                options = listOf(
                    ChoiceOption(SpaceNeighborKey.NONE, stringRes(R.string.settings__keyboard__space_neighbor_none)),
                    ChoiceOption(SpaceNeighborKey.EMOJI, stringRes(R.string.settings__keyboard__space_neighbor_emoji)),
                    ChoiceOption(SpaceNeighborKey.LANGUAGE, stringRes(R.string.settings__keyboard__space_neighbor_language)),
                    ChoiceOption(
                        SpaceNeighborKey.AUTOMATIC,
                        stringRes(R.string.settings__keyboard__space_neighbor_automatic),
                        stringRes(R.string.settings__keyboard__space_neighbor_automatic_summary),
                    ),
                    ChoiceOption(
                        SpaceNeighborKey.SWITCH_KEYBOARD,
                        stringRes(R.string.settings__keyboard__space_neighbor_switch_keyboard),
                    ),
                ),
                selected = SpaceNeighborKey.of(utilityKeyEnabled, utilityKeyAction),
                onSelect = { choice ->
                    val writes = choice.writes()
                    scope.launch {
                        writes.action?.let { prefs.keyboard.utilityKeyAction.set(it) }
                        prefs.keyboard.utilityKeyEnabled.set(writes.enabled)
                    }
                },
            )

            ListPreference(
                prefs.keyboard.splitLayoutMode,
                title = stringRes(R.string.pref__keyboard__split_layout_mode__label),
                entries = enumDisplayEntriesOf(SplitLayoutMode::class),
            )
            DialogSliderPreference(
                prefs.keyboard.splitLayoutGapPercent,
                title = stringRes(R.string.settings__keyboard__split_gap),
                valueLabel = percent,
                min = SplitLayout.GapPercentMin,
                max = SplitLayout.GapPercentMax,
                stepIncrement = 1,
                visibleIf = { prefs.keyboard.splitLayoutMode isNotEqualTo SplitLayoutMode.NEVER },
            )
            DialogSliderPreference(
                prefs.keyboard.floatingSplitOpacity,
                title = stringRes(R.string.floating_split__opacity),
                valueLabel = percent,
                min = 0,
                max = 100,
                stepIncrement = 1,
                visibleIf = {
                    prefs.keyboard.splitLayoutMode isNotEqualTo SplitLayoutMode.NEVER &&
                        isChanged(prefs.keyboard.floatingSplitOpacity)
                },
            )
            ListPreference(
                prefs.keyboard.landscapeInputUiMode,
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
                visibleIf = { isChanged(prefs.keyboard.landscapeInputUiMode) },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__group_keys)) {
            DialogSliderPreference(
                prefs.keyboard.fontSizeMultiplierPortrait,
                title = stringRes(R.string.settings__keyboard__key_label_size),
                valueLabel = percent,
                min = KeyLabelSize.MIN,
                max = KeyLabelSize.MAX,
                stepIncrement = KeyLabelSize.STEP,
                // The drop-in writes portrait; landscape follows so both orientations match.
                onPreviewSelectedValue = { size ->
                    val writes = KeyLabelSize.writes(size)
                    scope.launch { prefs.keyboard.fontSizeMultiplierLandscape.set(writes.landscape) }
                },
            )
            DialogSliderPreference(
                prefs.keyboard.longPressDelay,
                title = stringRes(R.string.settings__keyboard__long_press_delay),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100,
                max = 700,
                stepIncrement = 10,
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.settings__keyboard__key_preview),
                summary = stringRes(R.string.settings__keyboard__key_preview_summary),
            )
            ListPreference(
                prefs.keyboard.spaceBarMode,
                title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
                entries = enumDisplayEntriesOf(SpaceBarMode::class),
                visibleIf = { isChanged(prefs.keyboard.spaceBarMode) },
            )
            ListPreference(
                prefs.keyboard.capitalizationBehavior,
                title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
                entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
                visibleIf = { isChanged(prefs.keyboard.capitalizationBehavior) },
            )
            SwitchPreference(
                prefs.keyboard.mergeHintPopupsEnabled,
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
                summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
                visibleIf = { isChanged(prefs.keyboard.mergeHintPopupsEnabled) },
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
                visibleIf = { isChanged(prefs.keyboard.spaceBarSwitchesToCharacters) },
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.keySpacingVertical,
                secondaryPref = prefs.keyboard.keySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = percent,
                min = 50,
                max = 150,
                stepIncrement = 5,
                visibleIf = {
                    isChanged(prefs.keyboard.keySpacingVertical) || isChanged(prefs.keyboard.keySpacingHorizontal)
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__group_toolbar)) {
            SwitchPreference(
                prefs.smartbar.enabled,
                title = stringRes(R.string.settings__keyboard__show_toolbar),
                summary = stringRes(R.string.settings__keyboard__show_toolbar_summary),
            )
            SwitchPreference(
                prefs.smartbar.flipToggles,
                title = stringRes(R.string.settings__keyboard__dictation_left),
                enabledIf = { prefs.smartbar.enabled isEqualTo true },
            )
            ListPreference(
                prefs.suggestion.displayMode,
                title = stringRes(R.string.settings__keyboard__suggestion_layout),
                entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
                visibleIf = { isChanged(prefs.suggestion.displayMode) },
            )
            ListPreference(
                prefs.smartbar.extendedActionsPlacement,
                title = stringRes(R.string.pref__smartbar__extended_actions_placement__label),
                entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class),
                visibleIf = { isChanged(prefs.smartbar.extendedActionsPlacement) },
            )
            Preference(
                modifier = Modifier.testTag("keyboard-reset-toolbar"),
                title = stringRes(R.string.settings__keyboard__reset_toolbar),
                onClick = { pendingReset = KeyboardReset.TOOLBAR },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__group_other)) {
            SwitchPreference(
                prefs.physicalKeyboard.showOnScreenKeyboard,
                title = stringRes(R.string.settings__keyboard__physical_keyboard),
                summary = stringRes(R.string.settings__keyboard__physical_keyboard_summary),
            )
            ListPreference(
                listPref = prefs.keyboard.incognitoDisplayMode,
                title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
                entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
                visibleIf = { isChanged(prefs.keyboard.incognitoDisplayMode) },
            )
            Preference(
                modifier = Modifier.testTag("keyboard-reset-window"),
                title = stringRes(R.string.settings__keyboard__reset_window),
                onClick = { pendingReset = KeyboardReset.WINDOW },
            )
        }

        when (pendingReset) {
            KeyboardReset.TOOLBAR -> OwnkeyDialog(
                title = stringRes(R.string.settings__keyboard__reset_toolbar_confirm_title),
                text = stringRes(R.string.settings__keyboard__reset_toolbar_confirm_text),
                confirmLabel = stringRes(R.string.action__reset),
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { pendingReset = null },
                onConfirm = {
                    pendingReset = null
                    // Same reset as Devtools.
                    scope.launch {
                        prefs.smartbar.actionArrangement.set(QuickActionArrangement.Default)
                        context.showLongToast(R.string.settings__keyboard__reset_toolbar_done)
                    }
                },
            )
            KeyboardReset.WINDOW -> OwnkeyDialog(
                title = stringRes(R.string.settings__keyboard__reset_window_confirm_title),
                text = stringRes(R.string.settings__keyboard__reset_window_confirm_text),
                confirmLabel = stringRes(R.string.action__reset),
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { pendingReset = null },
                onConfirm = {
                    pendingReset = null
                    // Same reset as Devtools.
                    scope.launch {
                        prefs.keyboard.windowConfig.reset().fold(
                            onSuccess = {
                                context.showLongToast(R.string.settings__keyboard__reset_window_done)
                            },
                            onFailure = { error ->
                                context.showLongToast(
                                    R.string.settings__keyboard__reset_window_failed,
                                    "message" to "${error.localizedMessage}",
                                )
                            },
                        )
                    }
                },
            )
            null -> Unit
        }
    }
}
