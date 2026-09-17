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
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionFailureReason
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOutcome
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

private fun cursorContent(text: String, cursor: Int = text.length, offset: Int = 0) = EditorContent(
    text = text,
    offset = offset,
    localSelection = EditorRange.cursor(cursor - offset),
    localComposing = EditorRange.Unspecified,
    localCurrentWord = EditorRange.Unspecified,
)

private fun selectedContent(text: String, start: Int, end: Int) = EditorContent(
    text = text,
    offset = 0,
    localSelection = EditorRange(start, end),
    localComposing = EditorRange.Unspecified,
    localCurrentWord = EditorRange.Unspecified,
)

private class FakeEditor : DictationFixEditorGateway {
    val emissions = MutableSharedFlow<EditorContent>(extraBufferCapacity = 64)
    override val contentFlow: Flow<EditorContent> get() = emissions
    override var activeSessionId: Long = 1L
    override var activeHostPackage: String? = "com.example.notes"
    override var activeFieldId: Int = 7
    var selectRangeResult = true
    val selections = ArrayList<Pair<Int, Int>>()
    override fun selectRange(start: Int, end: Int): Boolean {
        selections.add(start to end)
        return selectRangeResult
    }

    fun emit(content: EditorContent) {
        check(emissions.tryEmit(content))
    }
}

class DictationFixModelTest : FunSpec({
    test("tokenizes words with offsets, keeping inner apostrophes, hyphens and dots") {
        val tokens = DictationFixModel.tokenize(" Hello, Bart's e-mail v1.2 works! ")
        tokens.map { it.text } shouldBe listOf("Hello", "Bart's", "e-mail", "v1.2", "works")
        tokens.map { it.start to it.end } shouldBe listOf(1 to 6, 8 to 14, 15 to 21, 22 to 26, 27 to 32)
        DictationFixModel.tokenize("...") shouldBe emptyList()
    }

    test("phrase spans cover the text between the selected words") {
        val text = "Own key, works"
        val tokens = DictationFixModel.tokenize(text)
        DictationFixModel.span(text, tokens, 0..1) shouldBe DictationToken("Own key", 0, 7)
        DictationFixModel.span(text, tokens, 1..1) shouldBe DictationToken("key", 4, 7)
    }

    test("tap semantics select, extend, restart and clear") {
        DictationFixModel.selectionAfterTap(null, 2) shouldBe 2..2
        DictationFixModel.selectionAfterTap(2..2, 3) shouldBe 2..3
        DictationFixModel.selectionAfterTap(2..3, 1) shouldBe 1..3
        DictationFixModel.selectionAfterTap(1..3, 5) shouldBe 5..5
        DictationFixModel.selectionAfterTap(1..3, 2) shouldBe 2..2
        DictationFixModel.selectionAfterTap(2..2, 2) shouldBe null
    }

    test("locates the committed text only while it still ends at the cursor") {
        val committed = " own key works"
        DictationFixModel.locateCommitted(cursorContent("Before own key works"), committed) shouldBe 6
        DictationFixModel.locateCommitted(cursorContent("Before own key works!"), committed) shouldBe null
        DictationFixModel.locateCommitted(cursorContent("Before own key works", cursor = 10), committed) shouldBe null
        DictationFixModel.locateCommitted(selectedContent("Before own key works", 7, 14), committed) shouldBe null
        DictationFixModel.locateCommitted(cursorContent("xx own key works", cursor = 116, offset = 100), committed) shouldBe 102
        DictationFixModel.locateCommitted(EditorContent.Unspecified, committed) shouldBe null
    }

    test("replacement preview is the text between the word start and the cursor") {
        DictationFixModel.replacementPreview(selectedContent("Before own key works", 7, 14), 7) shouldBe ReplacementPreview.Text("")
        DictationFixModel.replacementPreview(cursorContent("Before Ownkey works", cursor = 13), 7) shouldBe ReplacementPreview.Text("Ownkey")
        DictationFixModel.replacementPreview(cursorContent("Before Ownkey works", cursor = 3), 7) shouldBe ReplacementPreview.CursorLeft
        DictationFixModel.replacementPreview(cursorContent("yy Ownkey works", cursor = 109, offset = 100), 103) shouldBe ReplacementPreview.Text("Ownkey")
        // The window starts after the word: unknown for now, not the end of the fix.
        DictationFixModel.replacementPreview(cursorContent("nkey works", cursor = 106, offset = 100), 98) shouldBe ReplacementPreview.OutOfWindow
        DictationFixModel.replacementPreview(EditorContent.Unspecified, 7) shouldBe ReplacementPreview.CursorLeft
    }

    test("a long insertion is located from the visible tail of a truncated snapshot window") {
        val committed = " " + "word ".repeat(80).trim()
        // The editor keeps a bounded window before the cursor; here it starts inside the insertion.
        val tail = committed.takeLast(100)
        DictationFixModel.locateCommitted(cursorContent(tail, cursor = 500 + 100, offset = 500), committed) shouldBe 600 - committed.length
        // Without an offset the snapshot is complete, so a short prefix is a mismatch, not a tail.
        DictationFixModel.locateCommitted(cursorContent(tail), committed) shouldBe null
        DictationFixModel.locateCommitted(cursorContent("other text", cursor = 510, offset = 500), committed) shouldBe null
    }

    test("tokens keep combining marks so decomposed accents are replaced whole") {
        val decomposed = "Zoë works"
        DictationFixModel.tokenize(decomposed).map { it.text } shouldBe listOf("Zoë", "works")
    }
})

