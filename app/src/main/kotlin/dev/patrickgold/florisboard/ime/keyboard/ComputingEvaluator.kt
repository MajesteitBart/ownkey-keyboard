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

package dev.patrickgold.florisboard.ime.keyboard

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.sharp.Backspace
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.sharp.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.sharp.KeyboardReturn
import androidx.compose.material.icons.automirrored.sharp.Redo
import androidx.compose.material.icons.automirrored.sharp.Undo
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.sharp.ArrowUpward
import androidx.compose.material.icons.sharp.Assignment
import androidx.compose.material.icons.sharp.AutoAwesome
import androidx.compose.material.icons.sharp.ContentCopy
import androidx.compose.material.icons.sharp.EmojiEmotions
import androidx.compose.material.icons.sharp.KeyboardArrowDown
import androidx.compose.material.icons.sharp.KeyboardArrowUp
import androidx.compose.material.icons.sharp.Language
import androidx.compose.material.icons.sharp.Mic
import androidx.compose.material.icons.sharp.MoreHoriz
import androidx.compose.material.icons.sharp.Search
import androidx.compose.material.icons.sharp.SelectAll
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.ThemeIconStyle
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.vectorResource
import org.florisboard.lib.compose.icons.ForwardDelete

interface ComputingEvaluator {
    val version: Int

    val keyboard: Keyboard

    val editorInfo: FlorisEditorInfo

    val state: KeyboardState

    val subtype: Subtype

    fun context(): Context?

    fun displayLanguageNamesIn(): DisplayLanguageNamesIn

    fun evaluateEnabled(data: KeyData): Boolean

    fun evaluateVisible(data: KeyData): Boolean

    fun isSlot(data: KeyData): Boolean

    fun slotData(data: KeyData): KeyData?
}

object DefaultComputingEvaluator : ComputingEvaluator {
    override val version = -1

    override val keyboard = PlaceholderLoadingKeyboard

    override val editorInfo = FlorisEditorInfo.Unspecified

    override val state = KeyboardState.new()

    override val subtype = Subtype.DEFAULT

    override fun context(): Context? = null

    override fun displayLanguageNamesIn() = DisplayLanguageNamesIn.NATIVE_LOCALE

    override fun evaluateEnabled(data: KeyData): Boolean = true

    override fun evaluateVisible(data: KeyData): Boolean = true

    override fun isSlot(data: KeyData): Boolean = false

    override fun slotData(data: KeyData): KeyData? = null
}

private var cachedDisplayNameState = Triple(FlorisLocale.ROOT, DisplayLanguageNamesIn.SYSTEM_LOCALE, "")

/**
 * Compute language name with a cache to prevent repetitive calling of `locale.displayName()`, which invokes the
 * underlying `LocaleNative.getLanguageName()` method and in turn uses the rather slow ICU data table to look up the
 * language name. This only caches the last display name, but that's more than enough, as a one-time re-computation when
 * the subtype changes does not hurt, the repetitive computation for the same language hurts.
 */
private fun computeLanguageDisplayName(locale: FlorisLocale, displayLanguageNamesIn: DisplayLanguageNamesIn): String {
    val (cachedLocale, cachedDisplayLanguageNamesIn, cachedDisplayName) = cachedDisplayNameState
    if (cachedLocale == locale && cachedDisplayLanguageNamesIn == displayLanguageNamesIn) {
        return cachedDisplayName
    }
    val displayName = when (displayLanguageNamesIn) {
        DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
        DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
    }
    cachedDisplayNameState = Triple(locale, displayLanguageNamesIn, displayName)
    return displayName
}

private fun String.withoutTrailingRegion(): String {
    val regionStart = indexOf(" (")
    return if (regionStart > 0 && endsWith(")")) substring(0, regionStart) else this
}

private fun ComputingEvaluator.tablerIcon(id: Int): ImageVector? {
    return context()?.vectorResource(id = id)
}

/**
 * Picks the icon from the user's selected icon package (Theme settings): thin Tabler outline,
 * or the filled/rounded/sharp Material sets.
 */
