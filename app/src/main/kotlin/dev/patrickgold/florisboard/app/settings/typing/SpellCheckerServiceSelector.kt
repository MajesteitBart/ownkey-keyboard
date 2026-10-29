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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.lib.util.launchActivity
import org.florisboard.lib.android.AndroidSettings
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.observeAsState
import org.florisboard.lib.compose.stringRes

/** What Android reports about the system spell checker. */
class SpellCheckerState(
    val isEnabled: Boolean,
    /** Label of the selected spell checker app, or null when none is selected. */
    val label: String?,
    /** True when Ownkey itself is the selected spell checker. */
    val isOwnkey: Boolean,
)

@Composable
fun rememberSpellCheckerState(): SpellCheckerState {
    val context = LocalContext.current
    val systemSpellCheckerId by AndroidSettings.Secure.observeAsState(
        key = "selected_spell_checker",
        foregroundOnly = true,
    )
    val systemSpellCheckerEnabled by AndroidSettings.Secure.observeAsState(
        key = "spell_checker_enabled",
        foregroundOnly = true,
    )
    val packageName = remember(systemSpellCheckerId) {
        systemSpellCheckerId?.let { ComponentName.unflattenFromString(it)?.packageName }
    }
    val label = remember(packageName) {
        packageName?.let { name ->
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString()
            }.getOrDefault(name)
        }
    }
    val isEnabled = isSystemSpellCheckerEnabled(systemSpellCheckerEnabled)
    return SpellCheckerState(
        isEnabled = isEnabled,
        label = label,
        isOwnkey = isEnabled && packageName == context.packageName,
    )
}

/** Android treats a missing `spell_checker_enabled` as on; only an explicit "0" turns the spell checker off. */
internal fun isSystemSpellCheckerEnabled(rawValue: String?): Boolean = rawValue?.trim() != "0"

/**
 * The "Spell checker" row: shows the active spell checker or "Off" and opens Android's spell checker
 * settings. A missing selection shows as a notice under the row.
 */
@Composable
fun SpellCheckerRow(state: SpellCheckerState) {
    val context = LocalContext.current
    val needsChoice = state.isEnabled && state.label == null
    OwnkeyRow(
        modifier = Modifier.testTag("typing-spell-checker"),
        title = stringRes(R.string.settings__typing__spell_checker),
        value = when {
            !state.isEnabled -> stringRes(R.string.settings__typing__spell_checker_off)
            state.label == null -> stringRes(R.string.settings__typing__spell_checker_not_set)
            else -> state.label
        },
        valueColor = if (needsChoice) OwnkeyTokens.Warning else Color.Unspecified,
        onClick = { openSystemSpellCheckerSettings(context) },
    )
    if (needsChoice) {
        OwnkeyNotice(
            text = stringRes(R.string.settings__typing__spell_checker_none_notice),
            tone = OwnkeyTone.Warning,
            actionLabel = stringRes(R.string.settings__typing__spell_checker_choose),
            onAction = { openSystemSpellCheckerSettings(context) },
        )
    }
}

private fun openSystemSpellCheckerSettings(context: Context) {
    val componentToLaunch = ComponentName(
        "com.android.settings",
        "com.android.settings.Settings\$SpellCheckersSettingsActivity",
    )
    context.launchActivity {
        it.addCategory(Intent.CATEGORY_DEFAULT)
        it.component = componentToLaunch
    }
}
