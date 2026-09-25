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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.ui.SnyggBox

val ImeWindowSpec.isFloatingSplit: Boolean
    get() = this is ImeWindowSpec.Floating && floatingMode == ImeWindowMode.Floating.SPLIT

/** Two separate surfaces behind a single key layout; no full-width background or clipped-out hole. */
@Composable
internal fun ImeWindowSurface(
    floatingSplit: Boolean,
    modifier: Modifier,
    inner: Boolean = false,
    attributes: SnyggQueryAttributes = emptyMap(),
    content: @Composable BoxScope.() -> Unit,
) {
    if (!floatingSplit) {
        SnyggBox(
            elementName = if (inner) FlorisImeUi.WindowInner.elementName else FlorisImeUi.Window.elementName,
            attributes = attributes,
            modifier = modifier,
            supportsBackgroundImage = !inner,
            allowClip = false,
            content = content,
        )
        return
    }
    val controller = LocalWindowController.current
    val prefs by FlorisPreferenceStore
    val opacityPercent by prefs.keyboard.floatingSplitOpacity.collectAsState()
    val opacity = opacityPercent.coerceIn(0, 100) / 100f
    val gap by controller.floatingSplitGap.collectAsState()
    val insets by controller.activeWindowInsets.collectAsState()
    val density = LocalDensity.current

    // Each island takes the complete Window style of the current window mode: background, image, border,
    // shape and shadow, exactly as a single floating window would.
    @Composable
    fun Island(islandModifier: Modifier) {
        SnyggBox(
            elementName = FlorisImeUi.Window.elementName,
            attributes = attributes,
            modifier = islandModifier,
            supportsBackgroundImage = true,
        ) {}
    }

    Box(modifier) {
        if (!inner) {
            Box(Modifier.matchParentSize()) {
                val bounds = insets?.boundsPx
                val split = gap
                if (bounds != null && split != null) {
                    if (opacity > 0f) {
                        val leftPx = (split.left - bounds.left).coerceIn(0, bounds.width)
                        val rightPx = (split.right - bounds.left).coerceIn(leftPx, bounds.width)
                        val leftWidth = with(density) { leftPx.toDp() }
                        val rightStart = with(density) { rightPx.toDp() }
                        val rightWidth = with(density) { (bounds.width - rightPx).toDp() }
                        Island(Modifier.fillMaxHeight().width(leftWidth).alpha(opacity))
                        Island(Modifier.absoluteOffset(x = rightStart).fillMaxHeight().width(rightWidth).alpha(opacity))
                    }
                } else {
                    // An explicitly opened action/media panel uses a single temporary surface.
                    Island(Modifier.matchParentSize())
                }
            }
        }
        content()
    }
}
