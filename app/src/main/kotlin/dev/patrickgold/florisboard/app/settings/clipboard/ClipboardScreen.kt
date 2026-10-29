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

package dev.patrickgold.florisboard.app.settings.clipboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.DialogSliderPreference
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistoryHelper
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSkinTone
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ClipboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__clipboard__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearEmojiHistoryDialog by rememberSaveable { mutableStateOf(false) }

    content {
        val clipboard = prefs.clipboard
        PreferenceGroup(title = stringRes(R.string.settings__clipboard__group_clipboard)) {
            SwitchPreference(
                clipboard.historyEnabled,
                modifier = Modifier.testTag("clipboard-history"),
                title = stringRes(R.string.settings__clipboard__history),
                summary = stringRes(R.string.settings__clipboard__history_summary),
            )
            SwitchPreference(
                clipboard.suggestionEnabled,
                title = stringRes(R.string.settings__clipboard__suggest_recent),
                summary = stringRes(R.string.settings__clipboard__suggest_recent_summary),
            )
            DialogSliderPreference(
                clipboard.suggestionTimeout,
                title = stringRes(R.string.pref__clipboard__suggestion_timeout__label),
                valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
                min = 30,
                max = 300,
                stepIncrement = 5,
                enabledIf = { clipboard.suggestionEnabled isEqualTo true },
                visibleIf = { clipboard.suggestionTimeout isNotEqualTo clipboard.suggestionTimeout.default },
            )
            SwitchPreference(
                clipboard.historyAutoCleanOldEnabled,
                modifier = Modifier.testTag("clipboard-delete-old"),
                title = stringRes(R.string.settings__clipboard__delete_old),
                summary = stringRes(R.string.settings__clipboard__delete_old_summary),
                enabledIf = { clipboard.historyEnabled isEqualTo true },
            )
            DialogSliderPreference(
                clipboard.historyAutoCleanOldAfter,
                title = stringRes(R.string.settings__clipboard__after),
                valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
                min = 0,
                max = 120,
                stepIncrement = 5,
                enabledIf = { clipboard.historyEnabled isEqualTo true },
                visibleIf = { clipboard.historyAutoCleanOldEnabled isEqualTo true },
            )
            SwitchPreference(
                clipboard.historyAutoCleanSensitiveEnabled,
                title = stringRes(R.string.settings__clipboard__clear_sensitive),
                summary = stringRes(R.string.settings__clipboard__clear_sensitive_summary),
                enabledIf = { clipboard.historyEnabled isEqualTo true },
                visibleIf = { AndroidVersion.ATLEAST_API33_T },
            )
            DialogSliderPreference(
                clipboard.historyAutoCleanSensitiveAfter,
                title = stringRes(R.string.settings__clipboard__after),
                valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                min = 0,
                max = 300,
                stepIncrement = 10,
                enabledIf = { clipboard.historyEnabled isEqualTo true },
                visibleIf = {
                    AndroidVersion.ATLEAST_API33_T && clipboard.historyAutoCleanSensitiveEnabled isEqualTo true
                },
            )
            HiddenClipboardHistoryRows()
            // A retained sync-to-system value still hands copies to Android, so the summary can't promise they stay.
            val syncToSystem by clipboard.syncToSystem.collectAsState()
            SwitchPreference(
                clipboard.useInternalClipboard,
                title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
                summary = if (syncToSystem.shouldSyncSet) {
                    stringRes(R.string.settings__clipboard__internal_summary_synced)
                } else {
                    stringRes(R.string.settings__clipboard__internal_summary)
                },
            )
            ListPreference(
                clipboard.syncToFloris,
                title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
                enabledIf = { clipboard.useInternalClipboard isEqualTo true },
                visibleIf = { clipboard.syncToFloris isNotEqualTo clipboard.syncToFloris.default },
            )
            ListPreference(
                clipboard.syncToSystem,
                title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
                entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
                enabledIf = { clipboard.useInternalClipboard isEqualTo true },
                visibleIf = { clipboard.syncToSystem isNotEqualTo clipboard.syncToSystem.default },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__clipboard__group_emoji)) {
            ListPreference(
                prefs.emoji.preferredSkinTone,
                modifier = Modifier.testTag("emoji-skin-tone"),
                title = stringRes(R.string.settings__clipboard__skin_tone),
                entries = enumDisplayEntriesOf(EmojiSkinTone::class),
            )
            SwitchPreference(
                prefs.emoji.historyEnabled,
                title = stringRes(R.string.settings__clipboard__recent_emoji),
                summary = stringRes(R.string.settings__clipboard__recent_emoji_summary),
            )
            HiddenEmojiHistoryRows()
            OwnkeyRow(
                modifier = Modifier.testTag("emoji-clear-history"),
                title = stringRes(R.string.settings__clipboard__clear_emoji_history),
                titleColor = OwnkeyTokens.Danger,
                onClick = { showClearEmojiHistoryDialog = true },
            )
        }

        if (showClearEmojiHistoryDialog) {
            OwnkeyDialog(
                title = stringRes(R.string.settings__clipboard__clear_emoji_history_title),
                text = stringRes(R.string.settings__clipboard__clear_emoji_history_message),
                confirmLabel = stringRes(R.string.settings__clipboard__clear_emoji_history_confirm),
                destructive = true,
                onConfirm = {
                    showClearEmojiHistoryDialog = false
                    scope.launch {
                        EmojiHistoryHelper.deletePinned(prefs = prefs)
                        EmojiHistoryHelper.deleteHistory(prefs = prefs)
                        context.showShortToast(R.string.settings__clipboard__clear_emoji_history_done)
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showClearEmojiHistoryDialog = false },
            )
        }
    }
}