class OrdinaryDictationCleanupTest : FunSpec({
    val cleaner = TranscriptCleaner(
        CleanupSettings(true, FillerRules.fillerWords(listOf("en", "nl")), listOf(CorrectionRule("own key", "Ownkey"))),
    )

    test("transcripts are cleaned, keep the raw text, and filler-only speech is a neutral result") {
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Um, own key uh works."), cleaner) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("Ownkey works."), "Um, own key uh works.")
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Uh, um."), cleaner) shouldBe
            DictationCleanupResult.OnlyFillers("Uh, um.")
        // A supplementary-plane letter is text, even though it spans two UTF-16 chars.
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Um, 𝔘."), cleaner) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("𝔘."), "Um, 𝔘.")
    }

    test("symbols produced on purpose by a correction are inserted, not treated as filler-only") {
        val symbols = TranscriptCleaner(
            CleanupSettings(true, FillerRules.fillerWords(listOf("en")), listOf(CorrectionRule("smiley", "😊"), CorrectionRule("period", "."))),
        )
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Um, smiley"), symbols) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("😊"), "Um, smiley")
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("period"), symbols) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("."), "period")
        // A transcript that was punctuation to begin with passes through as before.
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("."), symbols) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("."), ".")
        // Fillers that leave only a full stop are still nothing to insert.
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Uh, um."), symbols) shouldBe
            DictationCleanupResult.OnlyFillers("Uh, um.")
    }

    test("failures, cancellation and identity cleaners pass through unchanged") {
        val failure = TranscriptionOutcome.Failure(TranscriptionFailureReason.PROVIDER)
        OrdinaryDictationCleanup.apply(failure, cleaner) shouldBe DictationCleanupResult.Ready(failure, null)
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Cancelled, cleaner) shouldBe DictationCleanupResult.Ready(TranscriptionOutcome.Cancelled, null)
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Um, hi"), null) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("Um, hi"), "Um, hi")
        val identity = TranscriptCleaner(CleanupSettings(false, emptyList(), emptyList()))
        OrdinaryDictationCleanup.apply(TranscriptionOutcome.Transcript("Um, hi"), identity) shouldBe
            DictationCleanupResult.Ready(TranscriptionOutcome.Transcript("Um, hi"), "Um, hi")
    }
})

