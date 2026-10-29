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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceRow
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeySwitchRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.text.dictation.SpeechDictionaryUse
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyField
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyHints
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
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.TranscriptCleanup
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.entries
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.userStringRes
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.speechDictionary
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing

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
        val endpointUrl by prefsRef.voxtral.endpointUrl.collectAsState()
        val offline = remember { context.offlineDictation() }
        val localCompatible = remember { offline.compatible }
        val localModel by offline.state.collectAsState()
        val backendPreference by prefsRef.voxtral.dictationBackend.collectAsState()
        // The key check decrypts the Keystore-backed store, so it runs off the main thread; no notice until it's known.
        val hasCloudKey by produceState<Boolean?>(null) {
            value = withContext(Dispatchers.IO) { runCatching { VoxtralSecretsStore(context).hasApiKey() }.getOrDefault(false) }
        }
        // While the model store is still being checked the answer is unknown; no notice until it is known.
        val localModelReady = localModel.phase == ModelPhase.CHECKING || (localCompatible && localModel.currentId != null)
        val dictionaryUse = hasCloudKey?.let { key ->
            TranscriptionBackend.resolve(backendPreference, key, BuildConfig.DEBUG).speechDictionaryUse(key, localModelReady)
        }
        var editing by remember { mutableStateOf<SpeechDictionaryEntry?>(null) }
        var removed by remember { mutableStateOf<RemovedEntry?>(null) }
        var undoFailed by remember { mutableStateOf(false) }

        LaunchedEffect(removed) {
            if (removed != null) {
                delay(UNDO_MILLIS)
                removed = null
            }
        }

        when (dictionaryUse) {
            SpeechDictionaryUse.SYSTEM_VOICE_INPUT ->
                OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__backend_unused), tone = OwnkeyTone.Warning)
            SpeechDictionaryUse.NOT_SET_UP ->
                OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__backend_not_set_up), tone = OwnkeyTone.Warning)
            SpeechDictionaryUse.APPLIED, null -> Unit
        }
        when (state.loadError) {
            SpeechDictionaryLoadError.UNREADABLE ->
                OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__load_error), tone = OwnkeyTone.Error)
            SpeechDictionaryLoadError.NEWER_VERSION ->
                OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__load_error_newer), tone = OwnkeyTone.Error)
            null -> Unit
        }
        if (state.saveError) {
            OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__save_error), tone = OwnkeyTone.Error)
        }
        AddEntryGroup(
            document = state.document,
            initialHeard = heard,
            onAddWord = { pendingKey, word ->
                val id = pendingKey?.let { key ->
                    repository.state.value.document.words.firstOrNull { it.word.lowercase() == key.lowercase() }?.id
                }
                if (id != null) repository.updateWord(id, word)
                else repository.addWord(word)
            },
            onAddCorrection = { pendingKey, source, replacement ->
                val id = pendingKey?.let { key ->
                    repository.state.value.document.corrections.firstOrNull { it.source.lowercase() == key.lowercase() }?.id
                }
                if (id != null) {
                    repository.updateCorrection(id, source, replacement)
                } else repository.addCorrection(source, replacement)
            },
        )
        EntriesGroup(
            document = state.document,
            removed = removed,
            undoFailed = undoFailed,
            onEdit = { editing = it },
            onDelete = { entry ->
                scope.launch { undoFailed = false; removed = repository.remove(entry.id) }
            },
            onUndo = {
                val toRestore = removed ?: return@EntriesGroup
                scope.launch {
                    val restored = repository.restoreRemoved(toRestore)
                    if (removed === toRestore) {
                        if (restored) removed = null
                        undoFailed = !restored
                    }
                }
            },
        )
        FillerWordsGroup(document = state.document, repository = repository, saveError = state.saveError)
        AdvancedGroup(
            words = state.document.words.map { it.word },
            endpointUrl = endpointUrl,
            cloudMode = CloudVocabularyMode.fromPreference(cloudMode),
            onCloudModeChange = { mode -> scope.launch { prefsRef.voxtral.cloudVocabularyMode.set(mode.preference) } },
        )
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

