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

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteManager.RewriteStep
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RewritePanelBodyTest : FunSpec({
    test("the hub renders only while no preset step and no voice session is in progress") {
        RewriteStep.entries.forEach { step ->
            VoiceRewriteSurface.entries.forEach { surface ->
                val isIdle = step == RewriteStep.OPTIONS && surface == VoiceRewriteSurface.HUB
                (rewritePanelBody(step, surface) == RewritePanelBody.HUB) shouldBe isIdle
            }
        }
    }

    test("every voice surface other than the hub replaces the hub regardless of the preset step") {
        val expected = mapOf(
            VoiceRewriteSurface.TARGETING to RewritePanelBody.VOICE_TARGETING,
            VoiceRewriteSurface.DISCLOSURE to RewritePanelBody.VOICE_DISCLOSURE,
            VoiceRewriteSurface.RECORDING to RewritePanelBody.VOICE_CAPTURE,
            VoiceRewriteSurface.PAUSED to RewritePanelBody.VOICE_CAPTURE,
            VoiceRewriteSurface.PROCESSING to RewritePanelBody.VOICE_PROCESSING,
            VoiceRewriteSurface.RESULT to RewritePanelBody.VOICE_RESULT,
            VoiceRewriteSurface.RECOVERY to RewritePanelBody.VOICE_RECOVERY,
            VoiceRewriteSurface.SUCCESS to RewritePanelBody.VOICE_SUCCESS,
        )
        // Every surface must be mapped so a new one cannot fall through to the hub.
        (expected.keys + VoiceRewriteSurface.HUB) shouldBe VoiceRewriteSurface.entries.toSet()

        expected.forEach { (surface, body) ->
            RewriteStep.entries.forEach { step ->
                rewritePanelBody(step, surface) shouldBe body
            }
        }
    }

    test("recording and paused share one body so pausing swaps controls without replacing the surface") {
        rewritePanelBody(RewriteStep.OPTIONS, VoiceRewriteSurface.RECORDING) shouldBe
            rewritePanelBody(RewriteStep.OPTIONS, VoiceRewriteSurface.PAUSED)
    }

    test("the preset flow drives the body only while no voice session is active") {
        rewritePanelBody(RewriteStep.GENERATING, VoiceRewriteSurface.HUB) shouldBe RewritePanelBody.PRESET_GENERATING
        rewritePanelBody(RewriteStep.RESULT, VoiceRewriteSurface.HUB) shouldBe RewritePanelBody.PRESET_RESULT
        rewritePanelBody(RewriteStep.DONE, VoiceRewriteSurface.HUB) shouldBe RewritePanelBody.PRESET_DONE
    }
})
