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

package dev.patrickgold.florisboard.app.settings.typing

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun TypingScreen() = FlorisScreen {
    title = stringRes(R.string.settings__typing__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        FlorisErrorCard(
            modifier = Modifier.padding(8.dp),
            text = """
                Suggestions and spell checking are available in this build, but still experimental and language-dependent.
                If behavior looks off for your language, please report feedback.
            """.trimIndent().replace('\n', ' '),
        )

        PreferenceGroup(title = stringRes(R.string.pref__suggestion__title)) {
            SwitchPreference(
                prefs.suggestion.enabled,
                title = stringRes(R.string.pref__suggestion__enabled__label),
                summary = stringRes(R.string.pref__suggestion__enabled__summary),
            )
            SwitchPreference(
                prefs.suggestion.blockPossiblyOffensive,
                title = stringRes(R.string.pref__suggestion__block_possibly_offensive__label),
                summary = stringRes(R.string.pref__suggestion__block_possibly_offensive__summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.suggestion.api30InlineSuggestionsEnabled,
                title = stringRes(R.string.pref__suggestion__api30_inline_suggestions_enabled__label),
                summary = stringRes(R.string.pref__suggestion__api30_inline_suggestions_enabled__summary),
                visibleIf = { AndroidVersion.ATLEAST_API30_R },
            )
            ListPreference(
                prefs.suggestion.incognitoMode,
                icon = ImageVector.vectorResource(id = R.drawable.ic_incognito),
                title = stringRes(R.string.pref__suggestion__incognito_mode__label),
                entries = enumDisplayEntriesOf(IncognitoMode::class),
            )
            SwitchPreference(
                prefs.suggestion.personalizedLearningEnabled,
                title = stringRes(R.string.pref__suggestion__personalized_learning__label),
                summary = stringRes(R.string.pref__suggestion__personalized_learning__summary),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val nlpManager by context.nlpManager()
            var showClearLearnedDataDialog by remember { mutableStateOf(false) }
            Preference(
                icon = Icons.Default.DeleteSweep,
                title = stringRes(R.string.pref__suggestion__clear_personalized_data__label),
                summary = stringRes(R.string.pref__suggestion__clear_personalized_data__summary),
                onClick = { showClearLearnedDataDialog = true },
            )
            if (showClearLearnedDataDialog) {
                JetPrefAlertDialog(
                    title = stringRes(R.string.pref__suggestion__clear_personalized_data__label),
                    confirmLabel = stringRes(R.string.action__yes),
                    onConfirm = {
                        scope.launch {
                            nlpManager.clearPersonalizedData()
                            context.showShortToast(R.string.pref__suggestion__clear_personalized_data__done)
                        }
                        showClearLearnedDataDialog = false
                    },
                    dismissLabel = stringRes(R.string.action__no),
                    onDismiss = { showClearLearnedDataDialog = false },
                ) {
                    Text(text = stringRes(R.string.pref__suggestion__clear_personalized_data__confirm_message))
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.pref__correction__title)) {
            SwitchPreference(
                prefs.correction.autoCapitalization,
                title = stringRes(R.string.pref__correction__auto_capitalization__label),
                summary = stringRes(R.string.pref__correction__auto_capitalization__summary),
            )
            SwitchPreference(
                prefs.correction.appSpecificAutocorrectProfilesEnabled,
                title = stringRes(R.string.pref__correction__app_specific_autocorrect_profiles_enabled__label),
                summary = stringRes(R.string.pref__correction__app_specific_autocorrect_profiles_enabled__summary),
            )
            DialogSliderPreference(
                prefs.correction.appSpecificAutocorrectChatAggressivenessPercent,
                title = stringRes(R.string.pref__correction__app_specific_autocorrect_chat_aggressiveness__label),
                valueLabel = { percent -> stringRes(R.string.unit__percent__symbol, "v" to percent) },
                min = 70,
                max = 130,
                stepIncrement = 2,
                enabledIf = { prefs.correction.appSpecificAutocorrectProfilesEnabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.correction.appSpecificAutocorrectEmailAggressivenessPercent,
                title = stringRes(R.string.pref__correction__app_specific_autocorrect_email_aggressiveness__label),
                valueLabel = { percent -> stringRes(R.string.unit__percent__symbol, "v" to percent) },
                min = 70,
                max = 130,
                stepIncrement = 2,
                enabledIf = { prefs.correction.appSpecificAutocorrectProfilesEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.correction.autoSpacePunctuation,
                icon = Icons.Default.SpaceBar,
                title = stringRes(R.string.pref__correction__auto_space_punctuation__label),
                summary = stringRes(R.string.pref__correction__auto_space_punctuation__summary),
            )
            SwitchPreference(
                prefs.correction.rememberCapsLockState,
                title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
                summary = stringRes(R.string.pref__correction__remember_caps_lock_state__summary),
            )
            SwitchPreference(
                prefs.correction.doubleSpacePeriod,
                title = stringRes(R.string.pref__correction__double_space_period__label),
                summary = stringRes(R.string.pref__correction__double_space_period__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__spelling__title)) {
            val florisSpellCheckerEnabled = remember { mutableStateOf(false) }
            SpellCheckerServiceSelector(florisSpellCheckerEnabled)
            ListPreference(
                prefs.spelling.languageMode,
                icon = Icons.Default.Language,
                title = stringRes(R.string.pref__spelling__language_mode__label),
                entries = enumDisplayEntriesOf(SpellingLanguageMode::class),
                enabledIf = { florisSpellCheckerEnabled.value },
            )
            SwitchPreference(
                prefs.spelling.useContacts,
                icon = Icons.Default.Contacts,
                title = stringRes(R.string.pref__spelling__use_contacts__label),
                summary = stringRes(R.string.pref__spelling__use_contacts__summary),
                enabledIf = { florisSpellCheckerEnabled.value },
                visibleIf = { false }, // For now
            )
            SwitchPreference(
                prefs.spelling.useUdmEntries,
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.pref__spelling__use_udm_entries__label),
                summary = stringRes(R.string.pref__spelling__use_udm_entries__summary),
                enabledIf = { florisSpellCheckerEnabled.value },
                visibleIf = { false }, // For now
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__dictionary__title)) {
            Preference(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = stringRes(R.string.settings__dictionary__title),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
            )
        }
    }
}
