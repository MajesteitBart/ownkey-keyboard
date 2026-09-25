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

    /** Signature of the keys and the geometry measured from them. */
    private class Cached(val signature: Long, val measured: KeyGeometry.Measured)

    @Volatile
    private var cached: Cached? = null

    fun update(characterKeys: List<TextKey>) {
        // Every recomposition hands over a new list, for example when shift turns off after the first letter.
        // Only other key objects make another layout.
        if (sameKeys(characterKeys, keys)) return
        // Taps recorded on the previous layout are in other coordinates.
        TapTrail.clear()
        keys = characterKeys
    }

    fun current(): KeyGeometry? = measured()?.geometry

    private fun measured(): KeyGeometry.Measured? {
        val snapshot = keys
        if (snapshot.isEmpty()) return null
        val signature = signatureOf(snapshot)
        cached?.let { if (it.signature == signature) return it.measured }
        val centers = HashMap<Char, Pair<Float, Float>>()
        for (key in snapshot) {
            // The evaluated data: letter keys behind a case or shift selector have no code of their own.
            val code = key.computedData.code
            if (code <= 0 || !Character.isLetter(code)) continue
            val bounds = key.visibleBounds
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            if (width <= 0f || height <= 0f) continue
            val ch = Character.toChars(code).singleOrNull()?.lowercaseChar() ?: continue
            centers[ch] = (bounds.left + width / 2f) to (bounds.top + height / 2f)
        }
        val measured = KeyGeometry.fromPixels(centers) ?: return null
        cached = Cached(signature, measured)
        return measured
    }

    /** A touch point of the keyboard view in pixels, in the key-pitch units of [current]; null before layout. */
    internal fun toKeyUnits(x: Float, y: Float): LatinTap? {
        val unit = measured()?.unit ?: return null
        if (unit <= 0.0) return null
        return LatinTap(x / unit, y / unit)
    }

    private fun sameKeys(a: List<TextKey>, b: List<TextKey>): Boolean {
        if (a === b) return true
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i] !== b[i]) return false
        }
        return true
    }

    private fun signatureOf(keys: List<TextKey>): Long {
        var signature = System.identityHashCode(keys).toLong()
        for (key in keys) {
            val bounds = key.visibleBounds
            signature = signature * 31 + (bounds.left * 7 + bounds.top * 13 + bounds.right * 17 + bounds.bottom).toLong()
            signature = signature * 31 + Character.toLowerCase(key.computedData.code)
        }
        return signature
    }
}
