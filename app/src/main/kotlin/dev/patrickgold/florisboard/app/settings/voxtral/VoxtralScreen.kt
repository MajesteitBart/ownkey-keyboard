/*
 * Copyright (C) 2021-2026 The FlorisBoard Contributors
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviderPreset
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders.Custom
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@Composable
fun VoxtralScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voxtral__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val voxtralSecretsStore = remember { VoxtralSecretsStore(context) }
    val llmRewriteSecretsStore = remember { LlmRewriteSecretsStore(context) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasStoredApiKey by remember {
        mutableStateOf(voxtralSecretsStore.hasApiKey())
    }
    var apiKeyInput by remember { mutableStateOf("") }
    var hasStoredLlmApiKey by remember {
        mutableStateOf(llmRewriteSecretsStore.hasApiKey())
    }
    var llmApiKeyInput by remember { mutableStateOf("") }

    val requestRecordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    content {
        val prefsRef = prefs
        val endpointUrl by prefsRef.voxtral.endpointUrl.collectAsState()
        val model by prefsRef.voxtral.model.collectAsState()
        val languageHint by prefsRef.voxtral.languageHint.collectAsState()
        val rewriteEndpointUrl by prefsRef.voxtral.postProcessingEndpointUrl.collectAsState()
        val rewriteModel by prefsRef.voxtral.postProcessingModel.collectAsState()
        val rewriteProviderId by prefsRef.voxtral.postProcessingProvider.collectAsState()
        val rewritePromptsJson by prefsRef.voxtral.rewritePrompts.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        var wearSyncStatus by remember { mutableStateOf("") }
        var promptDrafts by remember(rewritePromptsJson) {
            mutableStateOf(RewritePromptPresets.decode(rewritePromptsJson))
        }

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

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = OwnkeyBrand.TrustBlue,
                onPrimary = OwnkeyBrand.Bone,
                background = OwnkeyBrand.Key,
                onBackground = OwnkeyBrand.Bone,
                surface = OwnkeyBrand.Panel,
                onSurface = OwnkeyBrand.Bone,
                surfaceVariant = OwnkeyBrand.Action,
                onSurfaceVariant = OwnkeyBrand.Ash,
                outline = OwnkeyBrand.Line,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OwnkeyBrand.Key)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiIntroCard()

                AiSectionCard(
                    title = stringRes(R.string.pref__voxtral__group_auth__label),
                    summary = stringRes(R.string.pref__voxtral__api_key__summary),
                ) {
                    StatusText(
                        text = if (hasStoredApiKey) {
                            stringRes(R.string.pref__voxtral__api_key__status_set)
                        } else {
                            stringRes(R.string.pref__voxtral__api_key__status_missing)
                        },
                    )
                    OwnkeyButton(
                        label = stringRes(R.string.pref__voxtral__create_account_action),
                        onClick = { context.launchUrl(R.string.voxtral__mistral_signup_url) },
                    )
                    OwnkeyOutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = stringRes(R.string.pref__voxtral__api_key__label),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OwnkeyButton(
                            label = stringRes(R.string.pref__voxtral__api_key__save_action),
                            onClick = {
                                val normalizedApiKey = apiKeyInput.trim()
                                voxtralSecretsStore.setApiKey(normalizedApiKey)
                                apiKeyInput = ""
                                hasStoredApiKey = voxtralSecretsStore.hasApiKey()
                                coroutineScope.launch {
                                    prefsRef.voxtral.apiKey.set("")
                                }

                                WearVoxtralSync.pushConfig(
                                    context = context,
                                    config = WearVoxtralConfig(
                                        apiKey = normalizedApiKey,
                                        endpointUrl = endpointUrl,
                                        model = model,
                                        languageHint = languageHint,
                                    ),
                                ) { result ->
                                    wearSyncStatus = result.fold(
                                        onSuccess = { count -> "Wear sync gelukt ($count device${if (count == 1) "" else "s"})" },
                                        onFailure = { error -> "Wear sync mislukt: ${error.message}" },
                                    )
                                }
                            },
                            enabled = apiKeyInput.trim().isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        if (hasStoredApiKey) {
                            OwnkeyButton(
                                label = stringRes(R.string.pref__voxtral__api_key__clear_action),
                                onClick = {
                                    voxtralSecretsStore.clearApiKey()
                                    hasStoredApiKey = false
                                    coroutineScope.launch {
                                        prefsRef.voxtral.apiKey.set("")
                                    }

                                    WearVoxtralSync.pushConfig(
                                        context = context,
                                        config = WearVoxtralConfig(
                                            apiKey = "",
                                            endpointUrl = endpointUrl,
                                            model = model,
                                            languageHint = languageHint,
                                        ),
                                    ) { result ->
                                        wearSyncStatus = result.fold(
                                            onSuccess = { count -> "Wear sync gelukt ($count device${if (count == 1) "" else "s"})" },
                                            onFailure = { error -> "Wear sync mislukt: ${error.message}" },
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                secondary = true,
                            )
                        }
                    }
                }

                AiSectionCard(
                    title = stringRes(R.string.pref__voxtral__group_connection__label),
                    summary = stringRes(R.string.pref__voxtral__endpoint__summary),
                ) {
                    OwnkeyOutlinedTextField(
                        value = endpointUrl,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.endpointUrl.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__voxtral__endpoint__label),
                    )
                    OwnkeyOutlinedTextField(
                        value = model,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.model.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__voxtral__model__label),
                    )
                    StatusText(text = stringRes(R.string.pref__voxtral__language_hint__summary))
                    OwnkeyOutlinedTextField(
                        value = languageHint,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.languageHint.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__voxtral__language_hint__label),
                    )
                    OwnkeyButton(
                        label = stringRes(R.string.pref__voxtral__sync_wear__action),
                        onClick = {
                            WearVoxtralSync.pushConfig(
                                context = context,
                                config = WearVoxtralConfig(
                                    apiKey = voxtralSecretsStore.getApiKey(),
                                    endpointUrl = endpointUrl,
                                    model = model,
                                    languageHint = languageHint,
                                ),
                            ) { result ->
                                wearSyncStatus = result.fold(
                                    onSuccess = { count -> "Wear sync gelukt ($count device${if (count == 1) "" else "s"})" },
                                    onFailure = { error -> "Wear sync mislukt: ${error.message}" },
                                )
                            }
                        },
                    )
                    if (wearSyncStatus.isNotBlank()) {
                        StatusText(text = wearSyncStatus)
                    }
                }

                AiSectionCard(
                    title = stringRes(R.string.pref__ai__rewrite_group__label),
                    summary = stringRes(R.string.pref__ai__rewrite_group__summary),
                ) {
                    StatusText(
                        text = if (hasStoredLlmApiKey) {
                            stringRes(R.string.pref__ai__rewrite_key__status_set)
                        } else {
                            stringRes(R.string.pref__ai__rewrite_key__status_missing)
                        },
                    )
                    OwnkeyOutlinedTextField(
                        value = llmApiKeyInput,
                        onValueChange = { llmApiKeyInput = it },
                        label = stringRes(R.string.pref__ai__rewrite_key__label),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OwnkeyButton(
                            label = stringRes(R.string.pref__ai__rewrite_key__save_action),
                            onClick = {
                                llmRewriteSecretsStore.setApiKey(llmApiKeyInput.trim())
                                llmApiKeyInput = ""
                                hasStoredLlmApiKey = llmRewriteSecretsStore.hasApiKey()
                            },
                            enabled = llmApiKeyInput.trim().isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                        if (hasStoredLlmApiKey) {
                            OwnkeyButton(
                                label = stringRes(R.string.pref__ai__rewrite_key__clear_action),
                                onClick = {
                                    llmRewriteSecretsStore.clearApiKey()
                                    hasStoredLlmApiKey = false
                                },
                                modifier = Modifier.weight(1f),
                                secondary = true,
                            )
                        }
                    }

                    SectionLabel(text = stringRes(R.string.pref__ai__rewrite_provider__label))
                    LlmRewriteProviders.presets.forEach { provider ->
                        ProviderOption(
                            provider = provider,
                            selected = rewriteProviderId == provider.id,
                            onClick = {
                                coroutineScope.launch {
                                    prefsRef.voxtral.postProcessingProvider.set(provider.id)
                                    if (provider.isCustom) {
                                        if (rewriteProviderId != Custom) {
                                            prefsRef.voxtral.postProcessingEndpointUrl.set("")
                                            prefsRef.voxtral.postProcessingModel.set("")
                                        }
                                    } else {
                                        prefsRef.voxtral.postProcessingEndpointUrl.set(provider.endpointUrl)
                                        prefsRef.voxtral.postProcessingModel.set(provider.defaultModel)
                                    }
                                }
                            },
                        )
                    }

                    OwnkeyOutlinedTextField(
                        value = rewriteEndpointUrl,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.postProcessingEndpointUrl.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__ai__rewrite_endpoint__label),
                        enabled = rewriteProviderId == Custom,
                    )
                    OwnkeyOutlinedTextField(
                        value = rewriteModel,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.postProcessingModel.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__ai__rewrite_model__label),
                    )

                    SectionLabel(text = stringRes(R.string.pref__ai__rewrite_voices__label))
                    promptDrafts.forEachIndexed { index, prompt ->
                        PromptCard {
                            OwnkeyOutlinedTextField(
                                value = prompt.name,
                                onValueChange = { value ->
                                    promptDrafts = promptDrafts.toMutableList().also { prompts ->
                                        prompts[index] = prompt.copy(name = value)
                                    }
                                },
                                label = stringRes(R.string.pref__ai__rewrite_voice_name__label),
                            )
                            OwnkeyOutlinedTextField(
                                value = prompt.instruction,
                                onValueChange = { value ->
                                    promptDrafts = promptDrafts.toMutableList().also { prompts ->
                                        prompts[index] = prompt.copy(instruction = value)
                                    }
                                },
                                label = stringRes(R.string.pref__ai__rewrite_instruction__label),
                                singleLine = false,
                                minLines = 2,
                            )
                            OwnkeyButton(
                                label = stringRes(R.string.pref__ai__rewrite_voice__remove_action),
                                onClick = {
                                    promptDrafts = promptDrafts.toMutableList().also { prompts ->
                                        prompts.removeAt(index)
                                    }
                                },
                                enabled = promptDrafts.size > 1,
                                secondary = true,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OwnkeyButton(
                            label = stringRes(R.string.pref__ai__rewrite_voice__add_action),
                            onClick = {
                                promptDrafts = promptDrafts.plus(RewritePromptPresets.newCustom(promptDrafts.size))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        OwnkeyButton(
                            label = stringRes(R.string.pref__ai__rewrite_voice__reset_action),
                            onClick = {
                                promptDrafts = RewritePromptPresets.defaults
                                coroutineScope.launch {
                                    prefsRef.voxtral.rewritePrompts.set(RewritePromptPresets.defaultJson)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            secondary = true,
                        )
                    }
                    OwnkeyButton(
                        label = stringRes(R.string.pref__ai__rewrite_voice__save_action),
                        onClick = {
                            coroutineScope.launch {
                                prefsRef.voxtral.rewritePrompts.set(RewritePromptPresets.encode(promptDrafts))
                            }
                        },
                    )
                }

                AiSectionCard(title = stringRes(R.string.pref__voxtral__group_permissions__label)) {
                    StatusText(text = stringRes(R.string.pref__voxtral__permission__summary))
                    StatusText(
                        text = if (hasMicPermission) {
                            stringRes(R.string.pref__voxtral__permission__granted)
                        } else {
                            stringRes(R.string.pref__voxtral__permission__missing)
                        },
                    )
                    if (!hasMicPermission) {
                        OwnkeyButton(
                            label = stringRes(R.string.pref__voxtral__permission__grant_action),
                            onClick = { requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiIntroCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeyBrand.Line, RoundedCornerShape(22.dp)),
        color = OwnkeyBrand.Panel,
        contentColor = OwnkeyBrand.Bone,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_ownkey_mark),
                contentDescription = stringRes(R.string.floris_app_name),
                modifier = Modifier.size(width = 58.dp, height = 44.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringRes(R.string.pref__ai__intro_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.pref__ai__intro_summary),
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AiSectionCard(
    title: String,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeyBrand.Line, RoundedCornerShape(22.dp)),
        color = OwnkeyBrand.Panel,
        contentColor = OwnkeyBrand.Bone,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

@Composable
private fun PromptCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeyBrand.Line, RoundedCornerShape(18.dp)),
        color = OwnkeyBrand.Action.copy(alpha = 0.62f),
        contentColor = OwnkeyBrand.Bone,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ProviderOption(
    provider: LlmRewriteProviderPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) OwnkeyBrand.SignalOrange else OwnkeyBrand.Line,
                shape = shape,
            )
            .clickable(onClick = onClick),
        color = if (selected) OwnkeyBrand.PanelRaised else OwnkeyBrand.Action.copy(alpha = 0.52f),
        contentColor = OwnkeyBrand.Bone,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = OwnkeyBrand.SignalOrange,
                    unselectedColor = OwnkeyBrand.Ash,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = provider.summary,
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun OwnkeyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = OwnkeyBrand.Bone,
            unfocusedTextColor = OwnkeyBrand.Bone,
            disabledTextColor = OwnkeyBrand.Ash,
            focusedContainerColor = OwnkeyBrand.Graphite,
            unfocusedContainerColor = OwnkeyBrand.Graphite,
            disabledContainerColor = OwnkeyBrand.Action.copy(alpha = 0.5f),
            focusedBorderColor = OwnkeyBrand.SignalOrange,
            unfocusedBorderColor = OwnkeyBrand.Line,
            disabledBorderColor = OwnkeyBrand.Line.copy(alpha = 0.7f),
            focusedLabelColor = OwnkeyBrand.SignalOrange,
            unfocusedLabelColor = OwnkeyBrand.Ash,
            disabledLabelColor = OwnkeyBrand.Ash,
            cursorColor = OwnkeyBrand.SignalOrange,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun OwnkeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) OwnkeyBrand.Action else OwnkeyBrand.TrustBlue,
            contentColor = OwnkeyBrand.Bone,
            disabledContainerColor = OwnkeyBrand.Action.copy(alpha = 0.38f),
            disabledContentColor = OwnkeyBrand.Ash.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = OwnkeyBrand.Ash,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OwnkeyBrand.Bone,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}
