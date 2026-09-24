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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.nlp.latin.engine.KeyGeometry
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey

/**
 * Key positions of the character keyboard currently on screen, for pricing typos by key distance. The keyboard
 * layout registers its keys; bounds are only known after layout, so the geometry is built lazily and cached until
 * the keys or their bounds change. Falls back to [KeyGeometry.QwertyPhone] until then.
 */
object KeyboardGeometrySource {
    @Volatile
    private var keys: List<TextKey> = emptyList()

    /** Signature of the keys, the geometry built from them, and its unit: the median key width in pixels. */
    private class Cached(val signature: Long, val geometry: KeyGeometry, val unit: Float)

    @Volatile
    private var cached: Cached? = null

    fun update(characterKeys: List<TextKey>) {
        // Taps recorded on the previous layout are in other coordinates.
        if (characterKeys !== keys) TapTrail.clear()
        keys = characterKeys
    }

    fun current(): KeyGeometry? {
        val snapshot = keys
        if (snapshot.isEmpty()) return null
        val signature = signatureOf(snapshot)
        cached?.let { if (it.signature == signature) return it.geometry }
        val centers = HashMap<Char, Pair<Float, Float>>()
        val widths = ArrayList<Float>()
        val heights = ArrayList<Float>()
        for (key in snapshot) {
            val code = (key.data as? KeyData)?.code ?: continue
            if (code <= 0 || !Character.isLetter(code)) continue
            val bounds = key.visibleBounds
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            if (width <= 0f || height <= 0f) continue
            val ch = Character.toChars(code).singleOrNull()?.lowercaseChar() ?: continue
            centers[ch] = (bounds.left + width / 2f) to (bounds.top + height / 2f)
            widths.add(width)
            heights.add(height)
        }
        val geometry = KeyGeometry.fromPixels(centers, widths, heights) ?: return null
        cached = Cached(signature, geometry, widths.sorted()[widths.size / 2])
        return geometry
    }

    /** A touch point of the keyboard view in pixels, in the key-width units of [current]; null before layout. */
    internal fun toKeyUnits(x: Float, y: Float): LatinTap? {
        current() ?: return null
        val unit = cached?.unit ?: return null
        if (unit <= 0f) return null
        return LatinTap(x / unit.toDouble(), y / unit.toDouble())
    }

    private fun signatureOf(keys: List<TextKey>): Long {
        var signature = System.identityHashCode(keys).toLong()
        for (key in keys) {
            val bounds = key.visibleBounds
            signature = signature * 31 + (bounds.left * 7 + bounds.top * 13 + bounds.right * 17 + bounds.bottom).toLong()
        }
        return signature
    }
}
