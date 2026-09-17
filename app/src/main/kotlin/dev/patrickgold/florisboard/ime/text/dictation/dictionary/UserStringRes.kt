/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation.dictionary

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import org.florisboard.lib.compose.stringRes

private val placeholder = Regex("\\{([A-Za-z0-9_]+)\\}")

/**
 * Formats a string resource with values the user typed or dictated.
 *
 * The shared curly formatter re-scans after each replacement, so a value that contains its own
 * placeholder (`{word}`) never terminates, and a value containing another argument's placeholder
 * would be rewritten by that argument. Dictionary entries and transcripts are arbitrary text, so
 * the template is scanned exactly once here and inserted values are never scanned at all.
 */
@Composable
internal fun userStringRes(@StringRes id: Int, vararg args: Pair<String, Any>): String =
    formatUserPlaceholders(stringRes(id), args.toMap())

internal fun formatUserPlaceholders(template: String, values: Map<String, Any>): String =
    placeholder.replace(template) { match ->
        values[match.groupValues[1]]?.toString() ?: match.value
    }
