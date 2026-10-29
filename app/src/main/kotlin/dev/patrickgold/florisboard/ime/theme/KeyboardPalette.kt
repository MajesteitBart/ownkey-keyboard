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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.OwnkeyBrand
import org.florisboard.lib.compose.onAccentFor
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Colours of the active keyboard theme for surfaces that Compose draws itself (the rewrite panel, the
 * dictation row and fix panel, hints, toasts). Reading them from the theme keeps those surfaces legible on
 * every stylesheet, Signal Bone included, instead of assuming light ink on a dark keyboard.
 *
 * @property background The keyboard and toolbar background.
 * @property ink Text and icons on the keyboard background.
 * @property inkSecondary Secondary text and quiet icons (the toolbar icon colour).
 * @property key The letter-key fill, used for cards and buttons inside panels.
 * @property keyPressed The pressed-key fill.
 * @property keyInk Text and icons on [key].
 * @property popup The popup fill, used for floating chips, hints and toasts.
 * @property popupInk Text on [popup].
 * @property pill Fill of a primary pill: the label colour.
 * @property onPill Text on [pill].
 */
@Immutable
data class KeyboardPalette(
    val background: Color,
    val ink: Color,
    val inkSecondary: Color,
    val key: Color,
    val keyPressed: Color,
    val keyInk: Color,
    val popup: Color,
    val popupInk: Color,
    val pill: Color,
    val onPill: Color,
) {
    /** Light keyboards (Signal Bone) get a 2dp shadow on floating surfaces; nothing is lighter than white. */
    val isLight: Boolean get() = background.luminanceApprox() > 0.5f
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
fun rememberKeyboardPalette(): KeyboardPalette {
    val window = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val smartbar = rememberSnyggThemeQuery(FlorisImeUi.Smartbar.elementName)
    val actionKey = rememberSnyggThemeQuery(FlorisImeUi.SmartbarActionKey.elementName)
    val key = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val keyPressed = rememberSnyggThemeQuery(
        FlorisImeUi.Key.elementName,
        selector = org.florisboard.lib.snygg.SnyggSelector.PRESSED,
    )
    val popup = rememberSnyggThemeQuery(FlorisImeUi.KeyPopupBox.elementName)
    return remember(window, smartbar, actionKey, key, keyPressed, popup) {
        val background = window.background(default = OwnkeyBrand.Glass.Canvas)
        val ink = smartbar.foreground(default = window.foreground(default = OwnkeyBrand.Glass.Ink))
        val keyFill = key.background(default = OwnkeyBrand.Glass.Key)
        val keyInk = key.foreground(default = ink)
        val popupFill = popup.background(default = keyFill)
        KeyboardPalette(
            background = background,
            ink = ink,
            inkSecondary = actionKey.foreground(default = ink.copy(alpha = 0.7f)),
            key = keyFill,
            keyPressed = keyPressed.background(default = OwnkeyBrand.Glass.KeyPressed),
            keyInk = keyInk,
            popup = popupFill,
            popupInk = popup.foreground(default = keyInk),
            pill = ink,
            onPill = onAccentFor(ink),
        )
    }
}
