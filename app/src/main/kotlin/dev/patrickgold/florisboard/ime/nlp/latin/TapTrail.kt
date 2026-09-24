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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap

/**
 * Where the letters of the word being typed were tapped, in key widths, for the touch model of autocorrect.
 *
 * The keyboard records each tapped letter; backspace removes the last one and word boundaries clear the trail. The
 * taps are only handed out when the most recent ones spell exactly the word being scored, so text that came from
 * glide typing, paste or a hardware keyboard never gets someone else's taps. Kept in memory for the current word
 * only; never logged or stored.
 */
object TapTrail {
    private const val MaxTaps = 48

    private val chars = StringBuilder()
    private val taps = ArrayList<LatinTap>()

    /** A tapped character at view position ([x], [y]) in pixels. Characters outside words are ignored. */
    fun record(code: Int, x: Float, y: Float) {
        val tap = KeyboardGeometrySource.toKeyUnits(x, y) ?: LatinTap(Double.NaN, Double.NaN)
        add(code, tap)
    }

    /** A character entered without a known position, such as an accent chosen from a popup. */
    fun recordWithoutPosition(code: Int) = add(code, LatinTap(Double.NaN, Double.NaN))

    @Synchronized
    fun removeLast() {
        if (chars.isEmpty()) return
        chars.setLength(chars.length - 1)
        taps.removeAt(taps.lastIndex)
    }

    @Synchronized
    fun clear() {
        chars.setLength(0)
        taps.clear()
    }

    /** The taps for [word] when the most recent taps spell it exactly (ignoring case), or null. */
    @Synchronized
    internal fun tapsFor(word: String): List<LatinTap>? {
        if (word.isEmpty() || word.length > chars.length) return null
        val start = chars.length - word.length
        for (i in word.indices) {
            if (!chars[start + i].equals(normalize(word[i]), ignoreCase = true)) return null
        }
        return taps.subList(start, taps.size).toList()
    }

    @Synchronized
    private fun add(code: Int, tap: LatinTap) {
        val ch = Character.toChars(code).singleOrNull() ?: return
        if (!ch.isLetter() && ch != '\'' && ch != '’' && ch != '-') return
        if (chars.length == MaxTaps) {
            chars.deleteCharAt(0)
            taps.removeAt(0)
        }
        chars.append(normalize(ch))
        taps.add(tap)
    }

    private fun normalize(ch: Char): Char = if (ch == '’') '\'' else ch.lowercaseChar()
}