private fun ComputingEvaluator.styledIcon(
    tablerId: Int,
    filled: ImageVector,
    rounded: ImageVector,
    sharp: ImageVector,
): ImageVector? {
    val prefs by FlorisPreferenceStore
    return when (prefs.theme.iconStyle.get()) {
        ThemeIconStyle.THIN_OUTLINE -> tablerIcon(tablerId) ?: filled
        ThemeIconStyle.FILLED -> filled
        ThemeIconStyle.ROUNDED -> rounded
        ThemeIconStyle.SHARP -> sharp
    }
}

fun ComputingEvaluator.computeLabel(data: KeyData): String? {
    val evaluator = this
    return if (data.type == KeyType.CHARACTER && data.code != KeyCode.SPACE && data.code != KeyCode.CJK_SPACE
        && data.code != KeyCode.HALF_SPACE && data.code != KeyCode.KESHIDA || data.type == KeyType.NUMERIC
    ) {
        data.asString(isForDisplay = true)
    } else {
        when (data.code) {
            KeyCode.PHONE_PAUSE -> evaluator.context()?.getString(R.string.key__phone_pause)
            KeyCode.PHONE_WAIT -> evaluator.context()?.getString(R.string.key__phone_wait)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                when (evaluator.keyboard.mode) {
                    KeyboardMode.CHARACTERS -> evaluator.subtype.primaryLocale.let { locale ->
                        computeLanguageDisplayName(locale, evaluator.displayLanguageNamesIn()).withoutTrailingRegion()
                    }
                    else -> null
                }
            }
            KeyCode.IME_UI_MODE_TEXT,
            KeyCode.VIEW_CHARACTERS -> {
                evaluator.context()?.getString(R.string.key__view_characters)
            }
            KeyCode.VIEW_NUMERIC,
            KeyCode.VIEW_NUMERIC_ADVANCED -> {
                evaluator.context()?.getString(R.string.key__view_numeric)
            }
            KeyCode.VIEW_PHONE -> {
                evaluator.context()?.getString(R.string.key__view_phone)
            }
            KeyCode.VIEW_PHONE2 -> {
                evaluator.context()?.getString(R.string.key__view_phone2)
            }
            KeyCode.VIEW_SYMBOLS -> {
                evaluator.context()?.getString(R.string.key__view_symbols)
            }
            KeyCode.VIEW_SYMBOLS2 -> {
                evaluator.context()?.getString(R.string.key__view_symbols2)
            }
            KeyCode.HALF_SPACE -> {
                evaluator.context()?.getString(R.string.key__view_half_space)
            }
            KeyCode.KESHIDA -> {
                evaluator.context()?.getString(R.string.key__view_keshida)
            }
            else -> null
        }
    }
}

