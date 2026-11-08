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

package org.florisboard.lib.prediction

import kotlin.math.exp
import kotlin.math.ln

/** A touch point in key-pitch units (x divided by the horizontal key pitch, y by the vertical row pitch). */
data class TouchPoint(val x: Double, val y: Double)

/**
 * Keyboard geometry and the spatial error model for the 26 letter keys `a`-`z`. Other letter keys (`ä`, `ö`, `ß`,
 * `é`, ...) are not modelled: input containing them is matched on its key form without spatial costs (see
 * [PredictionEngine]). Use real key centres ([fromKeyCentres]) for anything but a plain QWERTY layout.
 *
 * Key centres are in key-pitch units: one unit is the centre-to-centre distance of neighbouring keys in a row
 * horizontally and of neighbouring rows vertically, the unit used by `tools/kbbench` for tap noise.
 *
 * Two channels are provided:
 * - **Key codes** ([keyCost]): `P(typed t | intended k)` from an isotropic Gaussian of width [sigma] on the
 *   centre distance, normalised over the letters and mixed with a uniform [floor].
 * - **Touch points** ([touchCosts]): for a measured touch `p`, the Gaussian likelihood `N(p; centre_k, sigmaTouch)`
 *   normalised over the letters (same floor). Dividing by the per-touch normaliser does not change the ranking of
 *   words because every alignment explains each touch exactly once; it keeps costs on the same scale as the
 *   key-code channel so one set of edit costs serves both.
 *
 * Costs are negative natural logs. Instances are immutable.
 */
class SpatialModel private constructor(
    private val cx: DoubleArray,
    private val cy: DoubleArray,
    val sigma: Double,
    val sigmaTouch: Double,
    val floor: Double,
) {
    /** `cost[k * 26 + t] = -ln P(t | k)`. */
    private val keyCosts = FloatArray(KEYS * KEYS)

    init {
        val w = DoubleArray(KEYS)
        for (k in 0 until KEYS) {
            var sum = 0.0
            for (t in 0 until KEYS) {
                val dx = cx[t] - cx[k]
                val dy = cy[t] - cy[k]
                w[t] = exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))
                sum += w[t]
            }
            for (t in 0 until KEYS) {
                val p = (1 - KEYS * floor) * w[t] / sum + floor
                keyCosts[k * KEYS + t] = (-ln(p)).toFloat()
            }
        }
    }

    /** Cost of the far-substitution floor, `-ln(floor)`. */
    val floorCost: Float = (-ln(floor)).toFloat()

    /** Centre of letter [c] in key-pitch units. */
    fun centre(c: Char): TouchPoint {
        val k = keyIndex(c)
        require(k >= 0) { "not a letter key: $c" }
        return TouchPoint(cx[k], cy[k])
    }

    /** `-ln P(typed | intended)` for two letter key indices (see [keyIndex]). */
    fun keyCost(intended: Int, typed: Int): Float = keyCosts[intended * KEYS + typed]

    /** `ln P(typed | intended)` for two letters; non-letters get the floor. */
    fun logChannel(typed: Char, intended: Char): Double {
        val t = keyIndex(typed)
        val k = keyIndex(intended)
        if (t < 0 || k < 0) return if (typed == intended) 0.0 else -floorCost.toDouble()
        return -keyCost(k, t).toDouble()
    }

    /** Writes `-ln P(key k | touch)` for all 26 keys into `out[offset + k]`. */
    fun touchCosts(touch: TouchPoint, out: FloatArray, offset: Int = 0) {
        val ll = DoubleArray(KEYS)
        var max = Double.NEGATIVE_INFINITY
        for (k in 0 until KEYS) {
            val dx = touch.x - cx[k]
            val dy = touch.y - cy[k]
            ll[k] = -(dx * dx + dy * dy) / (2 * sigmaTouch * sigmaTouch)
            if (ll[k] > max) max = ll[k]
        }
        var sum = 0.0
        for (k in 0 until KEYS) sum += exp(ll[k] - max)
        for (k in 0 until KEYS) {
            val p = (1 - KEYS * floor) * exp(ll[k] - max) / sum + floor
            out[offset + k] = (-ln(p)).toFloat()
        }
    }

    /** The letter whose centre is nearest to [touch]. */
    fun nearestKey(touch: TouchPoint): Char {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (k in 0 until KEYS) {
            val dx = touch.x - cx[k]
            val dy = touch.y - cy[k]
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = k
            }
        }
        return 'a' + best
    }

    /** Same geometry with other channel parameters. */
    fun withParameters(sigma: Double = this.sigma, sigmaTouch: Double = this.sigmaTouch, floor: Double = this.floor): SpatialModel {
        if (sigma == this.sigma && sigmaTouch == this.sigmaTouch && floor == this.floor) return this
        return SpatialModel(cx, cy, sigma, sigmaTouch, floor)
    }

    companion object {
        const val KEYS = 26
        const val DEFAULT_SIGMA = 0.45

        /**
         * Default touch-likelihood width: equal to [DEFAULT_SIGMA] on purpose. The benchmark's synthetic taps are
         * unbiased Gaussians (0.20 pitch) around the intended key centre, exactly what the touch model assumes, so
         * tuning this on the benchmark would make the touch channel overconfident for real, biased taps. Replace it
         * with a value fitted to real tap logs.
         */
        const val DEFAULT_SIGMA_TOUCH = DEFAULT_SIGMA
        const val DEFAULT_FLOOR = 1e-3

        private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        private val ROW_OFFSETS = doubleArrayOf(0.0, 0.5, 1.5)

        /** Key index 0..25 of a letter, or -1. */
        fun keyIndex(c: Char): Int = if (c in 'a'..'z') c - 'a' else -1

        /** Standard phone QWERTY: rows offset by 0, 0.5 and 1.5 keys, one row pitch apart. */
        fun qwerty(
            sigma: Double = DEFAULT_SIGMA,
            sigmaTouch: Double = DEFAULT_SIGMA_TOUCH,
            floor: Double = DEFAULT_FLOOR,
        ): SpatialModel {
            val cx = DoubleArray(KEYS)
            val cy = DoubleArray(KEYS)
            ROWS.forEachIndexed { r, row ->
                row.forEachIndexed { i, c ->
                    cx[c - 'a'] = ROW_OFFSETS[r] + i
                    cy[c - 'a'] = r.toDouble()
                }
            }
            return SpatialModel(cx, cy, sigma, sigmaTouch, floor)
        }

        /**
         * Geometry from measured key centres in pixels (for example `tools/kbbench/calib/ownkey.json`), converted
         * to key-pitch units with the horizontal and vertical pitch in pixels. All 26 letters are required.
         */
        fun fromKeyCentres(
            centresPx: Map<Char, Pair<Double, Double>>,
            pitchX: Double,
            pitchY: Double,
            sigma: Double = DEFAULT_SIGMA,
            sigmaTouch: Double = DEFAULT_SIGMA_TOUCH,
            floor: Double = DEFAULT_FLOOR,
        ): SpatialModel {
            val cx = DoubleArray(KEYS)
            val cy = DoubleArray(KEYS)
            for (k in 0 until KEYS) {
                val c = 'a' + k
                val (x, y) = requireNotNull(centresPx[c]) { "missing key centre for '$c'" }
                cx[k] = x / pitchX
                cy[k] = y / pitchY
            }
            return SpatialModel(cx, cy, sigma, sigmaTouch, floor)
        }
    }
}
