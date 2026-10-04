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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageHints
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageMode
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionProviderNaming
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders.Custom
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import dev.patrickgold.florisboard.ime.text.rewrite.ResolvedRewriteProvider
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.stringRes
import java.net.URI

@Composable
fun VoxtralScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voxtral__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val subtypeManager by context.subtypeManager()
    val voxtralSecretsStore = remember { VoxtralSecretsStore(context) }
    val llmRewriteSecretsStore = remember { LlmRewriteSecretsStore(context) }
    val offlineDictation = remember { context.offlineDictation() }

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
        val selectedBackend by prefsRef.voxtral.dictationBackend.collectAsState()
        val languageHint by prefsRef.voxtral.languageHint.collectAsState()
        val rewriteEndpointUrl by prefsRef.voxtral.postProcessingEndpointUrl.collectAsState()
        val rewriteModel by prefsRef.voxtral.postProcessingModel.collectAsState()
        val rewriteProviderId by prefsRef.voxtral.postProcessingProvider.collectAsState()
        val rewritePromptsJson by prefsRef.voxtral.rewritePrompts.collectAsState()
        val offlineState by offlineDictation.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()
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

        // Summaries describe what a request would use, so blank fields show the defaults the clients fall back to.
        val cloudProvider = TranscriptionProviderNaming.knownLabel(
            endpointUrl.trim().ifEmpty { VoxtralRelayTranscriptionClient.DefaultEndpointUrl },
        ) ?: stringRes(R.string.voice_rewrite__provider_custom)
        val cloudModel = model.trim().ifEmpty { VoxtralRelayTranscriptionClient.DefaultModel }
        val cloudEndpointValid = VoxtralRelayTranscriptionClient.hasHttpScheme(
            endpointUrl.trim().ifEmpty { VoxtralRelayTranscriptionClient.DefaultEndpointUrl },
        )
        val effectiveRewrite = LlmRewriteProviders.resolve(rewriteProviderId, rewriteEndpointUrl, rewriteModel)
        val rewriteProvider = effectiveRewrite.preset

        OwnkeyAiSettingsTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OwnkeyBrand.Key)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiOverviewCard(
                    dictation = dictationOverview(
                        backend = TranscriptionBackend.resolve(selectedBackend, hasStoredApiKey, BuildConfig.DEBUG),
                        hasCloudKey = hasStoredApiKey,
                        cloudEndpointValid = cloudEndpointValid,
                        // Until the start-up check has read the stored model, a missing model is not yet known.
                        localModelReady = offlineDictation.compatible &&
                            (offlineState.currentId != null || offlineState.phase == ModelPhase.CHECKING),
                        cloudProvider = cloudProvider,
                        cloudModel = cloudModel,
                    ),
                    rewrite = rewriteOverview(rewrite = effectiveRewrite, hasKey = hasStoredLlmApiKey),
                )
                DictationSettings(
                    hasCloudKey = hasStoredApiKey,
                    cloudEndpointValid = cloudEndpointValid,
                    cloudProvider = cloudProvider,
                    cloudModel = cloudModel,
                )

                AiSectionCard(
                    title = stringRes(R.string.pref__ai__cloud_group__label),
                    summary = stringRes(R.string.pref__ai__cloud_group__summary),
                ) {
                    SectionLabel(text = stringRes(R.string.pref__ai__api_key__section))
                    StatusText(
                        text = if (hasStoredApiKey) {
                            stringRes(R.string.pref__voxtral__api_key__status_set)
                        } else {
                            stringRes(R.string.pref__voxtral__api_key__status_missing)
                        },
                    )
                    StatusText(text = stringRes(R.string.pref__voxtral__api_key__summary))
                    if (!hasStoredApiKey && cloudProvider == MistralProviderName) {
                        OwnkeyButton(
                            label = stringRes(R.string.pref__voxtral__create_account_action),
                            onClick = { context.launchUrl(R.string.voxtral__mistral_signup_url) },
                        )
                    }
                    OwnkeyOutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = stringRes(R.string.pref__voxtral__api_key__label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                                val keep = TranscriptionBackend.choiceToKeepOnKeySave(selectedBackend, hasStoredApiKey)
                                coroutineScope.launch {
                                    // The shown choice is stored before the key: a key without it would let an
                                    // undecided install send its next recording to the cloud.
                                    if (keep != null && prefsRef.voxtral.dictationBackend.set(keep.preference).isFailure) {
                                        context.showShortToast(R.string.pref__voxtral__api_key__save_failed)
                                        return@launch
                                    }
                                    voxtralSecretsStore.setApiKey(normalizedApiKey)
                                    apiKeyInput = ""
                                    hasStoredApiKey = voxtralSecretsStore.hasApiKey()
                                    prefsRef.voxtral.apiKey.set("")
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
                                },
                                modifier = Modifier.weight(1f),
                                secondary = true,
                            )
                        }
                    }

                    SectionLabel(text = stringRes(R.string.pref__ai__cloud_endpoint__section))
                    OwnkeyOutlinedTextField(
                        value = endpointUrl,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.endpointUrl.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__voxtral__endpoint__label),
                        supportingText = stringRes(R.string.pref__voxtral__endpoint__summary),
                    )
                    OwnkeyOutlinedTextField(
                        value = model,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.model.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__voxtral__model__label),
                        supportingText = stringRes(
                            R.string.pref__voxtral__model__summary,
                            "model" to VoxtralRelayTranscriptionClient.DefaultModel,
                        ),
                    )

                    SectionLabel(text = stringRes(R.string.pref__voxtral__language_hint__group))
                    StatusText(text = stringRes(R.string.pref__voxtral__language_hint__summary))
                    DictationLanguageOptions(
                        storedLanguageHint = languageHint,
                        onModeChange = { mode ->
                            coroutineScope.launch {
                                prefsRef.voxtral.languageHint.set(
                                    TranscriptionLanguageHints.storedValueForSelection(
                                        mode = mode,
                                        currentStoredLanguageHint = languageHint,
                                        activeSubtypeLanguageTag = subtypeManager.activeSubtype.primaryLocale.languageTag(),
                                    ),
                                )
                            }
                        },
                        onExplicitLanguageChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.languageHint.set(value)
                            }
                        },
                    )
                }

                PersonalDictionaryCard(onOpen = { navController.navigate(Routes.Settings.SpeechDictionary()) })

                AiSectionCard(
                    title = stringRes(R.string.pref__ai__rewrite_group__label),
                    summary = stringRes(R.string.pref__ai__rewrite_group__summary),
                ) {
                    SectionLabel(text = stringRes(R.string.pref__ai__rewrite_provider__label))
                    LlmRewriteProviders.presets.forEach { provider ->
                        ChoiceOption(
                            label = provider.label,
                            summary = provider.summary,
                            selected = rewriteProvider.id == provider.id,
                            onClick = {
                                coroutineScope.launch {
                                    prefsRef.voxtral.postProcessingProvider.set(provider.id)
                                    if (provider.isCustom) {
                                        if (rewriteProvider.id != Custom) {
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

                    SectionLabel(text = stringRes(R.string.pref__ai__api_key__section))
                    StatusText(
                        text = if (hasStoredLlmApiKey) {
                            stringRes(R.string.pref__ai__rewrite_key__status_set)
                        } else {
                            stringRes(R.string.pref__ai__rewrite_key__status_missing)
                        },
                    )
                    StatusText(
                        text = if (rewriteProvider.isCustom) {
                            stringRes(R.string.pref__ai__rewrite_key__hint_custom)
                        } else {
                            stringRes(R.string.pref__ai__rewrite_key__hint, "provider" to rewriteProvider.providerName)
                        },
                    )
                    OwnkeyOutlinedTextField(
                        value = llmApiKeyInput,
                        onValueChange = { llmApiKeyInput = it },
                        label = stringRes(R.string.pref__ai__rewrite_key__label),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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

                    SectionLabel(text = stringRes(R.string.pref__ai__rewrite_model__section))
                    OwnkeyOutlinedTextField(
                        value = rewriteModel,
                        onValueChange = { value ->
                            coroutineScope.launch {
                                prefsRef.voxtral.postProcessingModel.set(value)
                            }
                        },
                        label = stringRes(R.string.pref__ai__rewrite_model__label),
                        supportingText = if (rewriteProvider.isCustom) {
                            stringRes(R.string.pref__ai__rewrite_model__summary_custom)
                        } else {
                            stringRes(
                                R.string.pref__ai__rewrite_model__summary,
                                "provider" to rewriteProvider.label,
                                "model" to rewriteProvider.defaultModel,
                            )
                        },
                    )
                    if (rewriteProvider.isCustom) {
                        OwnkeyOutlinedTextField(
                            value = rewriteEndpointUrl,
                            onValueChange = { value ->
                                coroutineScope.launch {
                                    prefsRef.voxtral.postProcessingEndpointUrl.set(value)
                                }
                            },
                            label = stringRes(R.string.pref__ai__rewrite_endpoint__label),
                            supportingText = stringRes(R.string.pref__ai__rewrite_endpoint__summary_custom),
                        )
                    } else {
                        StatusText(
                            text = stringRes(
                                R.string.pref__ai__rewrite_endpoint__summary,
                                "host" to (endpointHost(effectiveRewrite.endpointUrl) ?: rewriteProvider.providerName),
                            ),
                        )
                    }
                }

                AiSectionCard(
                    title = stringRes(R.string.pref__ai__rewrite_voices__label),
                    summary = stringRes(R.string.pref__ai__rewrite_voices__summary),
                ) {
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

/** The provider name [TranscriptionProviderNaming] reports for Mistral endpoints, the only one with a signup link. */
private const val MistralProviderName = "Mistral"

/** One line of the overview: what a feature uses, or what it still needs. */
private data class OverviewValue(val text: String, val needsSetup: Boolean)

@Composable
private fun dictationOverview(
    backend: TranscriptionBackend,
    hasCloudKey: Boolean,
    cloudEndpointValid: Boolean,
    localModelReady: Boolean,
    cloudProvider: String,
    cloudModel: String,
): OverviewValue = when (backend) {
    TranscriptionBackend.ORUKEET -> if (localModelReady) {
        OverviewValue(stringRes(R.string.orukeet__title), needsSetup = false)
    } else {
        OverviewValue(stringRes(R.string.pref__ai__overview_orukeet_missing), needsSetup = true)
    }
    TranscriptionBackend.CLOUD -> when {
        !hasCloudKey -> OverviewValue(
            stringRes(R.string.pref__ai__overview_key_missing, "provider" to cloudProvider),
            needsSetup = true,
        )
        !cloudEndpointValid -> OverviewValue(
            stringRes(R.string.pref__ai__overview_endpoint_invalid, "provider" to cloudProvider),
            needsSetup = true,
        )
        else -> OverviewValue(
            stringRes(R.string.pref__ai__overview_provider_model, "provider" to cloudProvider, "model" to cloudModel),
            needsSetup = false,
        )
    }
    TranscriptionBackend.EXTERNAL_IME -> OverviewValue(stringRes(R.string.orukeet__external_label), needsSetup = false)
    TranscriptionBackend.MOCK -> OverviewValue(stringRes(R.string.orukeet__mock_label), needsSetup = false)
    TranscriptionBackend.UNAVAILABLE -> OverviewValue(stringRes(R.string.pref__ai__overview_not_set_up), needsSetup = true)
}

@Composable
private fun rewriteOverview(rewrite: ResolvedRewriteProvider, hasKey: Boolean): OverviewValue = when {
    !hasKey -> OverviewValue(
        stringRes(R.string.pref__ai__overview_key_missing, "provider" to rewrite.preset.providerName),
        needsSetup = true,
    )
    !rewrite.isComplete -> OverviewValue(
        stringRes(R.string.pref__ai__overview_endpoint_missing, "provider" to rewrite.preset.providerName),
        needsSetup = true,
    )
    !rewrite.hasHttpEndpoint -> OverviewValue(
        stringRes(R.string.pref__ai__overview_endpoint_invalid, "provider" to rewrite.preset.providerName),
        needsSetup = true,
    )
    else -> OverviewValue(
        stringRes(
            R.string.pref__ai__overview_provider_model,
            "provider" to rewrite.preset.providerName,
            "model" to rewrite.model,
        ),
        needsSetup = false,
    )
}

/** Host of an endpoint URL, so settings can say where text goes without showing paths or keys. */
private fun endpointHost(endpointUrl: String): String? =
    runCatching { URI(endpointUrl.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }

/** States what dictation and rewrite use right now, so the choices below never have to be inferred. */
@Composable
private fun AiOverviewCard(dictation: OverviewValue, rewrite: OverviewValue) {
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringRes(R.string.pref__ai__intro_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                OverviewRow(label = stringRes(R.string.pref__ai__overview_dictation), value = dictation)
                OverviewRow(label = stringRes(R.string.pref__ai__overview_rewrite), value = rewrite)
            }
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: OverviewValue) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            color = OwnkeyBrand.Ash,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value.text,
            color = if (value.needsSetup) OwnkeyBrand.SignalAmber else OwnkeyBrand.Bone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun AiSectionCard(
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

/**
 * The three reachable dictation-language states.
 *
 * `Auto` is an explicit choice rather than an empty value, so provider-side detection stays
 * available now that following the keyboard language is the default.
 */
@Composable
private fun DictationLanguageOptions(
    storedLanguageHint: String,
    onModeChange: (TranscriptionLanguageMode) -> Unit,
    onExplicitLanguageChange: (String) -> Unit,
) {
    val mode = TranscriptionLanguageHints.modeOf(storedLanguageHint)
    ChoiceOption(
        label = stringRes(R.string.pref__voxtral__language_hint__mode_follow),
        summary = stringRes(R.string.pref__voxtral__language_hint__mode_follow_summary),
        selected = mode == TranscriptionLanguageMode.FOLLOW_KEYBOARD,
        onClick = { onModeChange(TranscriptionLanguageMode.FOLLOW_KEYBOARD) },
    )
    ChoiceOption(
        label = stringRes(R.string.pref__voxtral__language_hint__mode_auto),
        summary = stringRes(R.string.pref__voxtral__language_hint__mode_auto_summary),
        selected = mode == TranscriptionLanguageMode.AUTO,
        onClick = { onModeChange(TranscriptionLanguageMode.AUTO) },
    )
    ChoiceOption(
        label = stringRes(R.string.pref__voxtral__language_hint__mode_explicit),
        summary = stringRes(R.string.pref__voxtral__language_hint__mode_explicit_summary),
        selected = mode == TranscriptionLanguageMode.EXPLICIT,
        onClick = { onModeChange(TranscriptionLanguageMode.EXPLICIT) },
    )
    if (mode == TranscriptionLanguageMode.EXPLICIT) {
        var explicitDraft by remember { mutableStateOf(storedLanguageHint) }
        var lastValidDraft by remember { mutableStateOf(storedLanguageHint) }
        OwnkeyOutlinedTextField(
            value = explicitDraft,
            onValueChange = { value ->
                explicitDraft = value
                if (value.isNotBlank()) {
                    lastValidDraft = value
                    onExplicitLanguageChange(value)
                }
            },
            modifier = Modifier.onFocusChanged { focusState ->
                if (!focusState.isFocused && explicitDraft.isBlank()) {
                    explicitDraft = lastValidDraft
                }
            },
            label = stringRes(R.string.pref__voxtral__language_hint__label),
        )
    }
}

/**
 * One radio option with a summary. A disabled option stays visible and dimmed, and its [note] says what is missing,
 * so a choice that cannot work yet is never hidden or silently ignored.
 */
@Composable
internal fun ChoiceOption(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) OwnkeyBrand.SignalOrange else OwnkeyBrand.Line,
                shape = shape,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
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
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = OwnkeyBrand.SignalOrange,
                    unselectedColor = OwnkeyBrand.Ash,
                    disabledSelectedColor = OwnkeyBrand.SignalOrange.copy(alpha = 0.5f),
                    disabledUnselectedColor = OwnkeyBrand.Ash.copy(alpha = 0.4f),
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.alpha(if (enabled || selected) 1f else 0.55f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary,
                        color = OwnkeyBrand.Ash,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (note != null) {
                    Text(
                        text = note,
                        modifier = Modifier.padding(top = 4.dp),
                        color = OwnkeyBrand.SignalAmber,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OwnkeyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        supportingText = supportingText?.let { text -> { Text(text) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
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
            focusedSupportingTextColor = OwnkeyBrand.Ash,
            unfocusedSupportingTextColor = OwnkeyBrand.Ash,
            cursorColor = OwnkeyBrand.SignalOrange,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
internal fun OwnkeyButton(
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
internal fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = OwnkeyBrand.Ash,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OwnkeyBrand.Bone,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}
