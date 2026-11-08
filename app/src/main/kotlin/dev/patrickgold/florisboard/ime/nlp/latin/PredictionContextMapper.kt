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

import org.florisboard.lib.prediction.PredictionContext
import org.florisboard.lib.prediction.ngram.NgramModel
import org.florisboard.lib.prediction.ngram.NgramText

/**
 * Maps the editor text before the cursor to the [PredictionContext] of the prediction engine.
 *
 * Tokens follow the n-gram training rules ([NgramModel.contextOf], `tools/lm/lmtool/textnorm.py`): a word is a run of
 * letters with internal apostrophes, normalised with [NgramModel.normalizeToken] (lower case, straight apostrophes);
 * hyphens and other punctuation separate words; runs that contain a digit or underscore are dropped without breaking
 * the sentence. A sentence starts at the start of the field, after `!`, `?` or a single `.` (an ellipsis `..`, `...`
 * or `…` continues the sentence, as in [NgramModel.contextOf]) and after a line break (the training data starts a
 * sentence at every line).
 *
 * The result holds the words of the current sentence before the input (at most [MaxSentenceWords]; if there are
 * more, the sentence start is no longer known), whether the sentence start precedes them, and up to [MaxHintWords]
 * earlier words as language evidence. Pure; no Android APIs.
 */
object PredictionContextMapper {
    /** Words of the current sentence kept as n-gram context (the engine reads the last two plus six for evidence). */
    const val MaxSentenceWords = 12

    /** Earlier words kept as language evidence. */
    const val MaxHintWords = 12

    /**
     * @param textBeforeSelection Editor text before the cursor, including the word being typed.
     * @param rawInput The word being typed (composing text or current word, trimmed), or blank for next-word
     *  prediction. When blank, every word of [textBeforeSelection] is context, also a word that ends directly at the
     *  cursor (a phantom space after an accepted suggestion: the next tap starts a new word).
     * @param textStartsAtFieldStart True when [textBeforeSelection] starts at the start of the field (editor offset
     *  0). Otherwise the text is a window into a longer field: a word cut by the window start is dropped and the
     *  sentence start is only known from punctuation.
     */
    fun map(textBeforeSelection: CharSequence, rawInput: String, textStartsAtFieldStart: Boolean): PredictionContext {
        val text = contextText(textBeforeSelection, rawInput)
        val sentences = ArrayList<Sentence>()
        var current = Sentence(startsAtBoundary = textStartsAtFieldStart)
        sentences.add(current)
        var gap = Gap()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (NgramText.isWordChar(c)) {
                var j = i + 1
                while (j < n && NgramText.isWordChar(text[j])) j++
                val words = NgramText.wordsOfRun(text.substring(i, j))
                val cutByWindow = i == 0 && !textStartsAtFieldStart
                if (words.isNotEmpty() && !cutByWindow) {
                    if (gap.isBoundary && (current.words.isNotEmpty() || i > 0)) {
                        current = Sentence(startsAtBoundary = true)
                        sentences.add(current)
                    }
                    words.forEach { current.words.add(NgramModel.normalizeToken(it)) }
                    gap = Gap()
                }
                i = j
                continue
            }
            gap.add(c)
            i++
        }
        if (gap.isBoundary && (current.words.isNotEmpty() || n > 0)) {
            current = Sentence(startsAtBoundary = true)
            sentences.add(current)
        }

        val sentenceWords = current.words
        val truncated = sentenceWords.size > MaxSentenceWords
        val words = if (truncated) sentenceWords.takeLast(MaxSentenceWords) else sentenceWords.toList()
        val earlier = sentences.dropLast(1).flatMap { it.words } +
            if (truncated) sentenceWords.subList(0, sentenceWords.size - MaxSentenceWords) else emptyList()
        return PredictionContext(
            words = words,
            startsSentence = current.startsAtBoundary && !truncated,
            languageHintWords = earlier.takeLast(MaxHintWords),
        )
    }

    /**
     * [textBeforeSelection] without the word being typed: without [rawInput] when the text ends with it (ignoring
     * case), otherwise without the trailing word characters (the cursor is inside the word). Unchanged for a blank
     * [rawInput].
     */
    fun contextText(textBeforeSelection: CharSequence, rawInput: String): String {
        val text = textBeforeSelection.toString()
        val input = rawInput.trim()
        if (input.isEmpty()) return text
        if (text.endsWith(input, ignoreCase = true)) return text.substring(0, text.length - input.length)
        var end = text.length
        while (end > 0 && NgramText.isWordChar(text[end - 1])) end--
        return text.substring(0, end)
    }

    private class Sentence(val startsAtBoundary: Boolean) {
        val words = ArrayList<String>()
    }

    /** Punctuation between two words; same boundary rule as [NgramModel.contextOf] plus line breaks. */
    private class Gap {
        private var bang = false
        private var newline = false
        private var ellipsis = false
        private var dots = 0
        private var maxDots = 0

        fun add(c: Char) {
            when (c) {
                '!', '?' -> bang = true
                '\n', '\r', ' ', ' ' -> newline = true
                '…' -> ellipsis = true
            }
            if (c == '.') {
                dots++
                if (dots > maxDots) maxDots = dots
            } else {
                dots = 0
            }
        }

        val isBoundary: Boolean get() = bang || newline || (maxDots == 1 && !ellipsis)
    }
}
