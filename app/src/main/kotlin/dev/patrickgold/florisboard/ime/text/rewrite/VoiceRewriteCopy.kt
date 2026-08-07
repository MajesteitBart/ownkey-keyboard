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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/**
 * The single mapping from the pure presentation model onto string resources.
 *
 * Keeping it here means the model stays JVM-testable and free of Android types while every
 * user-facing string in the voice flow remains resource-backed and localizable.
 */
@StringRes
fun VoiceRewriteMessage.stringResId(): Int = when (this) {
    VoiceRewriteMessage.SPEAK_AN_EDIT -> R.string.voice_rewrite__state_speak_an_edit
    VoiceRewriteMessage.LISTENING -> R.string.voice_rewrite__state_listening
    VoiceRewriteMessage.PAUSED -> R.string.voice_rewrite__state_paused
    VoiceRewriteMessage.SELECTING_WHOLE_FIELD -> R.string.voice_rewrite__state_selecting_whole_field
    VoiceRewriteMessage.SELECT_TEXT_MANUALLY -> R.string.voice_rewrite__error_select_text_manually
    VoiceRewriteMessage.NOTHING_TO_REWRITE -> R.string.voice_rewrite__error_nothing_to_rewrite
    VoiceRewriteMessage.SECURE_FIELD -> R.string.voice_rewrite__error_secure_field
    VoiceRewriteMessage.INCOGNITO -> R.string.voice_rewrite__error_incognito
    VoiceRewriteMessage.NO_ACTIVE_EDITOR -> R.string.voice_rewrite__error_no_active_editor
    VoiceRewriteMessage.TARGET_TOO_LONG -> R.string.voice_rewrite__error_target_too_long
    VoiceRewriteMessage.MICROPHONE_PERMISSION -> R.string.voice_rewrite__error_microphone_permission
    VoiceRewriteMessage.DICTATION_PROVIDER_MISSING -> R.string.voice_rewrite__error_dictation_provider_missing
    VoiceRewriteMessage.REWRITE_PROVIDER_MISSING -> R.string.voice_rewrite__error_rewrite_provider_missing
    VoiceRewriteMessage.DICTATION_BUSY -> R.string.voice_rewrite__error_dictation_busy
    VoiceRewriteMessage.RECORDER_UNAVAILABLE -> R.string.voice_rewrite__error_recorder_unavailable
    VoiceRewriteMessage.UNDERSTANDING_INSTRUCTION -> R.string.voice_rewrite__state_understanding
    VoiceRewriteMessage.REWRITING_SELECTED_TEXT -> R.string.voice_rewrite__state_rewriting
    VoiceRewriteMessage.NO_SPEECH -> R.string.voice_rewrite__error_no_speech
    VoiceRewriteMessage.RECORDING_FAILED -> R.string.voice_rewrite__error_recording_failed
    VoiceRewriteMessage.TRANSCRIPTION_FAILED -> R.string.voice_rewrite__error_transcription_failed
    VoiceRewriteMessage.REWRITE_FAILED -> R.string.voice_rewrite__error_rewrite_failed
    VoiceRewriteMessage.EMPTY_RESULT -> R.string.voice_rewrite__error_empty_result
    VoiceRewriteMessage.TARGET_CHANGED -> R.string.voice_rewrite__error_target_changed
    VoiceRewriteMessage.TEXT_REPLACED -> R.string.voice_rewrite__state_text_replaced
}

@StringRes
fun VoiceRewriteAction.stringResId(): Int = when (this) {
    VoiceRewriteAction.CONTINUE -> R.string.voice_rewrite__action_continue
    VoiceRewriteAction.OPEN_AI_SETTINGS -> R.string.voice_rewrite__action_open_ai_settings
    VoiceRewriteAction.OPEN_INCOGNITO_SETTING -> R.string.voice_rewrite__action_open_incognito_setting
    VoiceRewriteAction.BACK -> R.string.voice_rewrite__action_back
    VoiceRewriteAction.CANCEL -> R.string.voice_rewrite__action_cancel
    VoiceRewriteAction.PAUSE -> R.string.voice_rewrite__action_pause
    VoiceRewriteAction.RESUME -> R.string.voice_rewrite__action_resume
    VoiceRewriteAction.STOP -> R.string.voice_rewrite__action_stop
    VoiceRewriteAction.TRY_AGAIN -> R.string.voice_rewrite__action_try_again
    VoiceRewriteAction.RECORD_AGAIN -> R.string.voice_rewrite__action_record_again
    VoiceRewriteAction.REPLACE -> R.string.voice_rewrite__action_replace
    VoiceRewriteAction.COPY_RESULT -> R.string.voice_rewrite__action_copy_result
    VoiceRewriteAction.CLOSE -> R.string.voice_rewrite__action_close
}

/** Resource-backed reason for a control that is visibly disabled rather than silently inert. */
@StringRes
fun CloudAiUnavailableReason.stringResId(): Int = when (this) {
    CloudAiUnavailableReason.NO_ACTIVE_EDITOR -> R.string.voice_rewrite__error_no_active_editor
    CloudAiUnavailableReason.SECURE_FIELD -> R.string.voice_recording__unavailable_secure
    CloudAiUnavailableReason.INCOGNITO -> R.string.voice_recording__unavailable_incognito
}

@Composable
fun VoiceRewriteMessage.text(): String = stringRes(stringResId())

@Composable
fun VoiceRewriteAction.label(): String = stringRes(stringResId())

@Composable
fun CloudAiUnavailableReason.text(): String = stringRes(stringResId())

@Composable
fun VoiceRewriteScopeLabel.text(): String = when (scope) {
    VoiceRewriteTargetScope.SELECTION -> stringRes(
        R.string.voice_rewrite__scope_selection,
        "count" to characterCount,
    )
    VoiceRewriteTargetScope.WHOLE_FIELD -> stringRes(
        R.string.voice_rewrite__scope_whole_field,
        "count" to characterCount,
    )
}
