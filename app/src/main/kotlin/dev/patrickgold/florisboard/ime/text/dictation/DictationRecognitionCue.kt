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

package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode

/**
 * Recognition-language cue shown on the space bar while ordinary dictation is running.
 *
 * The cue is always readable text, never a highlight or colour alone, and it is shown for every
 * space-bar display mode because it reports what is being recognised rather than what is being
 * typed. Voice rewrite deliberately has no cue: its instruction transcription sends no language
 * hint, so there is no recognition language to report.
 */
sealed interface DictationRecognitionCue {
    data class Language(val languageTag: String) : DictationRecognitionCue
    data object AutoDetect : DictationRecognitionCue
}

object DictationRecognitionCues {
    fun resolve(
        sessionOwner: AudioSessionOwner?,
        resolvedLanguageHint: String?,
    ): DictationRecognitionCue? = when (sessionOwner) {
        null, AudioSessionOwner.VOICE_REWRITE -> null
        AudioSessionOwner.DICTATION -> resolvedLanguageHint
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(DictationRecognitionCue::Language)
            ?: DictationRecognitionCue.AutoDetect
    }

    /**
     * Resolves the space-bar label.
     *
     * An active cue wins over every [SpaceBarMode], including `NOTHING` and the space-bar glyph, so
     * the recognition language is always present as readable text while dictation runs. A `null`
     * result means the space bar renders no label at all.
     */
    fun spaceBarLabel(
        spaceBarMode: SpaceBarMode,
        defaultLabel: String,
        recognitionCueLabel: String?,
    ): String? {
        if (recognitionCueLabel != null) return recognitionCueLabel
        return when (spaceBarMode) {
            SpaceBarMode.NOTHING -> null
            SpaceBarMode.CURRENT_LANGUAGE -> defaultLabel
            SpaceBarMode.SPACE_BAR_KEY -> "␣"
        }
    }
}
