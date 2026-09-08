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

/**
 * Joins a dictated transcript to the text around the cursor.
 *
 * Dictation inserts whole phrases, so unlike an accepted suggestion it must supply its own word
 * boundary: `keyboard.` followed by `When I` becomes `keyboard. When I`, not `keyboard.When I`.
 * The rules are deliberately narrow. A space is added only between two things that are visibly
 * word-like on both sides; existing whitespace, punctuation that attaches to its neighbour,
 * opening brackets or quotes, a straight quote after the cursor (far more often closing than
 * opening), and scripts that do not separate words with spaces all yield no change. When the
 * surrounding text is unavailable it looks empty, and empty context adds nothing, so the fallback
 * is the previous behaviour rather than a guessed space.
 *
 * Only the transcript is ever modified; the surrounding text is read, never edited.
 */
object DictationInsertionSpacing {
    private const val AttachesToPrevious = ".,;:!?)]}»”’…%"
    private const val OpensGroup = "([{«“‘"
    private const val StraightQuotes = "\"'"

    private val noSpaceScripts = setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI,
        Character.UnicodeScript.LAO,
        Character.UnicodeScript.KHMER,
        Character.UnicodeScript.MYANMAR,
        Character.UnicodeScript.TIBETAN,
    )

    fun join(transcript: String, textBefore: String, textAfter: String): String {
        if (transcript.isEmpty()) return transcript
        return leadingSeparator(textBefore, transcript) + transcript + trailingSeparator(transcript, textAfter)
    }

    private fun leadingSeparator(textBefore: String, transcript: String): String {
        if (textBefore.isEmpty()) return ""
        val previous = textBefore.last()
        val first = transcript.first()
        return when {
            previous.isSpacing() || first.isSpacing() -> ""
            first in AttachesToPrevious -> ""
            previous in OpensGroup -> ""
            previous in StraightQuotes && isOpeningQuote(textBefore) -> ""
            previous.isNoSpaceScript() || first.isNoSpaceScript() -> ""
            else -> " "
        }
    }

    private fun trailingSeparator(transcript: String, textAfter: String): String {
        if (textAfter.isEmpty()) return ""
        val last = transcript.last()
        val next = textAfter.first()
        return when {
            last.isSpacing() || next.isSpacing() -> ""
            next in AttachesToPrevious || next in StraightQuotes -> ""
            last in OpensGroup -> ""
            last.isNoSpaceScript() || next.isNoSpaceScript() -> ""
            else -> " "
        }
    }

    /** A straight quote opens a group when it starts the text or follows whitespace or a bracket. */
    private fun isOpeningQuote(textBefore: String): Boolean {
        if (textBefore.length == 1) return true
        val beforeQuote = textBefore[textBefore.length - 2]
        return beforeQuote.isSpacing() || beforeQuote in OpensGroup
    }

    /** Java's whitespace test excludes the no-break space, which editors do emit. */
    private fun Char.isSpacing(): Boolean = isWhitespace() || this == '\u00A0'

    private fun Char.isNoSpaceScript(): Boolean =
        isLetter() && Character.UnicodeScript.of(code) in noSpaceScripts
}
