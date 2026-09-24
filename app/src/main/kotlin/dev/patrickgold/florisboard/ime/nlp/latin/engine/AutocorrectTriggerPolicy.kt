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

import dev.patrickgold.florisboard.ime.editor.InputAttributes

/**
 * Decides which typed characters may apply an autocorrection to the word before the cursor, and which tokens are
 * never corrected.
 *
 * Autocorrect only runs when a word is clearly finished: on space or on sentence punctuation. Apostrophes, hyphens,
 * digits, `@` and `/` continue a token (`isn't`, `e-mail`, `bart@example.com`), so correcting there would change a
 * word the user has not finished typing.
 */
object AutocorrectTriggerPolicy {
    private val SentencePunctuation = setOf('.', ',', '!', '?', ';', ':')

    /** Whether typing [text] (a single committed character or string) ends the current word for autocorrect. */
    fun isTrigger(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.length == 1) {
            val ch = text[0]
            return ch.isWhitespace() || ch in SentencePunctuation
        }
        return false
    }

    /**
     * Whether the whitespace-delimited token directly before the cursor may be autocorrected when [trigger] is typed.
     * Tokens that look like e-mail addresses, URLs, paths, handles or numbers are left alone, and so is a single
     * letter before a period ("i.e.", or "i." in a list).
     */
    fun isCorrectableToken(tokenBeforeCursor: String, trigger: String = " "): Boolean {
        val token = tokenBeforeCursor.trimStart('(', '"', '\'', '“', '‘', '[')
        if (token.isEmpty()) return false
        if (trigger == "." && token.length == 1) return false
        if (token.any { it.isDigit() || it == '@' || it == '/' || it == '\\' || it == '#' || it == '_' }) return false
        val lowercase = token.lowercase()
        if (lowercase.startsWith("www") || lowercase.startsWith("http")) return false
        // A dot inside the token (not at its end) means a domain, file name or abbreviation such as "e.g".
        val innerDot = token.dropLast(1).indexOf('.')
        if (innerDot >= 0) return false
        return true
    }

    /**
     * Whether the focused field allows autocorrect at all. Passwords, e-mail addresses, URLs and person names are
     * typed exactly as meant, and apps can opt out with `TYPE_TEXT_FLAG_NO_SUGGESTIONS`.
     */
    fun allowsField(
        variation: InputAttributes.Variation,
        flagTextNoSuggestions: Boolean,
        isRichInputEditor: Boolean,
    ): Boolean {
        if (!isRichInputEditor || flagTextNoSuggestions) return false
        return when (variation) {
            InputAttributes.Variation.PASSWORD,
            InputAttributes.Variation.VISIBLE_PASSWORD,
            InputAttributes.Variation.WEB_PASSWORD,
            InputAttributes.Variation.EMAIL_ADDRESS,
            InputAttributes.Variation.WEB_EMAIL_ADDRESS,
            InputAttributes.Variation.URI,
            InputAttributes.Variation.PERSON_NAME -> false
            else -> true
        }
    }

    /** The whitespace-delimited token that ends at the cursor. */
    fun tokenBeforeCursor(textBeforeSelection: CharSequence): String {
        var start = textBeforeSelection.length
        while (start > 0 && !textBeforeSelection[start - 1].isWhitespace()) start--
        return textBeforeSelection.subSequence(start, textBeforeSelection.length).toString()
    }
}