// ---------------------------------------------------------------------------------------------
// Add form: one field, or heard/should be when correcting a misspelling
// ---------------------------------------------------------------------------------------------

@Composable
private fun AddEntryGroup(
    document: SpeechDictionaryDocument,
    initialHeard: String?,
    onAddWord: suspend (String?, String) -> EntryResult,
    onAddCorrection: suspend (String?, String, String) -> EntryResult,
) {
    val scope = rememberCoroutineScope()
    var correctionMode by rememberSaveable { mutableStateOf(!initialHeard.isNullOrBlank()) }
    var word by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(initialHeard.orEmpty()) }
    var replacement by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<EntryError?>(null) }
    var notice by remember { mutableStateOf<SpeechDictionaryEntry?>(null) }
    // A failed write's numeric ID may be reused after process death; retain the semantic key.
    var pendingWordKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCorrectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

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
        saveFailed = false
        pendingWordKey = null
        pendingCorrectionKey = null
    }

    fun save() {
        if (saving) return
        saving = true
        notice = null
        scope.launch {
            try {
                val result = if (correctionMode) onAddCorrection(pendingCorrectionKey, source, replacement)
                else onAddWord(pendingWordKey, word)
                when (result) {
                    is EntryResult.Saved -> {
                        saveFailed = !result.persisted
                        if (result.persisted) {
                            notice = result.entry
                            clear()
                        } else when (val entry = result.entry) {
                            is SpeechDictionaryEntry.Word -> pendingWordKey = entry.entry.word
                            is SpeechDictionaryEntry.Correction -> pendingCorrectionKey = entry.entry.source
                        }
                    }
                    is EntryResult.Rejected -> error = result.error
                }
            } finally {
                saving = false
            }
        }
    }

    val canSave = if (correctionMode) source.isNotBlank() && replacement.isNotBlank() else word.isNotBlank()

    OwnkeyGroup(
        title = userStringRes(R.string.speech_dictionary__add_title),
        footer = userStringRes(R.string.speech_dictionary__intro_summary),
    ) {
        OwnkeySwitchRow(
            title = userStringRes(R.string.speech_dictionary__correction_toggle),
            summary = userStringRes(R.string.speech_dictionary__correction_toggle_summary),
            checked = correctionMode,
            enabled = !saving,
            onCheckedChange = {
                correctionMode = it
                error = null
            },
            modifier = Modifier.testTag("dictionary-correction-mode"),
        )
        CardBlock {
            if (correctionMode) {
                OwnkeyTextField(
                    value = source,
                    enabled = !saving,
                    onValueChange = { source = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__source_label),
                    modifier = Modifier.testTag("dictionary-heard-field"),
                )
                OwnkeyTextField(
                    value = replacement,
                    enabled = !saving,
                    onValueChange = { replacement = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__replacement_label),
                    modifier = Modifier.testTag("dictionary-replacement-field"),
                )
            } else {
                OwnkeyTextField(
                    value = word,
                    enabled = !saving,
                    onValueChange = { word = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__word_label),
                    supportingText = if (document.isEmpty) userStringRes(R.string.speech_dictionary__entries_example) else null,
                    modifier = Modifier.testTag("dictionary-word-field"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnkeyButton(
                    label = userStringRes(R.string.speech_dictionary__save_action),
                    onClick = ::save,
                    enabled = canSave && !saving,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dictionary-save"),
                )
                OwnkeyButton(
                    label = userStringRes(R.string.speech_dictionary__clear_action),
                    onClick = ::clear,
                    enabled = !saving && (word.isNotEmpty() || source.isNotEmpty() || replacement.isNotEmpty()),
                    style = OwnkeyButtonStyle.Plain,
                )
            }
        }
        error?.let { OwnkeyNotice(text = it.text(), tone = OwnkeyTone.Error) }
        if (saveFailed) OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__save_error), tone = OwnkeyTone.Error)
        notice?.let { saved ->
            OwnkeyNotice(
                text = when (saved) {
                    is SpeechDictionaryEntry.Word -> userStringRes(R.string.speech_dictionary__saved_word, "word" to saved.entry.word)
                    is SpeechDictionaryEntry.Correction -> userStringRes(
                        R.string.speech_dictionary__saved_correction,
                        "source" to saved.entry.source,
                        "replacement" to saved.entry.replacement,
                    )
                },
                tone = OwnkeyTone.Success,
            )
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
private fun EntriesGroup(
    document: SpeechDictionaryDocument,
    removed: RemovedEntry?,
    undoFailed: Boolean,
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

    OwnkeyGroup(
        title = userStringRes(R.string.speech_dictionary__entries_title),
        footer = userStringRes(R.string.speech_dictionary__cloud_disclosure),
    ) {
        removed?.let { removedEntry ->
            OwnkeyNotice(
                text = userStringRes(R.string.speech_dictionary__removed, "entry" to removedEntry.entry.displayText()),
                actionLabel = userStringRes(R.string.speech_dictionary__undo_action),
                onAction = onUndo,
                modifier = Modifier.testTag("dictionary-undo"),
            )
            if (undoFailed) OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__undo_failed), tone = OwnkeyTone.Error)
        }
        if (entries.isEmpty()) {
            CardText(text = userStringRes(R.string.speech_dictionary__entries_empty))
            return@OwnkeyGroup
        }
        if (entries.size > SEARCH_THRESHOLD || query.isNotEmpty()) {
            CardBlock {
                OwnkeyTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = userStringRes(R.string.speech_dictionary__search_label),
                    modifier = Modifier.testTag("dictionary-search"),
                )
            }
        }
        if (visible.isEmpty()) {
            CardText(text = userStringRes(R.string.speech_dictionary__search_empty, "query" to query.trim()))
            return@OwnkeyGroup
        }
        if (words.isNotEmpty()) {
            OwnkeyGroup(title = userStringRes(R.string.speech_dictionary__words_heading, "count" to words.size)) {
                words.forEach { entry ->
                    EntryRow(text = entry.entry.word, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }
        if (corrections.isNotEmpty()) {
            OwnkeyGroup(title = userStringRes(R.string.speech_dictionary__corrections_heading, "count" to corrections.size)) {
                corrections.forEach { entry ->
                    EntryRow(text = entry.displayText(), onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                }
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
private fun SpeechDictionaryEntry.displayText(): String = when (this) {
    is SpeechDictionaryEntry.Word -> entry.word
    is SpeechDictionaryEntry.Correction -> userStringRes(
        R.string.speech_dictionary__correction_arrow,
        "source" to entry.source,
        "replacement" to entry.replacement,
    )
}

@Composable
private fun EntryRow(text: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    OwnkeyRow(
        title = text,
        trailing = {
            Row {
                RowIconButton(label = userStringRes(R.string.settings__ai__dictionary__edit_entry, "entry" to text), onClick = onEdit) {
                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = null, tint = OwnkeyTokens.Secondary)
                }
                RowIconButton(label = userStringRes(R.string.settings__ai__dictionary__delete_entry, "entry" to text), onClick = onDelete) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = OwnkeyTokens.Secondary)
                }
            }
        },
    )
}

/** A 48dp icon button whose accessible name says what it does to the row's entry. */
@Composable
private fun RowIconButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(OwnkeyTokens.MinTouchTarget)
            .ownkeyFocusRing(CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
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
    var saveFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val isCorrection = entry is SpeechDictionaryEntry.Correction

    OwnkeyDialog(
        title = userStringRes(
            if (isCorrection) R.string.speech_dictionary__edit_correction_title else R.string.speech_dictionary__edit_word_title,
        ),
        onDismiss = { if (!saving) onDismiss() },
        confirmLabel = userStringRes(R.string.speech_dictionary__save_action),
        confirmEnabled = !saving,
        onConfirm = {
            if (!saving) {
                saving = true
                scope.launch {
                    try {
                        val result = if (isCorrection) {
                            onSaveCorrection(entry.id, source, replacement)
                        } else {
                            onSaveWord(entry.id, word)
                        }
                        when (result) {
                            is EntryResult.Saved -> {
                                saveFailed = !result.persisted
                                if (result.persisted) onDismiss()
                            }
                            is EntryResult.Rejected -> error = result.error
                        }
                    } finally {
                        saving = false
                    }
                }
            }
        },
        dismissLabel = userStringRes(R.string.action__cancel),
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isCorrection) {
                OwnkeyTextField(
                    value = source,
                    enabled = !saving,
                    onValueChange = { source = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__source_label),
                )
                OwnkeyTextField(
                    value = replacement,
                    enabled = !saving,
                    onValueChange = { replacement = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__replacement_label),
                    isError = error != null || saveFailed,
                    errorText = error?.text() ?: userStringRes(R.string.speech_dictionary__save_error).takeIf { saveFailed },
                )
            } else {
                OwnkeyTextField(
                    value = word,
                    enabled = !saving,
                    onValueChange = { word = it; error = null },
                    label = userStringRes(R.string.speech_dictionary__word_label),
                    isError = error != null || saveFailed,
                    errorText = error?.text() ?: userStringRes(R.string.speech_dictionary__save_error).takeIf { saveFailed },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filler words
// ---------------------------------------------------------------------------------------------

@Composable
private fun FillerWordsGroup(document: SpeechDictionaryDocument, repository: SpeechDictionaryRepository, saveError: Boolean) {
    val scope = rememberCoroutineScope()
    val fillers = document.fillers
    val selected = FillerRules.normalizeLanguages(fillers.languages)
    val preview = remember(fillers) { FillerRules.fillerWords(fillers.languages, fillers.custom) }
    var extraDialog by rememberSaveable { mutableStateOf(false) }

    OwnkeyGroup(
        title = userStringRes(R.string.speech_dictionary__fillers_title),
        footer = when {
            !fillers.enabled -> userStringRes(R.string.speech_dictionary__fillers_off_note)
            preview.isEmpty() -> userStringRes(R.string.speech_dictionary__fillers_preview_none)
            else -> userStringRes(R.string.speech_dictionary__fillers_preview, "words" to preview.joinToString(", "))
        },
    ) {
        OwnkeySwitchRow(
            title = userStringRes(R.string.speech_dictionary__fillers_enabled),
            summary = userStringRes(R.string.settings__ai__dictionary__fillers_switch_summary),
            checked = fillers.enabled,
            onCheckedChange = { enabled -> scope.launch { repository.setFillersEnabled(enabled) } },
            modifier = Modifier.testTag("dictionary-fillers"),
        )
        CardText(text = userStringRes(R.string.settings__ai__dictionary__languages_text))
        FillerLanguage.entries.forEach { language ->
            val removes = userStringRes(R.string.speech_dictionary__fillers_language_words, "words" to language.fillers.joinToString(", "))
            val keeps = language.protectedWords.takeIf { it.isNotEmpty() }?.let {
                userStringRes(R.string.speech_dictionary__fillers_language_protects, "words" to it.joinToString(", "))
            }
            OwnkeySwitchRow(
                title = language.label(),
                summary = listOfNotNull(removes, keeps).joinToString("\n"),
                checked = language.code in selected,
                enabled = fillers.enabled,
                // Rebased against the stored document, so two quick taps cannot overwrite each other.
                onCheckedChange = { nowChecked -> scope.launch { repository.setFillerLanguage(language.code, nowChecked) } },
                modifier = Modifier.testTag("dictionary-filler-${language.code}"),
            )
        }
        OwnkeyRow(
            title = userStringRes(R.string.settings__ai__dictionary__extra_row),
            value = if (fillers.custom.isEmpty()) userStringRes(R.string.settings__ai__dictionary__extra_none) else fillers.custom.size.toString(),
            summary = fillers.custom.takeIf { it.isNotEmpty() }?.joinToString(", "),
            enabled = fillers.enabled,
            onClick = { extraDialog = true },
            modifier = Modifier.testTag("dictionary-extra-words"),
        )
    }

    if (extraDialog) {
        TextFieldDialog(
            title = userStringRes(R.string.speech_dictionary__fillers_custom_label),
            label = userStringRes(R.string.settings__ai__dictionary__extra_row),
            initialValue = fillers.custom.joinToString(", "),
            text = userStringRes(R.string.settings__ai__dictionary__extra_text),
            singleLine = false,
            // Compared after the same normalisation the repository applies, so Save is not offered for
            // a draft that only differs by whitespace, duplicates or letter case.
            canSave = { draft -> normalizeFillerDraft(draft) != fillers.custom || saveError },
            fieldTag = "dictionary-extra-words-field",
            onSave = { draft ->
                scope.launch { repository.setCustomFillers(normalizeFillerDraft(draft)) }
                extraDialog = false
            },
            onDismiss = { extraDialog = false },
        )
    }
}

private fun normalizeFillerDraft(draft: String): List<String> = TranscriptCleanup.normalizeVocabulary(draft.split(',', '\n'))

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
// Advanced: recognition hints
// ---------------------------------------------------------------------------------------------

@Composable
private fun AdvancedGroup(
    words: List<String>,
    endpointUrl: String,
    cloudMode: CloudVocabularyMode,
    onCloudModeChange: (CloudVocabularyMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OwnkeyGroup(footer = if (expanded) userStringRes(R.string.speech_dictionary__hints_summary) else null) {
        OwnkeyRow(
            title = userStringRes(R.string.settings__ai__dictionary__advanced),
            summary = userStringRes(R.string.settings__ai__dictionary__advanced_summary),
            onClick = { expanded = !expanded },
            trailing = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = OwnkeyTokens.Secondary,
                )
            },
            modifier = Modifier
                .testTag("dictionary-advanced")
                // TalkBack reads the expand/collapse action as the row's state.
                .semantics {
                    if (expanded) collapse { expanded = false; true } else expand { expanded = true; true }
                },
        )
        if (expanded) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(OwnkeyTokens.RowGap),
            ) {
                listOf(
                    Triple(CloudVocabularyMode.AUTO, R.string.speech_dictionary__hints_cloud_auto, R.string.speech_dictionary__hints_cloud_auto_summary),
                    Triple(CloudVocabularyMode.PROMPT, R.string.speech_dictionary__hints_cloud_prompt, R.string.speech_dictionary__hints_cloud_prompt_summary),
                    Triple(CloudVocabularyMode.OFF, R.string.speech_dictionary__hints_cloud_off, R.string.speech_dictionary__hints_cloud_off_summary),
                ).forEach { (mode, label, summary) ->
                    OwnkeyChoiceRow(
                        label = userStringRes(label),
                        summary = userStringRes(summary),
                        selected = cloudMode == mode,
                        onClick = { onCloudModeChange(mode) },
                        modifier = Modifier.testTag("dictionary-hints-${mode.preference}"),
                    )
                }
            }
            // The status follows the field the configured endpoint actually gets: in Automatic mode an
            // unknown endpoint receives no words at all, so a "some words fit" line would be wrong there.
            val resolvedField = CloudVocabularyHints.field(
                endpointUrl.trim().ifBlank { VoxtralRelayTranscriptionClient.DefaultEndpointUrl },
                cloudMode,
            )
            if (cloudMode == CloudVocabularyMode.AUTO && resolvedField == CloudVocabularyField.NONE) {
                OwnkeyNotice(text = userStringRes(R.string.speech_dictionary__hints_cloud_auto_unsupported), tone = OwnkeyTone.Warning)
            } else if (resolvedField != CloudVocabularyField.NONE && words.isNotEmpty()) {
                val bounded = remember(words) { CloudVocabularyHints.bound(words) }
                if (bounded.dropped > 0) {
                    OwnkeyNotice(
                        text = userStringRes(
                            R.string.speech_dictionary__hints_cloud_budget_exceeded,
                            "included" to bounded.included,
                            "total" to (bounded.included + bounded.dropped),
                        ),
                        tone = OwnkeyTone.Warning,
                    )
                }
            }
        }
    }
}
