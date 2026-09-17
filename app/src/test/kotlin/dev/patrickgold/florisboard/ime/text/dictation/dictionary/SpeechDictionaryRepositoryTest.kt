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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/** Uses real files in a temporary directory: persistence, atomicity, reload and restore are all disk-backed. */
class SpeechDictionaryRepositoryTest : FunSpec({
    fun temp(): File = Files.createTempDirectory("speech-dictionary").toFile()
    fun repository(dir: File, clock: () -> Long = { 1_000L }) = SpeechDictionaryRepository(
        file = File(dir, "personal_dictionary.json"),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        ioDispatcher = Dispatchers.IO,
        computeDispatcher = Dispatchers.Default,
        clock = clock,
    )

    test("fresh install has empty entries, filler removal on, English and Dutch selected") {
        val dir = temp()
        val state = runBlocking { repository(dir).awaitLoaded() }
        state.loaded shouldBe true
        state.loadError shouldBe null
        state.document.words shouldBe emptyList()
        state.document.corrections shouldBe emptyList()
        state.document.fillers shouldBe FillerSettings(enabled = true, languages = listOf("en", "nl"), custom = emptyList())
        File(dir, "personal_dictionary.json").exists() shouldBe false
    }

    test("entries get stable ids, keep saved order, normalise whitespace and reject duplicates") {
        val dir = temp()
        val repository = repository(dir)
        runBlocking {
            repository.addWord("Ownkey").shouldBeInstanceOf<EntryResult.Saved>()
            repository.addWord(" Orukeet\tv1 ").shouldBeInstanceOf<EntryResult.Saved>()
            repository.addWord("ownkey") shouldBe EntryResult.Rejected(EntryError.DUPLICATE_WORD)
            repository.addWord("   ") shouldBe EntryResult.Rejected(EntryError.BLANK_WORD)
            repository.addCorrection("own key", "Ownkey").shouldBeInstanceOf<EntryResult.Saved>()
            repository.addCorrection("Own Key", "Other") shouldBe EntryResult.Rejected(EntryError.DUPLICATE_CORRECTION)
            repository.addCorrection("bart", "Bart").shouldBeInstanceOf<EntryResult.Saved>()
            repository.addCorrection("same", "same") shouldBe EntryResult.Rejected(EntryError.IDENTICAL)
            repository.addCorrection("", "x") shouldBe EntryResult.Rejected(EntryError.BLANK_SOURCE)
            repository.addCorrection("x", " ") shouldBe EntryResult.Rejected(EntryError.BLANK_REPLACEMENT)
        }
        val document = repository.state.value.document
        document.words.map { it.word } shouldBe listOf("Ownkey", "Orukeet v1")
        document.words.map { it.id } shouldBe listOf(1L, 2L)
        document.corrections.map { it.source to it.replacement } shouldBe listOf("own key" to "Ownkey", "bart" to "Bart")
        document.corrections.map { it.id } shouldBe listOf(3L, 4L)
        document.nextId shouldBe 5L
    }

    test("edits and removals are atomic and a new instance reads the same document from disk") {
        val dir = temp()
        val first = repository(dir)
        runBlocking {
            first.addWord("Ownkey")
            first.addWord("Bart")
            first.addCorrection("own key", "Ownkey")
            first.updateWord(2L, "Bart van der Meeren").shouldBeInstanceOf<EntryResult.Saved>()
            first.updateWord(2L, "ownkey") shouldBe EntryResult.Rejected(EntryError.DUPLICATE_WORD)
            first.updateCorrection(3L, "own key", "OWNKEY").shouldBeInstanceOf<EntryResult.Saved>()
            first.setFillerLanguages(emptyList())
            first.setCustomFillers(listOf("basically", " Basically ", ""))
            first.setFillersEnabled(false)
        }
        File(dir, "personal_dictionary.json.tmp").exists() shouldBe false
        val reloaded = runBlocking { repository(dir).awaitLoaded() }.document
        reloaded.words.map { it.id to it.word } shouldBe listOf(1L to "Ownkey", 2L to "Bart van der Meeren")
        reloaded.corrections.map { it.replacement } shouldBe listOf("OWNKEY")
        reloaded.fillers shouldBe FillerSettings(enabled = false, languages = emptyList(), custom = listOf("basically"))
        reloaded.nextId shouldBe 4L
    }

    test("an explicitly empty language list survives reload and is not replaced by the defaults") {
        val dir = temp()
        runBlocking { repository(dir).setFillerLanguages(emptyList()) }
        val text = File(dir, "personal_dictionary.json").readText()
        text.contains("\"languages\": []") shouldBe true
        runBlocking { repository(dir).snapshot() }.fillers.languages shouldBe emptyList()
    }

    test("remove returns the entry with its index and undo puts it back in place") {
        val dir = temp()
        val repository = repository(dir)
        runBlocking {
            repository.addWord("a"); repository.addWord("b"); repository.addWord("c")
            val removed = repository.remove(2L)
            removed shouldNotBe null
            removed!!.index shouldBe 1
            repository.state.value.document.words.map { it.word } shouldBe listOf("a", "c")
            repository.restoreRemoved(removed) shouldBe true
            repository.state.value.document.words.map { it.id to it.word } shouldBe listOf(1L to "a", 2L to "b", 3L to "c")
            val removedAgain = repository.remove(2L)!!
            repository.addWord("B").shouldBeInstanceOf<EntryResult.Saved>()
            // A conflicting entry appeared in the meantime, so undo is refused instead of duplicating.
            repository.restoreRemoved(removedAgain) shouldBe false
            repository.state.value.document.words.map { it.word } shouldBe listOf("a", "c", "B")
            repository.remove(99L) shouldBe null
        }
    }

    test("snapshots are immutable per document version and compile the cleaner once") {
        val dir = temp()
        val repository = repository(dir)
        runBlocking {
            repository.addWord("Ownkey")
            repository.addCorrection("own key", "Ownkey")
            val first = repository.snapshot()
            val again = repository.snapshot()
            again shouldBeSameInstanceAs first
            first.vocabulary shouldBe listOf("Ownkey")
            first.cleaner.clean("Um, own key uh works.") shouldBe "Ownkey works."
            repository.addWord("Orukeet")
            val next = repository.snapshot()
            (next === first) shouldBe false
            first.vocabulary shouldBe listOf("Ownkey")
            next.vocabulary shouldBe listOf("Ownkey", "Orukeet")
        }
    }

    test("upsert updates an existing correction with the same source") {
        val dir = temp()
        val repository = repository(dir)
        runBlocking {
            repository.upsertCorrection("own key", "Ownkey").shouldBeInstanceOf<EntryResult.Saved>()
            repository.upsertCorrection("Own key", "OWNKEY").shouldBeInstanceOf<EntryResult.Saved>()
            repository.upsertCorrection("x", "x") shouldBe EntryResult.Rejected(EntryError.IDENTICAL)
        }
        repository.state.value.document.corrections.map { it.id to it.replacement } shouldBe listOf(1L to "OWNKEY")
    }

    test("an unreadable file is kept for inspection and reported instead of silently overwritten") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        file.writeText("{ not json")
        val repository = repository(dir, clock = { 42L })
        val state = runBlocking { repository.awaitLoaded() }
        state.loadError shouldBe SpeechDictionaryLoadError.UNREADABLE
        state.document.isEmpty shouldBe true
        File(dir, "personal_dictionary.json.unreadable-42").readText() shouldBe "{ not json"
        runBlocking { repository.addWord("Ownkey") }
        repository.state.value.loadError shouldBe null
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Ownkey")
    }

    test("unknown keys from a newer document are tolerated on load") {
        val dir = temp()
        File(dir, "personal_dictionary.json").writeText(
            """{"version":1,"nextId":3,"words":[{"id":1,"word":"Ownkey","future":true}],"corrections":[],"fillers":{"enabled":true,"languages":["nl"],"custom":[],"extra":1},"unknown":{}}""",
        )
        val document = runBlocking { repository(dir).awaitLoaded() }.document
        document.words.map { it.word } shouldBe listOf("Ownkey")
        document.fillers.languages shouldBe listOf("nl")
    }

    context("backup and restore") {
        test("export round trips through the versioned document and merge keeps existing entries") {
            val source = temp()
            val target = temp()
            val exporter = repository(source)
            runBlocking {
                exporter.addWord("Ownkey")
                exporter.addCorrection("own key", "Ownkey")
                exporter.setFillerLanguages(listOf("nl", "de"))
                exporter.setCustomFillers(listOf("basically"))
            }
            val encoded = SpeechDictionaryDocument.encode(runBlocking { exporter.export() })
            val importer = repository(target)
            runBlocking {
                importer.addWord("Existing")
                importer.addWord("ownkey")
                importer.restore(SpeechDictionaryDocument.decode(encoded), merge = true)
            }
            val merged = importer.state.value.document
            merged.words.map { it.word } shouldBe listOf("Existing", "ownkey")
            merged.corrections.map { it.source to it.replacement } shouldBe listOf("own key" to "Ownkey")
            merged.fillers shouldBe FillerSettings(true, listOf("nl", "de"), listOf("basically"))
            merged.nextId shouldBe 4L
        }

        test("erase replaces entries and rejected backups leave everything untouched") {
            val dir = temp()
            val repository = repository(dir)
            runBlocking {
                repository.addWord("Keep me")
                repository.restore(SpeechDictionaryDocument(words = listOf(VocabularyEntry(7L, "New"))), merge = false)
            }
            repository.state.value.document.words.map { it.id to it.word } shouldBe listOf(2L to "New")
            val before = repository.state.value.document
            shouldThrow<RestoreRejectedException> {
                runBlocking { repository.restore(SpeechDictionaryDocument(version = 99), merge = false) }
            }
            shouldThrow<RestoreRejectedException> {
                runBlocking { repository.restore(SpeechDictionaryDocument(version = 0), merge = true) }
            }
            repository.state.value.document shouldBeSameInstanceAs before
            runBlocking { repository(dir).awaitLoaded() }.document.words.map { it.word } shouldBe listOf("New")
        }

        test("restore drops blank, identical and duplicate backup entries but keeps the rest") {
            val dir = temp()
            val repository = repository(dir)
            val backup = SpeechDictionaryDocument(
                words = listOf(VocabularyEntry(1, " "), VocabularyEntry(2, "A"), VocabularyEntry(3, "a")),
                corrections = listOf(CorrectionEntry(4, "x", "x"), CorrectionEntry(5, "own key", "Ownkey"), CorrectionEntry(6, "OWN KEY", "Other")),
                fillers = FillerSettings(enabled = false, languages = listOf("xx", "es"), custom = listOf("")),
            )
            runBlocking { repository.restore(backup, merge = false) }
            val document = repository.state.value.document
            document.words.map { it.word } shouldBe listOf("A")
            document.corrections.map { it.source to it.replacement } shouldBe listOf("own key" to "Ownkey")
            document.fillers shouldBe FillerSettings(enabled = false, languages = listOf("es"), custom = emptyList())
        }

        test("a corrupt backup section fails to decode before anything is written") {
            shouldThrow<Exception> { SpeechDictionaryDocument.decode("{\"version\": \"one\"}") }
            shouldThrow<Exception> { SpeechDictionaryDocument.decode("nope") }
        }
    }
})
