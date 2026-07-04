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
 * Preset palettes for the Ownkey Glass style system. Each preset maps to a set of pre-generated
 * night stylesheets ([styleId] is the palette segment of the stylesheet id); day mode always uses
 * the light Glass palette.
 */
enum class ThemeGlassPreset(val styleId: String) {
    GLASS("night"),
    AMOLED("amoled"),
    SLATE("slate"),
    OCEAN("ocean");
}

/**
 * Icon style for keyboard and toolbar icons: thin outlined icons (Tabler) following the Liquid
 * Glass design, or the classic filled Material icons.
 */
enum class ThemeIconStyle {
    THIN_OUTLINE,
    FILLED;
}
