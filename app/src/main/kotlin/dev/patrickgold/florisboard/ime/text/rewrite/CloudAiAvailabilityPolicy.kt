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

import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CloudAiEditorSession(
    val sessionId: Long,
    val isIncognito: Boolean,
    val isSecureField: Boolean,
) {
    companion object {
        val None = CloudAiEditorSession(
            sessionId = 0L,
            isIncognito = false,
            isSecureField = false,
        )
    }
}

enum class CloudAiUnavailableReason {
    NO_ACTIVE_EDITOR,
    SECURE_FIELD,
    INCOGNITO,
}

sealed interface CloudAiAvailability {
    data object Available : CloudAiAvailability
    data class Unavailable(val reason: CloudAiUnavailableReason) : CloudAiAvailability
}

/**
 * The single process-local policy for configured-provider AI availability.
 *
 * Callers must consult [current] before reading editor content, acquiring the microphone, or
 * constructing a provider request. [state] exists for UI controls and active jobs which need to
 * react when the editor session becomes private or secure.
 */
class CloudAiAvailabilityPolicy(
    scope: CoroutineScope,
    private val editorSession: StateFlow<CloudAiEditorSession>,
) {
    val state: StateFlow<CloudAiAvailability> = editorSession
        .map(::evaluate)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = evaluate(editorSession.value),
        )

    fun current(): CloudAiAvailability = evaluate(editorSession.value)

    companion object {
        fun evaluate(session: CloudAiEditorSession): CloudAiAvailability = when {
            session.sessionId <= 0L -> CloudAiAvailability.Unavailable(CloudAiUnavailableReason.NO_ACTIVE_EDITOR)
            session.isSecureField -> CloudAiAvailability.Unavailable(CloudAiUnavailableReason.SECURE_FIELD)
            session.isIncognito -> CloudAiAvailability.Unavailable(CloudAiUnavailableReason.INCOGNITO)
            else -> CloudAiAvailability.Available
        }
    }
}

fun createCloudAiAvailabilityPolicy(
    scope: CoroutineScope,
    editorInstance: EditorInstance,
    keyboardManager: KeyboardManager,
): CloudAiAvailabilityPolicy {
    val initialSession = editorInstance.toCloudAiEditorSession(keyboardManager)
    val sessionState = combine(
        editorInstance.activeInputSessionIdFlow,
        editorInstance.activeInfoFlow,
        keyboardManager.activeState,
    ) { sessionId, editorInfo, keyboardState ->
        CloudAiEditorSession(
            sessionId = sessionId,
            isIncognito = keyboardState.isIncognitoMode,
            isSecureField = editorInfo.isCloudAiSecureField(),
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = initialSession,
    )
    return CloudAiAvailabilityPolicy(scope, sessionState)
}

fun FlorisEditorInfo.isCloudAiSecureField(): Boolean = when (inputAttributes.variation) {
    InputAttributes.Variation.PASSWORD,
    InputAttributes.Variation.VISIBLE_PASSWORD,
    InputAttributes.Variation.WEB_PASSWORD,
    -> true
    else -> false
}

private fun EditorInstance.toCloudAiEditorSession(keyboardManager: KeyboardManager) = CloudAiEditorSession(
    sessionId = activeInputSessionId,
    isIncognito = keyboardManager.activeState.isIncognitoMode,
    isSecureField = activeInfo.isCloudAiSecureField(),
)