class DictationFixControllerTest : FunSpec({
    class Harness {
        var now = 100_000L
        val editor = FakeEditor()
        val opened = ArrayList<String>()
        val dir: File = Files.createTempDirectory("fix-flow").toFile()
        val repository = SpeechDictionaryRepository(
            file = File(dir, "personal_dictionary.json"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined,
            clock = { now },
        )
        val controller = DictationFixController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            repository = repository,
            editor = editor,
            openDictionary = { opened.add(it) },
            clock = { now },
        )
        val committed = " own key works"
        val hostText = "Before own key works"

        fun insertion(agoMs: Long = 1_000L) = DictationInsertion(
            rawTranscript = "Um, own key works",
            committedText = committed,
            editorSessionId = editor.activeSessionId,
            hostPackage = editor.activeHostPackage,
            fieldId = editor.activeFieldId,
            committedAtMs = now - agoMs,
        )

        fun offerAndChoose(vararg taps: Int): DictationFixState.Choosing {
            controller.offer(insertion())
            controller.openChooser()
            taps.forEach(controller::tapToken)
            return controller.state.value as DictationFixState.Choosing
        }
    }

    test("an insertion offers a fix until the user types, and only after the commit grace period") {
        val h = Harness()
        h.controller.offer(h.insertion(agoMs = 0L))
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Offered>()
        // A stale emission right after the commit must not retire the offer.
        h.editor.emit(cursorContent("Before"))
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Offered>()
        h.now += 1_000L
        h.editor.emit(cursorContent(h.hostText))
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Offered>()
        h.editor.emit(cursorContent(h.hostText + " x"))
        h.controller.state.value shouldBe DictationFixState.Hidden
    }

    test("insertions without words are not offered") {
        val h = Harness()
        h.controller.offer(h.insertion().copy(committedText = " ... "))
        h.controller.state.value shouldBe DictationFixState.Hidden
    }

    test("the chooser selects single words and phrases") {
        val h = Harness()
        val single = h.offerAndChoose(0)
        single.tokens.map { it.text } shouldBe listOf("own", "key", "works")
        single.selectedText shouldBe "own"
        h.controller.tapToken(1)
        (h.controller.state.value as DictationFixState.Choosing).selectedText shouldBe "own key"
        h.controller.tapToken(99)
        (h.controller.state.value as DictationFixState.Choosing).selectedText shouldBe "own key"
        h.controller.tapToken(2)
        (h.controller.state.value as DictationFixState.Choosing).selectedText shouldBe "own key works"
    }

    test("retyping selects the phrase in the host editor, previews the typed text and saves both entries") {
        val h = Harness()
        h.offerAndChoose(0, 1)
        h.controller.beginReplacement(cursorContent(h.hostText))
        h.editor.selections shouldBe listOf(7 to 14)
        val replacing = h.controller.state.value as DictationFixState.Replacing
        replacing.source shouldBe "own key"
        replacing.absoluteStart shouldBe 7
        replacing.canSave shouldBe false
        h.editor.emit(selectedContent(h.hostText, 7, 14))
        (h.controller.state.value as DictationFixState.Replacing).replacement shouldBe ""
        h.editor.emit(cursorContent("Before Ownkey works", cursor = 13))
        val typed = h.controller.state.value as DictationFixState.Replacing
        typed.replacement shouldBe "Ownkey"
        typed.canSave shouldBe true
        h.controller.save()
        h.controller.state.value shouldBe DictationFixState.Saved("own key", "Ownkey", wordAdded = true)
        val document = runBlocking { h.repository.export() }
        document.corrections.map { it.source to it.replacement } shouldBe listOf("own key" to "Ownkey")
        document.words.map { it.word } shouldBe listOf("Ownkey")
    }

    test("the word hint is optional and a replacement equal to the source cannot be saved") {
        val h = Harness()
        h.offerAndChoose(1)
        h.controller.beginReplacement(cursorContent(h.hostText))
        h.controller.toggleAddAsWord()
        h.editor.emit(cursorContent("Before own Key works", cursor = 14))
        (h.controller.state.value as DictationFixState.Replacing).canSave shouldBe true
        h.editor.emit(cursorContent("Before own key works", cursor = 14))
        (h.controller.state.value as DictationFixState.Replacing).canSave shouldBe false
        h.controller.save()
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Replacing>()
        h.editor.emit(cursorContent("Before own Key works", cursor = 14))
        h.controller.save()
        h.controller.state.value shouldBe DictationFixState.Saved("key", "Key", wordAdded = false)
        runBlocking { h.repository.export() }.words shouldBe emptyList()
    }

    test("dictating over the selected word keeps the replacing row and previews the dictated text") {
        val h = Harness()
        h.offerAndChoose(0, 1)
        h.controller.beginReplacement(cursorContent(h.hostText))
        h.controller.interrupt()
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Replacing>()
        h.controller.offer(h.insertion().copy(committedText = "Ownkey"))
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Replacing>()
        h.editor.emit(cursorContent("Before Ownkey works", cursor = 13))
        (h.controller.state.value as DictationFixState.Replacing).replacement shouldBe "Ownkey"
    }

    test("a changed editor falls back to the manual path with the heard text") {
        val h = Harness()
        h.offerAndChoose(1)
        h.editor.activeFieldId = 8
        h.controller.beginReplacement(cursorContent(h.hostText))
        h.controller.state.value shouldBe DictationFixState.Manual("key")
        h.controller.openDictionaryForSource()
        h.opened shouldBe listOf("key")
        h.controller.state.value shouldBe DictationFixState.Hidden

        val second = Harness()
        second.offerAndChoose(1)
        second.editor.selectRangeResult = false
        second.controller.beginReplacement(cursorContent(second.hostText))
        second.controller.state.value shouldBe DictationFixState.Manual("key")

        val third = Harness()
        third.offerAndChoose(1)
        third.controller.beginReplacement(cursorContent("Before own key works!"))
        third.controller.state.value shouldBe DictationFixState.Manual("key")
    }

    test("moving the cursor elsewhere ends the replacement instead of learning unrelated text") {
        val h = Harness()
        h.offerAndChoose(1)
        h.controller.beginReplacement(cursorContent(h.hostText))
        (h.controller.state.value as DictationFixState.Replacing).expectedAfter shouldBe " works"
        // Typing over the selection keeps the suffix in front of the cursor.
        h.editor.emit(cursorContent("Before own Ownkey works", cursor = 17))
        (h.controller.state.value as DictationFixState.Replacing).replacement shouldBe "Ownkey"
        // A tap after `works` puts other text between the word start and the cursor: that is not a replacement.
        h.editor.emit(cursorContent("Before own Ownkey works", cursor = 23))
        h.controller.state.value shouldBe DictationFixState.Hidden

        val model = DictationFixModel.replacementPreview(cursorContent("Before own key works", cursor = 14), 11, " works")
        model shouldBe ReplacementPreview.Text("key")
        DictationFixModel.replacementPreview(cursorContent("Before own key works", cursor = 20), 11, " works") shouldBe ReplacementPreview.CursorLeft
        // The editor window after the cursor always covers the remembered suffix, so a shorter
        // suffix means the text after the word changed.
        DictationFixModel.replacementPreview(cursorContent("Before own key wo", cursor = 14), 11, " works") shouldBe ReplacementPreview.CursorLeft
    }

    test("a keystroke inside the commit grace still retires the offer once the grace ends") {
        val h = Harness()
        h.controller.offer(h.insertion(agoMs = 0L))
        h.editor.emit(cursorContent(h.hostText + " x"))
        h.controller.state.value.shouldBeInstanceOf<DictationFixState.Offered>()
        // The recheck runs on the real clock after the remaining grace.
        val deadline = System.currentTimeMillis() + 3_000L
        while (h.controller.state.value !is DictationFixState.Hidden && System.currentTimeMillis() < deadline) Thread.sleep(25)
        h.controller.state.value shouldBe DictationFixState.Hidden
    }

    test("a dismissal while the save is still being written wins over the late confirmation") {
        val dir = Files.createTempDirectory("fix-race").toFile()
        val slowRepository = SpeechDictionaryRepository(
            file = File(dir, "personal_dictionary.json"),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Unconfined,
        )
        val editor = FakeEditor()
        val controller = DictationFixController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            repository = slowRepository,
            editor = editor,
            openDictionary = {},
            clock = { 100_000L },
        )
        val insertion = DictationInsertion("raw", " own key works", 1L, "com.example.notes", 7, 99_000L)
        controller.offer(insertion)
        controller.openChooser()
        controller.tapToken(0)
        controller.beginReplacement(cursorContent("Before own key works"))
        editor.emit(cursorContent("Before Ownkey key works", cursor = 13))
        (controller.state.value as DictationFixState.Replacing).canSave shouldBe true
        controller.save()
        // The write hops to the IO dispatcher; the user dismisses before it lands.
        controller.dismiss()
        val deadline = System.currentTimeMillis() + 5_000L
        while (runBlocking { slowRepository.export() }.corrections.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(25)
        runBlocking { slowRepository.export() }.corrections.map { it.source to it.replacement } shouldBe listOf("own" to "Ownkey")
        Thread.sleep(100)
        controller.state.value shouldBe DictationFixState.Hidden
    }

    test("a new recording or another panel retires the offer and chooser, and a session switch ends the replacement") {
        val h = Harness()
        h.controller.offer(h.insertion())
        h.controller.interrupt()
        h.controller.state.value shouldBe DictationFixState.Hidden
        h.offerAndChoose(1)
        h.controller.interrupt()
        h.controller.state.value shouldBe DictationFixState.Hidden
        h.offerAndChoose(1)
        h.controller.beginReplacement(cursorContent(h.hostText))
        h.editor.activeSessionId = 2L
        h.editor.emit(cursorContent("Other field"))
        h.controller.state.value shouldBe DictationFixState.Hidden
    }
})
