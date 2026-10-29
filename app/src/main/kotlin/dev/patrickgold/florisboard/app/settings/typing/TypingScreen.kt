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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.DialogSliderPreference
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.Preference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun TypingScreen() = FlorisScreen {
    title = stringRes(R.string.settings__typing__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        PreferenceGroup(title = stringRes(R.string.settings__typing__group_suggestions)) {
            SwitchPreference(
                prefs.suggestion.enabled,
                title = stringRes(R.string.settings__typing__show_suggestions),
            )
            AutocorrectRow()
            // Hidden (shown only while it differs from the default, so it can be reset).
            SwitchPreference(
                prefs.correction.appSpecificAutocorrectProfilesEnabled,
                title = stringRes(R.string.pref__correction__app_specific_autocorrect_profiles_enabled__label),
                summary = stringRes(R.string.pref__correction__app_specific_autocorrect_profiles_enabled__summary),
                visibleIf = {
                    prefs.correction.appSpecificAutocorrectProfilesEnabled isNotEqualTo
                        prefs.correction.appSpecificAutocorrectProfilesEnabled.default
                },
            )
            SwitchPreference(
                prefs.emoji.suggestionEnabled,
                title = stringRes(R.string.settings__typing__emoji_suggestions),
                summary = stringRes(R.string.settings__typing__emoji_suggestions_summary),
            )
            HiddenEmojiSuggestionRows()
            SwitchPreference(
                prefs.suggestion.blockPossiblyOffensive,
                title = stringRes(R.string.settings__typing__block_offensive),
                enabledIf = { prefs.suggestion.enabled isEqualTo true },
            )
            SwitchPreference(
                prefs.suggestion.api30InlineSuggestionsEnabled,
                title = stringRes(R.string.settings__typing__autofill),
                summary = stringRes(R.string.settings__typing__autofill_summary),
                visibleIf = { AndroidVersion.ATLEAST_API30_R },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__typing__group_formatting)) {
            SwitchPreference(
                prefs.correction.autoCapitalization,
                title = stringRes(R.string.settings__typing__auto_capitalize),
                summary = stringRes(R.string.settings__typing__auto_capitalize_summary),
            )
            SwitchPreference(
                prefs.correction.autoSpacePunctuation,
                title = stringRes(R.string.settings__typing__space_after_punctuation),
                summary = stringRes(R.string.settings__typing__space_after_punctuation_summary),
            )
            SwitchPreference(
                prefs.correction.doubleSpacePeriod,
                title = stringRes(R.string.settings__typing__double_space_period),
                summary = stringRes(R.string.settings__typing__double_space_period_summary),
            )
            SwitchPreference(
                prefs.correction.rememberCapsLockState,
                title = stringRes(R.string.pref__correction__remember_caps_lock_state__label),
                summary = stringRes(R.string.pref__correction__remember_caps_lock_state__summary),
                visibleIf = {
                    prefs.correction.rememberCapsLockState isNotEqualTo prefs.correction.rememberCapsLockState.default
                },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__typing__group_words)) {
            Preference(
                title = stringRes(R.string.settings__dictionary__title),
                onClick = { navController.navigate(Routes.Settings.Dictionary) },
                chevron = true,
            )
            val spellChecker = rememberSpellCheckerState()
            SpellCheckerRow(spellChecker)
            ListPreference(
                prefs.spelling.languageMode,
                title = stringRes(R.string.settings__typing__spell_checker_languages),
                entries = enumDisplayEntriesOf(SpellingLanguageMode::class),
                enabledIf = { spellChecker.isOwnkey },
                visibleIf = { prefs.spelling.languageMode isNotEqualTo prefs.spelling.languageMode.default },
            )
        }
    }
}

