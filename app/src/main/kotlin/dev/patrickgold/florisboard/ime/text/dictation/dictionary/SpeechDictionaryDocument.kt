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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persisted speech dictionary. It is separate from the typing dictionaries on purpose: these
 * entries bias recognition, rewrite transcripts, and can travel to a configured cloud endpoint.
 *
 * The document is versioned so a backup made by a newer app fails visibly instead of being
 * misread. An explicitly empty [FillerSettings.languages] list is written out, so it never falls
 * back to the defaults on reload.
 */
@Serializable
data class SpeechDictionaryDocument(
    val version: Int = CURRENT_VERSION,
    val nextId: Long = 1L,
    val words: List<VocabularyEntry> = emptyList(),
    val corrections: List<CorrectionEntry> = emptyList(),
    val fillers: FillerSettings = FillerSettings(),
    /** Local recovery metadata committed atomically with entries; omitted from portable exports. */
    val consumedQuarantines: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = words.isEmpty() && corrections.isEmpty()

    companion object {
        const val CURRENT_VERSION = 1

        val json: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun encode(document: SpeechDictionaryDocument): String = json.encodeToString(serializer(), document)

        /** Parses without touching stored data; callers decide what an incompatible version means. */
        fun decode(text: String): SpeechDictionaryDocument = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class VocabularyEntry(
    val id: Long,
    val word: String,
    val createdAt: Long = 0L,
)

@Serializable
data class CorrectionEntry(
    val id: Long,
    val source: String,
    val replacement: String,
    val createdAt: Long = 0L,
)

@Serializable
data class FillerSettings(
    val enabled: Boolean = true,
    val languages: List<String> = FillerLanguage.DEFAULT_CODES,
    val custom: List<String> = emptyList(),
)

/** A unified list row for settings; the two entry kinds stay labelled distinctly. */
sealed interface SpeechDictionaryEntry {
    val id: Long
    val createdAt: Long

    data class Word(val entry: VocabularyEntry) : SpeechDictionaryEntry {
        override val id: Long get() = entry.id
        override val createdAt: Long get() = entry.createdAt
    }

    data class Correction(val entry: CorrectionEntry) : SpeechDictionaryEntry {
        override val id: Long get() = entry.id
        override val createdAt: Long get() = entry.createdAt
    }
}

fun SpeechDictionaryDocument.entries(): List<SpeechDictionaryEntry> =
    words.map { SpeechDictionaryEntry.Word(it) } + corrections.map { SpeechDictionaryEntry.Correction(it) }

enum class EntryError {
    BLANK_WORD,
    BLANK_SOURCE,
    BLANK_REPLACEMENT,
    IDENTICAL,
    DUPLICATE_WORD,
    DUPLICATE_CORRECTION,
}

sealed interface EntryResult {
    data class Saved(val entry: SpeechDictionaryEntry, val persisted: Boolean = true) : EntryResult
    data class Rejected(val error: EntryError) : EntryResult
}

/** Pure validation shared by the add form, the edit dialog, the keyboard fix flow and restore. */
object SpeechDictionaryValidation {
    fun validateWord(document: SpeechDictionaryDocument, word: String, editingId: Long? = null): EntryError? {
        val normalized = TranscriptCleanup.normalizeTerm(word)
        if (normalized.isEmpty()) return EntryError.BLANK_WORD
        val key = normalized.lowercase()
        val duplicate = document.words.any { it.id != editingId && TranscriptCleanup.normalizeTerm(it.word).lowercase() == key }
        return if (duplicate) EntryError.DUPLICATE_WORD else null
    }

    fun validateCorrection(
        document: SpeechDictionaryDocument,
        source: String,
        replacement: String,
        editingId: Long? = null,
    ): EntryError? {
        val normalizedSource = TranscriptCleanup.normalizeTerm(source)
        val normalizedReplacement = TranscriptCleanup.normalizeTerm(replacement)
        if (normalizedSource.isEmpty()) return EntryError.BLANK_SOURCE
        if (normalizedReplacement.isEmpty()) return EntryError.BLANK_REPLACEMENT
        // Case-only corrections such as `bart → Bart` are valid; only exactly identical pairs are rejected.
        if (normalizedSource == normalizedReplacement) return EntryError.IDENTICAL
        val key = normalizedSource.lowercase()
        val duplicate = document.corrections.any { it.id != editingId && TranscriptCleanup.normalizeTerm(it.source).lowercase() == key }
        return if (duplicate) EntryError.DUPLICATE_CORRECTION else null
    }
}
