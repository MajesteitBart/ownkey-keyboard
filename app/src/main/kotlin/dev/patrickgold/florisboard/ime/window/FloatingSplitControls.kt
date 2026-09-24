/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.MicIdleStyle
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButton
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** Controls stay on the islands, leaving the entire space between them free. */
@Composable
internal fun FloatingSplitControls(actions: Boolean = false) {
    val controller = LocalWindowController.current
    val gap by controller.floatingSplitGap.collectAsState()
    val insets by controller.activeWindowInsets.collectAsState()
    val split = gap ?: return
    val bounds = insets?.boundsPx ?: return
    val density = LocalDensity.current
    val keyboardManager by LocalContext.current.keyboardManager()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()
    val controlBackground = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName).background()
    val foreground = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName).foreground()
    val leftPx = (split.left - bounds.left).coerceIn(0, bounds.width)
    val rightPx = (split.right - bounds.left).coerceIn(leftPx, bounds.width)
    val left = with(density) { leftPx.toDp() }
    val center = with(density) { (rightPx - leftPx).toDp() }

    @Composable
    fun Action(data: TextKeyData) {
        QuickActionButton(
            action = QuickAction.InsertKey(data),
            evaluator = evaluator,
            modifier = Modifier.size(48.dp),
            aspectRatio = 1f,
            // In the strip the mic is one action among peers, so it idles quietly.
            micIdleStyle = MicIdleStyle.QUIET,
        )
    }

    @Composable
    fun MoveHandle(modifier: Modifier) {
        Box(
            modifier.height(48.dp).imeWindowMoveHandle(controller, onTap = { controller.editor.toggleEnabled() }),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(48.dp, 4.dp).background(foreground.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(left).background(controlBackground, RoundedCornerShape(14.dp))) {
            if (actions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Action(TextKeyData.AI_REWRITE)
                    Action(TextKeyData.UNDO)
                    Action(TextKeyData.CLIPBOARD_COPY)
                    Action(TextKeyData.TOGGLE_ACTIONS_OVERFLOW)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MoveHandle(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.width(center))
        Column(Modifier.weight(1f).background(controlBackground, RoundedCornerShape(14.dp))) {
            if (actions) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Action(TextKeyData.CLIPBOARD_PASTE)
                    Action(TextKeyData.IME_UI_MODE_CLIPBOARD)
                    Action(TextKeyData.VOICE_INPUT)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    MoveHandle(Modifier.weight(1f))
                    SnyggIconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = { controller.actions.toggleFloatingWindow() },
                    ) {
                        SnyggIcon(
                            elementName = FlorisImeUi.Window.elementName,
                            modifier = Modifier.size(24.dp),
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_hero_arrow_down_tray),
                            contentDescription = stringRes(R.string.floating_split__dock),
                        )
                    }
                }
            }
        }
    }
}
