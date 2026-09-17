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
    ) : DictationFixState {
        val canSave: Boolean
            get() = TranscriptCleanup.normalizeTerm(replacement).let { it.isNotEmpty() && it != TranscriptCleanup.normalizeTerm(source) }
    }

    /** The editor changed under the flow, so the correction can only be added in settings. */
    data class Manual(val source: String) : DictationFixState

    data class Saved(val source: String, val replacement: String, val wordAdded: Boolean) : DictationFixState
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val offerTimeoutMs: Long = OFFER_TIMEOUT_MS,
    private val savedDwellMs: Long = SAVED_DWELL_MS,
) {
    private val _state = MutableStateFlow<DictationFixState>(DictationFixState.Hidden)
    val state: StateFlow<DictationFixState> = _state

    private var timer: Job? = null

    init {
        scope.launch { editor.contentFlow.collect(::onEditorContent) }
    }

    fun offer(insertion: DictationInsertion) {
        // Dictating over the selected word is a valid way to retype it: the replacing row stays and
        // shows the dictated text through the editor content, so a new offer must not replace it.
        if (_state.value is DictationFixState.Replacing) return
        if (DictationFixModel.tokenize(insertion.committedText).isEmpty()) return
        publish(DictationFixState.Offered(insertion))
        timer = scope.launch {
            delay(offerTimeoutMs)
            if (_state.value.let { it is DictationFixState.Offered && it.insertion === insertion }) publish(DictationFixState.Hidden)
        }
    }

    /**
     * A new recording or another keyboard panel takes over. The offer, chooser and confirmations
     * go away; an active replacement stays, because the user may be dictating the new spelling.
     */
    fun interrupt() {
        val current = _state.value
        if (current is DictationFixState.Hidden || current is DictationFixState.Replacing) return
        publish(DictationFixState.Hidden)
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
        publish(DictationFixState.Replacing(insertion, source, start, replacement = "", addAsWord = true))
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
        scope.launch {
            val correction = repository.upsertCorrection(source, replacement)
            if (correction is EntryResult.Rejected) {
                publish(DictationFixState.Hidden)
                return@launch
            }
            val wordAdded = addAsWord && repository.addWord(replacement) is EntryResult.Saved
            publish(DictationFixState.Saved(source, replacement, wordAdded))
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
        when (val current = _state.value) {
            is DictationFixState.Offered -> {
                // The host confirms the commit asynchronously; an emission from before that must not
                // retire the offer. After the grace period typing, moving the cursor or switching
                // fields all retire it.
                if (clock() - current.insertion.committedAtMs < COMMIT_GRACE_MS) return
                val stillThere = editor.activeSessionId == current.insertion.editorSessionId &&
                    DictationFixModel.locateCommitted(content, current.insertion.committedText) != null
                if (!stillThere) publish(DictationFixState.Hidden)
            }
            is DictationFixState.Replacing -> {
                if (editor.activeSessionId != current.insertion.editorSessionId) {
                    publish(DictationFixState.Hidden)
                    return
                }
                val preview = DictationFixModel.replacementPreview(content, current.absoluteStart)
                if (preview == null) publish(DictationFixState.Hidden) else if (preview != current.replacement) {
                    publish(current.copy(replacement = preview))
                }
            }
            is DictationFixState.Choosing -> {
                if (editor.activeSessionId != current.insertion.editorSessionId) publish(DictationFixState.Hidden)
            }
            is DictationFixState.Manual, is DictationFixState.Saved, DictationFixState.Hidden -> Unit
        }
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
