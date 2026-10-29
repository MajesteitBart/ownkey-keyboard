/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.voxtral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPreset
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** A style row previews its instruction's first line in at most this many lines. */
private const val StyleSummaryMaxLines = 2

/**
 * The rewrite panel's style buttons. Tapping a style edits it in a dialog; every save, delete, add and reset is
 * stored right away, so leaving the screen never loses an edit.
 */
@Composable
fun RewriteStylesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__ai__rewrite_styles__title)
    previewFieldVisible = false

    content {
        val prefsRef = prefs
        val scope = rememberCoroutineScope()
        val stylesJson by prefsRef.voxtral.rewritePrompts.collectAsState()
        val styles = remember(stylesJson) { RewritePromptPresets.decode(stylesJson) }
        // The style being edited, kept whole so a new, unsaved style survives rotation too.
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var newStyleId by rememberSaveable { mutableStateOf<String?>(null) }
        var newStyleName by rememberSaveable { mutableStateOf("") }
        var confirmReset by rememberSaveable { mutableStateOf(false) }

        fun store(updated: List<RewritePromptPreset>) {
            scope.launch { prefsRef.voxtral.rewritePrompts.set(RewritePromptPresets.encode(updated)) }
        }

        OwnkeyGroup(footer = stringRes(R.string.pref__ai__rewrite_voices__summary)) {
            styles.forEachIndexed { index, style ->
                OwnkeyRow(
                    title = style.name,
                    summary = RewriteStyles.summary(style.instruction),
                    summaryMaxLines = StyleSummaryMaxLines,
                    onClick = { editingId = style.id },
                    modifier = Modifier.testTag("rewrite-style-$index"),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnkeyButton(
                label = stringRes(R.string.pref__ai__rewrite_voice__add_action),
                onClick = {
                    val created = RewriteStyles.newStyle(styles)
                    newStyleId = created.id
                    newStyleName = created.name
                    editingId = created.id
                },
                style = OwnkeyButtonStyle.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rewrite-style-add"),
            )
            OwnkeyButton(
                label = stringRes(R.string.pref__ai__rewrite_voice__reset_action),
                onClick = { confirmReset = true },
                style = OwnkeyButtonStyle.Plain,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rewrite-style-reset"),
            )
        }

        val editing = editingId?.let { id ->
            styles.firstOrNull { it.id == id }
                ?: RewritePromptPreset(id = id, name = newStyleName, instruction = "").takeIf { id == newStyleId }
        }
        if (editing != null) {
            val isNew = styles.none { it.id == editing.id }
            val close = {
                editingId = null
                newStyleId = null
            }
            StyleDialog(
                style = editing,
                isNew = isNew,
                canDelete = !isNew && RewriteStyles.canDelete(styles),
                onSave = { saved ->
                    store(RewriteStyles.save(styles, saved))
                    close()
                },
                onDelete = {
                    store(RewriteStyles.delete(styles, editing.id))
                    close()
                },
                onDismiss = close,
            )
        }
        if (confirmReset) {
            OwnkeyDialog(
                title = stringRes(R.string.settings__ai__styles__reset_title),
                onDismiss = { confirmReset = false },
                text = stringRes(R.string.settings__ai__styles__reset_text),
                confirmLabel = stringRes(R.string.settings__ai__styles__reset_confirm),
                onConfirm = {
                    scope.launch { prefsRef.voxtral.rewritePrompts.set(RewritePromptPresets.defaultJson) }
                    confirmReset = false
                },
                destructive = true,
                dismissLabel = stringRes(R.string.action__cancel),
            )
        }
    }
}

@Composable
private fun StyleDialog(
    style: RewritePromptPreset,
    isNew: Boolean,
    canDelete: Boolean,
    onSave: (RewritePromptPreset) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(style.id) { mutableStateOf(style.name) }
    var instruction by rememberSaveable(style.id) { mutableStateOf(style.instruction) }
    OwnkeyDialog(
        title = stringRes(if (isNew) R.string.settings__ai__styles__new_title else R.string.settings__ai__styles__edit_title),
        onDismiss = onDismiss,
        confirmLabel = stringRes(R.string.settings__ai__save),
        onConfirm = {
            if (RewriteStyles.canSave(name, instruction)) onSave(style.copy(name = name, instruction = instruction))
        },
        confirmEnabled = RewriteStyles.canSave(name, instruction),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (isNew) null else stringRes(R.string.pref__ai__rewrite_voice__remove_action),
        onNeutral = onDelete,
        neutralStyle = OwnkeyButtonStyle.Destructive,
        neutralEnabled = canDelete,
        // An outside tap shouldn't throw away a half-written instruction; back and Cancel still close.
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        OwnkeyTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.testTag("rewrite-style-name"),
            label = stringRes(R.string.pref__ai__rewrite_voice_name__label),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OwnkeyTextField(
            value = instruction,
            onValueChange = { instruction = it },
            modifier = Modifier.testTag("rewrite-style-instruction"),
            label = stringRes(R.string.pref__ai__rewrite_instruction__label),
            placeholder = stringRes(R.string.settings__ai__styles__instruction_hint),
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}
