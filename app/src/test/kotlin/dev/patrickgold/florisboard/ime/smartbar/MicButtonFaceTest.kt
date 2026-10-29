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

import dev.patrickgold.florisboard.R
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

    test("TalkBack hears what the mic does next: start, stop, or that it is processing") {
        micActionLabelRes(MicFaceState.IDLE) shouldBe R.string.voice_rewrite__action_start_dictation
        micActionLabelRes(MicFaceState.LISTENING) shouldBe R.string.ime__mic__stop_dictation
        micActionLabelRes(MicFaceState.PAUSED) shouldBe R.string.ime__mic__stop_dictation
        micActionLabelRes(MicFaceState.TRANSCRIBING) shouldBe R.string.voice_recording__processing
        micActionLabelRes(MicFaceState.SUCCESS) shouldBe R.string.voice_rewrite__action_start_dictation
        micActionLabelRes(MicFaceState.ERROR) shouldBe R.string.voice_rewrite__action_start_dictation
    }

    test("the mic states Recording while it listens and Paused while paused") {
        micStateLabelRes(MicFaceState.LISTENING) shouldBe R.string.ime__mic__recording
        micStateLabelRes(MicFaceState.PAUSED) shouldBe R.string.ime__mic__paused
        listOf(MicFaceState.IDLE, MicFaceState.TRANSCRIBING, MicFaceState.SUCCESS, MicFaceState.ERROR, MicFaceState.UNAVAILABLE)
            .forEach { micStateLabelRes(it) shouldBe null }
    }
})
