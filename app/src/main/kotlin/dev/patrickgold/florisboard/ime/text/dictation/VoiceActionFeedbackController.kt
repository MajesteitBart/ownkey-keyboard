/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class VoiceActionFeedbackPhase {
    IDLE,
    RECORDING,
    PAUSED,
    PROCESSING,
    SUCCESS,
    ERROR,
}

enum class VoiceActionErrorReason {
    AUDIO_SESSION_BUSY,
    MICROPHONE_PERMISSION,
    RECORDER_UNAVAILABLE,
    RECORDING,
    TRANSCRIPTION,
    EMPTY_AUDIO,
    EDITOR_COMMIT,
}

data class VoiceActionFeedbackState(
    val sessionId: Long? = null,
    val phase: VoiceActionFeedbackPhase = VoiceActionFeedbackPhase.IDLE,
    val errorReason: VoiceActionErrorReason? = null,
    /** Changes once per error outcome so accessibility can announce that outcome exactly once. */
    val announcementId: Long = 0L,
)

/** Session-safe transient feedback for the shared dictation/voice-rewrite action. */
class VoiceActionFeedbackController(
    private val scope: CoroutineScope,
    private val successDurationMs: Long = 900L,
    private val errorDurationMs: Long = 5_000L,
) {
    private val _state = MutableStateFlow(VoiceActionFeedbackState())
    val state: StateFlow<VoiceActionFeedbackState> = _state

    private var resetJob: Job? = null
    private var transitionToken = 0L
    private var nextStandaloneSessionId = -1L
    private var disposed = false

    fun begin(sessionId: Long): Boolean = transition(sessionId, VoiceActionFeedbackPhase.RECORDING)

    fun pause(sessionId: Long): Boolean = transition(sessionId, VoiceActionFeedbackPhase.PAUSED)

    fun resume(sessionId: Long): Boolean = transition(sessionId, VoiceActionFeedbackPhase.RECORDING)

    fun processing(sessionId: Long): Boolean = transition(sessionId, VoiceActionFeedbackPhase.PROCESSING)

    fun success(sessionId: Long): Boolean {
        if (disposed || _state.value.sessionId != sessionId) return false
        if (_state.value.phase != VoiceActionFeedbackPhase.PROCESSING) return false
        publish(
            VoiceActionFeedbackState(
                sessionId = sessionId,
                phase = VoiceActionFeedbackPhase.SUCCESS,
                announcementId = _state.value.announcementId,
            ),
            resetAfterMs = successDurationMs,
        )
        return true
    }

    fun error(sessionId: Long, reason: VoiceActionErrorReason): Boolean {
        if (disposed || _state.value.sessionId != sessionId) return false
        publishError(sessionId, reason)
        return true
    }

    fun standaloneError(reason: VoiceActionErrorReason): Long {
        if (disposed) return 0L
        val sessionId = nextStandaloneSessionId--
        publishError(sessionId, reason)
        return sessionId
    }

    fun cancel(sessionId: Long): Boolean {
        if (disposed || _state.value.sessionId != sessionId) return false
        resetJob?.cancel()
        resetJob = null
        transitionToken += 1
        _state.value = VoiceActionFeedbackState(announcementId = _state.value.announcementId)
        return true
    }

    fun dispose() {
        resetJob?.cancel()
        resetJob = null
        transitionToken += 1
        disposed = true
        _state.value = VoiceActionFeedbackState(announcementId = _state.value.announcementId)
    }

    private fun transition(sessionId: Long, phase: VoiceActionFeedbackPhase): Boolean {
        if (disposed) return false
        val current = _state.value
        if (current.sessionId != null && current.sessionId != sessionId &&
            current.phase !in setOf(
                VoiceActionFeedbackPhase.IDLE,
                VoiceActionFeedbackPhase.SUCCESS,
                VoiceActionFeedbackPhase.ERROR,
            )
        ) {
            return false
        }
        publish(
            VoiceActionFeedbackState(
                sessionId = sessionId,
                phase = phase,
                announcementId = current.announcementId,
            ),
        )
        return true
    }

    private fun publishError(sessionId: Long, reason: VoiceActionErrorReason) {
        publish(
            VoiceActionFeedbackState(
                sessionId = sessionId,
                phase = VoiceActionFeedbackPhase.ERROR,
                errorReason = reason,
                announcementId = _state.value.announcementId + 1,
            ),
            resetAfterMs = errorDurationMs,
        )
    }

    private fun publish(state: VoiceActionFeedbackState, resetAfterMs: Long? = null) {
        resetJob?.cancel()
        resetJob = null
        val token = ++transitionToken
        _state.value = state
        if (resetAfterMs != null) {
            resetJob = scope.launch {
                delay(resetAfterMs)
                if (!disposed && transitionToken == token && _state.value == state) {
                    _state.value = VoiceActionFeedbackState(announcementId = state.announcementId)
                    resetJob = null
                }
            }
        }
    }
}
