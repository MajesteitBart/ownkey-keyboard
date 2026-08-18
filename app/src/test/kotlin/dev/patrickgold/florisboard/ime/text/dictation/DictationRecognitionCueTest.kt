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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DictationRecognitionCueTest : FunSpec({
    test("an active dictation session reports the language actually sent to the provider") {
            DictationRecognitionCues.resolve(
                sessionOwner = AudioSessionOwner.DICTATION,
                sessionPhase = AudioSessionPhase.RECORDING,
                resolvedLanguageHint = "nl",
        ) shouldBe DictationRecognitionCue.Language("nl")
    }

    test("a dictation session with no language hint reports auto detection, never nothing") {
        listOf(null, "", "   ").forEach { hint ->
            DictationRecognitionCues.resolve(
                sessionOwner = AudioSessionOwner.DICTATION,
                sessionPhase = AudioSessionPhase.RECORDING,
                resolvedLanguageHint = hint,
            ) shouldBe DictationRecognitionCue.AutoDetect
        }
    }

    test("voice rewrite shows no recognition-language cue because it sends no hint") {
        DictationRecognitionCues.resolve(
            sessionOwner = AudioSessionOwner.VOICE_REWRITE,
            sessionPhase = AudioSessionPhase.RECORDING,
            resolvedLanguageHint = "nl",
        ).shouldBeNull()
    }

    test("no active session leaves the space bar alone") {
        DictationRecognitionCues.resolve(
            sessionOwner = null,
            sessionPhase = null,
            resolvedLanguageHint = "nl",
        ).shouldBeNull()
    }

    test("processing suppresses the recognition cue after recording stops") {
        DictationRecognitionCues.resolve(
            sessionOwner = AudioSessionOwner.DICTATION,
            sessionPhase = AudioSessionPhase.PROCESSING,
            resolvedLanguageHint = "nl",
        ).shouldBeNull()
    }

    test("the cue is readable text on the space bar for every space bar mode") {
        SpaceBarMode.entries.forEach { mode ->
            DictationRecognitionCues.spaceBarLabel(
                spaceBarMode = mode,
                defaultLabel = "English",
                recognitionCueLabel = "Nederlands",
            ) shouldBe "Nederlands"
        }
    }

    test("without a cue every space bar mode keeps its configured behaviour") {
        DictationRecognitionCues.spaceBarLabel(
            SpaceBarMode.NOTHING,
            defaultLabel = "English",
            recognitionCueLabel = null,
        ).shouldBeNull()
        DictationRecognitionCues.spaceBarLabel(
            SpaceBarMode.CURRENT_LANGUAGE,
            defaultLabel = "English",
            recognitionCueLabel = null,
        ) shouldBe "English"
        DictationRecognitionCues.spaceBarLabel(
            SpaceBarMode.SPACE_BAR_KEY,
            defaultLabel = "English",
            recognitionCueLabel = null,
        ) shouldBe "␣"
    }

    test("the dictation language setting exposes three distinct reachable states") {
        TranscriptionLanguageHints.modeOf("") shouldBe TranscriptionLanguageMode.FOLLOW_KEYBOARD
        TranscriptionLanguageHints.modeOf("   ") shouldBe TranscriptionLanguageMode.FOLLOW_KEYBOARD
        TranscriptionLanguageHints.modeOf("auto") shouldBe TranscriptionLanguageMode.AUTO
        TranscriptionLanguageHints.modeOf("AUTO") shouldBe TranscriptionLanguageMode.AUTO
        TranscriptionLanguageHints.modeOf("nl") shouldBe TranscriptionLanguageMode.EXPLICIT
    }

    test("selecting a mode stores a value that resolves back to the same mode") {
        TranscriptionLanguageMode.entries.forEach { mode ->
            val stored = TranscriptionLanguageHints.storedValueFor(mode, explicitLanguage = "nl")
            TranscriptionLanguageHints.modeOf(stored) shouldBe mode
        }
    }

    test("selecting Specific language leaves blank and Auto sentinels and reveals an explicit value") {
        listOf("", "   ", TranscriptionLanguageHints.AUTO, "AUTO").forEach { current ->
            val stored = TranscriptionLanguageHints.storedValueForSelection(
                mode = TranscriptionLanguageMode.EXPLICIT,
                currentStoredLanguageHint = current,
                activeSubtypeLanguageTag = "nl-NL",
            )

            stored shouldBe "nl-NL"
            TranscriptionLanguageHints.modeOf(stored) shouldBe TranscriptionLanguageMode.EXPLICIT
        }
    }

    test("selecting Specific language preserves an existing explicit value across subtype changes") {
        TranscriptionLanguageHints.storedValueForSelection(
            mode = TranscriptionLanguageMode.EXPLICIT,
            currentStoredLanguageHint = "de-DE",
            activeSubtypeLanguageTag = "nl-NL",
        ) shouldBe "de-DE"
    }

    test("auto is distinct from unset: it sends no hint while unset follows the subtype") {
        TranscriptionLanguageHints.resolve(
            purpose = TranscriptionPurpose.DICTATION,
            storedLanguageHint = TranscriptionLanguageHints.storedValueFor(
                TranscriptionLanguageMode.AUTO,
                explicitLanguage = "nl",
            ),
            activeSubtypeLanguageTag = "en",
        ).shouldBeNull()

        TranscriptionLanguageHints.resolve(
            purpose = TranscriptionPurpose.DICTATION,
            storedLanguageHint = TranscriptionLanguageHints.storedValueFor(
                TranscriptionLanguageMode.FOLLOW_KEYBOARD,
                explicitLanguage = "nl",
            ),
            activeSubtypeLanguageTag = "en",
        ) shouldBe "en"
    }
})
