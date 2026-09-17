/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.voxtral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyMode
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.EntryError
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.EntryResult
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.FillerLanguage
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.FillerRules
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.RemovedEntry
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionaryDocument
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionaryEntry
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionaryLoadError
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionaryRepository
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.entries
import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.speechDictionary
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.userStringRes
import org.florisboard.lib.compose.pluralsRes
import org.ownkey.offline.HotwordTransport

private const val NOTICE_MILLIS = 4_000L
private const val UNDO_MILLIS = 6_000L
private const val SEARCH_THRESHOLD = 8

/**
 * Personal dictionary for dictation: words that bias recognition, corrections that rewrite the
 * transcript, and filler-word settings. [heard] prefills a correction, used by the keyboard when the
 * text changed under the fix flow.
 */
@Composable
fun SpeechDictionaryScreen(heard: String? = null) = FlorisScreen {
    title = userStringRes(R.string.speech_dictionary__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val repository = remember { context.speechDictionary().value }
    val scope = rememberCoroutineScope()

    content {
        val prefsRef = prefs
        val state by repository.state.collectAsState()
        val cloudMode by prefsRef.voxtral.cloudVocabularyMode.collectAsState()
        val localHints by prefsRef.voxtral.localVocabularyHints.collectAsState()
        val localCompatible = remember { context.offlineDictation().compatible }
        var editing by remember { mutableStateOf<SpeechDictionaryEntry?>(null) }
        var removed by remember { mutableStateOf<RemovedEntry?>(null) }

        LaunchedEffect(removed) {
            if (removed != null) {
                delay(UNDO_MILLIS)
                removed = null
            }
        }

        OwnkeyAiSettingsTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OwnkeyBrand.Key)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IntroCard()
                when (state.loadError) {
                    SpeechDictionaryLoadError.UNREADABLE ->
                        NoticeBanner(text = userStringRes(R.string.speech_dictionary__load_error), warning = true)
                    SpeechDictionaryLoadError.NEWER_VERSION ->
                        NoticeBanner(text = userStringRes(R.string.speech_dictionary__load_error_newer), warning = true)
                    null -> Unit
                }
                AddEntryCard(
                    document = state.document,
                    initialHeard = heard,
                    onAddWord = { word -> repository.addWord(word) },
                    onAddCorrection = { source, replacement -> repository.addCorrection(source, replacement) },
                )
                EntriesCard(
                    document = state.document,
                    removed = removed,
                    onEdit = { editing = it },
                    onDelete = { entry ->
                        scope.launch { removed = repository.remove(entry.id) }
                    },
                    onUndo = {
                        val toRestore = removed ?: return@EntriesCard
                        removed = null
                        scope.launch { repository.restoreRemoved(toRestore) }
                    },
                )
                FillerWordsCard(document = state.document, repository = repository)
                RecognitionHintsCard(
                    words = state.document.words.map { it.word },
                    cloudMode = CloudVocabularyMode.fromPreference(cloudMode),
                    onCloudModeChange = { mode -> scope.launch { prefsRef.voxtral.cloudVocabularyMode.set(mode.preference) } },
                    localCompatible = localCompatible,
                    localHints = localHints,
                    onLocalHintsChange = { enabled -> scope.launch { prefsRef.voxtral.localVocabularyHints.set(enabled) } },
                )
            }
            editing?.let { entry ->
                EditEntryDialog(
                    entry = entry,
                    onDismiss = { editing = null },
                    onSaveWord = { id, word -> repository.updateWord(id, word) },
                    onSaveCorrection = { id, source, replacement -> repository.updateCorrection(id, source, replacement) },
                )
            }
        }
    }
}

