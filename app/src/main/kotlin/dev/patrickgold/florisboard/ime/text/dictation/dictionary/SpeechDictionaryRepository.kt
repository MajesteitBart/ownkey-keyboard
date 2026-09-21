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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    /** The last change could not be written to storage; it is kept in memory and retried on the next change. */
    val saveError: Boolean = false,
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
    /** Highest document version this app understands; a parameter so upgrade paths can be tested. */
    private val supportedVersion: Int = SpeechDictionaryDocument.CURRENT_VERSION,
    /** Removes a kept file after its entries were merged; a parameter so a failing deletion can be tested. */
    private val deleteKeptFile: (File) -> Boolean = File::delete,
    /** Filesystem seam for testing providers that reject atomic overwrite. */
    private val atomicReplace: (File, File) -> Unit = { source, target ->
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SpeechDictionaryState(SpeechDictionaryDocument(), loaded = false))
    val state: StateFlow<SpeechDictionaryState> = _state

    @Volatile
    private var compiled: Pair<SpeechDictionaryDocument, SpeechDictionarySnapshot>? = null

    /**
     * Set while a file that must be preserved (newer schema or unreadable) still sits at the main
     * path because moving it aside failed; the retry runs before any write. Declared before the
     * loader below, which may run synchronously during construction.
     */
    @Volatile
    private var blockedMainPath: (() -> Boolean)? = null

    /** Kept files already merged into the state whose removal waits for a successful write. */
    @Volatile
    private var pendingConsumed: List<File> = emptyList()

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

    suspend fun addWord(word: String, cancelBeforeCommit: Boolean = false): EntryResult = mutateEntry(cancelBeforeCommit) { document ->
        SpeechDictionaryValidation.validateWord(document, word)?.let { return@mutateEntry document to EntryResult.Rejected(it) }
        val entry = VocabularyEntry(document.nextId, TranscriptCleanup.normalizeTerm(word), clock())
        document.copy(nextId = document.nextId + 1, words = document.words + entry) to
            EntryResult.Saved(SpeechDictionaryEntry.Word(entry))
    }

    suspend fun addCorrection(source: String, replacement: String): EntryResult = mutateEntry { document ->
        SpeechDictionaryValidation.validateCorrection(document, source, replacement)
            ?.let { return@mutateEntry document to EntryResult.Rejected(it) }
        val entry = CorrectionEntry(
            id = document.nextId,
            source = TranscriptCleanup.normalizeTerm(source),
            replacement = TranscriptCleanup.normalizeTerm(replacement),
            createdAt = clock(),
        )
        document.copy(nextId = document.nextId + 1, corrections = document.corrections + entry) to
            EntryResult.Saved(SpeechDictionaryEntry.Correction(entry))
    }

    /** Upserts the correction and, optionally, its vocabulary word in one persisted document. */
    suspend fun upsertCorrection(
        source: String,
        replacement: String,
        cancelBeforeCommit: Boolean = false,
        addAsWord: Boolean = false,
    ): EntryResult = mutateEntry(cancelBeforeCommit) { document ->
        val key = TranscriptCleanup.normalizeTerm(source).lowercase()
        val existing = document.corrections.firstOrNull { it.source.lowercase() == key }
        SpeechDictionaryValidation.validateCorrection(document, existing?.source ?: source, replacement, existing?.id)
            ?.let { return@mutateEntry document to EntryResult.Rejected(it) }
        val normalizedReplacement = TranscriptCleanup.normalizeTerm(replacement)
        val entry = existing?.copy(replacement = normalizedReplacement) ?: CorrectionEntry(
            id = document.nextId,
            source = TranscriptCleanup.normalizeTerm(source),
            replacement = normalizedReplacement,
            createdAt = clock(),
        )
        var next = if (existing == null) {
            document.copy(nextId = document.nextId + 1, corrections = document.corrections + entry)
        } else {
            document.copy(corrections = document.corrections.map { if (it.id == existing.id) entry else it })
        }
        if (addAsWord && next.words.none { it.word.lowercase() == normalizedReplacement.lowercase() }) {
            val word = VocabularyEntry(next.nextId, normalizedReplacement, clock())
            next = next.copy(nextId = next.nextId + 1, words = next.words + word)
        }
        next to EntryResult.Saved(SpeechDictionaryEntry.Correction(entry))
    }

    suspend fun updateWord(id: Long, word: String): EntryResult = mutateEntry { document ->
        val existing = document.words.firstOrNull { it.id == id }
            ?: return@mutateEntry document to EntryResult.Rejected(EntryError.BLANK_WORD)
        SpeechDictionaryValidation.validateWord(document, word, id)?.let { return@mutateEntry document to EntryResult.Rejected(it) }
        val updated = existing.copy(word = TranscriptCleanup.normalizeTerm(word))
        document.copy(words = document.words.map { if (it.id == id) updated else it }) to
            EntryResult.Saved(SpeechDictionaryEntry.Word(updated))
    }

    suspend fun updateCorrection(id: Long, source: String, replacement: String): EntryResult = mutateEntry { document ->
        val existing = document.corrections.firstOrNull { it.id == id }
            ?: return@mutateEntry document to EntryResult.Rejected(EntryError.BLANK_SOURCE)
        SpeechDictionaryValidation.validateCorrection(document, source, replacement, id)
            ?.let { return@mutateEntry document to EntryResult.Rejected(it) }
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
        // Canonical code first, so disabling `EN` removes the stored `en`; unknown codes change nothing.
        val canonical = FillerLanguage.fromCode(code)?.code ?: return@mutate document to Unit
        val next = if (enabled) current + canonical else current - canonical
        document.copy(fillers = document.fillers.copy(languages = FillerRules.normalizeLanguages(next))) to Unit
    }

    suspend fun setCustomFillers(words: List<String>) = mutate { document ->
        document.copy(fillers = document.fillers.copy(custom = TranscriptCleanup.normalizeVocabulary(words))) to Unit
    }

    /** A portable copy for backups. */
    suspend fun export(): SpeechDictionaryDocument {
        initialized.await()
        return mutex.withLock {
            check(_state.value.loadError == null) { "Personal dictionary could not be loaded for backup." }
            _state.value.document.stamped().copy(consumedQuarantines = emptySet())
        }
    }

    /**
     * Every document this app publishes, saves or exports carries the version it writes. A document
     * read from an older file would otherwise keep the old label in memory, and a backup taken before
     * the next edit could be accepted by an older app that drops what it does not know.
     */
    private fun SpeechDictionaryDocument.stamped(): SpeechDictionaryDocument =
        if (version == supportedVersion) this else copy(version = supportedVersion)

    /**
     * Restores from a backup section. Validation happens before anything is written, so an invalid
     * or newer document leaves the current entries untouched. Merge keeps existing entries and adds
     * the ones that are missing; erase replaces the entries. Filler settings follow the backup in
     * both modes because they are one setting, not a list to merge.
     */
    /** Returns false when the restored document is only held in memory because the write failed. */
    suspend fun restore(backup: SpeechDictionaryDocument, merge: Boolean): Boolean {
        validateRestore(backup)
        return mutate(outcome = { _, persisted -> persisted }) { document ->
            val base = if (merge) document else document.copy(words = emptyList(), corrections = emptyList())
            merged(base, backup) to true
        }
    }

    /** Preflight validation for multi-target restore, without changing any dictionary state. */
    fun validateRestore(backup: SpeechDictionaryDocument) {
        if (backup.version > supportedVersion) {
            throw RestoreRejectedException(
                "Personal dictionary backup version ${backup.version} is newer than this app supports.",
            )
        }
        if (backup.version < 1) throw RestoreRejectedException("Personal dictionary backup has an invalid version.")
        // Every row is checked before anything is written, so an erase restore never replaces the
        // current entries with a partial set. Repeated rows are redundant, not invalid, and are folded.
        backup.words.forEachIndexed { index, word ->
            if (TranscriptCleanup.normalizeTerm(word.word).isEmpty()) {
                throw RestoreRejectedException("Personal dictionary backup word ${index + 1} is blank.")
            }
        }
        backup.corrections.forEachIndexed { index, correction ->
            val source = TranscriptCleanup.normalizeTerm(correction.source)
            val replacement = TranscriptCleanup.normalizeTerm(correction.replacement)
            if (source.isEmpty() || replacement.isEmpty()) {
                throw RestoreRejectedException("Personal dictionary backup correction ${index + 1} is incomplete.")
            }
            if (source == replacement) {
                throw RestoreRejectedException("Personal dictionary backup correction ${index + 1} does not change anything.")
            }
        }
    }

    /** Adds the entries of [incoming] that [base] lacks, and takes its filler settings. */
    private fun merged(base: SpeechDictionaryDocument, incoming: SpeechDictionaryDocument): SpeechDictionaryDocument {
        var next = base.copy(nextId = maxOf(base.nextId, 1L))
        for (word in incoming.words) {
            if (SpeechDictionaryValidation.validateWord(next, word.word) != null) continue
            val entry = VocabularyEntry(next.nextId, TranscriptCleanup.normalizeTerm(word.word), word.createdAt)
            next = next.copy(nextId = next.nextId + 1, words = next.words + entry)
        }
        for (correction in incoming.corrections) {
            if (SpeechDictionaryValidation.validateCorrection(next, correction.source, correction.replacement) != null) continue
            val entry = CorrectionEntry(
                id = next.nextId,
                source = TranscriptCleanup.normalizeTerm(correction.source),
                replacement = TranscriptCleanup.normalizeTerm(correction.replacement),
                createdAt = correction.createdAt,
            )
            next = next.copy(nextId = next.nextId + 1, corrections = next.corrections + entry)
        }
        return next.copy(
            fillers = FillerSettings(
                enabled = incoming.fillers.enabled,
                languages = FillerRules.normalizeLanguages(incoming.fillers.languages),
                custom = TranscriptCleanup.normalizeVocabulary(incoming.fillers.custom),
            ),
        )
    }

    /**
     * Accepted settings edits survive navigation, including while waiting for loading/the lock.
     * Only the keyboard fix flow opts into cancellation before committing for privacy transitions.
     */
    private suspend fun <R> mutate(
        cancelBeforeCommit: Boolean = false,
        outcome: (R, Boolean) -> R = { result, _ -> result },
        transform: (SpeechDictionaryDocument) -> Pair<SpeechDictionaryDocument, R>,
    ): R = if (cancelBeforeCommit) persistMutation(transform, outcome) else withContext(NonCancellable) {
        persistMutation(transform, outcome)
    }

    private suspend fun mutateEntry(
        cancelBeforeCommit: Boolean = false,
        transform: (SpeechDictionaryDocument) -> Pair<SpeechDictionaryDocument, EntryResult>,
    ): EntryResult = mutate(cancelBeforeCommit, outcome = { result, persisted ->
        if (result is EntryResult.Saved) result.copy(persisted = persisted) else result
    }, transform = transform)

    private suspend fun <R> persistMutation(
        transform: (SpeechDictionaryDocument) -> Pair<SpeechDictionaryDocument, R>,
        outcome: (R, Boolean) -> R,
    ): R {
        initialized.await()
        return mutex.withLock {
            val current = _state.value
            val (next, result) = transform(current.document)
            var persisted = !current.saveError
            if (next !== current.document) {
                // Retain the caller's cancellation signal even inside NonCancellable below.
                val callerContext = currentCoroutineContext()
                callerContext.ensureActive()
                val versioned = next.copy(version = supportedVersion)
                // While a newer file could not be moved aside, edits stay in memory rather than
                // overwrite the only newer-schema copy; the warning stays visible. A storage failure
                // (full disk, unwritable directory) is reported the same way instead of thrown at
                // the settings page or the keyboard: the change is kept and retried on the next one.
                // Check cancellation at IO entry and before file replacement. Once replacement
                // begins, finish publication non-cancellably so disk and memory cannot disagree.
                withContext(NonCancellable) {
                    var blocked = false
                    var mainWritten = false
                    val written = withContext(ioDispatcher) {
                        callerContext.ensureActive()
                        if (mainPathWritable()) {
                            runCatching { write(versioned) { callerContext.ensureActive() } }
                                .onFailure { if (it is CancellationException) throw it }
                                .onSuccess { mainWritten = true; retirePendingConsumed() }.isSuccess
                        } else {
                            blocked = true
                            false
                        }
                    }
                    // A cancelled failed write must not remain as an unsaved edit for later retry.
                    if (!written && !mainWritten) callerContext.ensureActive()
                    persisted = written
                    _state.value = SpeechDictionaryState(
                        versioned,
                        loaded = true,
                        loadError = if (!blocked) null else current.loadError ?: SpeechDictionaryLoadError.NEWER_VERSION,
                        // A blocked main path is an unsaved change too: restore and the keyboard
                        // row read this flag to decide whether they may report success.
                        saveError = !written,
                    )
                }
            }
            outcome(result, persisted)
        }
    }

    private fun load() {
        if (!file.exists()) {
            // A crash between moving a damaged file aside and rewriting it leaves only the copy; a
            // file kept for a newer app is taken back as soon as this app understands its version.
            // Any kept main outranks the stale backup, including after an upgrade that can now
            // read its version. This also covers a crash in older builds between rename and delete.
            val kept = quarantinedFiles()
            // An old backup cannot outrank a kept newer main. A backup that atomically records
            // every kept file as consumed is a valid fallback checkpoint, however.
            val recovered = recoverFromBackup()?.takeIf { backup ->
                kept.all { (file, _) -> file.name in backup.consumedQuarantines }
            }
            val absorbed = absorbQuarantined(
                recovered ?: SpeechDictionaryDocument(), alreadyMerged = recovered?.consumedQuarantines.orEmpty(),
            )
            pendingConsumed = pendingConsumed + absorbed.skipped
            val hasRecovery = recovered != null || absorbed.consumed.isNotEmpty()
            val persisted = if (hasRecovery) persistAbsorbed(absorbed) else true
            val unreadableKept = file.parentFile?.listFiles()?.any {
                it.isFile && it.name.startsWith("${file.name}.unreadable-")
            } == true
            _state.value = SpeechDictionaryState(
                absorbed.document.stamped(), loaded = true,
                loadError = quarantineError() ?: if (unreadableKept && (!hasRecovery || !persisted)) {
                    SpeechDictionaryLoadError.UNREADABLE
                } else null,
                saveError = !persisted,
            )
            return
        }
        // A version below 1 was never written by any app. It is handled like an unreadable file, the same
        // way restore rejects it, so it can never be relabelled and exported as a valid backup.
        val parsed = runCatching {
            SpeechDictionaryDocument.decode(file.readText(Charsets.UTF_8)).also { require(it.version >= 1) }
        }
        parsed.onSuccess { document ->
            if (document.version > supportedVersion) {
                // Unknown fields are ignored on decode, so saving would silently downgrade the
                // file. Keep it for the newer app and start empty, visibly. A copy left by the
                // non-atomic write path is older still and must not resurface on the next start.
                // If the move fails the newer file stays where it is, and no save may touch it.
                val version = document.version
                moveAsideOrBlock { quarantineNewer(version) }
                _state.value = SpeechDictionaryState(
                    SpeechDictionaryDocument(),
                    loaded = true,
                    loadError = SpeechDictionaryLoadError.NEWER_VERSION,
                )
                return
            }
            // Kept files recorded as merged are already part of this main file; only their deletion is
            // outstanding. Merging them again would bring back entries the user removed since.
            val absorbed = absorbQuarantined(document, alreadyMerged = document.consumedQuarantines + readMergedMarker())
            pendingConsumed = pendingConsumed + absorbed.skipped
            val persisted = if (absorbed.consumed.isNotEmpty()) persistAbsorbed(absorbed) else {
                retirePendingConsumed()
                true
            }
            _state.value = SpeechDictionaryState(
                absorbed.document.stamped(), loaded = true, loadError = quarantineError(), saveError = !persisted,
            )
        }.onFailure {
            // Keep the unreadable file for inspection instead of overwriting it on the next save.
            // If it cannot be moved aside now, no write may touch the main path until it can.
            val movedAside = moveAsideOrBlock { quarantineUnreadable() }
            // A durable copy only exists after a non-atomic write path; use it when it parses, and
            // write it back so a restart before the next edit does not start empty.
            val recovered = recoverFromBackup()
            _state.value = if (recovered != null) {
                // The warning only goes once the recovered copy is safely back at the main path.
                val absorbed = absorbQuarantined(recovered, recovered.consumedQuarantines + readMergedMarker())
                pendingConsumed = pendingConsumed + absorbed.skipped
                val writtenBack = if (movedAside) persistAbsorbed(absorbed) else {
                    pendingConsumed = pendingConsumed + absorbed.consumed
                    false
                }
                SpeechDictionaryState(
                    absorbed.document.stamped(),
                    loaded = true,
                    loadError = quarantineError() ?: if (writtenBack) null else SpeechDictionaryLoadError.UNREADABLE,
                    saveError = !writtenBack,
                )
            } else {
                SpeechDictionaryState(
                    SpeechDictionaryDocument(),
                    loaded = true,
                    loadError = SpeechDictionaryLoadError.UNREADABLE,
                )
            }
        }
    }

    private fun write(document: SpeechDictionaryDocument, beforeCommit: () -> Unit = {}) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream ->
            // The consumed-file record must commit with the entries, before any retirement can run.
            // A process death after replacement therefore cannot merge an absorbed file again.
            val persisted = document.copy(
                version = supportedVersion,
                consumedQuarantines = document.consumedQuarantines + pendingConsumed.map { it.name },
            )
            stream.write(SpeechDictionaryDocument.encode(persisted).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            beforeCommit()
            try {
                atomicReplace(temp, file)
            } catch (atomicFailure: java.io.IOException) {
                // Keep a durable previous copy before the non-atomic replacement.
                try {
                    if (file.exists()) Files.copy(file.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING)
                    beforeCommit()
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (fallbackFailure: java.io.IOException) {
                    atomicFailure.addSuppressed(fallbackFailure)
                    throw atomicFailure
                }
            }
        } catch (cancelled: CancellationException) {
            // The authoritative file is untouched, and the cancelled draft is never retried.
            temp.delete()
            throw cancelled
        }
    }

    private fun backupFile(): File = File(file.parentFile, "${file.name}.bak")

    /** Moves the file at the main path aside under [suffix]. False leaves it untouched for a retry. */
    private fun moveAside(suffix: String): Boolean {
        val kept = File(file.parentFile, "${file.name}.$suffix")
        return runCatching { Files.move(file.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING) }.isSuccess
    }

    /** Keeps a newer-schema file for a later upgrade; the older `.bak` copy must not resurface. */
    private fun quarantineNewer(version: Int): Boolean {
        // Retire the stale backup before making the main path absent. If deletion fails, leave
        // the authoritative main untouched and block writes until both steps can succeed.
        val backup = backupFile()
        if (!runCatching { !backup.exists() || backup.delete() }.getOrDefault(false)) return false
        return moveAside("newer-v$version-${clock()}")
    }

    private fun quarantineUnreadable(): Boolean = moveAside("unreadable-${clock()}")

    /** Runs [attempt] now; if it fails, remembers it so every write retries it first. */
    private fun moveAsideOrBlock(attempt: () -> Boolean): Boolean {
        if (attempt()) return true
        blockedMainPath = attempt
        return false
    }

    /** True when it is safe to write the main file, retrying a failed move-aside first. */
    private fun mainPathWritable(): Boolean {
        val retry = blockedMainPath ?: return true
        if (!file.exists() || retry()) {
            blockedMainPath = null
            return true
        }
        return false
    }

    private fun recoverFromBackup(): SpeechDictionaryDocument? = backupFile().takeIf { it.exists() }
        ?.let { backup -> runCatching { SpeechDictionaryDocument.decode(backup.readText(Charsets.UTF_8)) }.getOrNull() }
        ?.takeIf { it.version in 1..supportedVersion }

    /** Files moved aside because a newer app wrote them, with their document version. */
    private fun quarantinedFiles(): List<Pair<File, Int>> {
        // Built here rather than as a property: load() can run on the IO dispatcher before the
        // constructor has initialised later properties.
        val pattern = Regex("${Regex.escape(file.name)}\\.newer-v(\\d+)-\\d+")
        return file.parentFile?.listFiles()
            ?.mapNotNull { kept -> pattern.matchEntire(kept.name)?.let { kept to it.groupValues[1].toInt() } }
            ?.sortedBy { it.first.name }
            ?: emptyList()
    }

    private fun quarantineError(): SpeechDictionaryLoadError? =
        if (quarantinedFiles().any { it.second > supportedVersion }) SpeechDictionaryLoadError.NEWER_VERSION else null

    /** A merged document plus the kept files it came from, which may only go once it is on disk. */
    private class Absorbed(
        val document: SpeechDictionaryDocument,
        val consumed: List<File>,
        /** Kept files left out because the main file already holds their entries. */
        val skipped: List<File> = emptyList(),
    )

    /**
     * Merges the entries of files kept for a newer app once this app supports their version.
     * Entries added meanwhile are kept; the kept file's filler settings win. The files are returned
     * rather than deleted here, because they are the only copy until the merge has been written.
     */
    private fun absorbQuarantined(base: SpeechDictionaryDocument, alreadyMerged: Set<String> = emptySet()): Absorbed {
        var next = base
        val consumed = ArrayList<File>()
        val skipped = ArrayList<File>()
        for ((kept, version) in quarantinedFiles()) {
            if (version > supportedVersion) continue
            if (kept.name in alreadyMerged) { skipped.add(kept); continue }
            val document = runCatching { SpeechDictionaryDocument.decode(kept.readText(Charsets.UTF_8)) }.getOrNull() ?: continue
            if (document.version !in 1..supportedVersion) continue
            next = merged(next, document)
            consumed.add(kept)
        }
        return Absorbed(next, consumed, skipped)
    }

    /**
     * Writes the merged document and removes the kept files only when that write succeeded. If it
     * did not, the removal waits for the next successful write, so a later restart cannot merge the
     * same files again and resurrect entries the user removed in between.
     */
    private fun persistAbsorbed(absorbed: Absorbed): Boolean {
        pendingConsumed = pendingConsumed + absorbed.consumed
        val persisted = runCatching { write(absorbed.document) }.isSuccess
        if (persisted) retirePendingConsumed()
        return persisted
    }

    /**
     * Runs after a successful write, when the main file holds everything the pending kept files
     * contributed. The main document already contains their consumed-file record. The sidecar
     * remains a best-effort compatibility record; its failure cannot invalidate the atomic main.
     */
    private fun retirePendingConsumed() {
        val files = pendingConsumed
        if (files.isEmpty() && !mergedMarker().exists()) return
        pendingConsumed = undeleted(files)
        recordMerged(pendingConsumed)
    }

    /** Deletes the files and returns those that are still there, so a failed deletion is retried. */
    private fun undeleted(files: List<File>): List<File> = files.filter {
        it.exists() && !runCatching { deleteKeptFile(it) }.getOrDefault(false)
    }

    /** Names of kept files whose entries are in the main file but whose deletion has not succeeded yet. */
    private fun mergedMarker(): File = File(file.parentFile, "${file.name}.merged")

    private fun readMergedMarker(): Set<String> = runCatching {
        mergedMarker().readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    /** Optional legacy sidecar. New readers use the consumed-file record in the main document. */
    private fun recordMerged(files: List<File>): Boolean {
        val marker = mergedMarker()
        return runCatching {
            if (files.isEmpty()) {
                check(!marker.exists() || marker.delete())
            } else {
                val temp = File(marker.parentFile, "${marker.name}.tmp")
                FileOutputStream(temp).use { stream ->
                    stream.write(files.joinToString("\n") { it.name }.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                }
                try {
                    Files.move(temp.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (atomicFailure: java.io.IOException) {
                    try {
                        Files.move(temp.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    } catch (fallbackFailure: java.io.IOException) {
                        atomicFailure.addSuppressed(fallbackFailure)
                        throw atomicFailure
                    }
                }
            }
        }.isSuccess
    }
}
