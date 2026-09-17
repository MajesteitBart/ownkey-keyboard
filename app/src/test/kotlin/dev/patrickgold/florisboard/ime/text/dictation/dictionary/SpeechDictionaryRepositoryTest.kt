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

    test("single language toggles rebase on the stored document instead of a stale selection") {
        val dir = temp()
        val repository = repository(dir)
        runBlocking {
            repository.setFillerLanguage("de", true)
            repository.setFillerLanguage("fr", true)
            repository.state.value.document.fillers.languages shouldBe listOf("en", "nl", "de", "fr")
            repository.setFillerLanguage("en", false)
            repository.setFillerLanguage("xx", true)
            repository.state.value.document.fillers.languages shouldBe listOf("nl", "de", "fr")
        }
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

    test("a document written by a newer app is kept untouched and reported instead of being downgraded") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val newer = """{"version":2,"nextId":2,"words":[{"id":1,"word":"Ownkey","pronunciation":"own-key"}],"corrections":[],"fillers":{"enabled":true,"languages":["nl"],"custom":[]}}"""
        file.writeText(newer)
        val repository = repository(dir, clock = { 7L })
        val state = runBlocking { repository.awaitLoaded() }
        state.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        state.document.isEmpty shouldBe true
        file.exists() shouldBe false
        File(dir, "personal_dictionary.json.newer-v2-7").readText() shouldBe newer
        runBlocking { repository.addWord("Orukeet") }
        // The old app writes its own file; the newer one is still there, byte for byte.
        SpeechDictionaryDocument.decode(file.readText()).let { document ->
            document.version shouldBe SpeechDictionaryDocument.CURRENT_VERSION
            document.words.map { it.word } shouldBe listOf("Orukeet")
        }
        File(dir, "personal_dictionary.json.newer-v2-7").readText() shouldBe newer
        repository.state.value.loadError shouldBe null
    }

    test("a durable copy is used when the main file is damaged or missing, and written back at once") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val copy = SpeechDictionaryDocument.encode(SpeechDictionaryDocument(nextId = 2, words = listOf(VocabularyEntry(1, "Ownkey"))))
        File(dir, "personal_dictionary.json.bak").writeText(copy)
        file.writeText("{ torn")
        val damaged = runBlocking { repository(dir, clock = { 3L }).awaitLoaded() }
        damaged.loadError shouldBe null
        damaged.document.words.map { it.word } shouldBe listOf("Ownkey")
        // The recovered document is on disk again, so a restart before the next edit keeps it.
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Ownkey")
        File(dir, "personal_dictionary.json.unreadable-3").readText() shouldBe "{ torn"

        file.delete()
        val missing = runBlocking { repository(dir).awaitLoaded() }
        missing.document.words.map { it.word } shouldBe listOf("Ownkey")
        file.exists() shouldBe true
    }

    test("a stale copy never shadows a file kept for a newer app, and an upgrade takes that file back") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        File(dir, "personal_dictionary.json.bak").writeText(
            SpeechDictionaryDocument.encode(SpeechDictionaryDocument(nextId = 2, words = listOf(VocabularyEntry(1, "Stale")))),
        )
        val newer = """{"version":2,"nextId":3,"words":[{"id":1,"word":"Ownkey"},{"id":2,"word":"Orukeet"}],"corrections":[],"fillers":{"enabled":false,"languages":["nl"],"custom":[]}}"""
        file.writeText(newer)

        val downgraded = runBlocking { repository(dir, clock = { 5L }).awaitLoaded() }
        downgraded.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        downgraded.document.isEmpty shouldBe true
        File(dir, "personal_dictionary.json.bak").exists() shouldBe false
        File(dir, "personal_dictionary.json.newer-v2-5").readText() shouldBe newer

        // A restart before any edit keeps warning and does not resurrect anything stale.
        val restarted = runBlocking { repository(dir).awaitLoaded() }
        restarted.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        restarted.document.isEmpty shouldBe true
        file.exists() shouldBe false

        // The old app keeps working meanwhile.
        runBlocking { repository(dir).addWord("Meanwhile") }

        // An app that supports version 2 merges the kept file back and removes it.
        val upgraded = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            clock = { 9L },
            supportedVersion = 2,
        )
        val state = runBlocking { upgraded.awaitLoaded() }
        state.loadError shouldBe null
        state.document.words.map { it.word } shouldBe listOf("Meanwhile", "Ownkey", "Orukeet")
        state.document.fillers shouldBe FillerSettings(enabled = false, languages = listOf("nl"), custom = emptyList())
        File(dir, "personal_dictionary.json.newer-v2-5").exists() shouldBe false
        SpeechDictionaryDocument.decode(file.readText()).let { written ->
            written.version shouldBe 2
            written.words.map { it.word } shouldBe listOf("Meanwhile", "Ownkey", "Orukeet")
        }
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

        test("restore rejects blank or identical rows before touching anything and folds repeated rows") {
            val dir = temp()
            val repository = repository(dir)
            runBlocking { repository.addWord("Keep me") }
            val before = repository.state.value.document
            val blankWord = SpeechDictionaryDocument(words = listOf(VocabularyEntry(1, " "), VocabularyEntry(2, "A")))
            shouldThrow<RestoreRejectedException> { runBlocking { repository.restore(blankWord, merge = false) } }
            val identical = SpeechDictionaryDocument(corrections = listOf(CorrectionEntry(1, "x", "x")))
            shouldThrow<RestoreRejectedException> { runBlocking { repository.restore(identical, merge = false) } }
            val incomplete = SpeechDictionaryDocument(corrections = listOf(CorrectionEntry(1, "x", " ")))
            shouldThrow<RestoreRejectedException> { runBlocking { repository.restore(incomplete, merge = true) } }
            repository.state.value.document shouldBeSameInstanceAs before

            val repeated = SpeechDictionaryDocument(
                words = listOf(VocabularyEntry(2, "A"), VocabularyEntry(3, "a")),
                corrections = listOf(CorrectionEntry(5, "own key", "Ownkey"), CorrectionEntry(6, "OWN KEY", "Other")),
                fillers = FillerSettings(enabled = false, languages = listOf("xx", "es"), custom = listOf("")),
            )
            runBlocking { repository.restore(repeated, merge = false) }
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