/** Clipboard history details that left the UI; each shows up only while it holds a non-default value. */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.HiddenClipboardHistoryRows() {
    val clipboard = prefs.clipboard
    DialogSliderPreference(
        primaryPref = clipboard.historyNumGridColumnsPortrait,
        secondaryPref = clipboard.historyNumGridColumnsLandscape,
        title = stringRes(R.string.pref__clipboard__num_history_grid_columns__label),
        primaryLabel = stringRes(R.string.screen_orientation__portrait),
        secondaryLabel = stringRes(R.string.screen_orientation__landscape),
        valueLabel = { numGridColumns ->
            if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                stringRes(R.string.general__auto)
            } else {
                numGridColumns.toString()
            }
        },
        min = 0,
        max = 10,
        stepIncrement = 1,
        enabledIf = { clipboard.historyEnabled isEqualTo true },
        visibleIf = {
            val portraitChanged = clipboard.historyNumGridColumnsPortrait isNotEqualTo
                clipboard.historyNumGridColumnsPortrait.default
            val landscapeChanged = clipboard.historyNumGridColumnsLandscape isNotEqualTo
                clipboard.historyNumGridColumnsLandscape.default
            portraitChanged || landscapeChanged
        },
    )
    SwitchPreference(
        clipboard.historySizeLimitEnabled,
        title = stringRes(R.string.pref__clipboard__limit_history_size__label),
        enabledIf = { clipboard.historyEnabled isEqualTo true },
        visibleIf = { clipboard.historySizeLimitEnabled isNotEqualTo clipboard.historySizeLimitEnabled.default },
    )
    DialogSliderPreference(
        clipboard.historySizeLimit,
        title = stringRes(R.string.pref__clipboard__max_history_size__label),
        valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
        min = 5,
        max = 100,
        stepIncrement = 5,
        enabledIf = {
            clipboard.historyEnabled isEqualTo true && clipboard.historySizeLimitEnabled isEqualTo true
        },
        visibleIf = { clipboard.historySizeLimit isNotEqualTo clipboard.historySizeLimit.default },
    )
    SwitchPreference(
        clipboard.historyHideOnPaste,
        title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
        enabledIf = { clipboard.historyEnabled isEqualTo true },
        visibleIf = { clipboard.historyHideOnPaste isNotEqualTo clipboard.historyHideOnPaste.default },
    )
    SwitchPreference(
        clipboard.historyHideOnNextTextField,
        title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
        enabledIf = { clipboard.historyEnabled isEqualTo true },
        visibleIf = {
            clipboard.historyHideOnNextTextField isNotEqualTo clipboard.historyHideOnNextTextField.default
        },
    )
    SwitchPreference(
        clipboard.clearPrimaryClipAffectsHistoryIfUnpinned,
        title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
        summary = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__summary),
        enabledIf = { clipboard.historyEnabled isEqualTo true },
        visibleIf = {
            clipboard.clearPrimaryClipAffectsHistoryIfUnpinned isNotEqualTo
                clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.default
        },
    )
}

/** Emoji history details that left the UI; each shows up only while it holds a non-default value. */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.HiddenEmojiHistoryRows() {
    val emoji = prefs.emoji
    ListPreference(
        emoji.historyPinnedUpdateStrategy,
        title = stringRes(R.string.prefs__media__emoji_history_pinned_update_strategy),
        entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
        enabledIf = { emoji.historyEnabled isEqualTo true },
        visibleIf = { emoji.historyPinnedUpdateStrategy isNotEqualTo emoji.historyPinnedUpdateStrategy.default },
    )
    ListPreference(
        emoji.historyRecentUpdateStrategy,
        title = stringRes(R.string.prefs__media__emoji_history_recent_update_strategy),
        entries = enumDisplayEntriesOf(EmojiHistory.UpdateStrategy::class),
        enabledIf = { emoji.historyEnabled isEqualTo true },
        visibleIf = { emoji.historyRecentUpdateStrategy isNotEqualTo emoji.historyRecentUpdateStrategy.default },
    )
    DialogSliderPreference(
        primaryPref = emoji.historyPinnedMaxSize,
        secondaryPref = emoji.historyRecentMaxSize,
        title = stringRes(R.string.prefs__media__emoji_history_max_size),
        primaryLabel = stringRes(R.string.emoji__history__pinned),
        secondaryLabel = stringRes(R.string.emoji__history__recent),
        valueLabel = { maxSize ->
            if (maxSize == EmojiHistory.MaxSizeUnlimited) {
                stringRes(R.string.general__unlimited)
            } else {
                pluralsRes(R.plurals.unit__items__written, maxSize, "v" to maxSize)
            }
        },
        min = 0,
        max = 120,
        stepIncrement = 1,
        enabledIf = { emoji.historyEnabled isEqualTo true },
        visibleIf = {
            val pinnedChanged = emoji.historyPinnedMaxSize isNotEqualTo emoji.historyPinnedMaxSize.default
            val recentChanged = emoji.historyRecentMaxSize isNotEqualTo emoji.historyRecentMaxSize.default
            pinnedChanged || recentChanged
        },
    )
}
