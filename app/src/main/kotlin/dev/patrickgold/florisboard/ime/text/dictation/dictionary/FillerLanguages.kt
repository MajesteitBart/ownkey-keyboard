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

/**
 * Built-in hesitation inventories, ported unchanged from the Windows reference.
 *
 * Words that carry meaning (`like`, `well`, `dus`, `gewoon`) are deliberately absent. A filler in one
 * language can be an ordinary word in another; [protectedWords] lists those, and they stay whenever
 * that second language is selected. This is configured language handling, not language detection.
 */
enum class FillerLanguage(val code: String, val fillers: List<String>, val protectedWords: List<String>) {
    ENGLISH("en", listOf("uh", "uhh", "um", "umm", "uhm", "erm", "er", "mhm"), emptyList()),
    DUTCH("nl", listOf("uh", "uhm", "eh", "ehm", "euh", "euhm", "hm"), listOf("er", "este")),
    GERMAN("de", listOf("äh", "ähm", "ehm", "hm", "mh"), listOf("um", "er", "este")),
    FRENCH("fr", listOf("euh", "heu", "hum"), listOf("este")),
    SPANISH("es", listOf("eh", "em", "ehm", "este"), listOf("er"));

    companion object {
        val DEFAULT_CODES: List<String> = listOf(ENGLISH.code, DUTCH.code)

        fun fromCode(code: String): FillerLanguage? {
            val normalized = code.trim().lowercase()
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

object FillerRules {
    /**
     * Known language codes in the given order without duplicates. Unknown codes are dropped. An
     * explicitly empty list stays empty so every built-in list can be switched off at once.
     */
    fun normalizeLanguages(codes: Iterable<String>): List<String> {
        val result = ArrayList<String>()
        for (code in codes) {
            val language = FillerLanguage.fromCode(code) ?: continue
            if (language.code !in result) result.add(language.code)
        }
        return result
    }

    /**
     * Fillers to remove for the selected languages, minus words another selected language needs.
     * Custom removals are added afterwards, so a custom entry can deliberately override protection.
     */
    fun fillerWords(languages: Iterable<String>, custom: Iterable<String> = emptyList()): List<String> {
        val selected = normalizeLanguages(languages).mapNotNull(FillerLanguage::fromCode)
        val protectedWords = selected.flatMap { it.protectedWords }.toSet()
        val words = ArrayList<String>()
        for (language in selected) {
            for (word in language.fillers) {
                if (word !in protectedWords && word !in words) words.add(word)
            }
        }
        for (word in TranscriptCleanup.normalizeVocabulary(custom)) {
            val lowercase = word.lowercase()
            if (lowercase !in words) words.add(lowercase)
        }
        return words
    }
}
