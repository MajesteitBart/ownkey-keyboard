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

import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecorder
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.VoiceActionFeedbackController
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor

private class ControllerFixture(
    val controller: VoiceRewriteUiController,
    val sessions: MutableStateFlow<CloudAiEditorSession>,
    val panelVisibility: MutableList<Boolean>,
    val selectionReads: MutableList<String>,
    val providerReads: MutableList<String>,
    val settingsOpened: MutableList<String>,
)

private fun availableSession() = CloudAiEditorSession(
    sessionId = 4L,
    isIncognito = false,
    isSecureField = false,
)

private fun resolvedTarget() = VoiceRewriteTargetResolution.Resolved(
    VoiceRewriteTargetSnapshot(
        editorSessionId = 4L,
        hostPackage = "test.host",
        fieldId = 1,
        scope = VoiceRewriteTargetScope.SELECTION,
        range = EditorRange.normalized(0, 5),
        sourceText = "hello",
        characterCount = 5,
        integrityHash = "hash",
    ),
)

private fun controllerFixture(
    scope: CoroutineScope,
    session: CloudAiEditorSession = availableSession(),
    selectionCharacterCount: Int? = 184,
    transcriptionConfigured: Boolean = true,
    rewriteConfigured: Boolean = true,
    disclosureVersion: Int = 1,
    acknowledgedDisclosureVersion: Int = 1,
    targetResolution: VoiceRewriteTargetResolution =
        VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EMPTY_TARGET),
): ControllerFixture {
    val sessions = MutableStateFlow(session)
    val policy = CloudAiAvailabilityPolicy(scope, sessions)
    val panelVisibility = mutableListOf<Boolean>()
    val selectionReads = mutableListOf<String>()
    val providerReads = mutableListOf<String>()
    val settingsOpened = mutableListOf<String>()
    val manager = VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = policy,
        targetSource = { targetResolution },
        audioSessionCoordinator = AudioSessionCoordinator(),
        feedbackController = VoiceActionFeedbackController(scope),
        audioRecorderProvider = { SilentRecorder() },
        audioSessionModeProvider = { AudioSessionMode.MOCK },
        microphonePermission = { true },
        providerConfiguration = object : VoiceRewriteProviderConfigurationSource {
            override fun transcriptionProvider() = VoiceRewriteProviderConfiguration(true, "Mistral")
            override fun rewriteProvider() = VoiceRewriteProviderConfiguration(true, "OpenAI")
        },
        configurationDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        disclosureStore = object : VoiceRewriteDisclosureStore {
            private var acknowledged = acknowledgedDisclosureVersion
            override fun acknowledgedVersion(): Int = acknowledged
            override fun acknowledge(version: Int) {
                acknowledged = version
            }
        },
        disclosureVersion = disclosureVersion,
    )
    val controller = VoiceRewriteUiController(
        scope = scope,
        sessionManager = manager,
        availabilityPolicy = policy,
        providerConfiguration = object : VoiceRewriteProviderConfigurationSource {
            override fun transcriptionProvider(): VoiceRewriteProviderConfiguration {
                providerReads += "transcription"
                return VoiceRewriteProviderConfiguration(transcriptionConfigured, "Mistral")
            }

            override fun rewriteProvider(): VoiceRewriteProviderConfiguration {
                providerReads += "rewrite"
                return VoiceRewriteProviderConfiguration(rewriteConfigured, "OpenAI")
            }
        },
        selectionCharacterCount = {
            selectionReads += "selection"
            selectionCharacterCount
        },
        replacementGateway = { error("not used") },
        setPanelVisible = { visible -> panelVisibility += visible },
        openAiSettingsRoute = { settingsOpened += "ai" },
        openIncognitoSettingRoute = { settingsOpened += "incognito" },
    )
    return ControllerFixture(controller, sessions, panelVisibility, selectionReads, providerReads, settingsOpened)
}
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewriteUiControllerTest : FunSpec({
    test("the hub card reports the resolved selection and both configured provider names") {
        runTest {
            val fixture = controllerFixture(backgroundScope)
            runCurrent()

            val card = fixture.controller.hubCardState()

            card.isAvailable shouldBe true
            card.selectionCharacterCount shouldBe 184
            card.audioProvider shouldBe VoiceRewriteProviderConfiguration(true, "Mistral")
            card.rewriteProvider shouldBe VoiceRewriteProviderConfiguration(true, "OpenAI")
            card.providersConfigured shouldBe true
        }
    }

    test("no selection produces the whole-field intent copy rather than a character count") {
        runTest {
            val fixture = controllerFixture(backgroundScope, selectionCharacterCount = null)
            runCurrent()

            fixture.controller.hubCardState().selectionCharacterCount.shouldBeNull()
        }
    }

    test("an incognito session blocks the card without reading editor content or provider secrets") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                session = availableSession().copy(isIncognito = true),
            )
            runCurrent()

            val card = fixture.controller.hubCardState()

            card.isAvailable shouldBe false
            card.unavailableReason shouldBe CloudAiUnavailableReason.INCOGNITO
            card.selectionCharacterCount.shouldBeNull()
            fixture.selectionReads.isEmpty() shouldBe true
            fixture.providerReads.isEmpty() shouldBe true
        }
    }

    test("a secure field blocks the card for the same reason path without reading content") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                session = availableSession().copy(isSecureField = true),
            )
            runCurrent()

            fixture.controller.hubCardState().unavailableReason shouldBe CloudAiUnavailableReason.SECURE_FIELD
            fixture.selectionReads.isEmpty() shouldBe true
        }
    }

    test("leaving incognito restores availability with no residual disabled state") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                session = availableSession().copy(isIncognito = true),
            )
            runCurrent()
            fixture.controller.hubCardState().isAvailable shouldBe false

            fixture.sessions.value = availableSession()
            runCurrent()

            fixture.controller.hubCardState().isAvailable shouldBe true
        }
    }

    test("a missing rewrite key leaves the card usable but withholds the provider routing line") {
        runTest {
            val fixture = controllerFixture(backgroundScope, rewriteConfigured = false)
            runCurrent()

            val card = fixture.controller.hubCardState()

            card.isAvailable shouldBe true
            card.providersConfigured shouldBe false
            fixture.controller.providersConfigured() shouldBe false
        }
    }

    test("opening the hub performs no preflight; only an explicit entry starts one") {
        runTest {
            val fixture = controllerFixture(backgroundScope)
            runCurrent()

            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.HUB
            fixture.panelVisibility.isEmpty() shouldBe true

            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()

            fixture.panelVisibility shouldBe listOf(true)
            fixture.controller.uiState.value.origin shouldBe VoiceRewriteEntryOrigin.REWRITE_HUB
        }
    }

    test("cancel returns to the entry origin: the panel closes for the accelerator, stays for the hub") {
        runTest {
            val fromKey = controllerFixture(backgroundScope)
            runCurrent()
            fromKey.controller.begin(VoiceRewriteEntryOrigin.DICTATION_KEY)
            runCurrent()
            fromKey.controller.cancel()
            fromKey.panelVisibility shouldBe listOf(true, false)

            val fromHub = controllerFixture(backgroundScope)
            runCurrent()
            fromHub.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fromHub.controller.cancel()
            fromHub.panelVisibility shouldBe listOf(true)
        }
    }

    test("configuration and incognito recovery use their own separate settings routes") {
        runTest {
            val fixture = controllerFixture(backgroundScope)
            runCurrent()

            fixture.controller.openAiSettings()
            fixture.controller.openIncognitoSetting()

            fixture.settingsOpened shouldBe listOf("ai", "incognito")
        }
    }

    test("an unacknowledged disclosure version interrupts before the microphone opens") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                disclosureVersion = 2,
                acknowledgedDisclosureVersion = 1,
                targetResolution = resolvedTarget(),
            )
            runCurrent()

            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()

            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.DISCLOSURE
            fixture.controller.uiState.value.disclosure?.version shouldBe 2
        }
    }

    test("an acknowledged disclosure version is not shown again on the next use") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                disclosureVersion = 2,
                acknowledgedDisclosureVersion = 2,
                targetResolution = resolvedTarget(),
            )
            runCurrent()

            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()

            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING
        }
    }

    test("finishing after a confirmed replacement always returns to the keyboard") {
        VoiceRewriteEntryOrigin.entries.forEach { origin ->
            runTest {
                val fixture = controllerFixture(backgroundScope, targetResolution = resolvedTarget())
                runCurrent()
                fixture.controller.begin(origin)
                runCurrent()

                fixture.controller.finishAfterReplacement()
                runCurrent()

                fixture.panelVisibility.last() shouldBe false
                fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.HUB
            }
        }
    }

    test("a confirmation-bound finish is ignored once the surface it was started for is gone") {
        runTest {
            val fixture = controllerFixture(backgroundScope, targetResolution = resolvedTarget())
            runCurrent()
            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING

            // A stale success timer from an earlier session must not close or reset this one.
            val staleConfirmationId = fixture.controller.uiState.value.announcementId - 1
            fixture.controller.finishAfterReplacement(staleConfirmationId) shouldBe false
            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING
            fixture.panelVisibility shouldBe listOf(true)

            // Nor may a matching id finish anything but the success confirmation itself.
            val currentId = fixture.controller.uiState.value.announcementId
            fixture.controller.finishAfterReplacement(currentId) shouldBe false
            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING
        }
    }

    test("a lifecycle invalidation cancels an active session but never disturbs an idle hub") {
        runTest {
            val active = controllerFixture(backgroundScope, targetResolution = resolvedTarget())
            runCurrent()
            active.controller.begin(VoiceRewriteEntryOrigin.DICTATION_KEY)
            runCurrent()
            active.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING

            active.controller.invalidate(AudioSessionInvalidation.FIELD_SWITCH)
            runCurrent()
            active.controller.uiState.value.surface shouldBe VoiceRewriteSurface.HUB

            val idle = controllerFixture(backgroundScope, targetResolution = resolvedTarget())
            runCurrent()
            idle.controller.invalidate(AudioSessionInvalidation.KEYBOARD_HIDE)
            runCurrent()
            idle.controller.uiState.value.surface shouldBe VoiceRewriteSurface.HUB
            idle.panelVisibility.isEmpty() shouldBe true
        }
    }

    test("acknowledging stores the current version so the next session skips the disclosure") {
        runTest {
            val fixture = controllerFixture(
                backgroundScope,
                disclosureVersion = 2,
                acknowledgedDisclosureVersion = 0,
                targetResolution = resolvedTarget(),
            )
            runCurrent()

            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fixture.controller.acknowledgeDisclosure()
            runCurrent()
            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING

            fixture.controller.cancel()
            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()

            fixture.controller.uiState.value.surface shouldBe VoiceRewriteSurface.RECORDING
        }
    }
})

private class SilentRecorder : AudioRecorder {
    override fun start(): Boolean = true
    override fun pause(): Boolean = true
    override fun resume(): Boolean = true
    override fun stopAndRead(): Result<AudioRecording> = Result.success(
        AudioRecording(
            bytes = byteArrayOf(),
            sampleRateHz = 16_000,
            channelCount = 1,
            durationMs = 0,
            mimeType = "audio/test",
            fileName = "recording.test",
        ),
    )

    override fun cancel() = Unit
    override fun currentAmplitude(): Float = 0f
}
