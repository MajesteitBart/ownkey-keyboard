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

/**
 * Preset palettes for the Ownkey style system. The Signal presets are the brand themes: they use their own
 * shapes, so key borders and radius do not apply, and day mode uses Signal Bone. The Glass presets map to
 * pre-generated night stylesheets ([styleId] is the palette segment of the stylesheet id); their day mode
 * uses the light Glass palette.
 */
enum class ThemeGlassPreset(val styleId: String) {
    SIGNAL("graphite"),
    SIGNAL_BLACK("black"),
    GLASS("night"),
    AMOLED("amoled"),
    SLATE("slate"),
    OCEAN("ocean");

    val isSignal: Boolean
        get() = this == SIGNAL || this == SIGNAL_BLACK
}

/**
 * Icon package for keyboard and toolbar icons: the default solid Heroicons Mini set, thin outlined
 * icons (Tabler) following the Liquid Glass design, or the filled/rounded/sharp Material icon sets.
 */
enum class ThemeIconStyle {
    HEROICONS_MINI,
    THIN_OUTLINE,
    FILLED,
    ROUNDED,
    SHARP;
}
