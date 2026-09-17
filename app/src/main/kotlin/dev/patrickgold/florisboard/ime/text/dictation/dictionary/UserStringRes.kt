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

/**
 * Formats a string resource with values the user typed or dictated.
 *
 * The shared curly formatter re-scans after each replacement, so a value that contains its own
 * placeholder (`{word}`) never terminates. Dictionary entries and transcripts are arbitrary text,
 * so each placeholder is replaced exactly once here instead.
 */
@Composable
internal fun userStringRes(@StringRes id: Int, vararg args: Pair<String, Any>): String {
    var text = stringRes(id)
    for ((name, value) in args) {
        text = text.replace("{$name}", value.toString())
    }
    return text
}
