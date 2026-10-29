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

package dev.patrickgold.florisboard.app.settings.about

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.FlorisCanvasIcon
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen() = FlorisScreen {
    title = stringRes(R.string.about__title)

    val navController = LocalNavController.current
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val scope = rememberCoroutineScope()

    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    var unlock by remember { mutableStateOf(DevtoolsUnlock()) }

    content {
        val devtoolsPref = prefs.devtools.enabled
        val devtoolsEnabled by devtoolsPref.collectAsState()
        val copyLabel = stringRes(R.string.about__copy_version)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = OwnkeyTokens.GroupGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlorisCanvasIcon(
                modifier = Modifier.requiredSize(72.dp),
                iconId = R.mipmap.ownkey_app_icon,
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = stringRes(R.string.about__app_name),
                style = MaterialTheme.typography.titleMedium,
                color = OwnkeyTokens.Label,
            )
            Box(
                modifier = Modifier
                    .heightIn(min = OwnkeyTokens.MinTouchTarget)
                    .clip(OwnkeyTokens.PillShape)
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = copyLabel,
                        onLongClick = {
                            scope.launch {
                                try {
                                    clipboardManager.addNewPlaintext(appVersion)
                                    context.showShortToast(R.string.about__version_copied__title)
                                } catch (e: Throwable) {
                                    context.showShortToast(
                                        R.string.about__version_copied__error,
                                        "error_message" to e.message,
                                    )
                                }
                            }
                        },
                        onClick = {
                            unlock = unlock.tap(System.currentTimeMillis())
                            if (unlock.unlocksNow) {
                                val wasEnabled = devtoolsEnabled
                                scope.launch {
                                    if (wasEnabled) {
                                        context.showShortToast(R.string.about__devtools_already_on)
                                    } else {
                                        devtoolsPref.set(true)
                                        context.showShortToast(R.string.about__devtools_on)
                                    }
                                }
                            }
                        },
                    )
                    .padding(horizontal = 16.dp)
                    .testTag("about-version"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringRes(R.string.about__version_value, "version" to appVersion),
                    // No tabular figures: they put wide gaps around the dots, hyphen and plus of the version.
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnkeyTokens.Secondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        OwnkeyGroup {
            AboutLinkRow(
                tag = "about-whats-new",
                title = stringRes(R.string.about__changelog__title),
                onClick = { context.launchUrl(R.string.florisboard__changelog_url, "version" to BuildConfig.VERSION_NAME) },
            )
            AboutLinkRow(
                tag = "about-source-code",
                title = stringRes(R.string.about__repository__title),
                onClick = { context.launchUrl(R.string.florisboard__repo_url) },
            )
            AboutLinkRow(
                tag = "about-privacy-policy",
                title = stringRes(R.string.about__privacy_policy__title),
                onClick = { context.launchUrl(R.string.florisboard__privacy_policy_url) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("about-license"),
                title = stringRes(R.string.about__project_license__title),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.ProjectLicense) },
            )
            OwnkeyRow(
                modifier = Modifier.testTag("about-third-party"),
                title = stringRes(R.string.about__third_party_licenses__title),
                chevron = true,
                onClick = { navController.navigate(Routes.Settings.ThirdPartyLicenses) },
            )
        }

        if (devtoolsEnabled || isDebuggable) {
            OwnkeyGroup {
                OwnkeyRow(
                    modifier = Modifier.testTag("about-devtools"),
                    title = stringRes(R.string.devtools__title),
                    chevron = true,
                    onClick = { navController.navigate(Routes.Devtools.Home) },
                )
            }
        }
    }
}

/** A row that opens a web page; the trailing icon says it leaves the app. */
@Composable
private fun AboutLinkRow(tag: String, title: String, onClick: () -> Unit) {
    OwnkeyRow(
        modifier = Modifier.testTag(tag),
        title = title,
        onClick = onClick,
        trailing = {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = OwnkeyTokens.Secondary,
            )
        },
    )
}