/** Off / Gentle / Normal / Strong, written to the enable switch and the strength through [AutocorrectLevel]. */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.AutocorrectRow() {
    val scope = rememberCoroutineScope()
    val enabled by prefs.correction.highCertaintyAutocorrectEnabled.collectAsState()
    val strength by prefs.correction.autocorrectStrength.collectAsState()
    OwnkeyChoiceListRow(
        modifier = Modifier.testTag("typing-autocorrect"),
        title = stringRes(R.string.pref__correction__autocorrect__label),
        options = listOf(
            ChoiceOption(
                AutocorrectLevel.OFF,
                stringRes(R.string.settings__typing__autocorrect_off),
                stringRes(R.string.settings__typing__autocorrect_off_summary),
            ),
            ChoiceOption(
                AutocorrectLevel.GENTLE,
                stringRes(R.string.enum__autocorrect_strength__gentle),
                stringRes(R.string.enum__autocorrect_strength__gentle__description),
            ),
            ChoiceOption(
                AutocorrectLevel.NORMAL,
                stringRes(R.string.enum__autocorrect_strength__normal),
                stringRes(R.string.enum__autocorrect_strength__normal__description),
            ),
            ChoiceOption(
                AutocorrectLevel.STRONG,
                stringRes(R.string.enum__autocorrect_strength__strong),
                stringRes(R.string.enum__autocorrect_strength__strong__description),
            ),
        ),
        selected = AutocorrectLevel.of(AutocorrectPrefs(enabled, strength)),
        onSelect = { level ->
            val next = level.toPrefs(currentStrength = strength)
            scope.launch {
                prefs.correction.autocorrectStrength.set(next.strength)
                prefs.correction.highCertaintyAutocorrectEnabled.set(next.enabled)
            }
        },
    )
}

/** Emoji suggestion details that left the UI; each shows up only while it holds a non-default value. */
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.HiddenEmojiSuggestionRows() {
    val emoji = prefs.emoji
    ListPreference(
        emoji.suggestionType,
        title = stringRes(R.string.prefs__media__emoji_suggestion_type),
        entries = enumDisplayEntriesOf(EmojiSuggestionType::class),
        enabledIf = { emoji.suggestionEnabled isEqualTo true },
        visibleIf = { emoji.suggestionType isNotEqualTo emoji.suggestionType.default },
    )
    SwitchPreference(
        emoji.suggestionUpdateHistory,
        title = stringRes(R.string.prefs__media__emoji_suggestion_update_history),
        summary = stringRes(R.string.prefs__media__emoji_suggestion_update_history__summary),
        enabledIf = { emoji.suggestionEnabled isEqualTo true },
        visibleIf = { emoji.suggestionUpdateHistory isNotEqualTo emoji.suggestionUpdateHistory.default },
    )
    SwitchPreference(
        emoji.suggestionCandidateShowName,
        title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_show_name),
        enabledIf = { emoji.suggestionEnabled isEqualTo true },
        visibleIf = { emoji.suggestionCandidateShowName isNotEqualTo emoji.suggestionCandidateShowName.default },
    )
    DialogSliderPreference(
        emoji.suggestionQueryMinLength,
        title = stringRes(R.string.prefs__media__emoji_suggestion_query_min_length),
        valueLabel = { length -> pluralsRes(R.plurals.unit__characters__written, length, "v" to length) },
        min = 1,
        max = 5,
        stepIncrement = 1,
        enabledIf = { emoji.suggestionEnabled isEqualTo true },
        visibleIf = { emoji.suggestionQueryMinLength isNotEqualTo emoji.suggestionQueryMinLength.default },
    )
    DialogSliderPreference(
        emoji.suggestionCandidateMaxCount,
        title = stringRes(R.string.prefs__media__emoji_suggestion_candidate_max_count),
        valueLabel = { count -> pluralsRes(R.plurals.unit__candidates__written, count, "v" to count) },
        min = 1,
        max = 10,
        stepIncrement = 1,
        enabledIf = { emoji.suggestionEnabled isEqualTo true },
        visibleIf = { emoji.suggestionCandidateMaxCount isNotEqualTo emoji.suggestionCandidateMaxCount.default },
    )
}
