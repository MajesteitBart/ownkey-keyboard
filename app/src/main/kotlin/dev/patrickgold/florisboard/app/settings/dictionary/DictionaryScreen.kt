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

package dev.patrickgold.florisboard.app.settings.dictionary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.Preference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

@Composable
fun DictionaryScreen() = FlorisScreen {
    title = stringRes(R.string.settings__dictionary__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        val dictionary = prefs.dictionary
        PreferenceGroup(
            title = "",
            footer = stringRes(R.string.settings__typing__saved_words_footer),
        ) {
            // The enable switches left the UI; each shows up only while it is off, so it can be turned back on.
            SwitchPreference(
                dictionary.enableFlorisUserDictionary,
                title = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__enable_internal_user_dictionary__summary),
                visibleIf = {
                    dictionary.enableFlorisUserDictionary isNotEqualTo dictionary.enableFlorisUserDictionary.default
                },
            )
            Preference(
                modifier = Modifier.testTag("saved-words-yours"),
                title = stringRes(R.string.settings__udm__title_floris),
                summary = stringRes(R.string.settings__typing__your_words_summary),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
                enabledIf = { dictionary.enableFlorisUserDictionary isEqualTo true },
                chevron = true,
            )
            SwitchPreference(
                dictionary.enableSystemUserDictionary,
                title = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__label),
                summary = stringRes(R.string.pref__dictionary__enable_system_user_dictionary__summary),
                visibleIf = {
                    dictionary.enableSystemUserDictionary isNotEqualTo dictionary.enableSystemUserDictionary.default
                },
            )
            Preference(
                modifier = Modifier.testTag("saved-words-system"),
                title = stringRes(R.string.settings__udm__title_system),
                summary = stringRes(R.string.settings__typing__system_dictionary_summary),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
                enabledIf = { dictionary.enableSystemUserDictionary isEqualTo true },
                chevron = true,
            )
            // Speech entries live apart from the typing dictionaries: they bias recognition and rewrite
            // transcripts, and they can travel to a cloud endpoint. Typing entries never do.
            Preference(
                modifier = Modifier.testTag("saved-words-personal"),
                title = stringRes(R.string.settings__typing__personal_dictionary),
                summary = stringRes(R.string.settings__typing__personal_dictionary_summary),
                onClick = { navController.navigate(Routes.Settings.SpeechDictionary()) },
                chevron = true,
            )
        }
    }
}
