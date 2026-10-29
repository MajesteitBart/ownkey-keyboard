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
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceRow
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeySheet
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageHints
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageMode
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyMode
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviderPreset
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders.Custom
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.florisboard.speechDictionary
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.DisposableLifecycleEffect
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

/** Dialogs and sheets of the AI screen; at most one is open. */
private enum class AiPanel { CloudKey, CloudProvider, Language, RewriteProvider, RewriteKey, RewriteModel, RewriteServer }

@Composable
fun VoxtralScreen() = FlorisScreen {
    title = stringRes(R.string.settings__voxtral__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val subtypeManager by context.subtypeManager()
    val voxtralSecretsStore = remember { VoxtralSecretsStore(context) }
    val llmRewriteSecretsStore = remember { LlmRewriteSecretsStore(context) }
    val speechDictionary = remember { context.speechDictionary().value }

    var hasMicPermission by remember { mutableStateOf(context.hasMicPermission()) }
    // After a second denial Android stops showing the prompt, so Allow opens the app's system settings instead.
    var micPromptBlocked by rememberSaveable { mutableStateOf(false) }
    val requestRecordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasMicPermission = isGranted
        micPromptBlocked = !isGranted && !context.canShowMicPrompt()
    }
    val appPrefs by FlorisPreferenceStore
    val micPermissionRequested by appPrefs.internal.microphonePermissionRequested.collectAsState()
    val micPermissionScope = rememberCoroutineScope()
    // Decided at tap time: once Ownkey has asked and Android no longer offers a rationale, the prompt is blocked
    // and a request would fail without showing anything.
    val onAllowMicrophone = {
        if (micPromptBlocked || (micPermissionRequested && !context.canShowMicPrompt())) {
            context.openAppSettings()
        } else {
            micPermissionScope.launch { appPrefs.internal.microphonePermissionRequested.set(true) }
            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    // Permission can change in system settings while the screen is in the background.
    DisposableLifecycleEffect(onResume = { hasMicPermission = context.hasMicPermission() }, onPause = { })

    content {
        val prefsRef = prefs
        val scope = rememberCoroutineScope()
        val endpointUrl by prefsRef.voxtral.endpointUrl.collectAsState()
        val cloudModel by prefsRef.voxtral.model.collectAsState()
        val selectedBackend by prefsRef.voxtral.dictationBackend.collectAsState()
        val languageHint by prefsRef.voxtral.languageHint.collectAsState()
        val cloudVocabularyMode by prefsRef.voxtral.cloudVocabularyMode.collectAsState()
        val rewriteEndpointUrl by prefsRef.voxtral.postProcessingEndpointUrl.collectAsState()
        val rewriteModel by prefsRef.voxtral.postProcessingModel.collectAsState()
        val rewriteProviderId by prefsRef.voxtral.postProcessingProvider.collectAsState()
        val rewritePromptsJson by prefsRef.voxtral.rewritePrompts.collectAsState()
        val dictionaryState by speechDictionary.state.collectAsState()
        var panel by rememberSaveable { mutableStateOf<AiPanel?>(null) }

        // Key checks open the Keystore-backed stores and decrypt them, so they run off the main thread. The screen
        // waits for both: every route and status below depends on them. They are saveable so that coming back to
        // this screen restores the content at once and the saved scroll position isn't clamped to the top.
        var hasCloudKey by rememberSaveable { mutableStateOf<Boolean?>(null) }
        var hasRewriteKey by rememberSaveable { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            val (cloud, rewrite) = withContext(Dispatchers.IO) {
                val legacyApiKey = prefsRef.voxtral.apiKey.get().trim()
                if (legacyApiKey.isNotBlank()) {
                    // The plain-text key is only cleared once the encrypted store holds a key; dictation reads
                    // the legacy one until then.
                    val moved = runCatching {
                        if (!voxtralSecretsStore.hasApiKey()) voxtralSecretsStore.setApiKey(legacyApiKey)
                    }.isSuccess
                    if (moved) prefsRef.voxtral.apiKey.set("")
                }
                readKey { voxtralSecretsStore.hasApiKey() } to readKey { llmRewriteSecretsStore.hasApiKey() }
            }
            hasCloudKey = cloud
            hasRewriteKey = rewrite
        }
        val cloudKey = hasCloudKey
        val rewriteKey = hasRewriteKey

        if (cloudKey != null && rewriteKey != null) {
            val backend = TranscriptionBackend.resolve(selectedBackend, cloudKey, BuildConfig.DEBUG)
            val effectiveRewrite = LlmRewriteProviders.resolve(rewriteProviderId, rewriteEndpointUrl, rewriteModel)
            val rewritePreset = effectiveRewrite.preset
            val cloudProvider = cloudProviderName(endpointUrl)
            val yourServer = stringRes(R.string.settings__ai__your_server)

            val recordsAudio = backend == TranscriptionBackend.ORUKEET || backend == TranscriptionBackend.CLOUD ||
                backend == TranscriptionBackend.MOCK
            if (!hasMicPermission && recordsAudio) {
                OwnkeyNotice(
                    text = stringRes(R.string.settings__ai__mic__notice),
                    tone = OwnkeyTone.Warning,
                    actionLabel = stringRes(R.string.settings__ai__mic__allow),
                    onAction = onAllowMicrophone,
                    modifier = Modifier.testTag("ai-mic-notice"),
                )
            }

            DictationSettings(
                hasCloudKey = cloudKey,
                cloudEndpointUrl = endpointUrl,
                onAddCloudKey = { panel = AiPanel.CloudKey },
                onFixCloudAddress = { panel = AiPanel.CloudProvider },
                dictionaryRow = {
                    val document = dictionaryState.document
                    val entries = document.words.size + document.corrections.size
                    OwnkeyRow(
                        title = stringRes(R.string.speech_dictionary__title),
                        value = if (entries == 0) {
                            stringRes(R.string.settings__ai__dictionary__empty)
                        } else {
                            pluralsRes(R.plurals.settings__ai__dictionary__count, entries, "count" to entries)
                        },
                        chevron = true,
                        onClick = { navController.navigate(Routes.Settings.SpeechDictionary()) },
                        modifier = Modifier.testTag("ai-dictionary"),
                    )
                },
            )

            val cloudFooter = AiSettings.cloudFooter(
                storedEndpointUrl = endpointUrl,
                mode = CloudVocabularyMode.fromPreference(cloudVocabularyMode),
                dictionaryWords = dictionaryState.document.words.size,
            )
            OwnkeyGroup(
                title = stringRes(R.string.settings__ai__cloud__title),
                footer = stringRes(
                    if (cloudFooter.sendsDictionaryWords) R.string.settings__ai__cloud__footer_words else R.string.settings__ai__cloud__footer,
                    "host" to (cloudFooter.host ?: yourServer),
                ),
            ) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__key__row),
                    value = keyValue(cloudKey),
                    valueColor = warningIf(!cloudKey && backend == TranscriptionBackend.CLOUD),
                    onClick = { panel = AiPanel.CloudKey },
                    modifier = Modifier.testTag("ai-cloud-key"),
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__language__row),
                    value = languageLabel(languageHint),
                    onClick = { panel = AiPanel.Language },
                    modifier = Modifier.testTag("ai-cloud-language"),
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__provider__row),
                    value = cloudProvider ?: stringRes(R.string.settings__ai__status__custom_server),
                    valueColor = warningIf(!isUsableCloudEndpoint(endpointUrl)),
                    onClick = { panel = AiPanel.CloudProvider },
                    modifier = Modifier.testTag("ai-cloud-provider"),
                )
            }

            OwnkeyGroup(
                title = stringRes(R.string.settings__ai__rewrite__title),
                footer = stringRes(
                    R.string.settings__ai__rewrite__footer,
                    "host" to (AiSettings.rewriteHost(effectiveRewrite) ?: yourServer),
                ),
            ) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__provider__row),
                    value = stringRes(rewritePreset.labelRes),
                    onClick = { panel = AiPanel.RewriteProvider },
                    modifier = Modifier.testTag("ai-rewrite-provider"),
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__key__row),
                    value = keyValue(rewriteKey),
                    valueColor = warningIf(!rewriteKey),
                    onClick = { panel = AiPanel.RewriteKey },
                    modifier = Modifier.testTag("ai-rewrite-key"),
                )
                val modelValue = AiSettings.rewriteModelValue(rewritePreset, rewriteModel)
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__rewrite_model__row),
                    value = when (modelValue) {
                        RewriteModelValue.Default -> stringRes(R.string.settings__ai__rewrite_model__default)
                        RewriteModelValue.NotSet -> stringRes(R.string.settings__ai__not_set)
                        is RewriteModelValue.Named -> modelValue.model
                    },
                    valueColor = warningIf(modelValue == RewriteModelValue.NotSet),
                    onClick = { panel = AiPanel.RewriteModel },
                    modifier = Modifier.testTag("ai-rewrite-model"),
                )
                if (rewritePreset.isCustom) {
                    val address = rewriteEndpointUrl.trim()
                    OwnkeyRow(
                        title = stringRes(R.string.settings__ai__rewrite_server__row),
                        value = when {
                            address.isEmpty() -> stringRes(R.string.settings__ai__not_set)
                            effectiveRewrite.hasHttpEndpoint -> endpointHost(address) ?: address
                            else -> address
                        },
                        valueColor = warningIf(!effectiveRewrite.hasHttpEndpoint),
                        onClick = { panel = AiPanel.RewriteServer },
                        modifier = Modifier.testTag("ai-rewrite-server"),
                    )
                }
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__rewrite_styles__row),
                    value = RewritePromptPresets.decode(rewritePromptsJson).size.toString(),
                    chevron = true,
                    onClick = { navController.navigate(Routes.Settings.RewriteStyles) },
                    modifier = Modifier.testTag("ai-rewrite-styles"),
                )
            }

            // The failure of the last Save or Remove in the open key dialog; a new panel starts without one.
            var keyError by remember(panel) { mutableStateOf<String?>(null) }
            val saveFailed = stringRes(R.string.pref__voxtral__api_key__save_failed)
            val removeFailed = stringRes(R.string.settings__ai__key__remove_failed)
            val closePanel = { panel = null }
            when (panel) {
                AiPanel.CloudKey -> ApiKeyDialog(
                    title = stringRes(R.string.settings__ai__cloud_key__title),
                    text = null,
                    hasKey = cloudKey,
                    onSave = { key ->
                        val keep = TranscriptionBackend.choiceToKeepOnKeySave(selectedBackend, cloudKey)
                        scope.launch {
                            keyError = null
                            // The shown choice is stored before the key: a key without it would let an
                            // undecided install send its next recording to the cloud.
                            if (keep != null && prefsRef.voxtral.dictationBackend.set(keep.preference).isFailure) {
                                keyError = saveFailed
                                return@launch
                            }
                            val change = withContext(Dispatchers.IO) {
                                val stored = runCatching { voxtralSecretsStore.setApiKey(key) }.isSuccess
                                AiSettings.apiKeySaved(stored, readKey { voxtralSecretsStore.hasApiKey() })
                            }
                            hasCloudKey = change.hasKey
                            if (!change.succeeded) {
                                keyError = saveFailed
                                return@launch
                            }
                            prefsRef.voxtral.apiKey.set("")
                            closePanel()
                        }
                    },
                    onRemove = {
                        scope.launch {
                            keyError = null
                            val change = withContext(Dispatchers.IO) {
                                runCatching { voxtralSecretsStore.clearApiKey() }
                                AiSettings.apiKeyRemoved(readKey { voxtralSecretsStore.hasApiKey() })
                            }
                            hasCloudKey = change.hasKey
                            if (!change.succeeded) {
                                keyError = removeFailed
                                return@launch
                            }
                            prefsRef.voxtral.apiKey.set("")
                            closePanel()
                        }
                    },
                    onDismiss = closePanel,
                    errorText = keyError,
                ) {
                    if (!cloudKey && cloudProvider == MistralProviderName) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OwnkeyButton(
                            label = stringRes(R.string.settings__ai__cloud_key__signup),
                            onClick = { context.launchUrl(R.string.voxtral__mistral_signup_url) },
                            style = OwnkeyButtonStyle.Plain,
                            modifier = Modifier.testTag("ai-mistral-signup"),
                        )
                    }
                }
                AiPanel.CloudProvider -> CloudProviderDialog(
                    storedEndpointUrl = endpointUrl,
                    storedModel = cloudModel,
                    onSave = { address, model ->
                        scope.launch {
                            prefsRef.voxtral.endpointUrl.set(address)
                            prefsRef.voxtral.model.set(model)
                        }
                        closePanel()
                    },
                    onDismiss = closePanel,
                )
                AiPanel.Language -> LanguageSheet(
                    storedLanguageHint = languageHint,
                    onModeChange = { mode ->
                        val value = TranscriptionLanguageHints.storedValueForSelection(
                            mode = mode,
                            currentStoredLanguageHint = languageHint,
                            activeSubtypeLanguageTag = subtypeManager.activeSubtype.primaryLocale.languageTag(),
                        )
                        scope.launch { prefsRef.voxtral.languageHint.set(value) }
                    },
                    onExplicitLanguageChange = { code -> scope.launch { prefsRef.voxtral.languageHint.set(code) } },
                    onDismiss = closePanel,
                )
                AiPanel.RewriteProvider -> OwnkeySheet(
                    onDismiss = closePanel,
                    title = stringRes(R.string.settings__ai__rewrite_provider__title),
                    initialScrollIndex = LlmRewriteProviders.presets.indexOfFirst { it.id == rewritePreset.id }.coerceAtLeast(0),
                ) { hide ->
                    itemsIndexed(LlmRewriteProviders.presets) { index, provider ->
                        OwnkeyChoiceRow(
                            label = stringRes(provider.labelRes),
                            summary = stringRes(provider.summaryRes),
                            selected = rewritePreset.id == provider.id,
                            onClick = {
                                scope.launch { selectRewriteProvider(prefsRef, provider, rewritePreset) }
                                hide()
                            },
                            modifier = Modifier.testTag("sheet-option-$index"),
                        )
                    }
                }
                AiPanel.RewriteKey -> ApiKeyDialog(
                    title = stringRes(R.string.settings__ai__rewrite_key__title),
                    text = if (rewritePreset.isCustom) {
                        stringRes(R.string.settings__ai__rewrite_key__help_custom)
                    } else {
                        stringRes(R.string.settings__ai__rewrite_key__help, "provider" to rewritePreset.providerName)
                    },
                    hasKey = rewriteKey,
                    onSave = { key ->
                        scope.launch {
                            keyError = null
                            val change = withContext(Dispatchers.IO) {
                                val stored = runCatching { llmRewriteSecretsStore.setApiKey(key) }.isSuccess
                                AiSettings.apiKeySaved(stored, readKey { llmRewriteSecretsStore.hasApiKey() })
                            }
                            hasRewriteKey = change.hasKey
                            if (!change.succeeded) {
                                keyError = saveFailed
                                return@launch
                            }
                            closePanel()
                        }
                    },
                    onRemove = {
                        scope.launch {
                            keyError = null
                            val change = withContext(Dispatchers.IO) {
                                runCatching { llmRewriteSecretsStore.clearApiKey() }
                                AiSettings.apiKeyRemoved(readKey { llmRewriteSecretsStore.hasApiKey() })
                            }
                            hasRewriteKey = change.hasKey
                            if (!change.succeeded) {
                                keyError = removeFailed
                                return@launch
                            }
                            closePanel()
                        }
                    },
                    onDismiss = closePanel,
                    errorText = keyError,
                )
                AiPanel.RewriteModel -> TextFieldDialog(
                    title = stringRes(R.string.settings__ai__rewrite_model__title),
                    label = stringRes(R.string.settings__ai__rewrite_model__row),
                    initialValue = AiSettings.rewriteModelDraft(rewritePreset, rewriteModel),
                    placeholder = rewritePreset.defaultModel.ifEmpty { null },
                    supportingText = if (rewritePreset.isCustom) {
                        stringRes(R.string.settings__ai__rewrite_model__hint_custom)
                    } else {
                        stringRes(R.string.settings__ai__rewrite_model__hint)
                    },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    fieldTag = "ai-rewrite-model-field",
                    onSave = { input ->
                        val model = AiSettings.rewriteModelToStore(rewritePreset, input)
                        scope.launch { prefsRef.voxtral.postProcessingModel.set(model) }
                        closePanel()
                    },
                    onDismiss = closePanel,
                )
                AiPanel.RewriteServer -> {
                    val addressError = stringRes(R.string.settings__ai__address_error)
                    TextFieldDialog(
                        title = stringRes(R.string.settings__ai__rewrite_server__title),
                        label = stringRes(R.string.settings__ai__rewrite_server__row),
                        initialValue = rewriteEndpointUrl.trim(),
                        placeholder = "https://",
                        supportingText = stringRes(R.string.settings__ai__rewrite_server__hint),
                        errorFor = { input ->
                            addressError.takeIf { AiSettings.showsServerAddressError(input, emptyAllowed = true) }
                        },
                        canSave = { input -> AiSettings.isAcceptableServerAddress(input, emptyAllowed = true) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        fieldTag = "ai-rewrite-server-field",
                        onSave = { input ->
                            scope.launch { prefsRef.voxtral.postProcessingEndpointUrl.set(input.trim()) }
                            closePanel()
                        },
                        onDismiss = closePanel,
                    )
                }
                null -> Unit
            }
        }
    }
}

