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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceRow
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

enum class ThemeManagerScreenAction(val id: String) {
    SELECT_DAY("select-day"),
    SELECT_NIGHT("select-night");
}

@Composable
fun ThemeManagerScreen(action: ThemeManagerScreenAction?) = FlorisScreen {
    title = stringRes(when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> R.string.settings__theme_manager__title_day
        ThemeManagerScreenAction.SELECT_NIGHT -> R.string.settings__theme_manager__title_night
        else -> error("Theme manager screen action must not be null")
    })
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val themeManager by context.themeManager()
    val scope = rememberCoroutineScope()

    val indexedThemeExtensions by extensionManager.themes.collectAsState()
    // Ownkey's own themes first, then the rest by title.
    val sortedThemeExtensions = remember(indexedThemeExtensions) {
        indexedThemeExtensions.sortedWith(
            compareBy<ThemeExtension> { it.meta.id != SignalThemes.Bone.extensionId }.thenBy { it.meta.title }
        )
    }

    fun getThemeIdPref() = when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> prefs.theme.dayThemeId
        ThemeManagerScreenAction.SELECT_NIGHT -> prefs.theme.nightThemeId
    }

    fun setTheme(extId: String, componentId: String) {
        val extComponentName = ExtensionComponentName(extId, componentId)
        scope.launch {
            getThemeIdPref().set(extComponentName)
        }
    }

    val activeThemeId by getThemeIdPref().collectAsState()

    content {
        DisposableEffect(activeThemeId) {
            themeManager.previewThemeId.value = activeThemeId
            onDispose {
                themeManager.previewThemeId.value = null
            }
        }
        val dark = stringRes(R.string.settings__appearance__theme_dark)
        val light = stringRes(R.string.settings__appearance__theme_light)
        for (ext in sortedThemeExtensions) key(ext.meta.id) {
            OwnkeyGroup(title = ext.meta.title, selectableGroup = true) {
                for (config in ext.themes.sortedBy { it.label }) key(ext.meta.id, config.id) {
                    OwnkeyChoiceRow(
                        modifier = Modifier.testTag("theme-${config.id}"),
                        label = config.label,
                        summary = if (config.isNightTheme) dark else light,
                        selected = activeThemeId.extensionId == ext.meta.id && activeThemeId.componentId == config.id,
                        onClick = { setTheme(ext.meta.id, config.id) },
                    )
                }
            }
        }
    }
}
