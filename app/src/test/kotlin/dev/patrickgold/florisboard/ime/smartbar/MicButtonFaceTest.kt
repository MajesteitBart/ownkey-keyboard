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

package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackPhase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MicButtonFaceTest : FunSpec({
    test("each dictation phase has its own face") {
        mapOf(
            VoiceActionFeedbackPhase.IDLE to MicFaceState.IDLE,
            VoiceActionFeedbackPhase.RECORDING to MicFaceState.LISTENING,
            VoiceActionFeedbackPhase.PAUSED to MicFaceState.PAUSED,
            VoiceActionFeedbackPhase.PROCESSING to MicFaceState.TRANSCRIBING,
            VoiceActionFeedbackPhase.SUCCESS to MicFaceState.SUCCESS,
            VoiceActionFeedbackPhase.ERROR to MicFaceState.ERROR,
        ).forEach { (phase, face) ->
            micFaceState(phase, aiUnavailable = false) shouldBe face
        }
    }

    test("an editor without AI always shows the unavailable face") {
        VoiceActionFeedbackPhase.entries.forEach { phase ->
            micFaceState(phase, aiUnavailable = true) shouldBe MicFaceState.UNAVAILABLE
        }
    }
})
