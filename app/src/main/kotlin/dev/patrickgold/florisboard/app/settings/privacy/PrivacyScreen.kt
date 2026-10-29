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

package dev.patrickgold.florisboard.app.settings.privacy

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
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.Preference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.nlpManager
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

@Composable
fun PrivacyScreen() = FlorisScreen {
    title = stringRes(R.string.settings__privacy__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val nlpManager by context.nlpManager()
    val scope = rememberCoroutineScope()
    var showClearLearnedDataDialog by rememberSaveable { mutableStateOf(false) }

    content {
        PreferenceGroup(title = stringRes(R.string.settings__privacy__group_typing_data)) {
            ListPreference(
                prefs.suggestion.incognitoMode,
                modifier = Modifier.testTag("privacy-incognito"),
                title = stringRes(R.string.pref__suggestion__incognito_mode__label),
                summary = stringRes(R.string.settings__privacy__incognito_summary),
                entries = enumDisplayEntriesOf(IncognitoMode::class),
            )
            SwitchPreference(
                prefs.suggestion.personalizedLearningEnabled,
                modifier = Modifier.testTag("privacy-personalized"),
                title = stringRes(R.string.pref__suggestion__personalized_learning__label),
                summary = stringRes(R.string.settings__privacy__personalized_summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("privacy-clear-learned"),
                title = stringRes(R.string.pref__suggestion__clear_personalized_data__label),
                titleColor = OwnkeyTokens.Danger,
                onClick = { showClearLearnedDataDialog = true },
            )
        }

        PreferenceGroup(
            title = stringRes(R.string.settings__privacy__group_backup),
            footer = stringRes(R.string.settings__privacy__backup_footer),
        ) {
            Preference(
                modifier = Modifier.testTag("privacy-backup"),
                title = stringRes(R.string.settings__privacy__back_up),
                onClick = { navController.navigate(Routes.Settings.Backup) },
                chevron = true,
            )
            Preference(
                modifier = Modifier.testTag("privacy-restore"),
                title = stringRes(R.string.settings__privacy__restore),
                onClick = { navController.navigate(Routes.Settings.Restore) },
                chevron = true,
            )
        }

        if (showClearLearnedDataDialog) {
            OwnkeyDialog(
                title = stringRes(R.string.settings__privacy__clear_learned_title),
                text = stringRes(R.string.settings__privacy__clear_learned_message),
                confirmLabel = stringRes(R.string.settings__privacy__clear_learned_confirm),
                destructive = true,
                onConfirm = {
                    showClearLearnedDataDialog = false
                    scope.launch {
                        nlpManager.clearPersonalizedData()
                        context.showShortToast(R.string.pref__suggestion__clear_personalized_data__done)
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = { showClearLearnedDataDialog = false },
            )
        }
    }
}
