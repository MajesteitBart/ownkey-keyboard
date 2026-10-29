/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggStylesheet

/** Function keys, which sit on the darker `--surface-fn` fill. Never enter (10) or space (32). */
private val FunctionKeyCodes = buildList {
    addAll(-13..-1)
    addAll(-28..-21)
    addAll(-207..-201)
    addAll(-213..-212)
    addAll(-228..-221)
    add(KeyCode.VOICE_INPUT)
    add(KeyCode.TAB)
}

val FlorisImeThemeBaseStyle = SnyggStylesheet.v2 {
    // Signal Graphite, used when no stylesheet loads. Keep in step with ownkey_signal_graphite.json.
    defines {
        "--primary" to rgbaColor(222, 95, 20)
        "--primary-variant" to rgbaColor(195, 84, 18)
        "--secondary" to rgbaColor(222, 95, 20)
        "--secondary-variant" to rgbaColor(222, 95, 20)
        "--background" to rgbaColor(28, 28, 30)
        "--background-variant" to rgbaColor(28, 28, 30)
        "--surface" to rgbaColor(58, 58, 60)
        "--surface-fn" to rgbaColor(44, 44, 46)
        "--surface-variant" to rgbaColor(84, 84, 86)
        "--popup-surface" to rgbaColor(84, 84, 86)
        "--enter" to rgbaColor(84, 84, 86)
        "--enter-pressed" to rgbaColor(99, 99, 102)

        "--on-primary" to rgbaColor(255, 255, 255)
        "--on-background" to rgbaColor(255, 255, 255)
        "--on-background-disabled" to rgbaColor(255, 255, 255, 0.38f)
        "--on-surface" to rgbaColor(255, 255, 255)
        "--on-surface-variant" to rgbaColor(174, 174, 178)
        "--candidate" to rgbaColor(209, 209, 214)
        "--pill" to rgbaColor(255, 255, 255)
        "--on-pill" to rgbaColor(0, 0, 0)

        "--shape" to roundedCornerShape(8.dp)
        "--shape-variant" to roundedCornerShape(16.dp)
    }

    FlorisImeUi.Window.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    FlorisImeUi.Key.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(21.sp)
        shadowElevation = size(0.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
    }
    FlorisImeUi.Key.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface-variant")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to FunctionKeyCodes) {
        background = `var`("--surface-fn")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.ENTER)) {
        background = `var`("--enter")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.ENTER), selector = SnyggSelector.PRESSED) {
        background = `var`("--enter-pressed")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.SPACE)) {
        background = `var`("--surface")
        foreground = `var`("--on-surface-variant")
        fontSize = fontSize(13.sp)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2,
    )) {
        fontSize = fontSize(16.sp)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED,
    )) {
        fontSize = fontSize(12.sp)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.VIEW_NUMERIC_ADVANCED)) {
        textMaxLines = textMaxLines(2)
    }
    FlorisImeUi.Key.elementName(
        FlorisImeUi.Attr.Code to listOf(KeyCode.SHIFT),
        FlorisImeUi.Attr.ShiftState to listOf(InputShiftState.CAPS_LOCK.toString()),
    ) {
        foreground = `var`("--primary")
    }
    FlorisImeUi.KeyHint.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        fontSize = fontSize(9.sp)
        padding = padding(0.dp, 5.dp, 6.dp, 0.dp)
        textMaxLines = textMaxLines(1)
    }
    FlorisImeUi.KeyPopupBox.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(21.sp)
        shape = `var`("--shape")
        shadowElevation = size(0.dp)
    }
    FlorisImeUi.KeyPopupElement.elementName(selector = SnyggSelector.FOCUS) {
        background = `var`("--on-surface")
        foreground = `var`("--background")
        shape = `var`("--shape")
    }
    FlorisImeUi.KeyPopupExtendedIndicator.elementName {
        fontSize = fontSize(16.sp)
    }

    FlorisImeUi.Smartbar.elementName {
        background = `var`("--background")
        foreground = `var`("--on-surface")
        fontSize = fontSize(18.sp)
    }
    FlorisImeUi.SmartbarSharedActionsToggle.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        margin = padding(4.dp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarExtendedActionsToggle.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        margin = padding(4.dp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarActionKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        fontSize = fontSize(22.sp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarActionKey.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }

    FlorisImeUi.SmartbarActionsOverflow.elementName {
        margin = padding(4.dp)
    }
    FlorisImeUi.SmartbarActionsOverflowCustomizeButton.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(0.dp, 8.dp, 0.dp, 0.dp)
        shape = roundedCornerShape(24.dp)
    }
    FlorisImeUi.SmartbarActionTile.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(13.sp)
        margin = padding(4.dp)
        padding = padding(4.dp)
        shape = `var`("--shape-variant")
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionTile.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }
    FlorisImeUi.SmartbarActionTileIcon.elementName {
        fontSize = fontSize(24.sp)
        margin = padding(0.dp, 0.dp, 0.dp, 8.dp)
    }

    FlorisImeUi.SmartbarActionsEditor.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SmartbarActionsEditorHeader.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(16.sp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorHeaderButton.elementName {
        margin = padding(4.dp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarActionsEditorSubheader.elementName {
        foreground = `var`("--on-surface")
        fontSize = fontSize(13.sp)
        fontWeight = fontWeight(FontWeight.Medium)
        padding = padding(12.dp, 16.dp, 12.dp, 8.dp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorTileGrid.elementName {
        margin = padding(4.dp, 0.dp)
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName {
        margin = padding(4.dp)
        padding = padding(8.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.NOOP)) {
        foreground = `var`("--on-background-disabled")
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.DRAG_MARKER)) {
        foreground = `var`("--on-surface-variant")
    }

    FlorisImeUi.SmartbarCandidateWord.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--candidate")
        fontSize = fontSize(16.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarCandidateWord.elementName("auto-commit" to listOf(1)) {
        foreground = `var`("--on-surface")
        fontWeight = fontWeight(FontWeight.Medium)
    }
    FlorisImeUi.SmartbarCandidateWord.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.SmartbarCandidateWordSecondaryText.elementName {
        fontSize = fontSize(8.sp)
        margin = padding(0.dp, 2.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SmartbarCandidateClip.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--candidate")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = roundedCornerShape(8)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarCandidateClip.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.SmartbarCandidateClipIcon.elementName {
        margin = padding(0.dp, 0.dp, 4.dp, 0.dp)
    }
    FlorisImeUi.SmartbarCandidateSpacer.elementName {
        foreground = rgbaColor(0, 0, 0, 0f)
    }

    FlorisImeUi.ClipboardHeader.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(16.sp)
    }
    FlorisImeUi.ClipboardSubheader.elementName {
        fontSize = fontSize(14.sp)
        margin = padding(6.dp)
    }
    FlorisImeUi.ClipboardContent.elementName {
        padding = padding(10.dp)
    }
    FlorisImeUi.ClipboardItem.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(0.dp)
        textMaxLines = textMaxLines(10)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.ClipboardItemPopup.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(0.dp)
    }
    FlorisImeUi.ClipboardItemActions.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
        margin = padding(4.dp)
        shape = `var`("--shape-variant")
        shadowElevation = size(0.dp)
    }
    FlorisImeUi.ClipboardItemAction.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(12.dp)
    }
    FlorisImeUi.ClipboardItemActionText.elementName {
        margin = padding(8.dp, 0.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.ClipboardHistoryDisabledButton.elementName {
        background = `var`("--pill")
        foreground = `var`("--on-pill")
        shape = roundedCornerShape(50)
    }

    FlorisImeUi.MediaEmojiKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
    }
    FlorisImeUi.MediaEmojiKey.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    FlorisImeUi.GlideTrail.elementName {
        foreground = rgbaColor(222, 95, 20, 0.7f)
    }

    FlorisImeUi.InlineAutofillChip.elementName {
        background = `var`("--popup-surface")
        foreground = `var`("--on-surface")
    }

    FlorisImeUi.IncognitoModeIndicator.elementName {
        foreground = rgbaColor(255, 255, 255, 0.067f)
    }

    FlorisImeUi.OneHandedPanel.elementName {
        background = `var`("--background")
        foreground = `var`("--on-surface")
    }

    FlorisImeUi.SubtypePanel.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
        shape = roundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SubtypePanelHeader.elementName {
        background = `var`("--background")
        foreground = `var`("--on-surface")
        fontSize = fontSize(18.sp)
        padding = padding(12.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SubtypePanelListItem.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(16.dp)
    }
    FlorisImeUi.SubtypePanelListItemIconLeading.elementName {
        fontSize = fontSize(24.sp)
        padding = padding(0.dp, 0.dp, 16.dp, 0.dp)
    }
    FlorisImeUi.SubtypePanelListItemText.elementName {
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
}