/** The provider name [cloudProviderName] reports for Mistral endpoints, the only one with a signup link. */
private const val MistralProviderName = "Mistral"

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Whether Android would still show the microphone prompt after a denial (it offers a rationale). */
private fun Context.canShowMicPrompt(): Boolean {
    val activity = this as? Activity ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/** A key store that can't be opened counts as having no key; saving a key again is the fix. */
private inline fun readKey(read: () -> Boolean): Boolean = runCatching { read() }.getOrDefault(false)

private fun warningIf(condition: Boolean): Color = if (condition) OwnkeyTokens.Warning else Color.Unspecified

@Composable
private fun keyValue(hasKey: Boolean): String =
    stringRes(if (hasKey) R.string.settings__ai__key__saved else R.string.settings__ai__not_set)

@Composable
private fun languageLabel(storedLanguageHint: String): String = when (val value = AiSettings.languageValue(storedLanguageHint)) {
    DictationLanguageValue.KeyboardLanguage -> stringRes(R.string.settings__ai__language__keyboard)
    DictationLanguageValue.Automatic -> stringRes(R.string.settings__ai__language__auto)
    is DictationLanguageValue.Specific -> remember(value.code) {
        runCatching { FlorisLocale.fromTag(value.code).displayName() }.getOrNull()?.takeIf { it.isNotBlank() } ?: value.code
    }
}

/**
 * Picking a built-in provider fills in its address and default model. Picking Other keeps a custom
 * address and model that are already set, and clears a built-in's, so they never leak into a custom setup.
 */
private suspend fun selectRewriteProvider(
    prefs: FlorisPreferenceModel,
    provider: LlmRewriteProviderPreset,
    current: LlmRewriteProviderPreset,
) {
    prefs.voxtral.postProcessingProvider.set(provider.id)
    if (provider.isCustom) {
        if (current.id != Custom) {
            prefs.voxtral.postProcessingEndpointUrl.set("")
            prefs.voxtral.postProcessingModel.set("")
        }
    } else {
        prefs.voxtral.postProcessingEndpointUrl.set(provider.endpointUrl)
        prefs.voxtral.postProcessingModel.set(provider.defaultModel)
    }
}

/** Server address and model of cloud dictation. Empty fields mean Mistral's defaults, shown as hints. */
@Composable
private fun CloudProviderDialog(
    storedEndpointUrl: String,
    storedModel: String,
    onSave: (address: String, model: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultAddress = VoxtralRelayTranscriptionClient.DefaultEndpointUrl
    val defaultModel = VoxtralRelayTranscriptionClient.DefaultModel
    var address by rememberSaveable { mutableStateOf(AiSettings.draftOf(storedEndpointUrl, defaultAddress)) }
    var model by rememberSaveable { mutableStateOf(AiSettings.draftOf(storedModel, defaultModel)) }
    val canSave = AiSettings.isAcceptableServerAddress(address, emptyAllowed = true)
    OwnkeyDialog(
        title = stringRes(R.string.settings__ai__cloud_provider__title),
        onDismiss = onDismiss,
        text = stringRes(R.string.settings__ai__cloud_provider__text),
        confirmLabel = stringRes(R.string.settings__ai__save),
        onConfirm = {
            if (canSave) {
                onSave(AiSettings.storedValueOf(address, defaultAddress), AiSettings.storedValueOf(model, defaultModel))
            }
        },
        confirmEnabled = canSave,
        dismissLabel = stringRes(R.string.action__cancel),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        OwnkeyTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.testTag("ai-cloud-address-field"),
            label = stringRes(R.string.settings__ai__cloud_provider__address),
            placeholder = defaultAddress,
            isError = AiSettings.showsServerAddressError(address, emptyAllowed = true),
            errorText = stringRes(R.string.settings__ai__address_error),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OwnkeyTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.testTag("ai-cloud-model-field"),
            label = stringRes(R.string.settings__ai__cloud_provider__model),
            placeholder = defaultModel,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Empty fields mean Mistral's defaults, so resetting clears them; Save stores the reset.
        OwnkeyButton(
            label = stringRes(R.string.settings__ai__cloud_provider__reset),
            onClick = {
                address = ""
                model = ""
            },
            enabled = address.isNotEmpty() || model.isNotEmpty(),
            style = OwnkeyButtonStyle.Plain,
            modifier = Modifier.testTag("ai-cloud-reset"),
        )
    }
}

/**
 * The three dictation language choices. Keyboard language and Detect automatically apply and close;
 * Specific language stays open with a code field. The field only stores non-empty codes and falls back to
 * the last one when left empty, so the stored value never drops back to another mode by accident.
 */
@Composable
private fun LanguageSheet(
    storedLanguageHint: String,
    onModeChange: (TranscriptionLanguageMode) -> Unit,
    onExplicitLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mode = TranscriptionLanguageHints.modeOf(storedLanguageHint)
    OwnkeySheet(
        onDismiss = onDismiss,
        title = stringRes(R.string.settings__ai__language__title),
        initialScrollIndex = mode.ordinal,
    ) { hide ->
        item {
            OwnkeyChoiceRow(
                label = stringRes(R.string.settings__ai__language__keyboard),
                summary = stringRes(R.string.settings__ai__language__keyboard_summary),
                selected = mode == TranscriptionLanguageMode.FOLLOW_KEYBOARD,
                onClick = {
                    onModeChange(TranscriptionLanguageMode.FOLLOW_KEYBOARD)
                    hide()
                },
                modifier = Modifier.testTag("sheet-option-0"),
            )
        }
        item {
            OwnkeyChoiceRow(
                label = stringRes(R.string.settings__ai__language__auto),
                summary = stringRes(R.string.settings__ai__language__auto_summary),
                selected = mode == TranscriptionLanguageMode.AUTO,
                onClick = {
                    onModeChange(TranscriptionLanguageMode.AUTO)
                    hide()
                },
                modifier = Modifier.testTag("sheet-option-1"),
            )
        }
        item {
            OwnkeyChoiceRow(
                label = stringRes(R.string.settings__ai__language__specific),
                summary = stringRes(R.string.settings__ai__language__specific_summary),
                selected = mode == TranscriptionLanguageMode.EXPLICIT,
                onClick = { onModeChange(TranscriptionLanguageMode.EXPLICIT) },
                modifier = Modifier.testTag("sheet-option-2"),
            )
        }
        if (mode == TranscriptionLanguageMode.EXPLICIT) {
            item(key = "language-code") {
                var draft by remember { mutableStateOf(storedLanguageHint.trim()) }
                var lastValid by remember { mutableStateOf(storedLanguageHint.trim()) }
                CardBlock {
                    OwnkeyTextField(
                        value = draft,
                        onValueChange = { value ->
                            draft = value
                            if (value.isNotBlank()) {
                                lastValid = value.trim()
                                onExplicitLanguageChange(value.trim())
                            }
                        },
                        modifier = Modifier
                            .testTag("ai-language-code-field")
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused && draft.isBlank()) draft = lastValid
                            },
                        label = stringRes(R.string.settings__ai__language__code),
                        supportingText = stringRes(R.string.settings__ai__language__code_hint),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    )
                    OwnkeyButton(
                        label = stringRes(R.string.settings__ai__done),
                        onClick = hide,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai-language-done"),
                    )
                }
            }
        }
    }
}
