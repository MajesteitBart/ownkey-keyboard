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

package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.lib.util.BatteryTraceSink
import dev.patrickgold.florisboard.lib.util.NoOpBatteryTraceSink
import dev.patrickgold.florisboard.lib.util.OwnkeyBatteryTraceLabels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AudioSessionOwner {
    DICTATION,
    VOICE_REWRITE,
}

enum class AudioSessionMode {
    MOCK,
    CONFIGURED_PROVIDER,
}

enum class AudioSessionPhase {
    RECORDING,
    PAUSED,
    PROCESSING,
}

enum class AudioSessionInvalidation {
    FIELD_SWITCH,
    KEYBOARD_HIDE,
    INPUT_RESTART,
    SECURE_TRANSITION,
    IME_TEARDOWN,
    OWNER_CANCELLED,
}

data class AudioSessionState(
    val sessionId: Long,
    val owner: AudioSessionOwner,
    val mode: AudioSessionMode,
    val phase: AudioSessionPhase,
    val startedAtMs: Long,
    val pausedAtMs: Long? = null,
    val pausedDurationMs: Long = 0L,
    val measuredLevel: Float = 0f,
) {
    fun elapsedMs(nowMs: Long): Long {
        val activePauseMs = pausedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        return (nowMs - startedAtMs - pausedDurationMs - activePauseMs).coerceAtLeast(0L)
    }
}

sealed interface AudioSessionStartResult {
    data class Started(val lease: AudioSessionLease) : AudioSessionStartResult
    data class Busy(val owner: AudioSessionOwner, val phase: AudioSessionPhase) : AudioSessionStartResult
    data object RecorderUnavailable : AudioSessionStartResult
}

sealed interface AudioSessionStopResult {
    data class Stopped(val recording: AudioRecording) : AudioSessionStopResult
    data class Failed(val error: Throwable) : AudioSessionStopResult
    data object AlreadyStopped : AudioSessionStopResult
    data object Stale : AudioSessionStopResult
}

/**
 * Process-local single-recorder boundary shared by dictation and voice rewrite.
 *
 * Every operation is generation-scoped through [AudioSessionLease]. A callback holding an old
 * lease can therefore never pause, finish, or clear a newer recording session.
 */
