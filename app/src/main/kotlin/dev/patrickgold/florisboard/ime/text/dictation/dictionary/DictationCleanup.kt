/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOutcome

sealed interface DictationCleanupResult {
    /** Commit this outcome. [rawTranscript] is the provider text before cleanup, when there was one. */
    data class Ready(val outcome: TranscriptionOutcome, val rawTranscript: String?) : DictationCleanupResult

    /** The transcript held nothing but filler words. Nothing is inserted and nothing failed. */
    data class OnlyFillers(val rawTranscript: String) : DictationCleanupResult
}

/**
 * Cleanup for ordinary dictation only. Spoken rewrite instructions never pass through here, so an
 * instruction that mentions a filler word keeps it.
 */
object OrdinaryDictationCleanup {
    fun apply(outcome: TranscriptionOutcome, cleaner: TranscriptCleaner?): DictationCleanupResult {
        if (outcome !is TranscriptionOutcome.Transcript) return DictationCleanupResult.Ready(outcome, null)
        if (cleaner == null || cleaner.isIdentity) return DictationCleanupResult.Ready(outcome, outcome.text)
        val cleaned = cleaner.clean(outcome.text).trim()
        // The reference rules keep sentence punctuation, so `Uh, um.` cleans to `.`; punctuation with
        // no letters or digits left is still nothing worth inserting.
        if (cleaned.none { it.isLetterOrDigit() }) return DictationCleanupResult.OnlyFillers(outcome.text)
        return DictationCleanupResult.Ready(TranscriptionOutcome.Transcript(cleaned), outcome.text)
    }
}

/** One committed dictation, kept in memory only for the fix flow. Never persisted or logged. */
data class DictationInsertion(
    val rawTranscript: String,
    val committedText: String,
    val editorSessionId: Long,
    val hostPackage: String?,
    val fieldId: Int,
    val committedAtMs: Long,
)

/** A word or phrase of the committed text with its character offsets inside [DictationInsertion.committedText]. */
data class DictationToken(val text: String, val start: Int, val end: Int)

object DictationFixModel {
    /** Words keep inner apostrophes, hyphens and dots (`Bart's`, `e-mail`, `v1.2`); surrounding punctuation is dropped. */
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+(?:['’.\\-][\\p{L}\\p{N}]+)*")

    fun tokenize(text: String): List<DictationToken> =
        tokenPattern.findAll(text).map { match -> DictationToken(match.value, match.range.first, match.range.last + 1) }.toList()

    /** The phrase covering the selected tokens, including whatever sits between them. */
    fun span(text: String, tokens: List<DictationToken>, selected: IntRange): DictationToken {
        val start = tokens[selected.first].start
        val end = tokens[selected.last].end
        return DictationToken(text.substring(start, end), start, end)
    }

    /**
     * Tap semantics for the chooser: a first tap selects one word, a tap next to the selection extends
     * it into a phrase, a tap inside a phrase restarts from that word, and a tap on the only selected
     * word clears the selection.
     */
    fun selectionAfterTap(current: IntRange?, index: Int): IntRange? {
        if (current == null) return index..index
        if (index in current) return if (current.first == current.last) null else index..index
        if (index == current.first - 1 || index == current.last + 1) {
            return minOf(current.first, index)..maxOf(current.last, index)
        }
        return index..index
    }

    /** Absolute editor offset where the committed text starts, if it still ends right at the cursor. */
    fun locateCommitted(content: EditorContent, committedText: String): Int? {
        if (committedText.isEmpty() || content.offset < 0) return null
        val selection = content.selection
        if (!selection.isValid || !selection.isCursorMode) return null
        if (!content.textBeforeSelection.endsWith(committedText)) return null
        return selection.start - committedText.length
    }

    /**
     * What the user has typed over the selected word so far: the text between the word's original
     * start and the cursor. Null once the cursor left that region, which ends the fix.
     */
    fun replacementPreview(content: EditorContent, absoluteStart: Int): String? {
        if (content.offset < 0 || !content.selection.isValid) return null
        val localStart = absoluteStart - content.offset
        val localCursor = content.selection.start - content.offset
        if (localStart < 0 || localCursor < localStart || localCursor > content.text.length) return null
        return content.text.substring(localStart, localCursor)
    }
}