/** Entry card on the AI screen: counts, filler status and the way in. */
@Composable
internal fun PersonalDictionaryCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { context.speechDictionary().value }
    val state by repository.state.collectAsState()
    val document = state.document
    AiSectionCard(
        title = userStringRes(R.string.speech_dictionary__title),
        summary = userStringRes(R.string.speech_dictionary__ai_card_summary),
    ) {
        StatusText(
            text = pluralsRes(R.plurals.speech_dictionary__ai_card_words, document.words.size, "count" to document.words.size) +
                " · " +
                pluralsRes(R.plurals.speech_dictionary__ai_card_corrections, document.corrections.size, "count" to document.corrections.size),
        )
        StatusText(text = fillerStatusText(document))
        OwnkeyButton(label = userStringRes(R.string.speech_dictionary__ai_card_open), onClick = onOpen)
    }
}

@Composable
private fun fillerStatusText(document: SpeechDictionaryDocument): String {
    val fillers = document.fillers
    if (!fillers.enabled) return userStringRes(R.string.speech_dictionary__ai_card_fillers_off)
    val languages = FillerRules.normalizeLanguages(fillers.languages).mapNotNull(FillerLanguage::fromCode)
    if (languages.isEmpty()) return userStringRes(R.string.speech_dictionary__ai_card_fillers_custom_only)
    val names = languages.map { it.label() }.joinToString(", ")
    return userStringRes(R.string.speech_dictionary__ai_card_fillers_on, "languages" to names)
}

@Composable
private fun IntroCard() {
    AiSectionCard(
        title = userStringRes(R.string.speech_dictionary__intro_title),
        summary = userStringRes(R.string.speech_dictionary__intro_summary),
    ) {
        StatusText(text = userStringRes(R.string.speech_dictionary__cloud_disclosure))
    }
}

@Composable
private fun NoticeBanner(text: String, warning: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (warning) OwnkeyBrand.SignalAmber else OwnkeyBrand.Line, RoundedCornerShape(16.dp)),
        color = OwnkeyBrand.PanelRaised,
        contentColor = OwnkeyBrand.Bone,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Add form (Windows parity: one field, or source/replacement when correcting a misspelling)
// ---------------------------------------------------------------------------------------------

@Composable
private fun AddEntryCard(
    document: SpeechDictionaryDocument,
    initialHeard: String?,
    onAddWord: suspend (String) -> EntryResult,
    onAddCorrection: suspend (String, String) -> EntryResult,
) {
    val scope = rememberCoroutineScope()
    var correctionMode by rememberSaveable { mutableStateOf(!initialHeard.isNullOrBlank()) }
    var word by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(initialHeard.orEmpty()) }
    var replacement by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<EntryError?>(null) }
    var notice by remember { mutableStateOf<SpeechDictionaryEntry?>(null) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

    fun clear() {
        word = ""
        source = ""
        replacement = ""
        error = null
    }

    fun save() {
        scope.launch {
            val result = if (correctionMode) onAddCorrection(source, replacement) else onAddWord(word)
            when (result) {
                is EntryResult.Saved -> {
                    notice = result.entry
                    clear()
                }
                is EntryResult.Rejected -> error = result.error
            }
        }
    }

    val canSave = if (correctionMode) source.isNotBlank() && replacement.isNotBlank() else word.isNotBlank()

    AiSectionCard(
        title = userStringRes(R.string.speech_dictionary__add_title),
        summary = userStringRes(R.string.speech_dictionary__add_summary),
    ) {
        SwitchRow(
            label = userStringRes(R.string.speech_dictionary__correction_toggle),
            summary = userStringRes(R.string.speech_dictionary__correction_toggle_summary),
            checked = correctionMode,
            onCheckedChange = {
                correctionMode = it
                error = null
            },
        )
        if (correctionMode) {
            OwnkeyOutlinedTextField(
                value = source,
                onValueChange = { source = it; error = null },
                label = userStringRes(R.string.speech_dictionary__source_label),
            )
            OwnkeyOutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it; error = null },
                label = userStringRes(R.string.speech_dictionary__replacement_label),
            )
        } else {
            OwnkeyOutlinedTextField(
                value = word,
                onValueChange = { word = it; error = null },
                label = userStringRes(R.string.speech_dictionary__word_label),
            )
        }
        error?.let { ErrorText(text = it.text()) }
        notice?.let { saved ->
            SuccessText(
                text = when (saved) {
                    is SpeechDictionaryEntry.Word -> userStringRes(R.string.speech_dictionary__saved_word, "word" to saved.entry.word)
                    is SpeechDictionaryEntry.Correction -> userStringRes(
                        R.string.speech_dictionary__saved_correction,
                        "source" to saved.entry.source,
                        "replacement" to saved.entry.replacement,
                    )
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OwnkeyButton(
                label = userStringRes(R.string.speech_dictionary__save_action),
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            )
            OwnkeyButton(
                label = userStringRes(R.string.speech_dictionary__clear_action),
                onClick = ::clear,
                enabled = word.isNotEmpty() || source.isNotEmpty() || replacement.isNotEmpty(),
                modifier = Modifier.weight(1f),
                secondary = true,
            )
        }
        if (document.isEmpty) {
            StatusText(text = userStringRes(R.string.speech_dictionary__entries_example))
        }
    }
}

