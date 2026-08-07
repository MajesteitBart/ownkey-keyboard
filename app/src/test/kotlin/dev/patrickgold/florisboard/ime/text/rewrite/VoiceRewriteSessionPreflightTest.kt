/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecorder
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionOwner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewriteSessionPreflightTest : FunSpec({
    test("state contract exposes every mutually exclusive session phase") {
        VoiceRewriteSessionPhase.entries shouldContainExactly listOf(
            VoiceRewriteSessionPhase.READY,
            VoiceRewriteSessionPhase.TARGETING,
            VoiceRewriteSessionPhase.DISCLOSURE,
            VoiceRewriteSessionPhase.RECORDING,
            VoiceRewriteSessionPhase.PAUSED,
            VoiceRewriteSessionPhase.TRANSCRIBING,
            VoiceRewriteSessionPhase.REWRITING,
            VoiceRewriteSessionPhase.RESULT,
            VoiceRewriteSessionPhase.WARNING,
            VoiceRewriteSessionPhase.ERROR,
            VoiceRewriteSessionPhase.SUCCESS,
            VoiceRewriteSessionPhase.CANCELLED,
        )
    }

    test("successful preflight shows provider-only disclosure then acquires one shared lease") {
        runTest {
            val events = mutableListOf<String>()
            val recorder = FakePreflightRecorder(events)
            val disclosure = FakeDisclosureStore()
            val fixture = preflightFixture(
                scope = backgroundScope,
                recorder = recorder,
                disclosureStore = disclosure,
                events = events,
            )

            fixture.manager.begin()
            runCurrent()
            val disclosureState = fixture.manager.state.value
            disclosureState.phase shouldBe VoiceRewriteSessionPhase.DISCLOSURE
            disclosureState.disclosure shouldBe VoiceRewriteProviderDisclosure(
                version = 3,
                audioProviderName = "Mistral",
                rewriteProviderName = "OpenAI",
            )
            recorder.startCount shouldBe 0

            fixture.manager.acknowledgeDisclosure()
            fixture.manager.acknowledgeDisclosure()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING
            fixture.manager.state.value.targetScope shouldBe VoiceRewriteTargetScope.SELECTION
            recorder.startCount shouldBe 1
            disclosure.version shouldBe 3
            events shouldContainExactly listOf("target", "permission", "transcription-config", "rewrite-config", "recorder-start")
        }
    }

    test("acknowledged disclosure version proceeds without repeated interruption") {
        runTest {
            val disclosure = FakeDisclosureStore(version = 3)
            val fixture = preflightFixture(backgroundScope, disclosureStore = disclosure)

            fixture.manager.begin()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING
            fixture.recorder.startCount shouldBe 1
        }
    }

    test("incognito and secure policy failures happen before target resolution") {
        runTest {
            listOf(
                preflightSession(isIncognito = true) to VoiceRewritePreflightFailure.INCOGNITO,
                preflightSession(isSecure = true) to VoiceRewritePreflightFailure.SECURE_FIELD,
            ).forEach { (session, expectedFailure) ->
                val events = mutableListOf<String>()
                val fixture = preflightFixture(backgroundScope, session = session, events = events)
                fixture.manager.begin()
                runCurrent()

                fixture.manager.state.value.failure shouldBe expectedFailure
                fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.WARNING
                events shouldContainExactly emptyList()
                fixture.recorder.startCount shouldBe 0
            }
        }
    }

    test("preflight failures preserve the approved side-effect order") {
        runTest {
            val targetFailureEvents = mutableListOf<String>()
            val targetFailure = preflightFixture(
                backgroundScope,
                targetResolution = VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EMPTY_TARGET),
                events = targetFailureEvents,
            )
            targetFailure.manager.begin()
            runCurrent()
            targetFailure.manager.state.value.targetFailure shouldBe VoiceRewriteTargetFailure.EMPTY_TARGET
            targetFailureEvents shouldContainExactly listOf("target")

            val permissionEvents = mutableListOf<String>()
            val permission = preflightFixture(backgroundScope, permissionGranted = false, events = permissionEvents)
            permission.manager.begin()
            runCurrent()
            permission.manager.state.value.failure shouldBe VoiceRewritePreflightFailure.MICROPHONE_PERMISSION
            permissionEvents shouldContainExactly listOf("target", "permission")

            val transcriptionEvents = mutableListOf<String>()
            val transcription = preflightFixture(
                backgroundScope,
                transcriptionConfigured = false,
                events = transcriptionEvents,
            )
            transcription.manager.begin()
            runCurrent()
            transcription.manager.state.value.failure shouldBe
                VoiceRewritePreflightFailure.DICTATION_PROVIDER_NOT_CONFIGURED
            transcriptionEvents shouldContainExactly listOf("target", "permission", "transcription-config")

            val rewriteEvents = mutableListOf<String>()
            val rewrite = preflightFixture(backgroundScope, rewriteConfigured = false, events = rewriteEvents)
            rewrite.manager.begin()
            runCurrent()
            rewrite.manager.state.value.failure shouldBe VoiceRewritePreflightFailure.REWRITE_PROVIDER_NOT_CONFIGURED
            rewriteEvents shouldContainExactly listOf(
                "target",
                "permission",
                "transcription-config",
                "rewrite-config",
            )
        }
    }

    test("busy coordinator blocks before permission configuration or a second recorder") {
        runTest {
            val coordinator = AudioSessionCoordinator()
            val existingRecorder = FakePreflightRecorder()
            coordinator.tryStart(AudioSessionOwner.DICTATION, AudioSessionMode.MOCK, existingRecorder)
            val events = mutableListOf<String>()
            val fixture = preflightFixture(backgroundScope, coordinator = coordinator, events = events)

            fixture.manager.begin()
            runCurrent()
            fixture.manager.state.value.failure shouldBe VoiceRewritePreflightFailure.AUDIO_SESSION_BUSY
            events shouldContainExactly listOf("target")
            fixture.recorder.startCount shouldBe 0
            coordinator.invalidate(AudioSessionInvalidation.OWNER_CANCELLED)
        }
    }

    test("cancellation and lifecycle invalidation discard target and stop stale work") {
        runTest {
            val target = CompletableDeferred<VoiceRewriteTargetResolution>()
            val fixture = preflightFixture(
                backgroundScope,
                targetSource = VoiceRewriteTargetSource { target.await() },
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.cancel()
            target.complete(VoiceRewriteTargetResolution.Resolved(snapshot()))
            runCurrent()
            fixture.manager.state.value shouldBe VoiceRewriteSessionState(
                generationId = 1,
                phase = VoiceRewriteSessionPhase.CANCELLED,
            )
            fixture.recorder.startCount shouldBe 0

            val recordingFixture = preflightFixture(
                backgroundScope,
                disclosureStore = FakeDisclosureStore(version = 3),
            )
            recordingFixture.manager.begin()
            runCurrent()
            recordingFixture.manager.invalidate(AudioSessionInvalidation.FIELD_SWITCH)
            recordingFixture.manager.state.value shouldBe VoiceRewriteSessionState(
                generationId = 1,
                phase = VoiceRewriteSessionPhase.CANCELLED,
            )
            recordingFixture.recorder.cancelCount shouldBe 1
            recordingFixture.coordinator.state.value shouldBe null
        }
    }

    test("each new session receives a unique generation and stale generations cannot start recording") {
        runTest {
            val fixture = preflightFixture(
                backgroundScope,
                disclosureStore = FakeDisclosureStore(version = 3),
            )
            fixture.manager.begin()
            runCurrent()
            fixture.manager.state.value.generationId shouldBe 1L
            fixture.manager.cancel()
            fixture.manager.begin()
            runCurrent()
            fixture.manager.state.value.generationId shouldBe 2L
            fixture.recorder.startCount shouldBe 2
        }
    }
})

