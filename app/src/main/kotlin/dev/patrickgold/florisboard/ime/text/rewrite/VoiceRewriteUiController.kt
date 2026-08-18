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

import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The single UI-facing seam between the headless [VoiceRewriteSessionManager] and every WS-D
 * surface: the dictation-key gesture, the smartbar recording row, the pinned rewrite-hub card, and
 * the review/recovery sheet.
 *
 * Compose observes [uiState] only. Keeping panel visibility, entry origin, and the replacement
 * gateway here means no composable re-derives session rules, so two independent UI owners cannot
 * disagree about recording, processing, or error.
 */
/**
 * Everything the pinned rewrite-hub card needs, resolved in one pass off the typing-critical thread.
 *
 * The selected character count is read only while cloud AI is available, so an incognito or secure
 * session never causes editor content to be inspected for AI use.
 */
data class VoiceRewriteHubCardState(
    val availability: CloudAiAvailability = CloudAiAvailability.Available,
    val selectionCharacterCount: Int? = null,
    val audioProvider: VoiceRewriteProviderConfiguration? = null,
    val rewriteProvider: VoiceRewriteProviderConfiguration? = null,
) {
    val isAvailable: Boolean get() = availability is CloudAiAvailability.Available
    val unavailableReason: CloudAiUnavailableReason?
        get() = (availability as? CloudAiAvailability.Unavailable)?.reason
    val providersConfigured: Boolean
        get() = audioProvider?.isConfigured == true && rewriteProvider?.isConfigured == true
}

class VoiceRewriteUiController(
    scope: CoroutineScope,
    private val sessionManager: VoiceRewriteSessionManager,
    private val availabilityPolicy: CloudAiAvailabilityPolicy,
    private val providerConfiguration: VoiceRewriteProviderConfigurationSource,
    private val selectionCharacterCount: () -> Int?,
    private val replacementGateway: () -> VoiceRewriteReplacementGateway,
    private val setPanelVisible: (Boolean) -> Unit,
    private val openAiSettingsRoute: () -> Unit,
    private val openIncognitoSettingRoute: () -> Unit,
) {
    val availability: StateFlow<CloudAiAvailability> = availabilityPolicy.state

    /**
     * Resolves the hub card. It touches the Keystore-backed secret stores, so callers must run it
     * off the main thread.
     */
    fun hubCardState(): VoiceRewriteHubCardState {
        val currentAvailability = availabilityPolicy.current()
        if (currentAvailability !is CloudAiAvailability.Available) {
            return VoiceRewriteHubCardState(availability = currentAvailability)
        }
        return VoiceRewriteHubCardState(
            availability = currentAvailability,
            selectionCharacterCount = selectionCharacterCount(),
            audioProvider = providerConfiguration.transcriptionProvider(),
            rewriteProvider = providerConfiguration.rewriteProvider(),
        )
    }

    private val _origin = MutableStateFlow(VoiceRewriteEntryOrigin.DICTATION_KEY)

    val uiState: StateFlow<VoiceRewriteUiModel> = combine(
        sessionManager.state,
        _origin,
    ) { state, origin ->
        voiceRewriteUiModel(state, origin)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = voiceRewriteUiModel(sessionManager.state.value, _origin.value),
    )

    /** True when both configured providers exist, which also gates the one-time coach mark. */
    fun providersConfigured(): Boolean =
        providerConfiguration.transcriptionProvider().isConfigured &&
            providerConfiguration.rewriteProvider().isConfigured

    /**
     * Starts the shared preflight for either entry. The rewrite panel is opened first so the
     * targeting, disclosure, and recovery states have a container regardless of entry origin.
     */
    fun begin(origin: VoiceRewriteEntryOrigin) {
        _origin.value = origin
        setPanelVisible(true)
        sessionManager.begin()
    }

    fun acknowledgeDisclosure() = sessionManager.acknowledgeDisclosure()

    fun pauseRecording(): Boolean = sessionManager.pauseRecording()

    fun resumeRecording(): Boolean = sessionManager.resumeRecording()

    fun stopRecording() = sessionManager.stopRecording()

    fun tryAgain() = sessionManager.tryAgain()

    fun recordInstructionAgain() = sessionManager.recordInstructionAgain()

    fun replaceResult(): VoiceRewriteReplacementOutcome =
        sessionManager.replaceResult(replacementGateway())

    fun copyResult(): Boolean = sessionManager.copyResult(replacementGateway())

    fun openAiSettings() = openAiSettingsRoute()

    fun openIncognitoSetting() = openIncognitoSettingRoute()

    /** Cancel while a session is active: stop all work and return to the entry origin. */
    fun cancel() {
        sessionManager.cancel()
        returnToOrigin()
    }

    /** Dismiss a terminal surface without cancelling an active recorder or provider job. */
    fun close() {
        sessionManager.reset()
        returnToOrigin()
    }

    /**
     * Ends the flow after a confirmed replacement. Unlike [close] it always leaves the rewrite
     * panel, because the user's text has already changed and the keyboard should return to typing
     * regardless of which entry started the session.
     */
    fun finishAfterReplacement() {
        sessionManager.reset()
        setPanelVisible(false)
    }

    /** Back inside the panel keeps the hub open; back from the accelerator closes the panel. */
    fun back() {
        sessionManager.reset()
        if (_origin.value == VoiceRewriteEntryOrigin.DICTATION_KEY) {
            setPanelVisible(false)
        }
    }

    fun invalidate(reason: AudioSessionInvalidation) {
        sessionManager.invalidate(reason)
    }

    private fun returnToOrigin() {
        if (_origin.value == VoiceRewriteEntryOrigin.DICTATION_KEY) {
            setPanelVisible(false)
        }
    }
}
