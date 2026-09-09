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

import kotlin.math.pow

/**
 * Calibration for the recorder-driven waveform.
 *
 * @property historySize Number of bars kept; at 20 Hz this is just under one second of speech.
 * @property baseline Silence level. Presenters treat it as "no input" and draw a flat row.
 * @property noiseFloor Measured amplitude below which a sample counts as silence. It gates before
 * any scaling, so room noise is never amplified into activity.
 * @property perceptualExponent Power curve applied above the noise floor. It lifts quiet speech
 * without flattening loud speech into a wall of full-height bars.
 * @property peakTarget Level the loudest recent input is scaled to. Phones apply their own
 * microphone gain, so a shout may arrive at a fifth of full scale on one device and clip on
 * another; scaling against a rolling peak lets the loudest thing heard fill the meter on any of
 * them while quieter speech keeps its proportion. The Windows overlay uses the same approach.
 * @property peakFloor Lowest value the rolling peak may take, which caps how much a quiet input
 * can be amplified (about five times) so a whisper does not masquerade as a shout. It sits just
 * below where a shout landed on a phone whose gain control reports it at a fifth of full scale.
 * @property peakDecay Per-sample decay of the rolling peak; at 20 Hz `0.994` forgets about
 * ninety percent of a shout in roughly twenty seconds, so the meter re-sensitises to speech.
 * @property attackAlpha Per-sample step towards a louder level. Near-instant, so each bar carries
 * the syllable that produced it.
 * @property releaseAlpha Per-sample step towards a quieter level. Kept short so a loud syllable
 * decays within a bar or two instead of smearing into a hump.
 * @property reducedMotionSampleStride Every n-th sample is shown under reduced motion.
 */
data class AudioLevelHistoryConfig(
    val historySize: Int = 18,
    val baseline: Float = 0.08f,
    val noiseFloor: Float = 0.02f,
    val perceptualExponent: Float = 0.75f,
    val peakTarget: Float = 0.92f,
    val peakFloor: Float = 0.2f,
    val peakDecay: Float = 0.994f,
    val attackAlpha: Float = 0.9f,
    val releaseAlpha: Float = 0.55f,
    val reducedMotionSampleStride: Int = 4,
) {
    init {
        require(historySize in 16..20)
        require(baseline in 0f..1f)
        require(noiseFloor in 0f..<1f)
        require(perceptualExponent > 0f && perceptualExponent <= 1f)
        require(peakTarget in 0f..1f)
        require(peakFloor > 0f && peakFloor <= 1f)
        require(peakDecay in 0f..1f)
        require(attackAlpha in 0f..1f)
        require(releaseAlpha in 0f..1f)
        require(reducedMotionSampleStride >= 1)
    }
}

data class AudioLevelHistoryState(
    /** Oldest to newest; every value sits in [baseline]..1. */
    val levels: List<Float>,
    val smoothedLevel: Float,
    val isPaused: Boolean,
    val sampleIndex: Long,
    /** The silence level, so a presenter can tell rest from measured input without the config. */
    val baseline: Float,
    /** Rolling peak of the gated, curved input that the level is scaled against. */
    val peak: Float,
)

/**
 * Pure reducer for the recorder-driven waveform.
 *
 * Every level comes from a measured amplitude fed in by the caller: 18 samples at roughly 20 Hz,
 * gated by a noise floor, curved, scaled against a rolling peak of that same measured input, and
 * lightly smoothed. Nothing in here reads a clock, so silence settles to the baseline and stays
 * there, and a mock recorder with no measured input never creates activity. Reduced motion
 * consumes the same real samples at 5 Hz and updates a stationary level instead of creating
 * travelling history.
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

        val gated = (
            (measuredAmplitude.coerceIn(0f, 1f) - config.noiseFloor) /
                (1f - config.noiseFloor)
            ).coerceIn(0f, 1f)
        val curved = gated.pow(config.perceptualExponent)
        // The peak only ever comes from measured input; it decays between loud moments and never
        // drops below the floor, so silence divides to zero and quiet input is not over-amplified.
        val peak = maxOf(curved, state.peak * config.peakDecay, config.peakFloor)
        val scaled = (curved / peak * config.peakTarget).coerceIn(0f, 1f)
        val target = config.baseline + scaled * (1f - config.baseline)
        val alpha = if (target > state.smoothedLevel) config.attackAlpha else config.releaseAlpha
        val smoothed = (state.smoothedLevel + (target - state.smoothedLevel) * alpha)
            .coerceIn(config.baseline, 1f)
        val levels = if (reducedMotion) {
            List(config.historySize) { smoothed }
        } else {
            state.levels.drop(1) + smoothed
        }
        return state.copy(
            levels = levels,
            smoothedLevel = smoothed,
            sampleIndex = sampleIndex,
            peak = peak,
        )
    }

    fun pause(state: AudioLevelHistoryState): AudioLevelHistoryState =
        baselineState(isPaused = true, sampleIndex = state.sampleIndex, peak = state.peak)

    fun resume(state: AudioLevelHistoryState): AudioLevelHistoryState =
        baselineState(isPaused = false, sampleIndex = state.sampleIndex, peak = state.peak)

    fun reset(): AudioLevelHistoryState = initialState()

    private fun baselineState(
        isPaused: Boolean,
        sampleIndex: Long = 0L,
        peak: Float = config.peakFloor,
    ) = AudioLevelHistoryState(
        levels = List(config.historySize) { config.baseline },
        smoothedLevel = config.baseline,
        isPaused = isPaused,
        sampleIndex = sampleIndex,
        baseline = config.baseline,
        peak = peak,
    )
}