private data class PreflightFixture(
    val manager: VoiceRewriteSessionManager,
    val recorder: FakePreflightRecorder,
    val coordinator: AudioSessionCoordinator,
)

private fun preflightFixture(
    scope: kotlinx.coroutines.CoroutineScope,
    session: CloudAiEditorSession = preflightSession(),
    targetResolution: VoiceRewriteTargetResolution = VoiceRewriteTargetResolution.Resolved(snapshot()),
    targetSource: VoiceRewriteTargetSource? = null,
    coordinator: AudioSessionCoordinator = AudioSessionCoordinator(),
    recorder: FakePreflightRecorder = FakePreflightRecorder(),
    permissionGranted: Boolean = true,
    transcriptionConfigured: Boolean = true,
    rewriteConfigured: Boolean = true,
    disclosureStore: FakeDisclosureStore = FakeDisclosureStore(),
    events: MutableList<String> = mutableListOf(),
): PreflightFixture {
    val sessions = MutableStateFlow(session)
    val policy = CloudAiAvailabilityPolicy(scope, sessions)
    val source = targetSource ?: VoiceRewriteTargetSource {
        events += "target"
        targetResolution
    }
    val manager = VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = policy,
        targetSource = source,
        audioSessionCoordinator = coordinator,
        audioRecorderProvider = { recorder },
        audioSessionModeProvider = { AudioSessionMode.CONFIGURED_PROVIDER },
        microphonePermission = VoiceRewriteMicrophonePermission {
            events += "permission"
            permissionGranted
        },
        providerConfiguration = object : VoiceRewriteProviderConfigurationSource {
            override fun transcriptionProvider(): VoiceRewriteProviderConfiguration {
                events += "transcription-config"
                return VoiceRewriteProviderConfiguration(transcriptionConfigured, "Mistral")
            }

            override fun rewriteProvider(): VoiceRewriteProviderConfiguration {
                events += "rewrite-config"
                return VoiceRewriteProviderConfiguration(rewriteConfigured, "OpenAI")
            }
        },
        disclosureStore = disclosureStore,
        disclosureVersion = 3,
    )
    return PreflightFixture(manager, recorder, coordinator)
}

private class FakeDisclosureStore(var version: Int = 0) : VoiceRewriteDisclosureStore {
    override fun acknowledgedVersion(): Int = version

    override fun acknowledge(version: Int) {
        this.version = version
    }
}

private class FakePreflightRecorder(
    private val events: MutableList<String>? = null,
) : AudioRecorder {
    var startCount = 0
        private set
    var cancelCount = 0
        private set

    override fun start(): Boolean {
        events?.add("recorder-start")
        startCount += 1
        return true
    }

    override fun pause(): Boolean = true
    override fun resume(): Boolean = true
    override fun stopAndRead(): Result<AudioRecording> = Result.failure(IllegalStateException("not used"))
    override fun cancel() {
        cancelCount += 1
    }
    override fun currentAmplitude(): Float = 0f
}

private fun snapshot() = VoiceRewriteTargetSnapshot(
    editorSessionId = 7L,
    hostPackage = "test.editor",
    fieldId = 42,
    scope = VoiceRewriteTargetScope.SELECTION,
    range = EditorRange(0, 5),
    sourceText = "hello",
    characterCount = 5,
    integrityHash = "integrity",
)

private fun preflightSession(
    isIncognito: Boolean = false,
    isSecure: Boolean = false,
) = CloudAiEditorSession(
    sessionId = 7L,
    isIncognito = isIncognito,
    isSecureField = isSecure,
)
