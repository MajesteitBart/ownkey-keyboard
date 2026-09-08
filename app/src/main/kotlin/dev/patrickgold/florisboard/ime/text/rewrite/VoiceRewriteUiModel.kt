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

/**
 * Pure presentation contract for the voice-rewrite surfaces.
 *
 * Compose renders [VoiceRewriteUiModel] and never re-derives state from the session manager, so the
 * entry, recording, processing, review, and recovery branches stay JVM-testable without Compose UI
 * test infrastructure. Copy is referenced as [VoiceRewriteMessage] identities and resolved to string
 * resources at the rendering boundary, which keeps the model free of Android types while every
 * user-facing string stays resource-backed and localizable.
 */

/** Where the user entered the flow, which decides where cancel/back returns to. */
enum class VoiceRewriteEntryOrigin {
    DICTATION_KEY,
    REWRITE_HUB,
}

/** The panel body that must be visible for the current session state. */
enum class VoiceRewriteSurface {
    HUB,
    DISCLOSURE,
    TARGETING,
    RECORDING,
    PAUSED,
    PROCESSING,
    RESULT,
    RECOVERY,
    SUCCESS,
}

/** Resource-backed copy identities. The rendering layer owns the string resource mapping. */
enum class VoiceRewriteMessage {
    SPEAK_AN_EDIT,
    LISTENING,
    PAUSED,
    SELECTING_WHOLE_FIELD,
    PREPARING_MICROPHONE,
    SELECT_TEXT_MANUALLY,
    NOTHING_TO_REWRITE,
    SECURE_FIELD,
    INCOGNITO,
    NO_ACTIVE_EDITOR,
    TARGET_TOO_LONG,
    MICROPHONE_PERMISSION,
    DICTATION_PROVIDER_MISSING,
    REWRITE_PROVIDER_MISSING,
    DICTATION_BUSY,
    RECORDER_UNAVAILABLE,
    UNDERSTANDING_INSTRUCTION,
    REWRITING_SELECTED_TEXT,
    NO_SPEECH,
    RECORDING_FAILED,
    TRANSCRIPTION_FAILED,
    REWRITE_FAILED,
    EMPTY_RESULT,
    TARGET_CHANGED,
    TEXT_REPLACED,
}

/** Interactive affordances the current surface must expose. */
enum class VoiceRewriteAction {
    CONTINUE,
    OPEN_AI_SETTINGS,
    OPEN_INCOGNITO_SETTING,
    BACK,
    CANCEL,
    PAUSE,
    RESUME,
    STOP,
    TRY_AGAIN,
    RECORD_AGAIN,
    REPLACE,
    COPY_RESULT,
    CLOSE,
}

data class VoiceRewriteScopeLabel(
    val scope: VoiceRewriteTargetScope,
    val characterCount: Int,
)

data class VoiceRewriteUiModel(
    val surface: VoiceRewriteSurface,
    val origin: VoiceRewriteEntryOrigin,
    val statusMessage: VoiceRewriteMessage? = null,
    val scopeLabel: VoiceRewriteScopeLabel? = null,
    val disclosure: VoiceRewriteProviderDisclosure? = null,
    val recognizedInstruction: String? = null,
    val resultText: String? = null,
    val actions: Set<VoiceRewriteAction> = emptySet(),
    /** Increments once per state the screen reader must announce, so levels/timers stay silent. */
    val announcementId: Long = 0L,
) {
    val isHub: Boolean get() = surface == VoiceRewriteSurface.HUB
    val isPresetGridInteractive: Boolean get() = surface == VoiceRewriteSurface.HUB

    /**
     * True while the rewrite panel presents the live microphone session. Voice rewrite never renders
     * in the smartbar recording row: the panel owns recording, pause, and processing, so ordinary
     * dictation is the row's only owner and rewrite controls cannot be routed to it.
     */
    val isCapturing: Boolean
        get() = surface == VoiceRewriteSurface.RECORDING || surface == VoiceRewriteSurface.PAUSED
}

/**
 * Maps one immutable session state onto one renderable surface.
 *
 * [announcementSeed] is the session generation so a new session restarts announcements, while state
 * changes inside a generation advance the announcement id exactly once per surface change.
 */