fun ComputingEvaluator.computeImageVector(data: KeyData): ImageVector? {
    val evaluator = this
    return when (data.code) {
        KeyCode.ARROW_LEFT -> {
            styledIcon(R.drawable.ic_tabler_chevron_left, Icons.AutoMirrored.Filled.KeyboardArrowLeft, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, Icons.AutoMirrored.Sharp.KeyboardArrowLeft)
        }
        KeyCode.ARROW_RIGHT -> {
            styledIcon(R.drawable.ic_tabler_chevron_right, Icons.AutoMirrored.Filled.KeyboardArrowRight, Icons.AutoMirrored.Rounded.KeyboardArrowRight, Icons.AutoMirrored.Sharp.KeyboardArrowRight)
        }
        KeyCode.ARROW_UP -> {
            styledIcon(R.drawable.ic_tabler_chevron_up, Icons.Filled.KeyboardArrowUp, Icons.Rounded.KeyboardArrowUp, Icons.Sharp.KeyboardArrowUp)
        }
        KeyCode.ARROW_DOWN -> {
            styledIcon(R.drawable.ic_tabler_chevron_down, Icons.Filled.KeyboardArrowDown, Icons.Rounded.KeyboardArrowDown, Icons.Sharp.KeyboardArrowDown)
        }
        KeyCode.CLIPBOARD_COPY -> {
            styledIcon(R.drawable.ic_tabler_copy, Icons.Filled.ContentCopy, Icons.Rounded.ContentCopy, Icons.Sharp.ContentCopy)
        }
        KeyCode.CLIPBOARD_CUT -> {
            Icons.Default.ContentCut
        }
        KeyCode.CLIPBOARD_PASTE -> {
            Icons.Default.ContentPasteGo
        }
        KeyCode.CLIPBOARD_SELECT_ALL -> {
            styledIcon(R.drawable.ic_tabler_select_all, Icons.Filled.SelectAll, Icons.Rounded.SelectAll, Icons.Sharp.SelectAll)
        }
        KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
            Icons.Default.DeleteSweep
        }
        KeyCode.COMPACT_LAYOUT_TO_LEFT,
        KeyCode.COMPACT_LAYOUT_TO_RIGHT,
        KeyCode.TOGGLE_COMPACT_LAYOUT -> {
            context()?.vectorResource(id = R.drawable.ic_accessibility_one_handed)
        }
        KeyCode.TOGGLE_FLOATING_WINDOW -> {
            val enabledIcon = context()?.vectorResource(id = R.drawable.ic_floating_keyboard)
            val disabledIcon = context()?.vectorResource(id = R.drawable.ic_floating_keyboard_disable)
            val windowController = FlorisImeService.windowControllerOrNull() ?: return enabledIcon
            when (windowController.activeWindowConfig.value.mode) {
                ImeWindowMode.FIXED -> enabledIcon
                ImeWindowMode.FLOATING -> disabledIcon
            }
        }
        KeyCode.TOGGLE_RESIZE_MODE -> {
            context()?.vectorResource(id = R.drawable.ic_resize)
        }
        KeyCode.VOICE_INPUT -> {
            styledIcon(R.drawable.ic_tabler_microphone, Icons.Filled.Mic, Icons.Rounded.Mic, Icons.Sharp.Mic)
        }
        KeyCode.IME_HIDE_UI -> {
            Icons.Default.KeyboardHide
        }
        KeyCode.DELETE -> {
            styledIcon(R.drawable.ic_tabler_backspace, Icons.AutoMirrored.Filled.Backspace, Icons.AutoMirrored.Rounded.Backspace, Icons.AutoMirrored.Sharp.Backspace)
        }
        KeyCode.ENTER -> {
            val imeOptions = evaluator.editorInfo.imeOptions
            val inputAttributes = evaluator.editorInfo.inputAttributes
            if (imeOptions.flagNoEnterAction || inputAttributes.flagTextMultiLine) {
                styledIcon(R.drawable.ic_tabler_corner_down_left, Icons.AutoMirrored.Filled.KeyboardReturn, Icons.AutoMirrored.Rounded.KeyboardReturn, Icons.AutoMirrored.Sharp.KeyboardReturn)
            } else {
                when (imeOptions.action) {
                    ImeOptions.Action.DONE -> Icons.Default.Done
                    ImeOptions.Action.GO -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.NEXT -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.NONE -> styledIcon(R.drawable.ic_tabler_corner_down_left, Icons.AutoMirrored.Filled.KeyboardReturn, Icons.AutoMirrored.Rounded.KeyboardReturn, Icons.AutoMirrored.Sharp.KeyboardReturn)
                    ImeOptions.Action.PREVIOUS -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.SEARCH -> styledIcon(R.drawable.ic_tabler_search, Icons.Filled.Search, Icons.Rounded.Search, Icons.Sharp.Search)
                    ImeOptions.Action.SEND -> Icons.AutoMirrored.Filled.Send
                    ImeOptions.Action.UNSPECIFIED -> styledIcon(R.drawable.ic_tabler_corner_down_left, Icons.AutoMirrored.Filled.KeyboardReturn, Icons.AutoMirrored.Rounded.KeyboardReturn, Icons.AutoMirrored.Sharp.KeyboardReturn)
                }
            }
        }
        KeyCode.FORWARD_DELETE -> {
            Icons.AutoMirrored.Default.ForwardDelete
        }
        KeyCode.IME_UI_MODE_MEDIA -> {
            styledIcon(R.drawable.ic_tabler_mood_smile, Icons.Filled.EmojiEmotions, Icons.Rounded.EmojiEmotions, Icons.Sharp.EmojiEmotions)
        }
        KeyCode.IME_UI_MODE_CLIPBOARD -> {
            styledIcon(R.drawable.ic_tabler_clipboard, Icons.Filled.Assignment, Icons.Rounded.Assignment, Icons.Sharp.Assignment)
        }
        KeyCode.LANGUAGE_SWITCH -> {
            styledIcon(R.drawable.ic_tabler_world, Icons.Filled.Language, Icons.Rounded.Language, Icons.Sharp.Language)
        }
        KeyCode.SETTINGS -> {
            styledIcon(R.drawable.ic_tabler_settings, Icons.Filled.Settings, Icons.Rounded.Settings, Icons.Sharp.Settings)
        }
        KeyCode.SHIFT -> {
            when (evaluator.state.inputShiftState != InputShiftState.UNSHIFTED) {
                true -> Icons.Default.KeyboardCapslock
                else -> styledIcon(R.drawable.ic_tabler_arrow_big_up, Icons.Filled.ArrowUpward, Icons.Rounded.ArrowUpward, Icons.Sharp.ArrowUpward)
            }
        }
        KeyCode.SPACE, KeyCode.CJK_SPACE -> {
            when (evaluator.keyboard.mode) {
                KeyboardMode.NUMERIC,
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.PHONE,
                KeyboardMode.PHONE2 -> {
                    Icons.Default.SpaceBar
                }
                else -> null
            }
        }
        KeyCode.UNDO -> {
            styledIcon(R.drawable.ic_tabler_arrow_back_up, Icons.AutoMirrored.Filled.Undo, Icons.AutoMirrored.Rounded.Undo, Icons.AutoMirrored.Sharp.Undo)
        }
        KeyCode.REDO -> {
            styledIcon(R.drawable.ic_tabler_arrow_forward_up, Icons.AutoMirrored.Filled.Redo, Icons.AutoMirrored.Rounded.Redo, Icons.AutoMirrored.Sharp.Redo)
        }
        KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
            styledIcon(R.drawable.ic_tabler_dots, Icons.Filled.MoreHoriz, Icons.Rounded.MoreHoriz, Icons.Sharp.MoreHoriz)
        }
        KeyCode.TOGGLE_INCOGNITO_MODE -> {
            if (evaluator.state.isIncognitoMode) {
                this.context()?.vectorResource(id = R.drawable.ic_incognito)
            } else {
                this.context()?.vectorResource(id = R.drawable.ic_incognito_off)
            }
        }
        KeyCode.TOGGLE_AUTOCORRECT -> {
            Icons.Default.FontDownload
        }
        KeyCode.AI_REWRITE -> {
            styledIcon(R.drawable.ic_tabler_sparkles, Icons.Filled.AutoAwesome, Icons.Rounded.AutoAwesome, Icons.Sharp.AutoAwesome)
        }
        KeyCode.KANA_SWITCHER -> {
            if (evaluator.state.isKanaKata) {
                this.context()?.vectorResource(R.drawable.ic_keyboard_kana_switcher_kata)
            } else {
                this.context()?.vectorResource(R.drawable.ic_keyboard_kana_switcher_hira)
            }
        }
        KeyCode.CHAR_WIDTH_SWITCHER -> {
            if (evaluator.state.isCharHalfWidth) {
                this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_full)
            } else {
                this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_half)
            }
        }
        KeyCode.CHAR_WIDTH_FULL -> {
            this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_full)
        }
        KeyCode.CHAR_WIDTH_HALF -> {
            this.context()?.vectorResource(R.drawable.ic_keyboard_char_width_switcher_half)
        }
        KeyCode.DRAG_MARKER -> {
            if (evaluator.state.debugShowDragAndDropHelpers) Icons.Default.Close else null
        }
        KeyCode.NOOP -> {
            Icons.Default.Close
        }
        else -> null
    }
}