@Composable
private fun EntryError.text(): String = userStringRes(
    when (this) {
        EntryError.BLANK_WORD -> R.string.speech_dictionary__error_blank_word
        EntryError.BLANK_SOURCE -> R.string.speech_dictionary__error_blank_source
        EntryError.BLANK_REPLACEMENT -> R.string.speech_dictionary__error_blank_replacement
        EntryError.IDENTICAL -> R.string.speech_dictionary__error_identical
        EntryError.DUPLICATE_WORD -> R.string.speech_dictionary__error_duplicate_word
        EntryError.DUPLICATE_CORRECTION -> R.string.speech_dictionary__error_duplicate_correction
    },
)

// ---------------------------------------------------------------------------------------------
// Entries
// ---------------------------------------------------------------------------------------------

@Composable
private fun EntriesCard(
    document: SpeechDictionaryDocument,
    removed: RemovedEntry?,
    onEdit: (SpeechDictionaryEntry) -> Unit,
    onDelete: (SpeechDictionaryEntry) -> Unit,
    onUndo: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = document.entries()
    val normalizedQuery = query.trim().lowercase()
    val visible = if (normalizedQuery.isEmpty()) entries else entries.filter { it.matches(normalizedQuery) }
    val words = visible.filterIsInstance<SpeechDictionaryEntry.Word>()
    val corrections = visible.filterIsInstance<SpeechDictionaryEntry.Correction>()

    AiSectionCard(title = userStringRes(R.string.speech_dictionary__entries_title)) {
        removed?.let { removedEntry ->
            UndoBanner(entry = removedEntry.entry, onUndo = onUndo)
        }
        if (entries.isEmpty()) {
            StatusText(text = userStringRes(R.string.speech_dictionary__entries_empty))
            return@AiSectionCard
        }
        if (entries.size > SEARCH_THRESHOLD) {
            OwnkeyOutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = userStringRes(R.string.speech_dictionary__search_label),
            )
        }
        if (visible.isEmpty()) {
            StatusText(text = userStringRes(R.string.speech_dictionary__search_empty, "query" to query.trim()))
            return@AiSectionCard
        }
        if (words.isNotEmpty()) {
            SectionLabel(text = userStringRes(R.string.speech_dictionary__words_heading, "count" to words.size))
            words.forEach { entry ->
                EntryRow(
                    kind = userStringRes(R.string.speech_dictionary__word_kind),
                    text = entry.entry.word,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
        if (corrections.isNotEmpty()) {
            SectionLabel(text = userStringRes(R.string.speech_dictionary__corrections_heading, "count" to corrections.size))
            corrections.forEach { entry ->
                EntryRow(
                    kind = userStringRes(R.string.speech_dictionary__correction_kind),
                    text = userStringRes(
                        R.string.speech_dictionary__correction_arrow,
                        "source" to entry.entry.source,
                        "replacement" to entry.entry.replacement,
                    ),
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

private fun SpeechDictionaryEntry.matches(query: String): Boolean = when (this) {
    is SpeechDictionaryEntry.Word -> entry.word.lowercase().contains(query)
    is SpeechDictionaryEntry.Correction ->
        entry.source.lowercase().contains(query) || entry.replacement.lowercase().contains(query)
}

@Composable
private fun EntryRow(
    kind: String,
    text: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val editLabel = userStringRes(R.string.speech_dictionary__edit_action)
    val deleteLabel = userStringRes(R.string.speech_dictionary__delete_action)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeyBrand.Line, shape),
        color = OwnkeyBrand.Action.copy(alpha = 0.52f),
        contentColor = OwnkeyBrand.Bone,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    text = kind,
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            RowIconButton(label = editLabel, onClick = onEdit) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = OwnkeyBrand.Bone)
            }
            RowIconButton(label = deleteLabel, onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = OwnkeyBrand.Ash)
            }
        }
    }
}

@Composable
private fun RowIconButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun UndoBanner(entry: SpeechDictionaryEntry, onUndo: () -> Unit) {
    val label = when (entry) {
        is SpeechDictionaryEntry.Word -> entry.entry.word
        is SpeechDictionaryEntry.Correction -> userStringRes(
            R.string.speech_dictionary__correction_arrow,
            "source" to entry.entry.source,
            "replacement" to entry.entry.replacement,
        )
    }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeyBrand.SignalAmber.copy(alpha = 0.6f), shape),
        color = OwnkeyBrand.PanelRaised,
        contentColor = OwnkeyBrand.Bone,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = userStringRes(R.string.speech_dictionary__removed, "entry" to label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onUndo) {
                Text(
                    text = userStringRes(R.string.speech_dictionary__undo_action),
                    color = OwnkeyBrand.SignalOrange,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: SpeechDictionaryEntry,
    onDismiss: () -> Unit,
    onSaveWord: suspend (Long, String) -> EntryResult,
    onSaveCorrection: suspend (Long, String, String) -> EntryResult,
) {
    val scope = rememberCoroutineScope()
    var word by remember { mutableStateOf((entry as? SpeechDictionaryEntry.Word)?.entry?.word.orEmpty()) }
    var source by remember { mutableStateOf((entry as? SpeechDictionaryEntry.Correction)?.entry?.source.orEmpty()) }
    var replacement by remember { mutableStateOf((entry as? SpeechDictionaryEntry.Correction)?.entry?.replacement.orEmpty()) }
    var error by remember { mutableStateOf<EntryError?>(null) }
    val isCorrection = entry is SpeechDictionaryEntry.Correction

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OwnkeyBrand.Panel,
        titleContentColor = OwnkeyBrand.Bone,
        textContentColor = OwnkeyBrand.Bone,
        title = {
            Text(
                text = userStringRes(
                    if (isCorrection) R.string.speech_dictionary__edit_correction_title else R.string.speech_dictionary__edit_word_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCorrection) {
                    OwnkeyOutlinedTextField(
                        value = source,
                        onValueChange = { source = it; error = null },
                        label = userStringRes(R.string.speech_dictionary__source_label),
                    )
                    OwnkeyOutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it; error = null },
                        label = userStringRes(R.string.speech_dictionary__replacement_label),
                    )
                } else {
                    OwnkeyOutlinedTextField(
                        value = word,
                        onValueChange = { word = it; error = null },
                        label = userStringRes(R.string.speech_dictionary__word_label),
                    )
                }
                error?.let { ErrorText(text = it.text()) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val result = if (isCorrection) {
                            onSaveCorrection(entry.id, source, replacement)
                        } else {
                            onSaveWord(entry.id, word)
                        }
                        when (result) {
                            is EntryResult.Saved -> onDismiss()
                            is EntryResult.Rejected -> error = result.error
                        }
                    }
                },
            ) {
                Text(text = userStringRes(R.string.speech_dictionary__save_action), color = OwnkeyBrand.SignalOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = userStringRes(R.string.action__cancel), color = OwnkeyBrand.Ash)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Filler words
// ---------------------------------------------------------------------------------------------

@Composable
private fun FillerWordsCard(document: SpeechDictionaryDocument, repository: SpeechDictionaryRepository) {
    val scope = rememberCoroutineScope()
    val fillers = document.fillers
    val selected = FillerRules.normalizeLanguages(fillers.languages)
    var customDraft by rememberSaveable(fillers.custom) { mutableStateOf(fillers.custom.joinToString(", ")) }
    val preview = remember(fillers) { FillerRules.fillerWords(fillers.languages, fillers.custom) }

    AiSectionCard(
        title = userStringRes(R.string.speech_dictionary__fillers_title),
        summary = userStringRes(R.string.speech_dictionary__fillers_summary),
    ) {
        SwitchRow(
            label = userStringRes(R.string.speech_dictionary__fillers_enabled),
            summary = null,
            checked = fillers.enabled,
            onCheckedChange = { enabled -> scope.launch { repository.setFillersEnabled(enabled) } },
        )
        if (!fillers.enabled) {
            StatusText(text = userStringRes(R.string.speech_dictionary__fillers_off_note))
        }
        SectionLabel(text = userStringRes(R.string.speech_dictionary__fillers_languages))
        StatusText(text = userStringRes(R.string.speech_dictionary__fillers_languages_summary))
        FillerLanguage.entries.forEach { language ->
            val checked = language.code in selected
            LanguageRow(
                language = language,
                checked = checked,
                enabled = fillers.enabled,
                onCheckedChange = { nowChecked ->
                    val next = if (nowChecked) selected + language.code else selected - language.code
                    scope.launch { repository.setFillerLanguages(next) }
                },
            )
        }
        SectionLabel(text = userStringRes(R.string.speech_dictionary__fillers_custom_label))
        StatusText(text = userStringRes(R.string.speech_dictionary__fillers_custom_summary))
        OwnkeyOutlinedTextField(
            value = customDraft,
            onValueChange = { customDraft = it },
            label = userStringRes(R.string.speech_dictionary__fillers_custom_label),
            enabled = fillers.enabled,
        )
        OwnkeyButton(
            label = userStringRes(R.string.speech_dictionary__fillers_custom_save),
            onClick = {
                val words = customDraft.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                scope.launch { repository.setCustomFillers(words) }
            },
            enabled = fillers.enabled && customDraft.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() } != fillers.custom,
            secondary = true,
        )
        if (fillers.enabled) {
            StatusText(
                text = if (preview.isEmpty()) {
                    userStringRes(R.string.speech_dictionary__fillers_preview_none)
                } else {
                    userStringRes(R.string.speech_dictionary__fillers_preview, "words" to preview.joinToString(", "))
                },
            )
        }
    }
}

@Composable
private fun LanguageRow(
    language: FillerLanguage,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (checked) OwnkeyBrand.SignalOrange.copy(alpha = 0.6f) else OwnkeyBrand.Line, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        color = if (checked) OwnkeyBrand.PanelRaised else OwnkeyBrand.Action.copy(alpha = 0.52f),
        contentColor = OwnkeyBrand.Bone,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = OwnkeyBrand.SignalOrange,
                    uncheckedColor = OwnkeyBrand.Ash,
                    checkmarkColor = OwnkeyBrand.Bone,
                ),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = language.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = userStringRes(R.string.speech_dictionary__fillers_language_words, "words" to language.fillers.joinToString(", ")),
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (language.protectedWords.isNotEmpty()) {
                    Text(
                        text = userStringRes(
                            R.string.speech_dictionary__fillers_language_protects,
                            "words" to language.protectedWords.joinToString(", "),
                        ),
                        color = OwnkeyBrand.Ash,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FillerLanguage.label(): String = userStringRes(
    when (this) {
        FillerLanguage.ENGLISH -> R.string.speech_dictionary__language_en
        FillerLanguage.DUTCH -> R.string.speech_dictionary__language_nl
        FillerLanguage.GERMAN -> R.string.speech_dictionary__language_de
        FillerLanguage.FRENCH -> R.string.speech_dictionary__language_fr
        FillerLanguage.SPANISH -> R.string.speech_dictionary__language_es
    },
)

// ---------------------------------------------------------------------------------------------
// Recognition hints
// ---------------------------------------------------------------------------------------------

@Composable
private fun RecognitionHintsCard(
    words: List<String>,
    cloudMode: CloudVocabularyMode,
    onCloudModeChange: (CloudVocabularyMode) -> Unit,
    localCompatible: Boolean,
    localHints: Boolean,
    onLocalHintsChange: (Boolean) -> Unit,
) {
    AiSectionCard(
        title = userStringRes(R.string.speech_dictionary__hints_title),
        summary = userStringRes(R.string.speech_dictionary__hints_summary),
    ) {
        SectionLabel(text = userStringRes(R.string.speech_dictionary__hints_cloud_label))
        ChoiceOption(
            label = userStringRes(R.string.speech_dictionary__hints_cloud_auto),
            summary = userStringRes(R.string.speech_dictionary__hints_cloud_auto_summary),
            selected = cloudMode == CloudVocabularyMode.AUTO,
            onClick = { onCloudModeChange(CloudVocabularyMode.AUTO) },
        )
        ChoiceOption(
            label = userStringRes(R.string.speech_dictionary__hints_cloud_prompt),
            summary = userStringRes(R.string.speech_dictionary__hints_cloud_prompt_summary),
            selected = cloudMode == CloudVocabularyMode.PROMPT,
            onClick = { onCloudModeChange(CloudVocabularyMode.PROMPT) },
        )
        ChoiceOption(
            label = userStringRes(R.string.speech_dictionary__hints_cloud_off),
            summary = userStringRes(R.string.speech_dictionary__hints_cloud_off_summary),
            selected = cloudMode == CloudVocabularyMode.OFF,
            onClick = { onCloudModeChange(CloudVocabularyMode.OFF) },
        )
        if (localCompatible) {
            SectionLabel(text = userStringRes(R.string.speech_dictionary__hints_local_label))
            SwitchRow(
                label = userStringRes(R.string.speech_dictionary__hints_local_toggle),
                summary = userStringRes(R.string.speech_dictionary__hints_local_summary),
                checked = localHints,
                onCheckedChange = onLocalHintsChange,
            )
            if (localHints && words.isNotEmpty()) {
                val encoded = remember(words) { HotwordTransport.encode(words) }
                StatusText(
                    text = if (encoded.dropped == 0) {
                        pluralsRes(R.plurals.speech_dictionary__hints_local_budget_ok, encoded.included, "count" to encoded.included)
                    } else {
                        userStringRes(
                            R.string.speech_dictionary__hints_local_budget_exceeded,
                            "included" to encoded.included,
                            "total" to (encoded.included + encoded.dropped),
                        )
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun SwitchRow(
    label: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = OwnkeyBrand.Ash,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OwnkeyBrand.Bone,
                checkedTrackColor = OwnkeyBrand.SignalOrange,
                uncheckedThumbColor = OwnkeyBrand.Ash,
                uncheckedTrackColor = OwnkeyBrand.Action,
                uncheckedBorderColor = OwnkeyBrand.Line,
            ),
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        color = OwnkeyBrand.ErrorRed,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SuccessText(text: String) {
    Text(
        text = text,
        color = OwnkeyBrand.SuccessGreen,
        style = MaterialTheme.typography.bodyMedium,
    )
}
