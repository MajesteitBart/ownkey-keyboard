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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.ui.LazyCardRow
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalOwnkeyContentPadding
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

const val SelectLocaleScreenResultLanguageTag = "SelectLocaleScreen.languageTag"

@Composable
fun SelectLocaleScreen() = FlorisScreen {
    title = stringRes(R.string.settings__localization__subtype_select_locale)
    previewFieldVisible = false
    scrollable = false

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current

    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    var searchTermValue by remember { mutableStateOf("") }
    val context = LocalContext.current
    val systemLocales =
        FlorisLocale.extendedAvailableLocales(context).sortedBy { locale ->
            when (displayLanguageNamesIn) {
                DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
            }.lowercase()
        }

    val filteredSystemLocales = remember(searchTermValue) {
        if (searchTermValue.isBlank()) {
            systemLocales
        } else {
            val term = searchTermValue.trim().lowercase()
            systemLocales.filter { locale ->
                locale.displayName().lowercase().contains(term) ||
                    locale.displayName(locale).lowercase().contains(term) ||
                    locale.displayName(FlorisLocale.ENGLISH).lowercase().contains(term) ||
                    locale.languageTag().lowercase().startsWith(term) ||
                    locale.localeTag().lowercase().startsWith(term)
            }
        }
    }

    content {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalOwnkeyContentPadding.current,
        ) {
            item {
                OwnkeyTextField(
                    modifier = Modifier
                        .padding(bottom = OwnkeyTokens.GroupGap)
                        .testTag("select-locale-search"),
                    value = searchTermValue,
                    onValueChange = { searchTermValue = it },
                    placeholder = stringRes(R.string.settings__localization__subtype_search_locale_placeholder),
                    containerColor = OwnkeyTokens.Card,
                    trailing = {
                        Icon(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(24.dp),
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OwnkeyTokens.Secondary,
                        )
                    },
                )
            }
            if (filteredSystemLocales.isEmpty()) {
                item {
                    OwnkeyNotice(
                        text = stringRes(
                            R.string.settings__localization__subtype_search_locale_not_found,
                            "search_term" to searchTermValue,
                        ),
                    )
                }
            }
            itemsIndexed(filteredSystemLocales) { index, systemLocale ->
                LazyCardRow(index = index, count = filteredSystemLocales.size) {
                    OwnkeyRow(
                        title = systemLocale.nameFor(displayLanguageNamesIn),
                        onClick = {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(SelectLocaleScreenResultLanguageTag, systemLocale.languageTag())
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
