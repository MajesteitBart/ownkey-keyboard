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

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.ui.LazyCardHeader
import dev.patrickgold.florisboard.app.ui.LazyCardRow
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MAX
import dev.patrickgold.florisboard.ime.dictionary.FREQUENCY_MIN
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryDao
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryValidation
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.ValidationResult
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalOwnkeyContentPadding
import dev.patrickgold.florisboard.lib.rememberValidationResult
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

private val AllLanguagesLocale = FlorisLocale.from(language = "zz")
private val UserDictionaryEntryToAdd = UserDictionaryEntry(id = 0, "", 255, null, null)
private const val SystemUserDictionaryUiIntentAction = "android.settings.USER_DICTIONARY_SETTINGS"

enum class UserDictionaryType(val id: String) {
    FLORIS("floris"),
    SYSTEM("system");
}

@Composable
fun UserDictionaryScreen(type: UserDictionaryType) = FlorisScreen {
    title = stringRes(when (type) {
        UserDictionaryType.FLORIS -> R.string.settings__udm__title_floris
        UserDictionaryType.SYSTEM -> R.string.settings__udm__title_system
    })
    previewFieldVisible = false
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var currentLocale by remember { mutableStateOf<FlorisLocale?>(null) }
    var languageList by remember { mutableStateOf(emptyList<FlorisLocale>()) }
    var wordList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var userDictionaryEntryForDialog by remember { mutableStateOf<UserDictionaryEntry?>(null) }

    fun userDictionaryDao(): UserDictionaryDao? {
        return when (type) {
            UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDao()
            UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDao()
        }
    }

    fun getDisplayNameForLocale(locale: FlorisLocale): String {
        return if (locale == AllLanguagesLocale) {
            context.stringRes(R.string.settings__udm__all_languages)
        } else {
            locale.displayName()
        }
    }

    fun buildUi() {
        if (currentLocale != null) {
            //subtitle = getDisplayNameForLocale(currentLocale)
            val locale = if (currentLocale == AllLanguagesLocale) null else currentLocale
            wordList = userDictionaryDao()?.queryAll(locale) ?: emptyList()
            if (wordList.isEmpty()) {
                currentLocale = null
            }
        }
        if (currentLocale == null) {
            //subtitle = null
            languageList = userDictionaryDao()
                ?.queryLanguageList()
                ?.sortedBy { it?.displayLanguage() }
                ?.map { it ?: AllLanguagesLocale }
                ?: emptyList()
        }
    }

    val importDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                context.showLongToastSync("Database handle is null, failed to import")
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.importCombinedList(context, uri)
            }.onSuccess {
                buildUi()
                context.showLongToastSync(R.string.settings__udm__dictionary_import_success)
            }.onFailure { error ->
                context.showLongToastSync("Error: ${error.localizedMessage}")
            }
        },
    )

    val exportDictionary = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity was cancelled (mostly
            // by pressing the back button), so we don't display an error message here.
            if (uri == null) return@rememberLauncherForActivityResult
            val db = when (type) {
                UserDictionaryType.FLORIS -> dictionaryManager.florisUserDictionaryDatabase()
                UserDictionaryType.SYSTEM -> dictionaryManager.systemUserDictionaryDatabase()
            }
            if (db == null) {
                context.showLongToastSync("Database handle is null, failed to export")
                return@rememberLauncherForActivityResult
            }
            runCatching {
                db.exportCombinedList(context, uri)
            }.onSuccess {
                context.showLongToastSync(R.string.settings__udm__dictionary_export_success)
            }.onFailure { error ->
                context.showLongToastSync("Error: ${error.localizedMessage}")
            }
        },
    )

    navigationIcon {
        IconButton(
            onClick = {
                if (currentLocale != null) {
                    currentLocale = null
                    buildUi()
                } else {
                    navController.popBackStack()
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringRes(R.string.ownkey_ui__back),
                tint = OwnkeyTokens.Label,
            )
        }
    }

    actions {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringRes(R.string.settings__typing__more_options),
                tint = OwnkeyTokens.Label,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = OwnkeyTokens.FieldShape,
            containerColor = OwnkeyTokens.Card,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            DropdownMenuItem(
                onClick = {
                    importDictionary.launch("*/*")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__import)) },
            )
            DropdownMenuItem(
                onClick = {
                    exportDictionary.launch("my-personal-dictionary.clb")
                    expanded = false
                },
                text = { Text(text = stringRes(R.string.action__export)) },
            )
            if (type == UserDictionaryType.SYSTEM) {
                DropdownMenuItem(
                    onClick = {
                        context.launchActivity { it.action = SystemUserDictionaryUiIntentAction }
                        expanded = false
                    },
                    text = { Text(text = stringRes(R.string.settings__udm__open_system_manager_ui)) },
                )
            }
        }
    }

    floatingActionButton {
        OwnkeyButton(
            modifier = Modifier.testTag("udm-add-word"),
            label = stringRes(R.string.settings__udm__dialog__title_add),
            icon = Icons.Default.Add,
            onClick = { userDictionaryEntryForDialog = UserDictionaryEntryToAdd },
        )
    }

    content {
        BackHandler(currentLocale != null) {
            currentLocale = null
            buildUi()
        }

        LaunchedEffect(Unit) {
            dictionaryManager.loadUserDictionariesIfNecessary()
            buildUi()
        }

        val contentPadding = LocalOwnkeyContentPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Room below the last row for the floating Add pill.
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = contentPadding.calculateBottomPadding() + 72.dp,
            ),
        ) {
            if (languageList.isEmpty()) {
                item {
                    OwnkeyNotice(text = stringRes(R.string.settings__typing__no_saved_words))
                }
            }
            if (currentLocale == null) {
                itemsIndexed(languageList) { index, language ->
                    LazyCardRow(index = index, count = languageList.size) {
                        OwnkeyRow(
                            title = getDisplayNameForLocale(language),
                            chevron = true,
                            onClick = {
                                scope.launch {
                                    // Delay makes UI ripple visible and experience better
                                    delay(150)
                                    currentLocale = language
                                    buildUi()
                                }
                            },
                        )
                    }
                }
            } else {
                item {
                    LazyCardHeader(text = getDisplayNameForLocale(currentLocale!!), first = true)
                }
                itemsIndexed(wordList) { index, wordEntry ->
                    LazyCardRow(index = index, count = wordList.size) {
                        OwnkeyRow(
                            title = wordEntry.word,
                            summary = wordEntry.shortcut?.let {
                                stringRes(R.string.settings__typing__word_shortcut, "shortcut" to it)
                            },
                            onClick = { userDictionaryEntryForDialog = wordEntry },
                        )
                    }
                }
            }
        }

        val wordEntry = userDictionaryEntryForDialog
        if (wordEntry != null) {
            var showValidationErrors by rememberSaveable { mutableStateOf(false) }
            val isAddWord = wordEntry === UserDictionaryEntryToAdd
            var word by rememberSaveable { mutableStateOf(wordEntry.word) }
            val wordValidation = rememberValidationResult(UserDictionaryValidation.Word, word)
            var freq by rememberSaveable { mutableStateOf(wordEntry.freq.toString()) }
            val freqValidation = rememberValidationResult(UserDictionaryValidation.Freq, freq)
            var shortcut by rememberSaveable { mutableStateOf(wordEntry.shortcut ?: "") }
            val shortcutValidation = rememberValidationResult(UserDictionaryValidation.Shortcut, shortcut)
            var locale by rememberSaveable { mutableStateOf(wordEntry.locale ?: "") }
            val localeValidation = rememberValidationResult(UserDictionaryValidation.Locale, locale)

            OwnkeyDialog(
                title = stringRes(if (isAddWord) {
                    R.string.settings__udm__dialog__title_add
                } else {
                    R.string.settings__udm__dialog__title_edit
                }),
                confirmLabel = stringRes(if (isAddWord) {
                    R.string.action__add
                } else {
                    R.string.action__save
                }),
                onConfirm = {
                    val isInvalid = wordValidation.isInvalid() ||
                        freqValidation.isInvalid() ||
                        shortcutValidation.isInvalid() ||
                        localeValidation.isInvalid()
                    if (isInvalid) {
                        showValidationErrors = true
                    } else {
                        val entry = UserDictionaryEntry(
                            id = wordEntry.id,
                            word = word.trim(),
                            freq = freq.toInt(10),
                            shortcut = shortcut.trim().takeIf { it.isNotBlank() },
                            locale = locale.trim().takeIf { it.isNotBlank() }?.let {
                                // Normalize tag
                                FlorisLocale.fromTag(it).localeTag()
                            },
                        )
                        if (isAddWord) {
                            userDictionaryDao()?.insert(entry)
                        } else {
                            userDictionaryDao()?.update(entry)
                        }
                        userDictionaryEntryForDialog = null
                        buildUi()
                    }
                },
                dismissLabel = stringRes(R.string.action__cancel),
                onDismiss = {
                    userDictionaryEntryForDialog = null
                },
                neutralLabel = if (isAddWord) {
                    null
                } else {
                    stringRes(R.string.action__delete)
                },
                neutralStyle = OwnkeyButtonStyle.Destructive,
                onNeutral = {
                    userDictionaryDao()?.delete(wordEntry)
                    userDictionaryEntryForDialog = null
                    buildUi()
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnkeyTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = stringRes(R.string.settings__udm__dialog__word_label),
                        isError = showValidationErrors && wordValidation.isInvalid(),
                        errorText = wordValidation.errorText(),
                    )
                    OwnkeyTextField(
                        value = freq,
                        onValueChange = { freq = it },
                        label = stringRes(
                            R.string.settings__udm__dialog__freq_label,
                            "f_min" to FREQUENCY_MIN, "f_max" to FREQUENCY_MAX,
                        ),
                        isError = showValidationErrors && freqValidation.isInvalid(),
                        errorText = freqValidation.errorText(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OwnkeyTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = stringRes(R.string.settings__udm__dialog__shortcut_label),
                        isError = showValidationErrors && shortcutValidation.isInvalid(),
                        errorText = shortcutValidation.errorText(),
                    )
                    OwnkeyTextField(
                        value = locale,
                        onValueChange = { locale = it },
                        label = stringRes(R.string.settings__udm__dialog__locale_label),
                        isError = showValidationErrors && localeValidation.isInvalid(),
                        errorText = localeValidation.errorText(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationResult.errorText(): String? {
    return if (this is ValidationResult.Invalid && hasErrorMessage()) errorMessage() else null
}
