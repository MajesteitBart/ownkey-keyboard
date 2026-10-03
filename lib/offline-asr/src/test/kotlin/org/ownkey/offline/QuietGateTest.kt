package org.ownkey.offline

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

class QuietGateTest {
    private val random = Random(7)

    /** Speech-like tone at roughly [rms] (linear), [seconds] long. */
    private fun tone(rms: Double, seconds: Double) = FloatArray((seconds * 16000).toInt()) {
        (rms * 1.414 * sin(2 * PI * 220 * it / 16000)).toFloat()
    }

    /** Room noise at roughly [rms] (linear). */
    private fun noise(rms: Double, seconds: Double) = FloatArray((seconds * 16000).toInt()) {
        ((random.nextDouble() * 2 - 1) * rms * 1.73).toFloat()
    }

    @Test fun `nothing counts as quiet before speech and noise are known`() {
        val gate = QuietGate()
        assertFalse(gate.isQuiet(noise(0.003, 1.0)))
        assertFalse(gate.isQuiet(noise(0.003, 1.0)))
    }

    @Test fun `room noise is skipped once speech has been heard, and speech is not`() {
        val gate = QuietGate()
        assertFalse(gate.isQuiet(tone(0.07, 2.0)))
        assertFalse(gate.isQuiet(noise(0.003, 1.0)))
        // The first 1.6 s of quiet after speech are still decoded, for the end of the last word.
        assertFalse(gate.isQuiet(noise(0.003, 0.8)))
        assertFalse(gate.isQuiet(noise(0.003, 0.8)))
        assertTrue(gate.isQuiet(noise(0.003, 0.8)))
        assertFalse(gate.isQuiet(tone(0.07, 0.8)))
        assertFalse(gate.isQuiet(noise(0.003, 0.8)))
        assertFalse(gate.isQuiet(noise(0.003, 0.8)))
        assertTrue(gate.isQuiet(noise(0.003, 0.8)))
    }

    @Test fun `soft speech is not quiet`() {
        val gate = QuietGate()
        gate.isQuiet(tone(0.07, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        assertFalse(gate.isQuiet(tone(0.008, 0.8)))
    }

    @Test fun `speech in the last few milliseconds is not quiet`() {
        val gate = QuietGate()
        gate.isQuiet(tone(0.07, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        assertFalse(gate.isQuiet(noise(0.003, 0.8) + tone(0.07, 0.05)))
    }

    @Test fun `a long pause stays quiet`() {
        val gate = QuietGate()
        gate.isQuiet(tone(0.07, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        repeat(75) { assertTrue(gate.isQuiet(noise(0.003, 0.8)), "after ${4 + it * 0.8} s of quiet") }
        assertFalse(gate.isQuiet(tone(0.07, 0.8)))
    }

    @Test fun `no new samples are as quiet as the audio before them`() {
        val gate = QuietGate()
        gate.isQuiet(tone(0.07, 2.0))
        assertFalse(gate.isQuiet(FloatArray(0)))
        gate.isQuiet(noise(0.003, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        assertTrue(gate.isQuiet(FloatArray(0)))
    }

    @Test fun `quiet audio with one spoken frame is not quiet`() {
        val gate = QuietGate()
        gate.isQuiet(tone(0.07, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        gate.isQuiet(noise(0.003, 2.0))
        assertFalse(gate.isQuiet(noise(0.003, 0.7) + tone(0.07, 0.1)))
    }
}
