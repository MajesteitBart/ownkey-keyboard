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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.settings.theme.AppearanceSummary
import dev.patrickgold.florisboard.app.settings.voxtral.AiStatusLine
import dev.patrickgold.florisboard.app.settings.voxtral.rememberAiStatus
import dev.patrickgold.florisboard.app.settings.voxtral.toLine
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyStatusDot
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.stringRes
import androidx.compose.runtime.collectAsState as collectFlowAsState

private const val StackedLineFontScale = 1.5f

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val isOwnkeyEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isOwnkeySelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isOwnkeyEnabled) {
            HomeSetupNotice(
                title = stringRes(R.string.settings__home__off_title),
                body = stringRes(R.string.settings__home__off_body),
                actionLabel = stringRes(R.string.settings__home__off_action),
                onAction = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isOwnkeySelected) {
            HomeSetupNotice(
                title = stringRes(R.string.settings__home__not_selected_title),
                body = null,
                actionLabel = stringRes(R.string.settings__home__not_selected_action),
                onAction = { InputMethodUtils.showImePicker(context) },
            )
        }

        HomeAiCard(onClick = { navController.navigate(Routes.Settings.Voxtral) })

        val subtypeManager by context.subtypeManager()
        val subtypes by subtypeManager.subtypesFlow.collectFlowAsState()
        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
        val languages = subtypes
            .map { subtype ->
                when (displayLanguageNamesIn) {
                    DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayLanguage()
                    DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayLanguage(subtype.primaryLocale)
                }
            }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

        val themeMode by prefs.theme.mode.collectAsState()
        val dayThemeId by prefs.theme.dayThemeId.collectAsState()
        val nightThemeId by prefs.theme.nightThemeId.collectAsState()
        val appearance = stringRes(
            when (AppearanceSummary.of(themeMode, dayThemeId, nightThemeId)) {
                AppearanceSummary.GRAPHITE -> R.string.settings__appearance__graphite
                AppearanceSummary.BLACK -> R.string.settings__appearance__black
                AppearanceSummary.LIGHT -> R.string.settings__appearance__light
                AppearanceSummary.AUTO_GRAPHITE -> R.string.settings__appearance__auto_graphite
                AppearanceSummary.AUTO_BLACK -> R.string.settings__appearance__auto_black
                AppearanceSummary.CUSTOM -> R.string.settings__appearance__custom
            }
        )

        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier.testTag("home-languages"),
                title = stringRes(R.string.settings__home__languages),
                value = languages,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-appearance"),
                title = stringRes(R.string.settings__home__appearance),
                value = appearance,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Theme) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-keyboard"),
                title = stringRes(R.string.settings__home__keyboard),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Keyboard) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-typing"),
                title = stringRes(R.string.settings__home__typing),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Typing) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-clipboard"),
                title = stringRes(R.string.settings__home__clipboard),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Clipboard) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("home-privacy"),
                title = stringRes(R.string.settings__home__privacy),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.Privacy) },
            )
        }

        OwnkeyGroup {
            OwnkeyRow(
                modifier = Modifier.testTag("home-about"),
                title = stringRes(R.string.settings__home__about),
                // The version without its build metadata ("+<commit>"); About shows the full string.
                value = BuildConfig.VERSION_NAME.substringBefore('+'),
                // Inter's tabular forms put wide gaps around the dots and the hyphen of "0.10.0-debug".
                tabularValue = false,
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.About) },
            )
        }
    }
}

/** Shown while Ownkey can't type yet: what's wrong in a few words and one button that fixes it. */
@Composable
private fun HomeSetupNotice(
    title: String,
    body: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(bottom = OwnkeyTokens.GroupGap)
            .fillMaxWidth()
            .clip(OwnkeyTokens.CardShape)
            .background(OwnkeyTokens.Card)
            .testTag("home-setup")
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OwnkeyTokens.Label,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = OwnkeyTokens.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        OwnkeyButton(
            modifier = Modifier.testTag("home-setup-action"),
            label = actionLabel,
            onClick = onAction,
        )
    }
}

/** What dictation and rewrite use right now. The whole card opens AI. */
@Composable
private fun HomeAiCard(onClick: () -> Unit) {
    val status = rememberAiStatus()
    val dictation = status.dictation.toLine()
    val rewrite = status.rewrite.toLine()
    Column(
        modifier = Modifier
            .padding(bottom = OwnkeyTokens.GroupGap)
            .fillMaxWidth()
            .clip(OwnkeyTokens.CardShape)
            .background(OwnkeyTokens.Card)
            .ownkeyFocusRing(OwnkeyTokens.CardShape, inset = true)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .testTag("home-ai")
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.settings__home__ai),
                style = MaterialTheme.typography.titleMedium,
                color = OwnkeyTokens.Label,
            )
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OwnkeyTokens.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AiStatusLineRow(label = stringRes(R.string.settings__home__ai_dictation), line = dictation)
        Spacer(modifier = Modifier.height(6.dp))
        AiStatusLineRow(label = stringRes(R.string.settings__home__ai_rewrite), line = rewrite)
    }
}

/** "Dictation    Cloud · Mistral": the label in Secondary, the value in Label, or Warning with a dot. */
@Composable
private fun AiStatusLineRow(label: String, line: AiStatusLine) {
    val typography = MaterialTheme.typography
    val labelText: @Composable () -> Unit = {
        Text(
            text = label,
            style = typography.bodyMedium,
            color = OwnkeyTokens.Secondary,
        )
    }
    // Stacked under its label at large font scales, the value starts at the label's edge and may take two lines,
    // like a stacked row value.
    val stacked = LocalDensity.current.fontScale >= StackedLineFontScale
    val valueText: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier,
            horizontalArrangement = if (stacked) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line.needsSetup) {
                OwnkeyStatusDot(tone = OwnkeyTone.Warning)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = line.text,
                style = typography.bodyMedium,
                color = if (line.needsSetup) OwnkeyTokens.Warning else OwnkeyTokens.Label,
                textAlign = if (stacked) TextAlign.Start else TextAlign.End,
                maxLines = if (stacked) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (stacked) {
        Column {
            labelText()
            valueText(Modifier)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelText()
            Spacer(modifier = Modifier.width(16.dp))
            valueText(Modifier.weight(1f))
        }
    }
}
