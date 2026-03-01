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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class TypingSpeedMetricsTest : FunSpec({
    beforeTest {
        TypingSpeedMetrics.resetForTests()
        TypingSpeedMetrics.setEnabledForTests(true)
    }

    afterTest {
        TypingSpeedMetrics.setEnabledForTests(null)
    }

    test("captures suggestion and autocorrect aggregates") {
        listOf(10L, 20L, 30L, 40L).forEach { TypingSpeedMetrics.recordSuggestionLatency(it) }
        TypingSpeedMetrics.recordSuggestionAccepted(0)
        TypingSpeedMetrics.recordSuggestionAccepted(2)
        TypingSpeedMetrics.recordSuggestionAccepted(4)
        TypingSpeedMetrics.recordAutoCorrectApplied()
        TypingSpeedMetrics.recordAutoCorrectApplied()
        TypingSpeedMetrics.recordAutoCorrectUndone()

        val snapshot = TypingSpeedMetrics.captureSnapshot()
        snapshot.suggestionLatencyP95Ms shouldBe (30.0 plusOrMinus 0.0001)
        snapshot.top3AcceptanceRate shouldBe (2.0 / 3.0 plusOrMinus 0.0001)
        snapshot.falseAutocorrectRatio shouldBe (0.5 plusOrMinus 0.0001)
    }

    test("computes keystrokes per word from text input boundaries") {
        TypingSpeedMetrics.recordTextInput("h")
        TypingSpeedMetrics.recordTextInput("i")
        TypingSpeedMetrics.recordTextInput(" ")
        TypingSpeedMetrics.recordTextInput("t")
        TypingSpeedMetrics.recordTextInput("h")
        TypingSpeedMetrics.recordTextInput("e")
        TypingSpeedMetrics.recordTextInput("r")
        TypingSpeedMetrics.recordTextInput("e")
        TypingSpeedMetrics.recordWordCommittedBySuggestion()

        val snapshot = TypingSpeedMetrics.captureSnapshot()
        snapshot.textInputKeystrokeCount shouldBe 8
        snapshot.committedWordCount shouldBe 2
        snapshot.keystrokesPerWord shouldBe (4.0 plusOrMinus 0.0001)
    }
})
