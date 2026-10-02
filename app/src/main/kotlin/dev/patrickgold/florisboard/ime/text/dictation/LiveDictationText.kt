/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

/**
 * The text one live dictation puts into the editor. Words that froze become committed text and never
 * change again; the live words after them are replaced on every preview. Both pass through the
 * speech dictionary's cleanup and get the same spacing ordinary dictation uses against the text around
 * the cursor. Plain logic, so it is testable without an editor.
 */
class LiveDictationText(
    private val textBefore: String,
    private val textAfter: String,
    private val clean: (String) -> String = { it },
) {
    private val committed = StringBuilder()
    private val raw = StringBuilder()

    /** Whether the last committed words were removed entirely by cleanup, for example as fillers. */
    private var lastPieceCleanedAway = true

    /** Everything committed so far, separators included. */
    val committedText: String get() = committed.toString()

    /** The words as recognized, before cleanup, for the fix flow. */
    val rawTranscript: String get() = raw.toString()

    var live: String = ""
        private set

    /**
     * Applies one preview. Returns the text to commit now (empty when nothing froze) and the live text
     * to show after it.
     */
    fun update(frozen: List<String>, liveWords: List<String>): Pair<String, String> {
        val commit = append(frozen)
        val piece = cleanPiece(liveWords)
        live = if (piece.isEmpty()) "" else DictationInsertionSpacing.join(piece, tail(), "")
        return commit to live
    }

    /** The final words after Stop. Returns the text to commit, including the space before the following text. */
    fun finish(frozen: List<String>): String {
        live = ""
        val commit = StringBuilder(append(frozen))
        if (committed.isNotEmpty()) {
            val last = committed.last().toString()
            val trailing = DictationInsertionSpacing.join(last, "", textAfter).substring(last.length)
            committed.append(trailing)
            commit.append(trailing)
        }
        return commit.toString()
    }

    private fun append(words: List<String>): String {
        if (words.isEmpty()) return ""
        // A mark decided after a pause comes first, and may arrive together with the words after it.
        val marks = words.takeWhile { word -> word.all { it in ATTACHED_MARKS } }
        if (marks.isNotEmpty() && marks.size < words.size) return append(marks) + append(words.drop(marks.size))
        val spoken = words.joinToString(" ")
        // A sentence mark decided after a pause arrives on its own and belongs to the word before it.
        val loneMark = spoken.all { it in ATTACHED_MARKS }
        if (raw.isNotEmpty() && spoken.first() !in ATTACHED_MARKS) raw.append(' ')
        raw.append(spoken)
        // Its words may have been cleaned away as fillers; then the mark has nothing to end.
        if (loneMark && lastPieceCleanedAway) return ""
        val piece = cleanPiece(words)
        if (!loneMark) lastPieceCleanedAway = piece.isEmpty()
        if (piece.isEmpty()) return ""
        val joined = DictationInsertionSpacing.join(piece, tail(), "")
        committed.append(joined)
        return joined
    }

    /**
     * Cleans one piece. A piece that continues a sentence is cleaned behind a neutral marker, because
     * the cleaner capitalizes whatever opens a text once a filler before it is removed.
     */
    private fun cleanPiece(words: List<String>): String {
        if (words.isEmpty()) return ""
        val text = words.joinToString(" ")
        // The first decode has no context, so its first word's casing is arbitrary. Like typing, a new
        // field or a new sentence starts with a capital; mid-sentence the model's casing stays.
        val previous = committed.trimEnd().lastOrNull() ?: textBefore.trimEnd().lastOrNull()
        if (previous == null || previous in SENTENCE_END) return capitalized(clean(text).trim())
        if (committed.isEmpty()) return clean(text).trim()
        val cleaned = clean("$CONTEXT $text")
        return if (cleaned.startsWith(CONTEXT)) cleaned.removePrefix(CONTEXT).trim() else clean(text).trim()
    }

    private fun capitalized(text: String): String {
        val first = text.firstOrNull() ?: return text
        return if (first.isLowerCase()) first.titlecase() + text.substring(1) else text
    }

    /** The last characters before the next piece, which decide its leading separator. */
    private fun tail(): String = (textBefore.takeLast(2) + committed.takeLast(2)).takeLast(2)

    private companion object {
        const val SENTENCE_END = ".!?…"
        const val ATTACHED_MARKS = ".,!?…;:"
        const val CONTEXT = ""
    }
}
