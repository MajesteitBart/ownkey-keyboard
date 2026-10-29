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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colours of the Ownkey Signal keyboard themes, so Appearance can draw a small keyboard without loading the
 * stylesheets. Each field mirrors the `@defines` entry named in [defines]; the stylesheets in
 * `assets/ime/theme/ai.bvdm.ownkey.themes.signal/stylesheets/` are the source of truth.
 */
@Immutable
data class SignalPalette(
    val background: Color,
    val surface: Color,
    val surfaceFn: Color,
    val surfaceVariant: Color,
    val enter: Color,
    val onEnter: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
) {
    /** The stylesheet define each colour mirrors. */
    val defines: Map<String, Color>
        get() = mapOf(
            "--background" to background,
            "--surface" to surface,
            "--surface-fn" to surfaceFn,
            "--surface-variant" to surfaceVariant,
            "--enter" to enter,
            "--on-enter" to onEnter,
            "--on-surface" to onSurface,
            "--on-surface-variant" to onSurfaceVariant,
        )

    companion object {
        val Black = SignalPalette(
            background = Color(0xFF000000),
            surface = Color(0xFF272729),
            surfaceFn = Color(0xFF18181A),
            surfaceVariant = Color(0xFF3A3A3C),
            enter = Color(0xFF3A3A3C),
            onEnter = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF98989D),
        )

        val Graphite = SignalPalette(
            background = Color(0xFF1C1C1E),
            surface = Color(0xFF3A3A3C),
            surfaceFn = Color(0xFF2C2C2E),
            surfaceVariant = Color(0xFF545456),
            enter = Color(0xFF545456),
            onEnter = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFFAEAEB2),
        )

        val Bone = SignalPalette(
            background = Color(0xFFE8E6E1),
            surface = Color(0xFFFFFFFF),
            surfaceFn = Color(0xFFD5D2CB),
            surfaceVariant = Color(0xFFC9C5BC),
            enter = Color(0xFFC9C5BC),
            onEnter = Color(0xFF111111),
            onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF5F5C56),
        )
    }
}
