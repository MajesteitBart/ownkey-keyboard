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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Immutable view of the dictionary taken at recording start. It carries the compiled cleaner so a
 * dictionary edit during a recording only affects the next recording.
 */
class SpeechDictionarySnapshot(
    val vocabulary: List<String>,
    val corrections: List<CorrectionRule>,
    val fillers: FillerSettings,
) {
    val fillerWords: List<String> = FillerRules.fillerWords(fillers.languages, fillers.custom)
    val cleaner: TranscriptCleaner = TranscriptCleaner(
        CleanupSettings(
            removeFillers = fillers.enabled,
            fillerWords = fillerWords,
            corrections = corrections,
        ),
    )

    companion object {
        fun from(document: SpeechDictionaryDocument): SpeechDictionarySnapshot = SpeechDictionarySnapshot(
            vocabulary = TranscriptCleanup.normalizeVocabulary(document.words.map { it.word }),
            corrections = TranscriptCleanup.normalizeCorrections(
                document.corrections.map { CorrectionRule(it.source, it.replacement) },
            ),
            fillers = document.fillers.copy(
                languages = FillerRules.normalizeLanguages(document.fillers.languages),
                custom = TranscriptCleanup.normalizeVocabulary(document.fillers.custom),
            ),
        )
    }
}

/** A removed entry with enough context to put it back at the same position for undo. */
data class RemovedEntry(val entry: SpeechDictionaryEntry, val index: Int)

enum class SpeechDictionaryLoadError {
    /** The saved file could not be parsed. It was kept next to the new file for inspection. */
    UNREADABLE,

    /**
     * The saved file was written by a newer app. It was kept untouched next to the new file so a
     * later upgrade can use it; an older app must not rewrite it as an older schema.
     */
    NEWER_VERSION,
}

data class SpeechDictionaryState(
    val document: SpeechDictionaryDocument,
    val loaded: Boolean,
    val loadError: SpeechDictionaryLoadError? = null,
)

class RestoreRejectedException(message: String) : IllegalStateException(message)

/**
 * Dedicated, file-backed speech dictionary.
 *
 * Every mutation rewrites the whole document through a temporary file and an atomic move, so a
 * crash mid-write leaves the previous document intact. The document stays small (hundreds of
 * entries at most), so there is no partial loading and no database schema to migrate. Nothing in
 * this class ever runs on the input path: dictation reads a prepared [SpeechDictionarySnapshot].
 */
