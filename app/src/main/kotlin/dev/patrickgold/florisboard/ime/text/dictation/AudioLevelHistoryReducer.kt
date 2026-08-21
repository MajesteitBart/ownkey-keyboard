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

import kotlin.math.sqrt

data class AudioLevelHistoryConfig(
    val historySize: Int = 18,
    val baseline: Float = 0.08f,
    val noiseFloor: Float = 0.02f,
    val attackAlpha: Float = 0.72f,
    val releaseAlpha: Float = 0.24f,
    val reducedMotionSampleStride: Int = 4,
) {
    init {
        require(historySize in 16..20)
        require(baseline in 0f..1f)
        require(noiseFloor in 0f..<1f)
        require(attackAlpha in 0f..1f)
        require(releaseAlpha in 0f..1f)
        require(reducedMotionSampleStride >= 1)
    }
}

data class AudioLevelHistoryState(
    val levels: List<Float>,
    val smoothedLevel: Float,
    val isPaused: Boolean,
    val sampleIndex: Long,
)

/**
 * Pure reducer for the recorder-driven waveform.
 *
 * The defaults come from the T-001 probe: 18 samples at roughly 20 Hz, a 0.02 noise floor,
 * approximately 70 ms attack and 200 ms release. Reduced motion consumes the same real samples at
 * 5 Hz and updates a stationary level instead of creating travelling history.
 */
class AudioLevelHistoryReducer(
    val config: AudioLevelHistoryConfig = AudioLevelHistoryConfig(),
) {
    fun initialState(): AudioLevelHistoryState = baselineState(isPaused = false)

    fun sample(
        state: AudioLevelHistoryState,
        measuredAmplitude: Float,
        reducedMotion: Boolean = false,
    ): AudioLevelHistoryState {
        if (state.isPaused) return state
        val sampleIndex = state.sampleIndex + 1
        if (reducedMotion && sampleIndex % config.reducedMotionSampleStride != 0L) {
            return state.copy(sampleIndex = sampleIndex)
        }

        val normalized = (
            (measuredAmplitude.coerceIn(0f, 1f) - config.noiseFloor) /
                (1f - config.noiseFloor)
            ).coerceIn(0f, 1f)
        val perceptual = config.baseline + sqrt(normalized) * (1f - config.baseline)
        val alpha = if (perceptual > state.smoothedLevel) config.attackAlpha else config.releaseAlpha
        val smoothed = (state.smoothedLevel + (perceptual - state.smoothedLevel) * alpha)
            .coerceIn(config.baseline, 1f)
        val levels = if (reducedMotion) {
            List(config.historySize) { smoothed }
        } else {
            state.levels.drop(1) + smoothed
        }
        return state.copy(levels = levels, smoothedLevel = smoothed, sampleIndex = sampleIndex)
    }

    fun pause(state: AudioLevelHistoryState): AudioLevelHistoryState =
        baselineState(isPaused = true, sampleIndex = state.sampleIndex)

    fun resume(state: AudioLevelHistoryState): AudioLevelHistoryState =
        baselineState(isPaused = false, sampleIndex = state.sampleIndex)

    fun reset(): AudioLevelHistoryState = initialState()

    private fun baselineState(isPaused: Boolean, sampleIndex: Long = 0L) = AudioLevelHistoryState(
        levels = List(config.historySize) { config.baseline },
        smoothedLevel = config.baseline,
        isPaused = isPaused,
        sampleIndex = sampleIndex,
    )
}