fun voiceRewriteUiModel(
    state: VoiceRewriteSessionState,
    origin: VoiceRewriteEntryOrigin,
): VoiceRewriteUiModel {
    val scopeLabel = state.targetScope?.let { scope ->
        VoiceRewriteScopeLabel(scope, state.targetCharacterCount ?: 0)
    }
    val announcementId = state.generationId * 100 + state.phase.ordinal
    return when (state.phase) {
        VoiceRewriteSessionPhase.READY,
        VoiceRewriteSessionPhase.CANCELLED,
        -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.HUB,
            origin = origin,
        )

        VoiceRewriteSessionPhase.TARGETING -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.TARGETING,
            origin = origin,
            statusMessage = VoiceRewriteMessage.SELECTING_WHOLE_FIELD,
            actions = setOf(VoiceRewriteAction.CANCEL),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.DISCLOSURE -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.DISCLOSURE,
            origin = origin,
            scopeLabel = scopeLabel,
            disclosure = state.disclosure,
            actions = setOf(
                VoiceRewriteAction.CONTINUE,
                VoiceRewriteAction.OPEN_AI_SETTINGS,
                VoiceRewriteAction.BACK,
            ),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.STARTING_RECORDING -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.TARGETING,
            origin = origin,
            statusMessage = VoiceRewriteMessage.PREPARING_MICROPHONE,
            scopeLabel = scopeLabel,
            actions = setOf(VoiceRewriteAction.CANCEL),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.RECORDING -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.RECORDING,
            origin = origin,
            statusMessage = VoiceRewriteMessage.SPEAK_AN_EDIT,
            scopeLabel = scopeLabel,
            actions = setOf(
                VoiceRewriteAction.PAUSE,
                VoiceRewriteAction.CANCEL,
                VoiceRewriteAction.STOP,
            ),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.PAUSED -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.PAUSED,
            origin = origin,
            statusMessage = VoiceRewriteMessage.PAUSED,
            scopeLabel = scopeLabel,
            actions = setOf(
                VoiceRewriteAction.RESUME,
                VoiceRewriteAction.CANCEL,
                VoiceRewriteAction.STOP,
            ),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.TRANSCRIBING -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.PROCESSING,
            origin = origin,
            statusMessage = VoiceRewriteMessage.UNDERSTANDING_INSTRUCTION,
            scopeLabel = scopeLabel,
            actions = setOf(VoiceRewriteAction.CANCEL),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.REWRITING -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.PROCESSING,
            origin = origin,
            statusMessage = VoiceRewriteMessage.REWRITING_SELECTED_TEXT,
            scopeLabel = scopeLabel,
            recognizedInstruction = state.recognizedInstruction,
            actions = setOf(VoiceRewriteAction.CANCEL),
            announcementId = announcementId,
        )

        VoiceRewriteSessionPhase.RESULT -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.RESULT,
            origin = origin,
            statusMessage = state.replacementFailure?.let { VoiceRewriteMessage.TARGET_CHANGED },
            scopeLabel = scopeLabel,
            recognizedInstruction = state.recognizedInstruction,
            resultText = state.resultText,
            // Close leaves without touching the editor; Replace is the only committing action.
            actions = if (state.canReplace) {
                setOf(
                    VoiceRewriteAction.CLOSE,
                    VoiceRewriteAction.TRY_AGAIN,
                    VoiceRewriteAction.RECORD_AGAIN,
                    VoiceRewriteAction.REPLACE,
                )
            } else {
                setOf(VoiceRewriteAction.COPY_RESULT, VoiceRewriteAction.CLOSE)
            },
            announcementId = announcementId + if (state.replacementFailure != null) 1 else 0,
        )

        VoiceRewriteSessionPhase.WARNING -> {
            val message = state.warningMessage()
            VoiceRewriteUiModel(
                surface = VoiceRewriteSurface.RECOVERY,
                origin = origin,
                statusMessage = message,
                scopeLabel = scopeLabel,
                actions = warningActions(state.failure),
                announcementId = announcementId,
            )
        }

        VoiceRewriteSessionPhase.ERROR -> {
            val failure = state.pipelineFailure
            VoiceRewriteUiModel(
                surface = VoiceRewriteSurface.RECOVERY,
                origin = origin,
                statusMessage = failure?.message(),
                scopeLabel = scopeLabel,
                recognizedInstruction = state.recognizedInstruction,
                actions = pipelineActions(failure, state.recognizedInstruction != null),
                announcementId = announcementId,
            )
        }

        VoiceRewriteSessionPhase.SUCCESS -> VoiceRewriteUiModel(
            surface = VoiceRewriteSurface.SUCCESS,
            origin = origin,
            statusMessage = VoiceRewriteMessage.TEXT_REPLACED,
            announcementId = announcementId,
        )
    }
}