class AudioSessionCoordinator(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val traceSink: BatteryTraceSink = NoOpBatteryTraceSink,
) {
    private data class ActiveSession(
        val lease: AudioSessionLease,
        val recorder: AudioRecorder,
        var state: AudioSessionState,
        var stopResult: AudioSessionStopResult? = null,
    )

    private val lock = Any()
    private var nextSessionId = 0L
    private var activeSession: ActiveSession? = null
    private val _state = MutableStateFlow<AudioSessionState?>(null)
    val state: StateFlow<AudioSessionState?> = _state

    fun tryStart(
        owner: AudioSessionOwner,
        mode: AudioSessionMode,
        recorder: AudioRecorder,
    ): AudioSessionStartResult = synchronized(lock) {
        activeSession?.let { active ->
            return@synchronized AudioSessionStartResult.Busy(
                owner = active.state.owner,
                phase = active.state.phase,
            )
        }
        if (!recorder.start()) {
            return@synchronized AudioSessionStartResult.RecorderUnavailable
        }

        val sessionId = ++nextSessionId
        val lease = AudioSessionLease(this, sessionId)
        val state = AudioSessionState(
            sessionId = sessionId,
            owner = owner,
            mode = mode,
            phase = AudioSessionPhase.RECORDING,
            startedAtMs = nowMs(),
        )
        activeSession = ActiveSession(lease = lease, recorder = recorder, state = state)
        _state.value = state
        beginTrace(state)
        AudioSessionStartResult.Started(lease)
    }

    fun invalidate(reason: AudioSessionInvalidation): Boolean {
        @Suppress("UNUSED_VARIABLE") val recordedReason = reason
        val session = synchronized(lock) {
            val current = activeSession ?: return false
            activeSession = null
            _state.value = null
            nextSessionId += 1
            current
        }
        session.recorder.cancel()
        endTrace(session.state)
        return true
    }

    internal fun isCurrent(sessionId: Long): Boolean = synchronized(lock) {
        activeSession?.state?.sessionId == sessionId
    }

    internal fun state(sessionId: Long): AudioSessionState? = synchronized(lock) {
        activeSession?.state?.takeIf { it.sessionId == sessionId }
    }

    internal fun pause(sessionId: Long): Boolean = synchronized(lock) {
        val session = activeSession?.takeIf { it.state.sessionId == sessionId } ?: return false
        if (session.state.phase != AudioSessionPhase.RECORDING || !session.recorder.pause()) return false
        updateState(
            session,
            session.state.copy(
                phase = AudioSessionPhase.PAUSED,
                pausedAtMs = nowMs(),
                measuredLevel = 0f,
            ),
        )
        true
    }

    internal fun resume(sessionId: Long): Boolean = synchronized(lock) {
        val session = activeSession?.takeIf { it.state.sessionId == sessionId } ?: return false
        if (session.state.phase != AudioSessionPhase.PAUSED || !session.recorder.resume()) return false
        val resumedAt = nowMs()
        val pausedAt = session.state.pausedAtMs ?: resumedAt
        updateState(
            session,
            session.state.copy(
                phase = AudioSessionPhase.RECORDING,
                pausedAtMs = null,
                pausedDurationMs = session.state.pausedDurationMs + (resumedAt - pausedAt).coerceAtLeast(0L),
                measuredLevel = 0f,
            ),
        )
        true
    }

    internal fun sampleLevel(sessionId: Long): Float = synchronized(lock) {
        val session = activeSession?.takeIf { it.state.sessionId == sessionId } ?: return 0f
        if (session.state.phase != AudioSessionPhase.RECORDING) return 0f
        val level = session.recorder.currentAmplitude().coerceIn(0f, 1f)
        updateState(session, session.state.copy(measuredLevel = level))
        level
    }

    internal fun stop(sessionId: Long): AudioSessionStopResult = synchronized(lock) {
        val session = activeSession?.takeIf { it.state.sessionId == sessionId }
            ?: return AudioSessionStopResult.Stale
        if (session.stopResult != null) return AudioSessionStopResult.AlreadyStopped
        if (session.state.phase == AudioSessionPhase.PROCESSING) {
            return AudioSessionStopResult.AlreadyStopped
        }
        val result = session.recorder.stopAndRead().fold(
            onSuccess = { AudioSessionStopResult.Stopped(it) },
            onFailure = { AudioSessionStopResult.Failed(it) },
        )
        session.stopResult = result
        endTrace(session.state)
        updateState(
            session,
            session.state.copy(
                phase = AudioSessionPhase.PROCESSING,
                pausedAtMs = null,
                measuredLevel = 0f,
            ),
        )
        beginTrace(session.state)
        result
    }

    internal fun cancel(sessionId: Long, reason: AudioSessionInvalidation): Boolean {
        @Suppress("UNUSED_VARIABLE") val recordedReason = reason
        val session = synchronized(lock) {
            val current = activeSession?.takeIf { it.state.sessionId == sessionId } ?: return false
            activeSession = null
            _state.value = null
            nextSessionId += 1
            current
        }
        session.recorder.cancel()
        endTrace(session.state)
        return true
    }

    internal fun complete(sessionId: Long): Boolean = synchronized(lock) {
        val session = activeSession?.takeIf { it.state.sessionId == sessionId } ?: return false
        if (session.state.phase != AudioSessionPhase.PROCESSING) return false
        endTrace(session.state)
        activeSession = null
        _state.value = null
        true
    }

    private fun updateState(session: ActiveSession, state: AudioSessionState) {
        session.state = state
        _state.value = state
    }

    private fun beginTrace(state: AudioSessionState) {
        runCatching {
            traceSink.beginAsyncSection(traceLabel(state), traceCookie(state.sessionId))
        }
    }

    private fun endTrace(state: AudioSessionState) {
        runCatching {
            traceSink.endAsyncSection(traceLabel(state), traceCookie(state.sessionId))
        }
    }

    private fun traceLabel(state: AudioSessionState): String = when (state.owner) {
        AudioSessionOwner.DICTATION -> when (state.phase) {
            AudioSessionPhase.RECORDING,
            AudioSessionPhase.PAUSED,
            -> OwnkeyBatteryTraceLabels.VoiceDictationRecording

            AudioSessionPhase.PROCESSING -> OwnkeyBatteryTraceLabels.VoiceDictationProcessing
        }

        AudioSessionOwner.VOICE_REWRITE -> when (state.phase) {
            AudioSessionPhase.RECORDING,
            AudioSessionPhase.PAUSED,
            -> OwnkeyBatteryTraceLabels.VoiceRewriteRecording

            AudioSessionPhase.PROCESSING -> OwnkeyBatteryTraceLabels.VoiceRewriteProcessing
        }
    }

    private fun traceCookie(sessionId: Long): Int = (sessionId and Int.MAX_VALUE.toLong()).toInt()
}

class AudioSessionLease internal constructor(
    private val coordinator: AudioSessionCoordinator,
    val sessionId: Long,
) {
    val isCurrent: Boolean get() = coordinator.isCurrent(sessionId)
    val state: AudioSessionState? get() = coordinator.state(sessionId)

    fun pause(): Boolean = coordinator.pause(sessionId)
    fun resume(): Boolean = coordinator.resume(sessionId)
    fun sampleLevel(): Float = coordinator.sampleLevel(sessionId)
    fun stop(): AudioSessionStopResult = coordinator.stop(sessionId)
    fun cancel(reason: AudioSessionInvalidation = AudioSessionInvalidation.OWNER_CANCELLED): Boolean =
        coordinator.cancel(sessionId, reason)
    fun complete(): Boolean = coordinator.complete(sessionId)
}
