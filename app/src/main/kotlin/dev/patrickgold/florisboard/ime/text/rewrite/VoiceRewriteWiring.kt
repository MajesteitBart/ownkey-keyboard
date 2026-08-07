/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.rewrite

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralDictationManager
import dev.patrickgold.florisboard.lib.util.launchActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Version of the first-use disclosure copy. Bump it only when the described data path changes, so a
 * provider swap stays visible on the voice card without re-prompting on every use.
 */
const val VOICE_REWRITE_DISCLOSURE_VERSION = 1

private const val AI_SETTINGS_DEEPLINK = "ui://florisboard/settings/voxtral"

/** Incognito lives with the typing privacy preferences, not with the AI provider forms. */
private const val INCOGNITO_SETTINGS_DEEPLINK = "ui://florisboard/settings/typing"

fun createVoiceRewriteSessionManager(
    context: Context,
    scope: CoroutineScope,
    availabilityPolicy: CloudAiAvailabilityPolicy,
    audioSessionCoordinator: AudioSessionCoordinator,
    editorInstance: EditorInstance,
    dictationManager: VoxtralDictationManager,
    rewriteManager: LlmRewriteManager,
): VoiceRewriteSessionManager {
    val appContext = context.applicationContext
    return VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = availabilityPolicy,
        targetSource = VoiceRewriteTargetResolver(EditorInstanceVoiceRewriteGateway(editorInstance)),
        audioSessionCoordinator = audioSessionCoordinator,
        audioRecorderProvider = dictationManager::voiceRewriteRecorder,
        audioSessionModeProvider = dictationManager::voiceRewriteAudioSessionMode,
        microphonePermission = {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        },
        providerConfiguration = AppVoiceRewriteProviderConfiguration(
            context = appContext,
            dictationManager = dictationManager,
            rewriteManager = rewriteManager,
        ),
        disclosureStore = PreferenceVoiceRewriteDisclosureStore(scope),
        disclosureVersion = VOICE_REWRITE_DISCLOSURE_VERSION,
        instructionTranscriptionClientProvider = dictationManager::instructionTranscriptionClient,
        rewriteOperation = { sourceText, instruction ->
            rewriteManager.voiceRewriteOperation().rewrite(sourceText, instruction)
        },
    )
}

fun createVoiceRewriteUiController(
    scope: CoroutineScope,
    context: Context,
    sessionManager: VoiceRewriteSessionManager,
    availabilityPolicy: CloudAiAvailabilityPolicy,
    dictationManager: VoxtralDictationManager,
    rewriteManager: LlmRewriteManager,
    editorInstance: EditorInstance,
    clipboardManager: ClipboardManager,
    keyboardManager: KeyboardManager,
): VoiceRewriteUiController {
    val appContext = context.applicationContext
    return VoiceRewriteUiController(
        scope = scope,
        sessionManager = sessionManager,
        availabilityPolicy = availabilityPolicy,
        providerConfiguration = AppVoiceRewriteProviderConfiguration(
            context = appContext,
            dictationManager = dictationManager,
            rewriteManager = rewriteManager,
        ),
        selectionCharacterCount = {
            val content = editorInstance.activeContent
            content.selectedText
                .takeIf { content.selection.isSelectionMode && it.isNotEmpty() }
                ?.let { selected -> selected.codePointCount(0, selected.length) }
        },
        replacementGateway = {
            EditorInstanceVoiceRewriteReplacementGateway(editorInstance, clipboardManager)
        },
        setPanelVisible = { visible -> keyboardManager.isRewriteOptionsVisible = visible },
        openAiSettingsRoute = { appContext.openSettingsDeepLink(AI_SETTINGS_DEEPLINK) },
        openIncognitoSettingRoute = { appContext.openSettingsDeepLink(INCOGNITO_SETTINGS_DEEPLINK) },
    )
}

/** Resolves configured provider readiness and display names without exposing keys or endpoints. */
private class AppVoiceRewriteProviderConfiguration(
    private val context: Context,
    private val dictationManager: VoxtralDictationManager,
    private val rewriteManager: LlmRewriteManager,
) : VoiceRewriteProviderConfigurationSource {
    override fun transcriptionProvider() = VoiceRewriteProviderConfiguration(
        isConfigured = dictationManager.isTranscriptionConfigured(),
        displayName = dictationManager.transcriptionProviderKnownLabel()
            ?: context.getString(R.string.voice_rewrite__provider_custom),
    )

    override fun rewriteProvider() = VoiceRewriteProviderConfiguration(
        isConfigured = rewriteManager.isRewriteConfigured(),
        displayName = rewriteManager.rewriteProviderLabel(),
    )
}

private class PreferenceVoiceRewriteDisclosureStore(
    private val scope: CoroutineScope,
) : VoiceRewriteDisclosureStore {
    private val prefs by FlorisPreferenceStore

    override fun acknowledgedVersion(): Int = prefs.voxtral.voiceRewriteDisclosureVersion.get()

    override fun acknowledge(version: Int) {
        scope.launch { prefs.voxtral.voiceRewriteDisclosureVersion.set(version) }
    }
}

private fun Context.openSettingsDeepLink(deepLink: String) {
    FlorisImeService.hideUi()
    launchActivity(FlorisAppActivity::class) { intent ->
        // The settings host only treats ACTION_VIEW as a navigation deep link when the browsable
        // category is present; without it the same URI is routed to extension import instead.
        intent.action = Intent.ACTION_VIEW
        intent.data = deepLink.toUri()
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
