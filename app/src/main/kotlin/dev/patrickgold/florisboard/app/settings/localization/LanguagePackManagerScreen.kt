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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ext.ExtensionImportScreenType
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.lib.compose.FlorisConfirmDeleteDialog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.Extension
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

enum class LanguagePackManagerScreenAction(val id: String) {
    MANAGE("manage-installed-language-packs");
}

// TODO: this file is based on ThemeManagerScreen.kt and can arguably be merged.
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun LanguagePackManagerScreen(action: LanguagePackManagerScreenAction?) = FlorisScreen {
    title = stringRes(when (action) {
        LanguagePackManagerScreenAction.MANAGE -> R.string.settings__languages__language_packs
        else -> error("LanguagePack manager screen action must not be null")
    })

    val prefs by FlorisPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val extensionManager by context.extensionManager()

    val indexedLanguagePackExtensions by extensionManager.languagePacks.collectAsState()
    val selectedManagerLanguagePackId = remember { mutableStateOf<ExtensionComponentName?>(null) }
    val extGroupedLanguagePacks = remember(indexedLanguagePackExtensions) {
        buildMap {
            for (ext in indexedLanguagePackExtensions) {
                put(ext.meta.id, ext.items)
            }
        }.mapValues { (_, configs) -> configs.sortedBy { it.label } }
    }

    fun getLanguagePackIdPref(): Nothing = TODO("Not implemented yet")

    fun setLanguagePack(extId: String, componentId: String) {
        val extComponentName = ExtensionComponentName(extId, componentId)
        when (action) {
            LanguagePackManagerScreenAction.MANAGE -> {
                selectedManagerLanguagePackId.value = extComponentName
            }
        }
    }

    val activeLanguagePackId by when (action) {
        LanguagePackManagerScreenAction.MANAGE -> selectedManagerLanguagePackId
    }
    var languagePackExtToDelete by remember { mutableStateOf<Extension?>(null) }

    content {
        if (action == LanguagePackManagerScreenAction.MANAGE) {
            OwnkeyButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = OwnkeyTokens.GroupGap)
                    .testTag("language-packs-import"),
                label = stringRes(R.string.action__import),
                icon = Icons.Default.Add,
                style = OwnkeyButtonStyle.Secondary,
                onClick = {
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_LANGUAGEPACK, null))
                },
            )
        }
        if (extGroupedLanguagePacks.isEmpty()) {
            OwnkeyNotice(text = stringRes(R.string.settings__languages__no_language_packs))
        }
        for ((extensionId, configs) in extGroupedLanguagePacks) key(extensionId) {
            val ext = extensionManager.getExtensionById(extensionId)!!
            OwnkeyGroup {
                OwnkeyRow(
                    title = ext.meta.title,
                    summary = extensionId,
                    chevron = true,
                    onClick = { navController.navigate(Routes.Ext.View(extensionId)) },
                )
                for (config in configs) key(extensionId, config.id) {
                    OwnkeyRow(
                        title = config.label,
                        onClick = { setLanguagePack(extensionId, config.id) },
                    )
                }
                if (action == LanguagePackManagerScreenAction.MANAGE && extensionManager.canDelete(ext)) {
                    OwnkeyRow(
                        title = stringRes(R.string.action__delete),
                        titleColor = OwnkeyTokens.Danger,
                        onClick = { languagePackExtToDelete = ext },
                    )
                }
            }
        }

        if (languagePackExtToDelete != null) {
            FlorisConfirmDeleteDialog(
                onConfirm = {
                    runCatching {
                        extensionManager.delete(languagePackExtToDelete!!)
                    }.onFailure { error ->
                        context.showLongToastSync(
                            R.string.error__snackbar_message,
                            "error_message" to error.localizedMessage,
                        )
                    }
                    languagePackExtToDelete = null
                },
                onDismiss = { languagePackExtToDelete = null },
                what = languagePackExtToDelete!!.meta.title,
            )
        }
    }
}
