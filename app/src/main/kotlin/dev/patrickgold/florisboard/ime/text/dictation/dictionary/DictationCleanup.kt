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
        val detailed = cleaner.cleanDetailed(outcome.text)
        val cleaned = detailed.text.trim()
        // The reference rules keep sentence punctuation, so `Uh, um.` cleans to `.`. Only when filler
        // removal took every letter and digit out of a transcript that had some is the result
        // "nothing to insert"; symbols a correction produced on purpose (`smiley` → `😊`) are kept.
        // Checked per code point so supplementary-plane letters count as text.
        val hadText = outcome.text.codePoints().anyMatch(Character::isLetterOrDigit)
        val fillersTookAllText = detailed.afterFillers.codePoints().noneMatch(Character::isLetterOrDigit)
        // A correction may turn a leftover symbol back into words (`$` → `dollar`); the final text decides.
        val endsWithoutText = cleaned.codePoints().noneMatch(Character::isLetterOrDigit)
        if (cleaned.isEmpty() || (hadText && fillersTookAllText && endsWithoutText)) {
            return DictationCleanupResult.OnlyFillers(outcome.text)
        }
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
    /**
     * Words keep inner apostrophes, hyphens and dots (`Bart's`, `e-mail`, `v1.2`); surrounding
     * punctuation is dropped. Combining marks belong to the word so decomposed accents are kept.
     */
    private val tokenPattern = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’.\\-][\\p{L}\\p{M}\\p{N}]+)*")

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

    /**
     * Absolute editor offset where the committed text starts, if it still ends right at the cursor.
     *
     * The editor snapshot holds a bounded window before the cursor. A long dictation can exceed it,
     * so when the window is truncated at its start only the visible tail has to match.
     */
    fun locateCommitted(content: EditorContent, committedText: String): Int? {
        if (committedText.isEmpty() || content.offset < 0) return null
        val selection = content.selection
        if (!selection.isValid || !selection.isCursorMode) return null
        val before = content.textBeforeSelection
        val matches = if (before.length >= committedText.length) {
            before.endsWith(committedText)
        } else {
            content.offset > 0 && before.isNotEmpty() && committedText.endsWith(before)
        }
        if (!matches) return null
        return selection.start - committedText.length
    }

    /** How much of the text after the selected word is remembered to detect cursor relocation. */
    const val EXPECTED_AFTER_LIMIT = 64

    /**
     * What the user has typed over the selected word so far: the text between the word's original
     * start and the cursor. [expectedAfter] is the text that followed the word when the replacement
     * began; the cursor must still sit right in front of it, otherwise it was moved elsewhere and
     * whatever lies between would be unrelated text, not a replacement. The window may not reach
     * the start right after the selection moved; that is not the same as the cursor having left.
     */
    fun replacementPreview(content: EditorContent, absoluteStart: Int, expectedAfter: String = ""): ReplacementPreview {
        if (content.offset < 0 || !content.selection.isValid) return ReplacementPreview.CursorLeft
        val cursor = content.selection.start
        if (cursor < absoluteStart) return ReplacementPreview.CursorLeft
        // The editor keeps at least 128 characters after the cursor and the remembered suffix is at
        // most 64, so a shorter or different suffix means the cursor moved or the text changed.
        if (!content.textAfterSelection.startsWith(expectedAfter)) return ReplacementPreview.CursorLeft
        val localStart = absoluteStart - content.offset
        val localCursor = cursor - content.offset
        if (localStart < 0 || localCursor > content.text.length) return ReplacementPreview.OutOfWindow
        return ReplacementPreview.Text(content.text.substring(localStart, localCursor))
    }
}

sealed interface ReplacementPreview {
    data class Text(val value: String) : ReplacementPreview

    /** The snapshot window does not cover the word yet; keep the last known replacement. */
    data object OutOfWindow : ReplacementPreview

    /** The cursor moved before the word, which ends the fix. */
    data object CursorLeft : ReplacementPreview
}
