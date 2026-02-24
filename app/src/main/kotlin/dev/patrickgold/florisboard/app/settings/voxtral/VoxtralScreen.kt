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

package dev.patrickgold.florisboard.app.settings.voxtral

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun VoxtralScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voxtral__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val voxtralSecretsStore = remember { VoxtralSecretsStore(context) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasStoredApiKey by remember {
        mutableStateOf(voxtralSecretsStore.hasApiKey())
    }
    var apiKeyInput by remember {
        mutableStateOf("")
    }

    val requestRecordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    content {
        val prefsRef = prefs
        val endpointUrl by prefsRef.voxtral.endpointUrl.collectAsState()
        val model by prefsRef.voxtral.model.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val legacyApiKey = prefsRef.voxtral.apiKey.get().trim()
            if (legacyApiKey.isNotBlank()) {
                if (!voxtralSecretsStore.hasApiKey()) {
                    voxtralSecretsStore.setApiKey(legacyApiKey)
                }
                prefsRef.voxtral.apiKey.set("")
                hasStoredApiKey = voxtralSecretsStore.hasApiKey()
            }
        }

        PreferenceGroup(title = stringRes(R.string.pref__voxtral__group_auth__label)) {
            Text(
                text = stringRes(R.string.pref__voxtral__api_key__summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = if (hasStoredApiKey) {
                    stringRes(R.string.pref__voxtral__api_key__status_set)
                } else {
                    stringRes(R.string.pref__voxtral__api_key__status_missing)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Button(
                onClick = { context.launchUrl(R.string.voxtral__mistral_signup_url) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringRes(R.string.pref__voxtral__create_account_action))
            }
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { value ->
                    apiKeyInput = value
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringRes(R.string.pref__voxtral__api_key__label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(
                    onClick = {
                        val normalizedApiKey = apiKeyInput.trim()
                        voxtralSecretsStore.setApiKey(normalizedApiKey)
                        apiKeyInput = ""
                        hasStoredApiKey = voxtralSecretsStore.hasApiKey()
                        coroutineScope.launch {
                            prefsRef.voxtral.apiKey.set("")
                        }
                    },
                    enabled = apiKeyInput.trim().isNotEmpty(),
                ) {
                    Text(stringRes(R.string.pref__voxtral__api_key__save_action))
                }
                if (hasStoredApiKey) {
                    Button(
                        onClick = {
                            voxtralSecretsStore.clearApiKey()
                            hasStoredApiKey = false
                            coroutineScope.launch {
                                prefsRef.voxtral.apiKey.set("")
                            }
                        },
                    ) {
                        Text(stringRes(R.string.pref__voxtral__api_key__clear_action))
                    }
                }
            }
        }

        PreferenceGroup(title = stringRes(R.string.pref__voxtral__group_connection__label)) {
            Text(
                text = stringRes(R.string.pref__voxtral__endpoint__summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = endpointUrl,
                onValueChange = { value ->
                    coroutineScope.launch {
                        prefsRef.voxtral.endpointUrl.set(value)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringRes(R.string.pref__voxtral__endpoint__label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = model,
                onValueChange = { value ->
                    coroutineScope.launch {
                        prefsRef.voxtral.model.set(value)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringRes(R.string.pref__voxtral__model__label)) },
                singleLine = true,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__voxtral__group_permissions__label)) {
            Text(
                text = stringRes(R.string.pref__voxtral__permission__summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = if (hasMicPermission) {
                    stringRes(R.string.pref__voxtral__permission__granted)
                } else {
                    stringRes(R.string.pref__voxtral__permission__missing)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!hasMicPermission) {
                Button(
                    onClick = { requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(stringRes(R.string.pref__voxtral__permission__grant_action))
                }
            }
        }
    }
}
