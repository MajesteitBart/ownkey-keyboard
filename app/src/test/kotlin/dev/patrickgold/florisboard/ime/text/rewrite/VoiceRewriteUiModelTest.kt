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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun state(
    phase: VoiceRewriteSessionPhase,
    build: VoiceRewriteSessionState.() -> VoiceRewriteSessionState = { this },
) = VoiceRewriteSessionState(generationId = 1L, phase = phase).build()

class VoiceRewriteUiModelTest : FunSpec({
    test("a fresh or cancelled session renders the hub and leaves the preset grid interactive") {
        listOf(VoiceRewriteSessionPhase.READY, VoiceRewriteSessionPhase.CANCELLED).forEach { phase ->
            val model = voiceRewriteUiModel(state(phase), VoiceRewriteEntryOrigin.REWRITE_HUB)

            model.surface shouldBe VoiceRewriteSurface.HUB
            model.isPresetGridInteractive shouldBe true
            model.ownsRecordingRow shouldBe false
            model.statusMessage.shouldBeNull()
        }
    }

    test("target resolution announces the whole-field scope work and keeps cancel available") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.TARGETING),
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.TARGETING
        model.statusMessage shouldBe VoiceRewriteMessage.SELECTING_WHOLE_FIELD
        model.actions shouldContainExactly setOf(VoiceRewriteAction.CANCEL)
        model.isPresetGridInteractive shouldBe false
    }

    test("the entry origin is carried through so cancel returns where the user came from") {
        VoiceRewriteEntryOrigin.entries.forEach { origin ->
            voiceRewriteUiModel(state(VoiceRewriteSessionPhase.RECORDING), origin).origin shouldBe origin
        }
    }

    test("recording shows the speak-an-edit mode status and owns the shared recording row") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.RECORDING) {
                copy(
                    targetScope = VoiceRewriteTargetScope.WHOLE_FIELD,
                    targetCharacterCount = 184,
                )
            },
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.RECORDING
        model.statusMessage shouldBe VoiceRewriteMessage.SPEAK_AN_EDIT
        model.scopeLabel shouldBe VoiceRewriteScopeLabel(VoiceRewriteTargetScope.WHOLE_FIELD, 184)
        model.ownsRecordingRow shouldBe true
        model.actions shouldContainExactly setOf(
            VoiceRewriteAction.PAUSE,
            VoiceRewriteAction.CANCEL,
            VoiceRewriteAction.STOP,
        )
    }

    test("pausing swaps pause for resume and announces the paused state") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.PAUSED),
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.PAUSED
        model.statusMessage shouldBe VoiceRewriteMessage.PAUSED
        model.ownsRecordingRow shouldBe true
        model.actions shouldContainExactly setOf(
            VoiceRewriteAction.RESUME,
            VoiceRewriteAction.CANCEL,
            VoiceRewriteAction.STOP,
        )
    }

    test("first use names both providers and requires an explicit continue before recording") {
        val disclosure = VoiceRewriteProviderDisclosure(
            version = 2,
            audioProviderName = "Mistral",
            rewriteProviderName = "OpenAI",
        )
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.DISCLOSURE) {
                copy(
                    disclosure = disclosure,
                    targetScope = VoiceRewriteTargetScope.SELECTION,
                    targetCharacterCount = 42,
                )
            },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        )

        model.surface shouldBe VoiceRewriteSurface.DISCLOSURE
        model.disclosure shouldBe disclosure
        model.scopeLabel shouldBe VoiceRewriteScopeLabel(VoiceRewriteTargetScope.SELECTION, 42)
        model.actions shouldContainExactly setOf(
            VoiceRewriteAction.CONTINUE,
            VoiceRewriteAction.OPEN_AI_SETTINGS,
            VoiceRewriteAction.BACK,
        )
    }

    test("every preflight failure names its own prerequisite instead of a generic failure") {
        val expected = mapOf(
            VoiceRewritePreflightFailure.NO_ACTIVE_EDITOR to VoiceRewriteMessage.NO_ACTIVE_EDITOR,
            VoiceRewritePreflightFailure.SECURE_FIELD to VoiceRewriteMessage.SECURE_FIELD,
            VoiceRewritePreflightFailure.INCOGNITO to VoiceRewriteMessage.INCOGNITO,
            VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY to VoiceRewriteMessage.DICTATION_BUSY,
            VoiceRewritePreflightFailure.MICROPHONE_PERMISSION to VoiceRewriteMessage.MICROPHONE_PERMISSION,
            VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED to
                VoiceRewriteMessage.DICTATION_PROVIDER_MISSING,
            VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED to
                VoiceRewriteMessage.REWRITE_PROVIDER_MISSING,
            VoiceRewritePreflightFailure.RECORDER_UNAVAILABLE to VoiceRewriteMessage.RECORDER_UNAVAILABLE,
        )
        // Every failure must be mapped, so a new one cannot silently fall back to generic copy.
        (expected.keys + VoiceRewritePreflightFailure.TARGET_REJECTED) shouldBe
            VoiceRewritePreflightFailure.entries.toSet()

        expected.forEach { (failure, message) ->
            val model = voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.WARNING) { copy(failure = failure) },
                VoiceRewriteEntryOrigin.REWRITE_HUB,
            )
            model.surface shouldBe VoiceRewriteSurface.RECOVERY
            model.statusMessage shouldBe message
        }
    }

    test("configuration and permission recovery routes to AI settings; target recovery does not") {
        listOf(
            VoiceRewritePreflightFailure.MICROPHONE_PERMISSION,
            VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED,
            VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED,
        ).forEach { failure ->
            voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.WARNING) { copy(failure = failure) },
                VoiceRewriteEntryOrigin.REWRITE_HUB,
            ).actions shouldContainExactly setOf(
                VoiceRewriteAction.OPEN_AI_SETTINGS,
                VoiceRewriteAction.CLOSE,
            )
        }

        // Incognito is a typing-privacy choice, so its route is the incognito setting.
        voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.WARNING) {
                copy(failure = VoiceRewritePreflightFailure.INCOGNITO)
            },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        ).actions shouldContainExactly setOf(
            VoiceRewriteAction.OPEN_INCOGNITO_SETTING,
            VoiceRewriteAction.CLOSE,
        )

        voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.WARNING) {
                copy(
                    failure = VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY,
                )
            },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        ).actions shouldContainExactly setOf(VoiceRewriteAction.CLOSE)
    }

    test("a rejected target reports the specific scope problem before any microphone use") {
        val expected = mapOf(
            VoiceRewriteTargetFailure.EMPTY_TARGET to VoiceRewriteMessage.NOTHING_TO_REWRITE,
            VoiceRewriteTargetFailure.TARGET_TOO_LONG to VoiceRewriteMessage.TARGET_TOO_LONG,
            VoiceRewriteTargetFailure.SECURE_FIELD to VoiceRewriteMessage.SECURE_FIELD,
            VoiceRewriteTargetFailure.NO_ACTIVE_EDITOR to VoiceRewriteMessage.NO_ACTIVE_EDITOR,
            VoiceRewriteTargetFailure.SELECT_ALL_UNSUPPORTED to VoiceRewriteMessage.SELECT_TEXT_MANUALLY,
            VoiceRewriteTargetFailure.SELECT_ALL_TIMED_OUT to VoiceRewriteMessage.SELECT_TEXT_MANUALLY,
            VoiceRewriteTargetFailure.RAW_EDITOR to VoiceRewriteMessage.SELECT_TEXT_MANUALLY,
        )

        expected.forEach { (targetFailure, message) ->
            voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.WARNING) {
                    copy(
                        failure = VoiceRewritePreflightFailure.TARGET_REJECTED,
                        targetFailure = targetFailure,
                    )
                },
                VoiceRewriteEntryOrigin.DICTATION_KEY,
            ).statusMessage shouldBe message
        }
    }

    test("transcribing shows labelled progress and keeps cancel outside the trailing action") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.TRANSCRIBING) {
                copy(targetScope = VoiceRewriteTargetScope.SELECTION, targetCharacterCount = 12)
            },
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.PROCESSING
        model.statusMessage shouldBe VoiceRewriteMessage.UNDERSTANDING_INSTRUCTION
        model.recognizedInstruction.shouldBeNull()
        model.actions shouldContainExactly setOf(VoiceRewriteAction.CANCEL)
    }

    test("rewriting surfaces the recognized instruction without inserting it into the editor") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.REWRITING) {
                copy(recognizedInstruction = "make this less defensive")
            },
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.PROCESSING
        model.statusMessage shouldBe VoiceRewriteMessage.REWRITING_SELECTED_TEXT
        model.recognizedInstruction shouldBe "make this less defensive"
        model.resultText.shouldBeNull()
        model.actions shouldContainExactly setOf(VoiceRewriteAction.CANCEL)
    }

    test("a reviewable result offers back, try again, record again and replace") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.RESULT) {
                copy(
                    recognizedInstruction = "shorten this",
                    resultText = "Shorter text.",
                    canReplace = true,
                )
            },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        )

        model.surface shouldBe VoiceRewriteSurface.RESULT
        model.resultText shouldBe "Shorter text."
        model.statusMessage.shouldBeNull()
        model.actions shouldContainExactly setOf(
            VoiceRewriteAction.BACK,
            VoiceRewriteAction.TRY_AGAIN,
            VoiceRewriteAction.RECORD_AGAIN,
            VoiceRewriteAction.REPLACE,
        )
    }

    test("a changed target keeps the result but replaces the rail with copy and close") {
        VoiceRewriteTargetVerificationFailure.entries.forEach { failure ->
            val model = voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.RESULT) {
                    copy(
                        recognizedInstruction = "shorten this",
                        resultText = "Shorter text.",
                        canReplace = false,
                        canCopyResult = true,
                        replacementFailure = failure,
                    )
                },
                VoiceRewriteEntryOrigin.REWRITE_HUB,
            )

            model.resultText shouldBe "Shorter text."
            model.statusMessage shouldBe VoiceRewriteMessage.TARGET_CHANGED
            model.actions shouldContainExactly setOf(
                VoiceRewriteAction.COPY_RESULT,
                VoiceRewriteAction.CLOSE,
            )
            (VoiceRewriteAction.REPLACE in model.actions) shouldBe false
        }
    }

    test("a blocked replacement announces itself once instead of reusing the result announcement") {
        val reviewable = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.RESULT) { copy(resultText = "x", canReplace = true) },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        )
        val blocked = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.RESULT) {
                copy(
                    resultText = "x",
                    canCopyResult = true,
                    replacementFailure = VoiceRewriteTargetVerificationFailure.SOURCE_CHANGED,
                )
            },
            VoiceRewriteEntryOrigin.REWRITE_HUB,
        )

        blocked.announcementId shouldBeGreaterThan reviewable.announcementId
    }

    test("no usable speech offers record again and never a rewrite retry") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.ERROR) {
                copy(pipelineFailure = VoiceRewritePipelineFailure.NO_SPEECH)
            },
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.RECOVERY
        model.statusMessage shouldBe VoiceRewriteMessage.NO_SPEECH
        model.actions shouldContainExactly setOf(
            VoiceRewriteAction.RECORD_AGAIN,
            VoiceRewriteAction.CLOSE,
        )
    }

    test("a failed rewrite keeps the recognized instruction available for try again") {
        listOf(
            VoiceRewritePipelineFailure.REWRITE to VoiceRewriteMessage.REWRITE_FAILED,
            VoiceRewritePipelineFailure.EMPTY_RESULT to VoiceRewriteMessage.EMPTY_RESULT,
        ).forEach { (failure, message) ->
            val model = voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.ERROR) {
                    copy(pipelineFailure = failure, recognizedInstruction = "shorten this")
                },
                VoiceRewriteEntryOrigin.DICTATION_KEY,
            )

            model.statusMessage shouldBe message
            model.recognizedInstruction shouldBe "shorten this"
            model.actions shouldContainExactly setOf(
                VoiceRewriteAction.TRY_AGAIN,
                VoiceRewriteAction.RECORD_AGAIN,
                VoiceRewriteAction.CLOSE,
            )
        }
    }

    test("a lost transcript drops try again so no rewrite runs without an instruction") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.ERROR) {
                copy(pipelineFailure = VoiceRewritePipelineFailure.REWRITE)
            },
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        (VoiceRewriteAction.TRY_AGAIN in model.actions) shouldBe false
    }

    test("transcription and recording failures recover by recording again, leaving source text alone") {
        listOf(
            VoiceRewritePipelineFailure.TRANSCRIPTION to VoiceRewriteMessage.TRANSCRIPTION_FAILED,
            VoiceRewritePipelineFailure.RECORDING to VoiceRewriteMessage.RECORDING_FAILED,
        ).forEach { (failure, message) ->
            val model = voiceRewriteUiModel(
                state(VoiceRewriteSessionPhase.ERROR) { copy(pipelineFailure = failure) },
                VoiceRewriteEntryOrigin.DICTATION_KEY,
            )

            model.statusMessage shouldBe message
            model.actions shouldContainExactly setOf(
                VoiceRewriteAction.RECORD_AGAIN,
                VoiceRewriteAction.CLOSE,
            )
        }
    }

    test("a confirmed replacement shows the replacement confirmation and offers no undo surface") {
        val model = voiceRewriteUiModel(
            state(VoiceRewriteSessionPhase.SUCCESS),
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        model.surface shouldBe VoiceRewriteSurface.SUCCESS
        model.statusMessage shouldBe VoiceRewriteMessage.TEXT_REPLACED
        model.actions.isEmpty() shouldBe true
        model.resultText.shouldBeNull()
        model.recognizedInstruction.shouldBeNull()
    }

    test("a new session generation advances the announcement id so a repeat state is announced again") {
        val first = voiceRewriteUiModel(
            VoiceRewriteSessionState(generationId = 1L, phase = VoiceRewriteSessionPhase.RECORDING),
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )
        val second = voiceRewriteUiModel(
            VoiceRewriteSessionState(generationId = 2L, phase = VoiceRewriteSessionPhase.RECORDING),
            VoiceRewriteEntryOrigin.DICTATION_KEY,
        )

        second.announcementId shouldBeGreaterThan first.announcementId
    }
})
