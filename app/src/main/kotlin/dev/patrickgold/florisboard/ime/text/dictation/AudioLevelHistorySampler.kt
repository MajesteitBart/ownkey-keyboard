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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single 20 Hz measured-level poller behind the shared recording waveform.
 *
 * Ordinary dictation and voice rewrite both render this history, so exactly one owner samples the
 * recorder and the two modes can never disagree about the displayed level. Every value comes from
 * [AudioSessionCoordinator.sampleLevel]; there is no clock-driven, random, or prerecorded source, so
 * a mock recorder with no measured input stays at the silence baseline.
 */
class AudioLevelHistorySampler(
    private val coordinator: AudioSessionCoordinator,
    private val scope: CoroutineScope,
    private val reducer: AudioLevelHistoryReducer = AudioLevelHistoryReducer(),
    private val sampleIntervalMs: Long = 50L,
    private val reducedMotionProvider: () -> Boolean = { false },
) {
    private val _state = MutableStateFlow(reducer.initialState())
    val state: StateFlow<AudioLevelHistoryState> = _state

    init {
        scope.launch {
            coordinator.state
                .map { session -> session?.let { it.sessionId to it.phase } }
                .distinctUntilChanged()
                .collectLatest { session ->
                when (session?.second) {
                    AudioSessionPhase.RECORDING -> {
                        _state.value = reducer.resume(_state.value)
                        val sessionId = session.first
                        val reducedMotion = reducedMotionProvider()
                        while (isActive && coordinator.isCurrent(sessionId)) {
                            val measured = coordinator.sampleLevel(sessionId)
                            _state.value = reducer.sample(_state.value, measured, reducedMotion)
                            delay(sampleIntervalMs)
                        }
                    }
                    // Pausing stops sampling and settles the bars to a dim low baseline rather than
                    // freezing a loud shape that could imply continued listening.
                    AudioSessionPhase.PAUSED -> _state.value = reducer.pause(_state.value)
                    AudioSessionPhase.PROCESSING, null -> _state.value = reducer.reset()
                }
            }
        }
    }

}
