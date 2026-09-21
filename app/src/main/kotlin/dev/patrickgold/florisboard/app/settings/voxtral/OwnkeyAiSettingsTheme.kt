/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.voxtral

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.app.OwnkeyBrand

/** The dark-first AI settings palette shared by the AI screen and its sub-pages. */
@Composable
internal fun OwnkeyAiSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OwnkeyBrand.TrustBlue,
            onPrimary = OwnkeyBrand.Bone,
            background = OwnkeyBrand.Key,
            onBackground = OwnkeyBrand.Bone,
            surface = OwnkeyBrand.Panel,
            onSurface = OwnkeyBrand.Bone,
            surfaceVariant = OwnkeyBrand.Action,
            onSurfaceVariant = OwnkeyBrand.Ash,
            outline = OwnkeyBrand.Line,
        ),
        content = content,
    )
}
