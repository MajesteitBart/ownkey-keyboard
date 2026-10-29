/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import org.florisboard.lib.compose.stringRes

@Composable
fun FlorisConfirmDeleteDialog(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    what: String,
) {
    OwnkeyDialog(
        modifier = modifier,
        title = stringRes(R.string.action__delete_confirm_title),
        text = stringRes(R.string.action__delete_confirm_message, "name" to what),
        confirmLabel = stringRes(R.string.action__delete),
        onConfirm = onConfirm,
        destructive = true,
        dismissLabel = stringRes(R.string.action__cancel),
        onDismiss = onDismiss,
    )
}

@Composable
fun FlorisUnsavedChangesDialog(
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    OwnkeyDialog(
        modifier = modifier,
        title = stringRes(R.string.action__discard_confirm_title),
        text = stringRes(R.string.action__discard_confirm_message),
        confirmLabel = stringRes(R.string.action__save),
        onConfirm = onSave,
        dismissLabel = stringRes(R.string.action__discard),
        onDismiss = onDiscard,
        onOutsideDismissal = onDismiss,
        neutralLabel = stringRes(R.string.action__cancel),
        onNeutral = onDismiss,
    )
}
