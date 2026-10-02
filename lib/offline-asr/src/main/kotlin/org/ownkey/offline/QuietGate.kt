package org.ownkey.offline

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tells quiet audio from speech by level, measured against this recording's own speech and room noise, so
 * live dictation doesn't decode silence over and over. Decoding the same words with more and more silence
 * after them makes the result drift, and Orukeet sometimes invents a word in quiet audio. This never stops
 * or ends anything: the recording runs until the user stops it.
 */
class QuietGate {
    private val histogram = IntArray(LEVELS)
    private var frames = 0

    /**
     * The speech level, which only goes up: in a long pause the quiet frames outnumber the spoken ones, and
     * the percentile alone would take room noise for speech.
     */
    private var speech = -(LEVELS - 1)

    /** 100 ms frames of quiet audio in a row, up to the samples passed last. */
    private var quietFrames = 0

    /**
     * Whether [samples] can be skipped: every 100 ms frame is at room-noise level, and at least 1.6 s of quiet
     * audio came before them. The first stretch after speech is always decoded, because the soft end of the
     * last word can be in it and the decoder needs it to write that word out. Then learns from the samples.
     * No samples at all is as quiet as the audio before.
     */
    fun isQuiet(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return isEstablished() && quietFrames >= QUIET_BEFORE_SKIP
        val levels = frameLevels(samples)
        val quiet = isEstablished() && levels.all { it < threshold() }
        levels.forEach { histogram[(it + LEVELS - 1).coerceIn(0, LEVELS - 1)]++ }
        frames += levels.size
        if (frames >= MIN_FRAMES) speech = max(speech, percentile(SPEECH))
        val skip = quiet && quietFrames >= QUIET_BEFORE_SKIP
        quietFrames = if (quiet) quietFrames + levels.size else 0
        return skip
    }

    /** Speech and noise are known once there are two seconds of audio with a clear difference between them. */
    private fun isEstablished(): Boolean = frames >= MIN_FRAMES && speech - percentile(NOISE) >= MIN_RANGE_DB

    /** Just above room noise: soft syllables at the end of a sentence still count as speech. */
    private fun threshold(): Int {
        val noise = percentile(NOISE)
        return noise + max(4, ((speech - noise) * 0.2).toInt())
    }

    private fun percentile(fraction: Double): Int {
        val target = (frames * fraction).toInt()
        var seen = 0
        for (i in histogram.indices) {
            seen += histogram[i]
            if (seen > target) return i - (LEVELS - 1)
        }
        return 0
    }

    private companion object {
        /** Levels from -120 dBFS to 0 dBFS in 1 dB steps. */
        const val LEVELS = 121
        const val FRAME = 1600
        const val SPEECH = 0.95
        const val NOISE = 0.2
        const val MIN_RANGE_DB = 12
        const val MIN_FRAMES = 20
        const val QUIET_BEFORE_SKIP = 16

        /** The level of each 100 ms frame. A shorter rest at the end is a frame of its own, so nothing goes unheard. */
        fun frameLevels(samples: FloatArray): List<Int> = (samples.indices step FRAME).map { from ->
            val to = minOf(from + FRAME, samples.size)
            var energy = 0.0
            for (i in from until to) energy += samples[i] * samples[i]
            (20 * log10(sqrt(energy / (to - from)) + 1e-6)).toInt().coerceIn(-(LEVELS - 1), 0)
        }
    }
}
