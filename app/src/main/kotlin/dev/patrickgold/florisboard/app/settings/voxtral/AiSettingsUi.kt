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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyTextField
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes

/**
 * Content inside a grouped card that isn't a row, such as fields, pill buttons or a paragraph. Paints the
 * card colour with row padding, so it sits in the card like a row.
 */
@Composable
internal fun CardBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = OwnkeyTokens.RowPaddingHorizontal, vertical = OwnkeyTokens.RowPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A paragraph inside a grouped card. */
@Composable
internal fun CardText(text: String, modifier: Modifier = Modifier) {
    CardBlock(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = OwnkeyTokens.Secondary,
        )
    }
}

/**
 * Choice rows inside a dialog: a Raised box with 2dp gaps, so the options read as one list and line up
 * with the dialog text. Rows must not carry summaries, because Secondary text is too weak on Raised.
 */
@Composable
internal fun DialogChoices(content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val raised = remember(colorScheme) { colorScheme.copy(surface = OwnkeyTokens.Raised) }
    MaterialTheme(colorScheme = raised, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OwnkeyTokens.FieldShape)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(OwnkeyTokens.RowGap),
            content = content,
        )
    }
}

/**
 * The masked API key dialog for cloud dictation and rewrite. The typed key lives only in this dialog's
 * memory, never in saved state, and is handed to [onSave] trimmed. Remove appears when a key is stored.
 * [errorText] replaces the storage note under the field when the last Save or Remove failed.
 */
@Composable
internal fun ApiKeyDialog(
    title: String,
    text: String?,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    errorText: String? = null,
    extra: @Composable ColumnScope.() -> Unit = { },
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val save = { if (input.isNotBlank()) onSave(input.trim()) }
    OwnkeyDialog(
        title = title,
        onDismiss = onDismiss,
        text = text,
        confirmLabel = stringRes(R.string.settings__ai__save),
        onConfirm = save,
        confirmEnabled = input.isNotBlank(),
        dismissLabel = stringRes(R.string.action__cancel),
        neutralLabel = if (hasKey) stringRes(R.string.settings__ai__remove) else null,
        onNeutral = onRemove,
        neutralStyle = OwnkeyButtonStyle.Destructive,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        OwnkeyTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier
                .focusRequester(focusRequester)
                .testTag("ai-key-field"),
            label = stringRes(R.string.settings__ai__key__field),
            supportingText = stringRes(R.string.settings__ai__key__storage),
            isError = errorText != null,
            errorText = errorText,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        extra()
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

/**
 * A dialog with one text field and Save/Cancel. [errorFor] returns the error to show for a value, or null;
 * [canSave] decides whether Save is enabled, so a half-typed value can be blocked without an error yet.
 */
@Composable
internal fun TextFieldDialog(
    title: String,
    label: String,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorFor: (String) -> String? = { null },
    canSave: (String) -> Boolean = { errorFor(it) == null },
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fieldTag: String = "dialog-field",
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val error = errorFor(value)
    OwnkeyDialog(
        title = title,
        onDismiss = onDismiss,
        text = text,
        confirmLabel = stringRes(R.string.settings__ai__save),
        onConfirm = { if (canSave(value)) onSave(value) },
        confirmEnabled = canSave(value),
        dismissLabel = stringRes(R.string.action__cancel),
    ) {
        Spacer(modifier = Modifier.height(if (text != null) 16.dp else 4.dp))
        OwnkeyTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .focusRequester(focusRequester)
                .testTag(fieldTag),
            label = label,
            placeholder = placeholder,
            supportingText = supportingText,
            isError = error != null,
            errorText = error,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = keyboardOptions,
        )
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}
