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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The host editor as the fix flow needs it. Abstracted so the state machine is testable without Android. */
interface DictationFixEditorGateway {
    val contentFlow: Flow<EditorContent>
    val activeSessionId: Long
    val activeHostPackage: String?
    val activeFieldId: Int
    fun selectRange(start: Int, end: Int): Boolean
}

sealed interface DictationFixState {
    data object Hidden : DictationFixState

    /** Text was just inserted; a chip offers to fix a word until the user types or time passes. */
    data class Offered(val insertion: DictationInsertion) : DictationFixState

    /** The chooser panel: tap the word that was heard wrong, extend to a phrase if needed. */
    data class Choosing(
        val insertion: DictationInsertion,
        val tokens: List<DictationToken>,
        val selected: IntRange?,
    ) : DictationFixState {
        val selectedText: String? get() = selected?.let { DictationFixModel.span(insertion.committedText, tokens, it).text }
    }

    /** The misheard word is selected in the host editor; whatever the user types replaces it. */
    data class Replacing(
        val insertion: DictationInsertion,
        val source: String,
        val absoluteStart: Int,
        val replacement: String,
        val addAsWord: Boolean,
        /** Text that followed the word when the replacement began; the cursor must stay in front of it. */
        val expectedAfter: String = "",
    ) : DictationFixState {
        val canSave: Boolean
            get() = TranscriptCleanup.normalizeTerm(replacement).let { it.isNotEmpty() && it != TranscriptCleanup.normalizeTerm(source) }
    }

    /** The editor changed under the flow, so the correction can only be added in settings. */
    data class Manual(val source: String) : DictationFixState

    data class Saved(val source: String, val replacement: String, val wordAdded: Boolean) : DictationFixState

    /** Storage refused the write; the entry is held in memory and retried, but nothing is confirmed. */
    data class SaveFailed(val source: String) : DictationFixState
}

/**
 * Keyboard-side flow that turns a misheard word into a saved correction in a few taps.
 *
 * The user retypes the word in the host editor with the ordinary keyboard: the flow only selects the
 * range, watches the editor content for the replacement, and saves the pair. No in-keyboard text
 * field is needed and the app's own undo keeps working. Nothing here runs on the typing path
 * beyond reading the editor snapshot that is already published for every content change.
 */
