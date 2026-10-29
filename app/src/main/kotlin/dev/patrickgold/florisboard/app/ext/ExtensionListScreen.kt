/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.ext

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.LazyCardRow
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalOwnkeyContentPadding
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

enum class ExtensionListScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val getExtensionIndex: (ExtensionManager) -> ExtensionManager.ExtensionIndex<*>,
    val launchExtensionCreate: ((NavController) -> Unit)?,
) {
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__list__ext_theme,
        getExtensionIndex = { it.themes },
        launchExtensionCreate = { it.navigate(Routes.Ext.Edit("null", ThemeExtension.SERIAL_TYPE)) },
    ),
    EXT_KEYBOARD(
        id = "ext-keyboard",
        titleResId = R.string.ext__list__ext_keyboard,
        getExtensionIndex = { it.keyboardExtensions },
        launchExtensionCreate = null,//{ it.navigate(Routes.Ext.Edit("null", KeyboardExtension.SERIAL_TYPE)) },
    ),
    EXT_LANGUAGEPACK(
        id = "ext-languagepack",
        titleResId = R.string.ext__list__ext_languagepack,
        getExtensionIndex = { it.languagePacks },
        launchExtensionCreate = null,//{ it.navigate(Routes.Ext.Edit("null", LanguagePackExtension.SERIAL_TYPE)) },
    );
}

@Composable
fun ExtensionListScreen(type: ExtensionListScreenType, showUpdate: Boolean) = FlorisScreen {
    title = stringRes(type.titleResId)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val extensionIndex by type.getExtensionIndex(extensionManager).collectAsState()

    var fabHeight by remember {
        mutableStateOf(0)
    }
    val fabHeightDp = with(LocalDensity.current) { fabHeight.toDp() + 16.dp }
    val listState = rememberLazyListState()

    content {
        val contentPadding = LocalOwnkeyContentPadding.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(state = listState, isVertical = true),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = contentPadding.calculateBottomPadding() + fabHeightDp,
            ),
        ) {
            if (showUpdate) {
                item {
                    ImportExtensionBox(navController)
                }
                item {
                    UpdateBox(extensionIndex = extensionIndex)
                }
            }
            if (extensionIndex.isEmpty()) {
                item {
                    OwnkeyNotice(text = stringRes(R.string.about__ext__list_empty))
                }
            }
            itemsIndexed(extensionIndex) { index, ext ->
                LazyCardRow(index = index, count = extensionIndex.size) {
                    OwnkeyRow(
                        modifier = Modifier.testTag("ext-list-item-$index"),
                        title = ext.meta.title,
                        summary = ext.meta.description?.takeIf { it.isNotBlank() } ?: ext.meta.id,
                        onClick = { navController.navigate(Routes.Ext.View(ext.meta.id)) },
                        trailing = if (extensionManager.canDelete(ext)) {
                            {
                                IconButton(
                                    modifier = Modifier.testTag("ext-list-edit-$index"),
                                    onClick = { navController.navigate(Routes.Ext.Edit(ext.meta.id)) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringRes(R.string.action__edit),
                                        tint = OwnkeyTokens.Label,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        chevron = !extensionManager.canDelete(ext),
                    )
                }
            }
        }
    }

    if (type.launchExtensionCreate != null) {
        floatingActionButton {
            OwnkeyButton(
                modifier = Modifier
                    .testTag("ext-list-create")
                    .onGloballyPositioned {
                        fabHeight = it.size.height
                    },
                label = stringRes(
                    if (type == ExtensionListScreenType.EXT_THEME) {
                        R.string.about__ext__new_theme
                    } else {
                        R.string.ext__editor__title_create_any
                    }
                ),
                icon = Icons.Default.Add,
                onClick = { type.launchExtensionCreate.invoke(navController) },
            )
        }
    }
}