private fun VoiceRewriteSessionState.warningMessage(): VoiceRewriteMessage? = when (failure) {
    VoiceRewritePreflightFailure.NO_ACTIVE_EDITOR -> VoiceRewriteMessage.NO_ACTIVE_EDITOR
    VoiceRewritePreflightFailure.SECURE_FIELD -> VoiceRewriteMessage.SECURE_FIELD
    VoiceRewritePreflightFailure.INCOGNITO -> VoiceRewriteMessage.INCOGNITO
    VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY -> VoiceRewriteMessage.DICTATION_BUSY
    VoiceRewritePreflightFailure.MICROPHONE_PERMISSION -> VoiceRewriteMessage.MICROPHONE_PERMISSION
    VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED ->
        VoiceRewriteMessage.DICTATION_PROVIDER_MISSING
    VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED ->
        VoiceRewriteMessage.REWRITE_PROVIDER_MISSING
    VoiceRewritePreflightFailure.RECORDER_UNAVAILABLE -> VoiceRewriteMessage.RECORDER_UNAVAILABLE
    VoiceRewritePreflightFailure.TARGET_REJECTED -> targetFailure.message()
    null -> null
}

private fun VoiceRewriteTargetFailure?.message(): VoiceRewriteMessage = when (this) {
    VoiceRewriteTargetFailure.EMPTY_TARGET -> VoiceRewriteMessage.NOTHING_TO_REWRITE
    VoiceRewriteTargetFailure.TARGET_TOO_LONG -> VoiceRewriteMessage.TARGET_TOO_LONG
    VoiceRewriteTargetFailure.SECURE_FIELD -> VoiceRewriteMessage.SECURE_FIELD
    VoiceRewriteTargetFailure.NO_ACTIVE_EDITOR -> VoiceRewriteMessage.NO_ACTIVE_EDITOR
    VoiceRewriteTargetFailure.RAW_EDITOR,
    VoiceRewriteTargetFailure.INVALID_SELECTION,
    VoiceRewriteTargetFailure.SELECT_ALL_UNSUPPORTED,
    VoiceRewriteTargetFailure.SELECT_ALL_TIMED_OUT,
    VoiceRewriteTargetFailure.SELECTED_TEXT_UNAVAILABLE,
    VoiceRewriteTargetFailure.EDITOR_SESSION_CHANGED,
    null,
    -> VoiceRewriteMessage.SELECT_TEXT_MANUALLY
}

private fun VoiceRewritePipelineFailure.message(): VoiceRewriteMessage = when (this) {
    VoiceRewritePipelineFailure.NO_SPEECH -> VoiceRewriteMessage.NO_SPEECH
    VoiceRewritePipelineFailure.RECORDING -> VoiceRewriteMessage.RECORDING_FAILED
    VoiceRewritePipelineFailure.TRANSCRIPTION -> VoiceRewriteMessage.TRANSCRIPTION_FAILED
    VoiceRewritePipelineFailure.REWRITE -> VoiceRewriteMessage.REWRITE_FAILED
    VoiceRewritePipelineFailure.EMPTY_RESULT -> VoiceRewriteMessage.EMPTY_RESULT
}

private fun warningActions(failure: VoiceRewritePreflightFailure?): Set<VoiceRewriteAction> = when (failure) {
    VoiceRewritePreflightFailure.MICROPHONE_PERMISSION,
    VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED,
    VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED,
    -> setOf(VoiceRewriteAction.OPEN_AI_SETTINGS, VoiceRewriteAction.CLOSE)

    // Incognito is a typing-privacy setting, not an AI configuration problem, so its recovery
    // routes to the incognito preference rather than the provider forms.
    VoiceRewritePreflightFailure.INCOGNITO -> setOf(
        VoiceRewriteAction.OPEN_INCOGNITO_SETTING,
        VoiceRewriteAction.CLOSE,
    )

    else -> setOf(VoiceRewriteAction.CLOSE)
}

private fun pipelineActions(
    failure: VoiceRewritePipelineFailure?,
    hasInstruction: Boolean,
): Set<VoiceRewriteAction> = when (failure) {
    VoiceRewritePipelineFailure.REWRITE,
    VoiceRewritePipelineFailure.EMPTY_RESULT,
    -> buildSet {
        if (hasInstruction) add(VoiceRewriteAction.TRY_AGAIN)
        add(VoiceRewriteAction.RECORD_AGAIN)
        add(VoiceRewriteAction.CLOSE)
    }

    VoiceRewritePipelineFailure.NO_SPEECH,
    VoiceRewritePipelineFailure.RECORDING,
    VoiceRewritePipelineFailure.TRANSCRIPTION,
    -> setOf(VoiceRewriteAction.RECORD_AGAIN, VoiceRewriteAction.CLOSE)

    null -> setOf(VoiceRewriteAction.CLOSE)
}
