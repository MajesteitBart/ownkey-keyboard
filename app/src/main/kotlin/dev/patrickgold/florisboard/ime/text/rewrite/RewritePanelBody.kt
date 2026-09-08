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

/**
 * The one body the AI rewrite panel renders at a time.
 *
 * The panel has a stable frame and exactly one body; nothing is layered over a dimmed hub. Voice
 * rewrite takes precedence over the preset flow because it owns the microphone, and recording and
 * paused share a body so pausing swaps controls without replacing the surface.
 */
enum class RewritePanelBody {
    HUB,
    PRESET_GENERATING,
    PRESET_RESULT,
    PRESET_DONE,
    VOICE_TARGETING,
    VOICE_DISCLOSURE,
    VOICE_CAPTURE,
    VOICE_PROCESSING,
    VOICE_RESULT,
    VOICE_RECOVERY,
    VOICE_SUCCESS,
}

fun rewritePanelBody(
    step: LlmRewriteManager.RewriteStep,
    surface: VoiceRewriteSurface,
): RewritePanelBody = when (surface) {
    VoiceRewriteSurface.TARGETING -> RewritePanelBody.VOICE_TARGETING
    VoiceRewriteSurface.DISCLOSURE -> RewritePanelBody.VOICE_DISCLOSURE
    VoiceRewriteSurface.RECORDING,
    VoiceRewriteSurface.PAUSED,
    -> RewritePanelBody.VOICE_CAPTURE
    VoiceRewriteSurface.PROCESSING -> RewritePanelBody.VOICE_PROCESSING
    VoiceRewriteSurface.RESULT -> RewritePanelBody.VOICE_RESULT
    VoiceRewriteSurface.RECOVERY -> RewritePanelBody.VOICE_RECOVERY
    VoiceRewriteSurface.SUCCESS -> RewritePanelBody.VOICE_SUCCESS
    VoiceRewriteSurface.HUB -> when (step) {
        LlmRewriteManager.RewriteStep.OPTIONS -> RewritePanelBody.HUB
        LlmRewriteManager.RewriteStep.GENERATING -> RewritePanelBody.PRESET_GENERATING
        LlmRewriteManager.RewriteStep.RESULT -> RewritePanelBody.PRESET_RESULT
        LlmRewriteManager.RewriteStep.DONE -> RewritePanelBody.PRESET_DONE
    }
}
