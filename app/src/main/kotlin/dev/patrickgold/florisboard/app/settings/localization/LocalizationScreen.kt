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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
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
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.app.ui.Preference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.serialization.json.Json
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

/** A language's name in the form the user picked under "Display language names in". */
internal fun FlorisLocale.nameFor(displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    return when (displayLanguageNamesIn) {
        DisplayLanguageNamesIn.SYSTEM_LOCALE -> displayName()
        DisplayLanguageNamesIn.NATIVE_LOCALE -> displayName(this)
    }
}

@Composable
fun LocalizationScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__title)
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val subtypeManager by context.subtypeManager()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    content {
        val subtypes by subtypeManager.subtypesFlow.collectAsState()
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
        if (subtypes.isEmpty()) {
            OwnkeyNotice(
                text = stringRes(R.string.settings__languages__empty),
                tone = OwnkeyTone.Warning,
            )
        } else {
            val layouts by keyboardManager.resources.layouts.collectAsState()
            OwnkeyGroup(
                footer = stringRes(R.string.settings__languages__list_footer),
            ) {
                subtypes.forEachIndexed { index, subtype ->
                    val layoutName = layouts[LayoutType.CHARACTERS]?.get(subtype.layoutMap.characters)?.label
                    OwnkeyRow(
                        modifier = Modifier.testTag("language-$index"),
                        title = subtype.primaryLocale.nameFor(displayLanguageNamesIn),
                        summary = layoutName ?: subtype.layoutMap.characters.componentId,
                        onClick = { navController.navigate(Routes.Settings.SubtypeEdit(subtype.id)) },
                        onLongClick = { chosenSubtypeToDelete = subtype },
                        chevron = true,
                    )
                }
            }
        }
        OwnkeyButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = OwnkeyTokens.GroupGap)
                .testTag("languages-add"),
            label = stringRes(R.string.settings__languages__add),
            icon = Icons.Default.Add,
            style = OwnkeyButtonStyle.Secondary,
            onClick = { navController.navigate(Routes.Settings.SubtypeAdd) },
        )

        PreferenceGroup(title = stringRes(R.string.settings__languages__group_advanced)) {
            Preference(
                modifier = Modifier.testTag("languages-packs"),
                title = stringRes(R.string.settings__languages__language_packs),
                summary = stringRes(R.string.settings__languages__language_packs_summary),
                onClick = {
                    navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
                },
                chevron = true,
            )
            // Hidden; each shows up only while it holds a non-default value, so it can be reset.
            ListPreference(
                prefs.localization.displayLanguageNamesIn,
                title = stringRes(R.string.settings__localization__display_language_names_in__label),
                entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class),
                visibleIf = {
                    prefs.localization.displayLanguageNamesIn isNotEqualTo
                        prefs.localization.displayLanguageNamesIn.default
                },
            )
            SwitchPreference(
                prefs.localization.displayKeyboardLabelsInSubtypeLanguage,
                title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
                visibleIf = {
                    prefs.localization.displayKeyboardLabelsInSubtypeLanguage isNotEqualTo
                        prefs.localization.displayKeyboardLabelsInSubtypeLanguage.default
                },
            )
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        onDismiss = {
            chosenSubtypeToDelete = null
        },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        },
    )
}

@Composable
fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    subtypeToDelete?.let { subtype ->
        OwnkeyDialog(
            title = stringRes(
                R.string.settings__languages__remove_title,
                "language" to subtype.primaryLocale.displayName(),
            ),
            text = stringRes(R.string.settings__languages__remove_message),
            confirmLabel = stringRes(R.string.settings__languages__remove),
            destructive = true,
            onConfirm = onConfirm,
            dismissLabel = stringRes(R.string.action__cancel),
            onDismiss = onDismiss,
        )
    }
}