class SpeechDictionaryRepository(
    private val file: File,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SpeechDictionaryState(SpeechDictionaryDocument(), loaded = false))
    val state: StateFlow<SpeechDictionaryState> = _state

    @Volatile
    private var compiled: Pair<SpeechDictionaryDocument, SpeechDictionarySnapshot>? = null

    private val initialized: Deferred<Unit> = scope.async(ioDispatcher) { load() }

    suspend fun awaitLoaded(): SpeechDictionaryState {
        initialized.await()
        return _state.value
    }

    /** Compiles once per document version, off the UI and input threads, and caches the result. */
    suspend fun snapshot(): SpeechDictionarySnapshot {
        initialized.await()
        val document = _state.value.document
        compiled?.let { (source, snapshot) -> if (source === document) return snapshot }
        val snapshot = withContext(computeDispatcher) { SpeechDictionarySnapshot.from(document) }
        compiled = document to snapshot
        return snapshot
    }

    suspend fun addWord(word: String): EntryResult = mutate { document ->
        SpeechDictionaryValidation.validateWord(document, word)?.let { return@mutate document to EntryResult.Rejected(it) }
        val entry = VocabularyEntry(document.nextId, TranscriptCleanup.normalizeTerm(word), clock())
        document.copy(nextId = document.nextId + 1, words = document.words + entry) to
            EntryResult.Saved(SpeechDictionaryEntry.Word(entry))
    }

    suspend fun addCorrection(source: String, replacement: String): EntryResult = mutate { document ->
        SpeechDictionaryValidation.validateCorrection(document, source, replacement)
            ?.let { return@mutate document to EntryResult.Rejected(it) }
        val entry = CorrectionEntry(
            id = document.nextId,
            source = TranscriptCleanup.normalizeTerm(source),
            replacement = TranscriptCleanup.normalizeTerm(replacement),
            createdAt = clock(),
        )
        document.copy(nextId = document.nextId + 1, corrections = document.corrections + entry) to
            EntryResult.Saved(SpeechDictionaryEntry.Correction(entry))
    }

    /** Adds the correction, or updates the replacement of an existing rule with the same source. */
    suspend fun upsertCorrection(source: String, replacement: String): EntryResult = mutate { document ->
        val key = TranscriptCleanup.normalizeTerm(source).lowercase()
        val existing = document.corrections.firstOrNull { it.source.lowercase() == key }
        if (existing == null) {
            SpeechDictionaryValidation.validateCorrection(document, source, replacement)
                ?.let { return@mutate document to EntryResult.Rejected(it) }
            val entry = CorrectionEntry(
                id = document.nextId,
                source = TranscriptCleanup.normalizeTerm(source),
                replacement = TranscriptCleanup.normalizeTerm(replacement),
                createdAt = clock(),
            )
            document.copy(nextId = document.nextId + 1, corrections = document.corrections + entry) to
                EntryResult.Saved(SpeechDictionaryEntry.Correction(entry))
        } else {
            SpeechDictionaryValidation.validateCorrection(document, existing.source, replacement, existing.id)
                ?.let { return@mutate document to EntryResult.Rejected(it) }
            val updated = existing.copy(replacement = TranscriptCleanup.normalizeTerm(replacement))
            document.copy(corrections = document.corrections.map { if (it.id == existing.id) updated else it }) to
                EntryResult.Saved(SpeechDictionaryEntry.Correction(updated))
        }
    }

    suspend fun updateWord(id: Long, word: String): EntryResult = mutate { document ->
        val existing = document.words.firstOrNull { it.id == id }
            ?: return@mutate document to EntryResult.Rejected(EntryError.BLANK_WORD)
        SpeechDictionaryValidation.validateWord(document, word, id)?.let { return@mutate document to EntryResult.Rejected(it) }
        val updated = existing.copy(word = TranscriptCleanup.normalizeTerm(word))
        document.copy(words = document.words.map { if (it.id == id) updated else it }) to
            EntryResult.Saved(SpeechDictionaryEntry.Word(updated))
    }

    suspend fun updateCorrection(id: Long, source: String, replacement: String): EntryResult = mutate { document ->
        val existing = document.corrections.firstOrNull { it.id == id }
            ?: return@mutate document to EntryResult.Rejected(EntryError.BLANK_SOURCE)
        SpeechDictionaryValidation.validateCorrection(document, source, replacement, id)
            ?.let { return@mutate document to EntryResult.Rejected(it) }
        val updated = existing.copy(
            source = TranscriptCleanup.normalizeTerm(source),
            replacement = TranscriptCleanup.normalizeTerm(replacement),
        )
        document.copy(corrections = document.corrections.map { if (it.id == id) updated else it }) to
            EntryResult.Saved(SpeechDictionaryEntry.Correction(updated))
    }

    suspend fun remove(id: Long): RemovedEntry? = mutate { document ->
        val wordIndex = document.words.indexOfFirst { it.id == id }
        if (wordIndex >= 0) {
            val entry = document.words[wordIndex]
            return@mutate document.copy(words = document.words.filterIndexed { index, _ -> index != wordIndex }) to
                RemovedEntry(SpeechDictionaryEntry.Word(entry), wordIndex)
        }
        val correctionIndex = document.corrections.indexOfFirst { it.id == id }
        if (correctionIndex >= 0) {
            val entry = document.corrections[correctionIndex]
            return@mutate document.copy(
                corrections = document.corrections.filterIndexed { index, _ -> index != correctionIndex },
            ) to RemovedEntry(SpeechDictionaryEntry.Correction(entry), correctionIndex)
        }
        document to null
    }

    /** Puts an entry removed by [remove] back where it was, unless a conflicting entry appeared meanwhile. */
    suspend fun restoreRemoved(removed: RemovedEntry): Boolean = mutate { document ->
        when (val entry = removed.entry) {
            is SpeechDictionaryEntry.Word -> {
                if (SpeechDictionaryValidation.validateWord(document, entry.entry.word) != null) return@mutate document to false
                val words = document.words.toMutableList()
                words.add(removed.index.coerceIn(0, words.size), entry.entry)
                document.copy(words = words) to true
            }
            is SpeechDictionaryEntry.Correction -> {
                val error = SpeechDictionaryValidation.validateCorrection(document, entry.entry.source, entry.entry.replacement)
                if (error != null) return@mutate document to false
                val corrections = document.corrections.toMutableList()
                corrections.add(removed.index.coerceIn(0, corrections.size), entry.entry)
                document.copy(corrections = corrections) to true
            }
        }
    }

    suspend fun setFillersEnabled(enabled: Boolean) = mutate { document ->
        document.copy(fillers = document.fillers.copy(enabled = enabled)) to Unit
    }

    /** An empty selection is stored as empty and stays empty; only custom fillers are removed then. */
    suspend fun setFillerLanguages(codes: List<String>) = mutate { document ->
        document.copy(fillers = document.fillers.copy(languages = FillerRules.normalizeLanguages(codes))) to Unit
    }

    /** Toggles one language against the current document under the lock, so quick taps never overwrite each other. */
    suspend fun setFillerLanguage(code: String, enabled: Boolean) = mutate { document ->
        val current = FillerRules.normalizeLanguages(document.fillers.languages)
        val next = if (enabled) current + code else current - code
        document.copy(fillers = document.fillers.copy(languages = FillerRules.normalizeLanguages(next))) to Unit
    }

    suspend fun setCustomFillers(words: List<String>) = mutate { document ->
        document.copy(fillers = document.fillers.copy(custom = TranscriptCleanup.normalizeVocabulary(words))) to Unit
    }

    /** A portable copy for backups. */
    suspend fun export(): SpeechDictionaryDocument {
        initialized.await()
        return _state.value.document
    }

    /**
     * Restores from a backup section. Validation happens before anything is written, so an invalid
     * or newer document leaves the current entries untouched. Merge keeps existing entries and adds
     * the ones that are missing; erase replaces the entries. Filler settings follow the backup in
     * both modes because they are one setting, not a list to merge.
     */
    suspend fun restore(backup: SpeechDictionaryDocument, merge: Boolean) {
        if (backup.version > SpeechDictionaryDocument.CURRENT_VERSION) {
            throw RestoreRejectedException(
                "Personal dictionary backup version ${backup.version} is newer than this app supports.",
            )
        }
        if (backup.version < 1) throw RestoreRejectedException("Personal dictionary backup has an invalid version.")
        mutate { document ->
            val base = if (merge) document else document.copy(words = emptyList(), corrections = emptyList())
            var next = base.copy(nextId = maxOf(base.nextId, 1L))
            for (word in backup.words) {
                if (SpeechDictionaryValidation.validateWord(next, word.word) != null) continue
                val entry = VocabularyEntry(next.nextId, TranscriptCleanup.normalizeTerm(word.word), word.createdAt)
                next = next.copy(nextId = next.nextId + 1, words = next.words + entry)
            }
            for (correction in backup.corrections) {
                if (SpeechDictionaryValidation.validateCorrection(next, correction.source, correction.replacement) != null) continue
                val entry = CorrectionEntry(
                    id = next.nextId,
                    source = TranscriptCleanup.normalizeTerm(correction.source),
                    replacement = TranscriptCleanup.normalizeTerm(correction.replacement),
                    createdAt = correction.createdAt,
                )
                next = next.copy(nextId = next.nextId + 1, corrections = next.corrections + entry)
            }
            next = next.copy(
                fillers = FillerSettings(
                    enabled = backup.fillers.enabled,
                    languages = FillerRules.normalizeLanguages(backup.fillers.languages),
                    custom = TranscriptCleanup.normalizeVocabulary(backup.fillers.custom),
                ),
            )
            next to Unit
        }
    }

    private suspend fun <R> mutate(transform: (SpeechDictionaryDocument) -> Pair<SpeechDictionaryDocument, R>): R {
        initialized.await()
        return mutex.withLock {
            val current = _state.value
            val (next, result) = transform(current.document)
            if (next !== current.document) {
                val versioned = next.copy(version = SpeechDictionaryDocument.CURRENT_VERSION)
                withContext(ioDispatcher) { write(versioned) }
                _state.value = SpeechDictionaryState(versioned, loaded = true, loadError = null)
            }
            result
        }
    }

    private fun load() {
        if (!file.exists()) {
            // A crash between moving a damaged file aside and rewriting it leaves only the copy.
            val recovered = recoverFromBackup()
            if (recovered != null) {
                runCatching { write(recovered) }
                _state.value = SpeechDictionaryState(recovered, loaded = true)
            } else {
                _state.value = SpeechDictionaryState(SpeechDictionaryDocument(), loaded = true)
            }
            return
        }
        val parsed = runCatching { SpeechDictionaryDocument.decode(file.readText(Charsets.UTF_8)) }
        parsed.onSuccess { document ->
            if (document.version > SpeechDictionaryDocument.CURRENT_VERSION) {
                // Unknown fields are ignored on decode, so saving would silently downgrade the
                // file. Keep it for the newer app and start empty, visibly.
                val kept = File(file.parentFile, "${file.name}.newer-v${document.version}-${clock()}")
                runCatching { Files.move(file.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                _state.value = SpeechDictionaryState(
                    SpeechDictionaryDocument(),
                    loaded = true,
                    loadError = SpeechDictionaryLoadError.NEWER_VERSION,
                )
                return
            }
            _state.value = SpeechDictionaryState(document, loaded = true)
        }.onFailure {
            // Keep the unreadable file for inspection instead of overwriting it on the next save.
            val kept = File(file.parentFile, "${file.name}.unreadable-${clock()}")
            runCatching { Files.move(file.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            // A durable copy only exists after a non-atomic write path; use it when it parses, and
            // write it back so a restart before the next edit does not start empty.
            val recovered = recoverFromBackup()
            _state.value = if (recovered != null) {
                runCatching { write(recovered) }
                SpeechDictionaryState(recovered, loaded = true)
            } else {
                SpeechDictionaryState(
                    SpeechDictionaryDocument(),
                    loaded = true,
                    loadError = SpeechDictionaryLoadError.UNREADABLE,
                )
            }
        }
    }

    private fun write(document: SpeechDictionaryDocument) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(SpeechDictionaryDocument.encode(document).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            // Without an atomic rename a crash mid-replace could leave a torn file. Keep the
            // previous document as a durable copy first; load() falls back to it when the main
            // file is unreadable.
            if (file.exists()) Files.copy(file.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun backupFile(): File = File(file.parentFile, "${file.name}.bak")

    private fun recoverFromBackup(): SpeechDictionaryDocument? = backupFile().takeIf { it.exists() }
        ?.let { backup -> runCatching { SpeechDictionaryDocument.decode(backup.readText(Charsets.UTF_8)) }.getOrNull() }
        ?.takeIf { it.version <= SpeechDictionaryDocument.CURRENT_VERSION }
}
