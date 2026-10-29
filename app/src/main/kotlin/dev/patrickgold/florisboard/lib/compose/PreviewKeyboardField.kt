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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.compose.verticalTween

private const val AnimationDuration = 200

private val PreviewEnterTransition = EnterTransition.verticalTween(AnimationDuration)
private val PreviewExitTransition = ExitTransition.verticalTween(AnimationDuration)
private val PreviewFieldHeight = 52.dp
// Keeps large-font text off the pill's edges once the pill grows past 52dp.
private val PreviewFieldTextVertical = 6.dp
private val PreviewFieldTextStart = 20.dp
// Room for the 48dp keyboard picker and its 4dp inset.
private val PreviewFieldTextEnd = 52.dp

val LocalPreviewFieldController = staticCompositionLocalOf<PreviewFieldController?> { null }

/** The typed text survives rotation and other activity re-creation, like any text field. */
@Composable
fun rememberPreviewFieldController(): PreviewFieldController {
    return rememberSaveable(saver = PreviewFieldController.Saver) { PreviewFieldController() }
}

class PreviewFieldController(initialText: TextFieldValue = TextFieldValue("")) {
    val focusRequester = FocusRequester()
    var isVisible by mutableStateOf(false)
    var isFocused by mutableStateOf(false)
    var text by mutableStateOf(initialText)

    companion object {
        /** Saves only the text; screens set [isVisible] again when they compose. */
        val Saver: Saver<PreviewFieldController, Any> = Saver(
            save = { controller -> with(TextFieldValue.Saver) { save(controller.text) } },
            restore = { saved -> PreviewFieldController(TextFieldValue.Saver.restore(saved) ?: TextFieldValue("")) },
        )
    }
}

/**
 * The "Try Ownkey here" field pinned under preview screens: a Card pill, 52dp or taller for large fonts,
 * with 16dp margins and a trailing keyboard picker. The label is the field's accessible name, not only a
 * placeholder.
 */
@Composable
fun PreviewKeyboardField(
    controller: PreviewFieldController,
    modifier: Modifier = Modifier,
    hint: String = stringRes(R.string.ownkey_ui__preview_field_label),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = OwnkeyTokens.Label,
        textDirection = TextDirection.ContentOrLtr,
    )
    val chooseKeyboardLabel = stringRes(R.string.ownkey_ui__choose_keyboard)

    AnimatedVisibility(
        visible = controller.isVisible,
        enter = PreviewEnterTransition,
        exit = PreviewExitTransition,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(OwnkeyTokens.Black)
                .padding(OwnkeyTokens.ScreenInset),
            contentAlignment = Alignment.Center,
        ) {
            // The pill holds the field, its placeholder and the keyboard picker side by side, so the picker is
            // a button next to the edit box instead of a control inside it. Compose 1.10 still reports the
            // field's label on a non-focusable child of the EditText: a text field merges its descendants, its
            // inner text layout is always a semantics node, and Compose sets a merging node's content
            // description on the node itself only when it has no children (it never sets hint text).
            Box(
                modifier = modifier
                    .widthIn(max = OwnkeyTokens.ContentMaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = PreviewFieldHeight)
                    .clip(OwnkeyTokens.PillShape)
                    .background(OwnkeyTokens.Card),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (controller.text.text.isEmpty()) {
                    Text(
                        modifier = Modifier
                            .padding(start = PreviewFieldTextStart, end = PreviewFieldTextEnd)
                            .clearAndSetSemantics { },
                        text = hint,
                        style = textStyle.copy(color = OwnkeyTokens.Secondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PreviewFieldHeight)
                        .testTag("preview-field")
                        .onFocusChanged { controller.isFocused = it.isFocused }
                        .semantics { contentDescription = hint }
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Back) {
                                focusManager.clearFocus()
                            }
                            false
                        }
                        .focusRequester(controller.focusRequester),
                    value = controller.text,
                    onValueChange = { controller.text = it },
                    textStyle = textStyle,
                    cursorBrush = SolidColor(accent),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = PreviewFieldHeight)
                                .padding(
                                    start = PreviewFieldTextStart,
                                    top = PreviewFieldTextVertical,
                                    end = PreviewFieldTextEnd,
                                    bottom = PreviewFieldTextVertical,
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            innerTextField()
                        }
                    },
                )
                IconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .semantics { contentDescription = chooseKeyboardLabel },
                    onClick = {
                        if (!InputMethodUtils.showImePicker(context)) {
                            context.showShortToastSync("Error: InputMethodManager service not available!")
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = OwnkeyTokens.Secondary,
                    )
                }
            }
        }
    }
}
