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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

import kotlin.math.hypot

/**
 * Key center positions of the letter keys, in key widths, plus the row height in key widths. Used to price
 * substitution typos by how far apart the intended and the typed key are.
 */
class KeyGeometry(
    private val centers: Map<Char, Pair<Double, Double>>,
    private val rowHeight: Double = PhoneRowHeight,
) {
    /**
     * Distance between two keys in key units: horizontal steps count in key widths and vertical steps in rows, so
     * a neighbor on the next row is about as close as a neighbor on the same row. Null when either key is not on
     * the layout.
     */
    fun distance(a: Char, b: Char): Double? {
        val pa = centers[a.lowercaseChar()] ?: return null
        val pb = centers[b.lowercaseChar()] ?: return null
        return hypot(pa.first - pb.first, (pa.second - pb.second) / rowHeight)
    }

    fun center(ch: Char): Pair<Double, Double>? = centers[ch.lowercaseChar()]

    val size: Int get() = centers.size

    companion object {
        /** Row height relative to key width on a typical phone layout. */
        const val PhoneRowHeight = 1.4

        /** Standard phone QWERTY: row offsets of 0, 0.5 and 1.5 key widths. Used until the real layout is known. */
        val QwertyPhone: KeyGeometry = rows(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"), listOf(0.0, 0.5, 1.5))

        fun rows(rows: List<String>, offsets: List<Double>, rowHeight: Double = PhoneRowHeight): KeyGeometry {
            val centers = HashMap<Char, Pair<Double, Double>>()
            rows.forEachIndexed { row, letters ->
                letters.forEachIndexed { index, ch ->
                    centers[ch] = (offsets[row] + index + 0.5) to (row + 0.5) * rowHeight
                }
            }
            return KeyGeometry(centers, rowHeight)
        }

        /**
         * Builds a geometry from measured key centers in pixels, normalized by the median key width. Returns null
         * when too few letter keys have been laid out yet.
         */
        fun fromPixels(centers: Map<Char, Pair<Float, Float>>, keyWidths: List<Float>, keyHeights: List<Float>): KeyGeometry? {
            if (centers.size < 10) return null
            val unit = median(keyWidths) ?: return null
            val height = median(keyHeights) ?: return null
            return KeyGeometry(centers.mapValues { (_, c) -> c.first / unit to c.second / unit }, height / unit)
        }

        private fun median(values: List<Float>): Double? {
            val sorted = values.filter { it > 0f }.sorted()
            if (sorted.isEmpty()) return null
            return sorted[sorted.size / 2].toDouble()
        }
    }
}
