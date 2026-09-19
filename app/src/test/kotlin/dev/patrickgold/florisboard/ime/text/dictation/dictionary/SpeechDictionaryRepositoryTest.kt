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
import kotlinx.coroutines.launch
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

    test("cancellation after IO starts but before replacement preserves disk and memory") {
        val dir = temp()
        val cancelOnWrite = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>()
        val file = object : File(dir, "personal_dictionary.json") {
            override fun getParentFile(): File? {
                // write() first asks for the parent directory after entering its IO block.
                cancelOnWrite.getAndSet(null)?.cancel()
                return super.getParentFile()
            }
        }
        val repository = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        runBlocking {
            repository.addWord("Existing")
            val before = repository.state.value
            val save = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                repository.addWord("Cancelled", cancelBeforeCommit = true)
            }
            cancelOnWrite.set(save)
            save.start()
            save.join()
            save.isCancelled shouldBe true
            repository.state.value shouldBeSameInstanceAs before
        }
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Existing")
        File(dir, "personal_dictionary.json.tmp").exists() shouldBe false
        runBlocking { repository.addWord("Later") }
        runBlocking { repository(dir).awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Existing", "Later")
    }

    test("cancelling after a quarantine retry preserves the newer file for an upgrade") {
        val dir = temp()
        val cancelOnWrite = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>()
        val file = object : File(dir, "personal_dictionary.json") {
            override fun getParentFile(): File? {
                if (!exists()) cancelOnWrite.getAndSet(null)?.cancel()
                return super.getParentFile()
            }
        }
        val newer = SpeechDictionaryDocument.encode(SpeechDictionaryDocument(
            version = 2, nextId = 2, words = listOf(VocabularyEntry(1, "Ownkey")),
        ))
        file.writeText(newer)
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        val child = File(kept, "child").apply { parentFile.mkdirs(); writeText("blocked") }
        val store = SpeechDictionaryRepository(
            file = file, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default), clock = { 5L },
        )
        runBlocking {
            val before = store.awaitLoaded()
            before.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
            child.delete() shouldBe true
            kept.delete() shouldBe true
            val save = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                store.addWord("Cancelled", cancelBeforeCommit = true)
            }
            cancelOnWrite.set(save)
            save.start()
            save.join()
            save.isCancelled shouldBe true
            store.state.value shouldBeSameInstanceAs before
        }
        kept.readText() shouldBe newer
        file.exists() shouldBe false
        File(dir, "personal_dictionary.json.tmp").exists() shouldBe false
        runBlocking { repository(dir).awaitLoaded() }.document.words shouldBe emptyList()
        val upgraded = SpeechDictionaryRepository(
            file = File(dir, file.name), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            supportedVersion = 2,
        )
        runBlocking { upgraded.awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Ownkey")
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Ownkey")
    }

    test("combined correction saves reuse existing words and keep entry ids unique") {
        val dir = temp()
        val store = repository(dir)
        runBlocking {
            store.addWord("Ownkey")
            store.upsertCorrection("own key", "OWNKEY", addAsWord = true).shouldBeInstanceOf<EntryResult.Saved>()
            store.upsertCorrection("own key", "Orukeet", addAsWord = true).shouldBeInstanceOf<EntryResult.Saved>()
            store.upsertCorrection("same", "same", addAsWord = true) shouldBe EntryResult.Rejected(EntryError.IDENTICAL)
        }
        val saved = runBlocking { repository(dir).awaitLoaded() }.document
        saved.words.map { it.word } shouldBe listOf("Ownkey", "Orukeet")
        saved.corrections.map { it.source to it.replacement } shouldBe listOf("own key" to "Orukeet")
        (saved.words.map { it.id } + saved.corrections.map { it.id }).sorted() shouldBe listOf(1L, 2L, 3L)
        saved.nextId shouldBe 4L
    }

    test("an interrupted unreadable quarantine stays visible until a valid main is saved") {
        val dir = temp()
        val kept = File(dir, "personal_dictionary.json.unreadable-5").apply { writeText("broken data") }
        repeat(2) {
            val state = runBlocking { repository(dir).awaitLoaded() }
            state.loadError shouldBe SpeechDictionaryLoadError.UNREADABLE
            kept.readText() shouldBe "broken data"
        }
        runBlocking { repository(dir).addWord("Recovered") }
        val reopened = runBlocking { repository(dir).awaitLoaded() }
        reopened.loadError shouldBe null
        reopened.document.words.map { it.word } shouldBe listOf("Recovered")
        kept.readText() shouldBe "broken data"
    }

    test("successful backup recovery resolves an interrupted unreadable quarantine warning") {
        val dir = temp()
        File(dir, "personal_dictionary.json.unreadable-5").writeText("broken data")
        File(dir, "personal_dictionary.json.bak").writeText(SpeechDictionaryDocument.encode(
            SpeechDictionaryDocument(nextId = 2, words = listOf(VocabularyEntry(1, "Recovered"))),
        ))
        val restored = runBlocking { repository(dir).awaitLoaded() }
        restored.loadError shouldBe null
        restored.document.words.map { it.word } shouldBe listOf("Recovered")
        runBlocking { repository(dir).awaitLoaded() }.loadError shouldBe null
    }

    test("an accepted settings save survives navigation cancelling its caller") {
        val dir = temp()
        val cancelOnWrite = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>()
        val file = object : File(dir, "personal_dictionary.json") {
            override fun getParentFile(): File? {
                cancelOnWrite.getAndSet(null)?.cancel()
                return super.getParentFile()
            }
        }
        val store = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        runBlocking {
            store.addWord("Existing")
            val save = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { store.addWord("Saved") }
            cancelOnWrite.set(save)
            save.start()
            save.join()
            store.state.value.document.words.map { it.word } shouldBe listOf("Existing", "Saved")
            store.state.value.saveError shouldBe false
        }
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Existing", "Saved")
        runBlocking { repository(dir).awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Existing", "Saved")
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
            // Codes are canonicalised before adding or removing, so `NL` removes the stored `nl`.
            repository.setFillerLanguage(" NL ", false)
            repository.setFillerLanguage("DE", true)
            repository.state.value.document.fillers.languages shouldBe listOf("de", "fr")
        }
    }

    test("a leftover copy is not restored while a file kept for a newer app is waiting") {
        val dir = temp()
        File(dir, "personal_dictionary.json.newer-v2-5").writeText(
            """{"version":2,"nextId":2,"words":[{"id":1,"word":"Ownkey"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""",
        )
        File(dir, "personal_dictionary.json.bak").writeText(
            SpeechDictionaryDocument.encode(SpeechDictionaryDocument(nextId = 2, words = listOf(VocabularyEntry(1, "Stale")))),
        )
        val state = runBlocking { repository(dir).awaitLoaded() }
        state.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        state.document.isEmpty shouldBe true
        File(dir, "personal_dictionary.json").exists() shouldBe false
    }

    test("an upgrade after interrupted quarantine never restores a stale backup ahead of the kept main") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val newer = SpeechDictionaryDocument(
            version = 2,
            nextId = 3,
            words = listOf(VocabularyEntry(1, "Current")),
            corrections = listOf(CorrectionEntry(2, "own key", "Ownkey")),
        )
        // State left by a crash after the main rename but before the old backup was removed.
        File(dir, "personal_dictionary.json.newer-v2-5").writeText(SpeechDictionaryDocument.encode(newer))
        File(dir, "personal_dictionary.json.bak").writeText(SpeechDictionaryDocument.encode(
            SpeechDictionaryDocument(
                nextId = 3,
                words = listOf(VocabularyEntry(1, "Deleted")),
                corrections = listOf(CorrectionEntry(2, "own key", "Outdated")),
            ),
        ))
        fun upgraded() = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            supportedVersion = 2,
        )
        val state = runBlocking { upgraded().awaitLoaded() }
        state.loadError shouldBe null
        state.document.words.map { it.word } shouldBe listOf("Current")
        state.document.corrections.map { it.replacement } shouldBe listOf("Ownkey")
        val persisted = SpeechDictionaryDocument.decode(file.readText())
        persisted.version shouldBe 2
        persisted.words.map { it.word } shouldBe listOf("Current")
        persisted.corrections.map { it.replacement } shouldBe listOf("Ownkey")
        File(dir, "personal_dictionary.json.bak").exists() shouldBe true
        File(dir, "personal_dictionary.json.newer-v2-5").exists() shouldBe false
        // The next restart uses the newly persisted main, without absorbing the old file again.
        runBlocking { upgraded().awaitLoaded() }.document shouldBe persisted
    }

    test("a failed stale backup deletion keeps the newer main in place until quarantine can safely retry") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val newer = SpeechDictionaryDocument.encode(SpeechDictionaryDocument(version = 2))
        file.writeText(newer)
        // A non-empty directory makes deletion fail on both Windows and Unix.
        val backup = File(dir, "personal_dictionary.json.bak")
        val child = File(backup, "child").apply { parentFile!!.mkdirs(); writeText("blocked") }
        val repository = repository(dir, clock = { 5L })
        runBlocking { repository.awaitLoaded() }.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        file.exists() shouldBe true
        file.readText() shouldBe newer
        File(dir, "personal_dictionary.json.newer-v2-5").exists() shouldBe false
        runBlocking { repository.addWord("Meanwhile") }
        repository.state.value.saveError shouldBe true
        file.readText() shouldBe newer

        child.delete() shouldBe true
        runBlocking { repository.addWord("Later") }
        repository.state.value.saveError shouldBe false
        backup.exists() shouldBe false
        File(dir, "personal_dictionary.json.newer-v2-5").readText() shouldBe newer
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Meanwhile", "Later")
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

    test("a newer file that cannot be moved aside is never overwritten, and edits wait in memory") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val newer = """{"version":2,"nextId":2,"words":[{"id":1,"word":"Ownkey"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}"""
        file.writeText(newer)
        File(dir, "personal_dictionary.json.bak").writeText("stale")
        // A non-empty directory at the quarantine target makes the move fail.
        val blocker = File(dir, "personal_dictionary.json.newer-v2-5")
        File(blocker, "child").apply { parentFile.mkdirs(); writeText("x") }
        val repository = repository(dir, clock = { 5L })
        val state = runBlocking { repository.awaitLoaded() }
        state.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        file.readText() shouldBe newer
        // The stale backup is retired first; a failed main rename still preserves the newer data.
        File(dir, "personal_dictionary.json.bak").exists() shouldBe false

        runBlocking { repository.addWord("Meanwhile") }
        // The edit is served but nothing on disk changed; the warning stays, the change counts as
        // unsaved, and a restore in this state reports failure.
        repository.state.value.document.words.map { it.word } shouldBe listOf("Meanwhile")
        repository.state.value.loadError shouldBe SpeechDictionaryLoadError.NEWER_VERSION
        repository.state.value.saveError shouldBe true
        runBlocking { repository.restore(SpeechDictionaryDocument(words = listOf(VocabularyEntry(1, "Restored"))), merge = true) } shouldBe false
        file.readText() shouldBe newer

        // Once the move can succeed, the next edit quarantines the newer file and lands on disk.
        blocker.deleteRecursively()
        runBlocking { repository.addWord("Later") }
        repository.state.value.loadError shouldBe null
        repository.state.value.saveError shouldBe false
        blocker.readText() shouldBe newer
        File(dir, "personal_dictionary.json.bak").exists() shouldBe false
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Meanwhile", "Restored", "Later")
    }

    test("an unreadable file that cannot be moved aside is never overwritten either") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        file.writeText("{ torn")
        File(dir, "personal_dictionary.json.bak").writeText(
            SpeechDictionaryDocument.encode(SpeechDictionaryDocument(nextId = 2, words = listOf(VocabularyEntry(1, "Copy")))),
        )
        val blocker = File(dir, "personal_dictionary.json.unreadable-3")
        File(blocker, "child").apply { parentFile.mkdirs(); writeText("x") }
        val repository = repository(dir, clock = { 3L })
        val state = runBlocking { repository.awaitLoaded() }
        // The copy is served, but the torn original stays where it is and nothing is written.
        state.document.words.map { it.word } shouldBe listOf("Copy")
        state.loadError shouldBe SpeechDictionaryLoadError.UNREADABLE
        file.readText() shouldBe "{ torn"

        runBlocking { repository.addWord("Meanwhile") }
        repository.state.value.document.words.map { it.word } shouldBe listOf("Copy", "Meanwhile")
        repository.state.value.loadError shouldBe SpeechDictionaryLoadError.UNREADABLE
        repository.state.value.saveError shouldBe true
        file.readText() shouldBe "{ torn"

        blocker.deleteRecursively()
        runBlocking { repository.addWord("Later") }
        repository.state.value.loadError shouldBe null
        blocker.readText() shouldBe "{ torn"
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Copy", "Meanwhile", "Later")
    }

    test("an upgraded app labels the loaded and exported document with the version it writes") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        file.writeText("""{"version":1,"nextId":2,"words":[{"id":1,"word":"Main"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""")
        File(dir, "personal_dictionary.json.newer-v2-5").writeText(
            """{"version":2,"nextId":2,"words":[{"id":1,"word":"Kept"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""",
        )
        val upgraded = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            supportedVersion = 2,
        )
        val state = runBlocking { upgraded.awaitLoaded() }
        state.document.words.map { it.word } shouldBe listOf("Main", "Kept")
        // A backup taken before the next edit must not claim the older schema: an older app would
        // accept it and drop what it does not know instead of rejecting it.
        state.document.version shouldBe 2
        runBlocking { upgraded.export() }.version shouldBe 2
        SpeechDictionaryDocument.decode(file.readText()).version shouldBe 2

        // The same holds without anything to absorb: an older main file alone.
        val plainDir = temp()
        val plain = File(plainDir, "personal_dictionary.json")
        plain.writeText("""{"version":1,"nextId":2,"words":[{"id":1,"word":"Main"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""")
        val plainRepository = SpeechDictionaryRepository(
            file = plain,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            supportedVersion = 2,
        )
        runBlocking { plainRepository.export() }.version shouldBe 2
    }

    test("a kept file survives a failed merge write and is retired by the next successful one") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        kept.writeText("""{"version":2,"nextId":2,"words":[{"id":1,"word":"Ownkey"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""")
        // A non-empty directory in the temp file's place makes every write fail.
        val blocker = File(dir, "personal_dictionary.json.tmp")
        File(blocker, "child").apply { parentFile.mkdirs(); writeText("x") }
        val upgraded = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            supportedVersion = 2,
        )
        val state = runBlocking { upgraded.awaitLoaded() }
        state.document.words.map { it.word } shouldBe listOf("Ownkey")
        state.saveError shouldBe true
        file.exists() shouldBe false
        kept.exists() shouldBe true

        // The user removes the merged entry while storage still fails: reported, not thrown.
        runBlocking { upgraded.remove(1L) } shouldNotBe null
        upgraded.state.value.saveError shouldBe true
        upgraded.state.value.document.words shouldBe emptyList()
        kept.exists() shouldBe true

        // Storage works again: the next change lands and the kept file goes, so a restart cannot
        // resurrect the removed entry.
        blocker.deleteRecursively()
        runBlocking { upgraded.addWord("Later") }
        upgraded.state.value.saveError shouldBe false
        kept.exists() shouldBe false
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("Later")
        val reloaded = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            supportedVersion = 2,
        )
        runBlocking { reloaded.awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Later")
    }

    test("restart after main replacement but before quarantine retirement cannot resurrect removed entries") {
        val dir = temp()
        val crashDir = temp()
        val file = File(dir, "personal_dictionary.json")
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        kept.writeText(SpeechDictionaryDocument.encode(SpeechDictionaryDocument(
            version = 2, nextId = 2, words = listOf(VocabularyEntry(1, "Recovered")),
        )))
        val blocker = File(dir, "personal_dictionary.json.tmp")
        val child = File(blocker, "child").apply { parentFile.mkdirs(); writeText("blocked") }
        var capture = false
        val store = SpeechDictionaryRepository(
            file = file, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default), supportedVersion = 2,
            deleteKeptFile = {
                if (capture) {
                    // Snapshot the exact crash window: committed main plus unretired quarantine,
                    // before any legacy marker can be recorded. Resume from those bytes below.
                    File(crashDir, file.name).writeBytes(file.readBytes())
                    File(crashDir, kept.name).writeBytes(kept.readBytes())
                    capture = false
                }
                false
            },
        )
        runBlocking { store.awaitLoaded() }.saveError shouldBe true
        runBlocking { store.remove(1L) }
        child.delete() shouldBe true
        blocker.delete() shouldBe true
        capture = true
        runBlocking { store.addWord("Later") }.shouldBeInstanceOf<EntryResult.Saved>().persisted shouldBe true
        capture shouldBe false
        File(crashDir, "personal_dictionary.json.merged").exists() shouldBe false
        val restarted = SpeechDictionaryRepository(
            file = File(crashDir, file.name), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            supportedVersion = 2, deleteKeptFile = { false },
        )
        runBlocking { restarted.awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Later")
        runBlocking { restarted.export() }.consumedQuarantines shouldBe emptySet()
    }

    test("fallback backup with an inline consumed record does not remerge its kept file") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        kept.writeText(SpeechDictionaryDocument.encode(SpeechDictionaryDocument(
            version = 2, nextId = 2, words = listOf(VocabularyEntry(1, "Removed")),
        )))
        File(dir, "${file.name}.bak").writeText(SpeechDictionaryDocument.encode(SpeechDictionaryDocument(
            version = 2, nextId = 3, words = listOf(VocabularyEntry(2, "Later")),
            consumedQuarantines = setOf(kept.name),
        )))
        val store = SpeechDictionaryRepository(
            file = file, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            supportedVersion = 2, deleteKeptFile = { false },
        )
        runBlocking { store.awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Later")
        SpeechDictionaryDocument.decode(file.readText()).consumedQuarantines shouldBe setOf(kept.name)
    }

    test("inline consumed record prevents resurrection even if a legacy marker cannot be written") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        kept.writeText(SpeechDictionaryDocument.encode(SpeechDictionaryDocument(
            version = 2, nextId = 2, words = listOf(VocabularyEntry(1, "Recovered")),
        )))
        val marker = File(dir, "personal_dictionary.json.merged")
        val child = File(marker, "child").apply { parentFile.mkdirs(); writeText("blocked") }
        fun upgraded() = SpeechDictionaryRepository(
            file = file, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            supportedVersion = 2, deleteKeptFile = { false },
        )
        val store = upgraded()
        runBlocking { store.awaitLoaded() }.saveError shouldBe false
        runBlocking { store.remove(1L) } shouldNotBe null
        store.state.value.saveError shouldBe false
        val pending = runBlocking { store.addWord("Later") }.shouldBeInstanceOf<EntryResult.Saved>()
        pending.persisted shouldBe true
        SpeechDictionaryDocument.decode(file.readText()).consumedQuarantines shouldBe setOf(kept.name)
        runBlocking { upgraded().awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Later")
        child.delete() shouldBe true
        marker.delete() shouldBe true
        runBlocking { store.updateWord(pending.entry.id, "Later") }
            .shouldBeInstanceOf<EntryResult.Saved>().persisted shouldBe true
        marker.readLines() shouldBe listOf(kept.name)
        val reopened = runBlocking { upgraded().awaitLoaded() }
        reopened.saveError shouldBe false
        reopened.document.words.map { it.word } shouldBe listOf("Later")
        kept.exists() shouldBe true
    }

    test("a kept file that cannot be deleted is not merged again after a restart") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val kept = File(dir, "personal_dictionary.json.newer-v2-5")
        val marker = File(dir, "personal_dictionary.json.merged")
        kept.writeText("""{"version":2,"nextId":2,"words":[{"id":1,"word":"Ownkey"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}""")
        var deletable = false
        fun upgraded() = SpeechDictionaryRepository(
            file = file,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ioDispatcher = Dispatchers.IO,
            computeDispatcher = Dispatchers.Default,
            supportedVersion = 2,
            deleteKeptFile = { deletable && it.delete() },
        )

        // The merge is written, the kept file stays, and that is recorded next to the main file.
        val first = upgraded()
        runBlocking { first.awaitLoaded() }.document.words.map { it.word } shouldBe listOf("Ownkey")
        kept.exists() shouldBe true
        marker.readLines() shouldBe listOf(kept.name)

        // The user removes the merged entry; the write succeeds, the deletion still fails.
        runBlocking { first.remove(1L) } shouldNotBe null
        first.state.value.saveError shouldBe false
        kept.exists() shouldBe true

        // A restart must not bring the removed entry back from the file that is still there.
        val second = upgraded()
        runBlocking { second.awaitLoaded() }.document.words shouldBe emptyList()
        kept.exists() shouldBe true
        marker.readLines() shouldBe listOf(kept.name)

        // Once the deletion works, the next start cleans up the file and the record of it.
        deletable = true
        val third = upgraded()
        runBlocking { third.awaitLoaded() }.document.words shouldBe emptyList()
        kept.exists() shouldBe false
        marker.exists() shouldBe false
    }

    test("a file with a version below one is kept aside as unreadable, never relabelled and exported") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val invalid = """{"version":0,"nextId":2,"words":[{"id":1,"word":"Ghost"}],"corrections":[],"fillers":{"enabled":true,"languages":["en"],"custom":[]}}"""
        file.writeText(invalid)
        val repository = repository(dir)
        val state = runBlocking { repository.awaitLoaded() }
        state.loadError shouldBe SpeechDictionaryLoadError.UNREADABLE
        state.document.words shouldBe emptyList()
        runBlocking { repository.export() }.words shouldBe emptyList()
        dir.listFiles()!!.single { it.name.contains(".unreadable-") }.readText() shouldBe invalid
    }

    test("a storage failure during an ordinary edit is reported in the state, not thrown") {
        val dir = temp()
        val file = File(dir, "personal_dictionary.json")
        val repository = repository(dir)
        runBlocking { repository.addWord("First") }
        val blocker = File(dir, "personal_dictionary.json.tmp")
        File(blocker, "child").apply { parentFile.mkdirs(); writeText("x") }
        val pending = runBlocking { repository.addWord("Second") }.shouldBeInstanceOf<EntryResult.Saved>()
        pending.persisted shouldBe false
        val correction = runBlocking { repository.addCorrection("own key", "Ownkey") }.shouldBeInstanceOf<EntryResult.Saved>()
        correction.persisted shouldBe false
        runBlocking { repository.updateCorrection(correction.entry.id, "own key", "OWNKEY") }
            .shouldBeInstanceOf<EntryResult.Saved>().persisted shouldBe false
        repository.state.value.saveError shouldBe true
        repository.state.value.document.words.map { it.word } shouldBe listOf("First", "Second")
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("First")
        blocker.deleteRecursively()
        runBlocking { repository.updateWord(pending.entry.id, "Second") }
            .shouldBeInstanceOf<EntryResult.Saved>().persisted shouldBe true
        // The old operation's outcome remains false after the later retry succeeds.
        pending.persisted shouldBe false
        runBlocking { repository.updateCorrection(correction.entry.id, "own key", "Ownkey") }
            .shouldBeInstanceOf<EntryResult.Saved>().persisted shouldBe true
        runBlocking { repository.addWord("Third") }
        repository.state.value.saveError shouldBe false
        SpeechDictionaryDocument.decode(file.readText()).words.map { it.word } shouldBe listOf("First", "Second", "Third")
        SpeechDictionaryDocument.decode(file.readText()).corrections.map { it.source to it.replacement } shouldBe
            listOf("own key" to "Ownkey")
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

        test("a restore whose write storage refused reports failure instead of success") {
            val dir = temp()
            val repository = repository(dir)
            runBlocking { repository.restore(SpeechDictionaryDocument(words = listOf(VocabularyEntry(1, "Ok"))), merge = false) } shouldBe true
            val blocker = File(dir, "personal_dictionary.json.tmp")
            File(blocker, "child").apply { parentFile.mkdirs(); writeText("x") }
            runBlocking { repository.restore(SpeechDictionaryDocument(words = listOf(VocabularyEntry(1, "Lost"))), merge = false) } shouldBe false
            repository.state.value.saveError shouldBe true
            SpeechDictionaryDocument.decode(File(dir, "personal_dictionary.json").readText()).words.map { it.word } shouldBe listOf("Ok")
        }

        test("a corrupt backup section fails to decode before anything is written") {
            shouldThrow<Exception> { SpeechDictionaryDocument.decode("{\"version\": \"one\"}") }
            shouldThrow<Exception> { SpeechDictionaryDocument.decode("nope") }
        }
    }
})
