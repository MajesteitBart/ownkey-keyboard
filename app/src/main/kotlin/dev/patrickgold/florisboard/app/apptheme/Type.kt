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

package dev.patrickgold.florisboard.app.apptheme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R

/** Inter 4.1 (SIL OFL 1.1), bundled for the settings app only. The keyboard keeps the system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val M3Defaults = Typography()

private fun TextStyle.inter(
    size: TextUnit,
    weight: FontWeight,
    tracking: TextUnit,
    lineHeight: TextUnit,
) = copy(
    fontFamily = Inter,
    fontSize = size,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeight = lineHeight,
)

val Typography = Typography(
    displayLarge = M3Defaults.displayLarge.inter(40.sp, FontWeight.Medium, (-0.6).sp, 48.sp),
    displayMedium = M3Defaults.displayMedium.inter(34.sp, FontWeight.Medium, (-0.5).sp, 40.sp),
    displaySmall = M3Defaults.displaySmall.inter(28.sp, FontWeight.Medium, (-0.4).sp, 34.sp),
    headlineLarge = M3Defaults.headlineLarge.inter(30.sp, FontWeight.Medium, (-0.4).sp, 36.sp),
    // Large title, read by LargeTopAppBar when expanded.
    headlineMedium = M3Defaults.headlineMedium.inter(28.sp, FontWeight.Medium, (-0.4).sp, 34.sp),
    // Dialog and sheet titles.
    headlineSmall = M3Defaults.headlineSmall.inter(22.sp, FontWeight.SemiBold, (-0.2).sp, 28.sp),
    // Collapsed bar title.
    titleLarge = M3Defaults.titleLarge.inter(18.sp, FontWeight.SemiBold, (-0.1).sp, 24.sp),
    titleMedium = M3Defaults.titleMedium.inter(17.sp, FontWeight.Medium, 0.sp, 22.sp),
    titleSmall = M3Defaults.titleSmall.inter(15.sp, FontWeight.Medium, 0.sp, 20.sp),
    // Row titles.
    bodyLarge = M3Defaults.bodyLarge.inter(16.sp, FontWeight.Normal, 0.sp, 22.sp),
    // Summaries.
    bodyMedium = M3Defaults.bodyMedium.inter(14.sp, FontWeight.Normal, 0.sp, 20.sp),
    bodySmall = M3Defaults.bodySmall.inter(13.sp, FontWeight.Normal, 0.sp, 18.sp),
    // Buttons.
    labelLarge = M3Defaults.labelLarge.inter(15.sp, FontWeight.SemiBold, 0.sp, 20.sp),
    // Group headers.
    labelMedium = M3Defaults.labelMedium.inter(13.sp, FontWeight.Medium, 0.sp, 18.sp),
    labelSmall = M3Defaults.labelSmall.inter(11.sp, FontWeight.Medium, 0.2.sp, 16.sp),
)