class DictationFixController(
    private val scope: CoroutineScope,
    private val repository: SpeechDictionaryRepository,
    private val editor: DictationFixEditorGateway,
    private val openDictionary: (heard: String) -> Unit,
    /** Whether AI features may run right now; false (incognito, secure field) ends the flow at any point. */
    private val available: Flow<Boolean> = flowOf(true),
    private val clock: () -> Long = System::currentTimeMillis,
    private val offerTimeoutMs: Long = OFFER_TIMEOUT_MS,
    private val savedDwellMs: Long = SAVED_DWELL_MS,
) {
    private val _state = MutableStateFlow<DictationFixState>(DictationFixState.Hidden)
    val state: StateFlow<DictationFixState> = _state

    private var timer: Job? = null
    private var saveJob: Job? = null
    private var lastContent: EditorContent? = null

    init {
        scope.launch { editor.contentFlow.collect(::onEditorContent) }
        scope.launch { available.collect { ok -> if (!ok) abort() } }
    }

    /**
     * Ends the flow in every state, including an active replacement and a save that has not
     * started writing yet: nothing may be learned now. A write already in progress completes so
     * disk and memory stay consistent; its word hint and confirmation are dropped.
     */
    fun abort() {
        saveJob?.cancel()
        saveJob = null
        if (_state.value !is DictationFixState.Hidden) publish(DictationFixState.Hidden)
    }

    fun offer(insertion: DictationInsertion) {
        // Dictating over the selected word is a valid way to retype it: the replacing row stays and
        // shows the dictated text through the editor content, so a new offer must not replace it.
        if (_state.value is DictationFixState.Replacing) return
        if (DictationFixModel.tokenize(insertion.committedText).isEmpty()) return
        publish(DictationFixState.Offered(insertion))
        timer = scope.launch {
            // Emissions inside the commit grace are not judged; re-judge the latest one once it ends
            // so a keystroke typed right after the insertion still retires the offer.
            val grace = COMMIT_GRACE_MS - (clock() - insertion.committedAtMs)
            if (grace > 0) {
                delay(grace)
                val offered = _state.value as? DictationFixState.Offered
                val content = lastContent
                if (offered != null && offered.insertion === insertion && content != null) judgeOffer(offered, content)
            }
            delay((offerTimeoutMs - maxOf(grace, 0L)).coerceAtLeast(0L))
            if (_state.value.let { it is DictationFixState.Offered && it.insertion === insertion }) publish(DictationFixState.Hidden)
        }
    }

    /**
     * A new recording starts. The offer, chooser and confirmations go away; an active replacement
     * stays, because dictating over the selected word is a valid way to give the new spelling.
     */
    fun onDictationStarted() {
        val current = _state.value
        if (current is DictationFixState.Hidden || current is DictationFixState.Replacing) return
        publish(DictationFixState.Hidden)
    }

    /**
     * Another keyboard panel takes over (media, clipboard, overflow, rewrite, pickers). The flow
     * ends in every state: text pasted or picked there is not a retyped word to learn from.
     */
    fun interrupt() {
        if (_state.value !is DictationFixState.Hidden) publish(DictationFixState.Hidden)
    }

    fun openChooser() {
        val current = _state.value as? DictationFixState.Offered ?: return
        publish(DictationFixState.Choosing(current.insertion, DictationFixModel.tokenize(current.insertion.committedText), null))
    }

    fun tapToken(index: Int) {
        val current = _state.value as? DictationFixState.Choosing ?: return
        if (index !in current.tokens.indices) return
        publish(current.copy(selected = DictationFixModel.selectionAfterTap(current.selected, index)))
    }

    fun beginReplacement(content: EditorContent) {
        val current = _state.value as? DictationFixState.Choosing ?: return
        val selected = current.selected ?: return
        val token = DictationFixModel.span(current.insertion.committedText, current.tokens, selected)
        val source = token.text
        val insertion = current.insertion
        val sameField = editor.activeSessionId == insertion.editorSessionId &&
            editor.activeHostPackage == insertion.hostPackage &&
            editor.activeFieldId == insertion.fieldId
        val committedStart = if (sameField) DictationFixModel.locateCommitted(content, insertion.committedText) else null
        if (committedStart == null) {
            publish(DictationFixState.Manual(source))
            return
        }
        val start = committedStart + token.start
        val end = committedStart + token.end
        if (!editor.selectRange(start, end)) {
            publish(DictationFixState.Manual(source))
            return
        }
        // What follows the word right now: the rest of the insertion plus whatever the field held after it.
        val expectedAfter = (insertion.committedText.substring(token.end) + content.textAfterSelection)
            .take(DictationFixModel.EXPECTED_AFTER_LIMIT)
        publish(DictationFixState.Replacing(insertion, source, start, replacement = "", addAsWord = true, expectedAfter = expectedAfter))
    }

    fun toggleAddAsWord() {
        val current = _state.value as? DictationFixState.Replacing ?: return
        publish(current.copy(addAsWord = !current.addAsWord))
    }

    fun save() {
        val current = _state.value as? DictationFixState.Replacing ?: return
        if (!current.canSave) return
        val source = current.source
        val replacement = TranscriptCleanup.normalizeTerm(current.replacement)
        val addAsWord = current.addAsWord
        saveJob = scope.launch {
            val correction = repository.upsertCorrection(source, replacement)
            // The write is asynchronous; a dismissal, an abort or a new dictation in the meantime
            // owns the state now, and no further learning happens for it.
            if (_state.value !== current) return@launch
            val wordAdded = correction is EntryResult.Saved && addAsWord && repository.addWord(replacement) is EntryResult.Saved
            if (_state.value !== current) return@launch
            if (correction is EntryResult.Rejected) {
                publish(DictationFixState.Hidden)
                return@launch
            }
            // The repository keeps a change it could not write and retries later; that is not a
            // confirmation the keyboard may show.
            publish(
                if (repository.state.value.saveError) DictationFixState.SaveFailed(source)
                else DictationFixState.Saved(source, replacement, wordAdded),
            )
            timer = scope.launch {
                delay(savedDwellMs)
                if (_state.value is DictationFixState.Saved) publish(DictationFixState.Hidden)
            }
        }
    }

    fun openDictionaryForSource() {
        val source = when (val current = _state.value) {
            is DictationFixState.Manual -> current.source
            is DictationFixState.Choosing -> current.selectedText ?: ""
            else -> ""
        }
        publish(DictationFixState.Hidden)
        openDictionary(source)
    }

    fun dismiss() {
        publish(DictationFixState.Hidden)
    }

    private fun onEditorContent(content: EditorContent) {
        lastContent = content
        when (val current = _state.value) {
            is DictationFixState.Offered -> {
                // The host confirms the commit asynchronously; an emission from before that must not
                // retire the offer. The timer re-judges the latest emission once the grace ends.
                if (clock() - current.insertion.committedAtMs < COMMIT_GRACE_MS) return
                judgeOffer(current, content)
            }
            is DictationFixState.Replacing -> {
                if (editor.activeSessionId != current.insertion.editorSessionId) {
                    publish(DictationFixState.Hidden)
                    return
                }
                when (val preview = DictationFixModel.replacementPreview(content, current.absoluteStart, current.expectedAfter)) {
                    is ReplacementPreview.Text -> if (preview.value != current.replacement) {
                        publish(current.copy(replacement = preview.value))
                    }
                    // The snapshot does not reach the word (a stale emission from before the selection
                    // moved, or a replacement longer than the window). The text is unknown, so nothing
                    // stale may be saved: the preview is blanked, which disables Save until it is back.
                    ReplacementPreview.OutOfWindow -> if (current.replacement.isNotEmpty()) publish(current.copy(replacement = ""))
                    ReplacementPreview.CursorLeft -> publish(DictationFixState.Hidden)
                }
            }
            is DictationFixState.Choosing -> {
                if (editor.activeSessionId != current.insertion.editorSessionId) publish(DictationFixState.Hidden)
            }
            is DictationFixState.Manual, is DictationFixState.Saved, is DictationFixState.SaveFailed, DictationFixState.Hidden -> Unit
        }
    }

    /** Typing, moving the cursor or switching fields all retire the offer. */
    private fun judgeOffer(current: DictationFixState.Offered, content: EditorContent) {
        val stillThere = editor.activeSessionId == current.insertion.editorSessionId &&
            DictationFixModel.locateCommitted(content, current.insertion.committedText) != null
        if (!stillThere) publish(DictationFixState.Hidden)
    }

    private fun publish(next: DictationFixState) {
        timer?.cancel()
        timer = null
        _state.value = next
    }

    companion object {
        const val OFFER_TIMEOUT_MS = 15_000L
        const val SAVED_DWELL_MS = 2_200L
        const val COMMIT_GRACE_MS = 500L
    }
}
