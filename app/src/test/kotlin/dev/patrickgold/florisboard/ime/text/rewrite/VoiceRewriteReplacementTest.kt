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
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOnlyOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewriteReplacementTest : FunSpec({
    test("reviewed result is replaced only after explicit intent and emits one content-free success") {
        runTest {
            val snapshot = replacementSnapshot()
            val manager = completedReplacementManager(
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                snapshot = snapshot,
            )
            val gateway = FakeReplacementGateway(frameFor(snapshot))

            manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT
            manager.state.value.canReplace shouldBe true
            gateway.replaceCalls shouldBe 0
            manager.replaceResult(gateway) shouldBe VoiceRewriteReplacementOutcome.Replaced
            gateway.replaceCalls shouldBe 1
            gateway.replacedRange shouldBe snapshot.range
            gateway.replacedText shouldBe "rewritten text"
            manager.state.value shouldBe VoiceRewriteSessionState(
                generationId = 1,
                phase = VoiceRewriteSessionPhase.SUCCESS,
            )
        }
    }

    test("selected and whole-field snapshots use the same exact replacement contract") {
        runTest {
            VoiceRewriteTargetScope.entries.forEach { scopeValue ->
                val snapshot = replacementSnapshot(scope = scopeValue)
                val manager = completedReplacementManager(
                    scope = backgroundScope,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    snapshot = snapshot,
                )
                val gateway = FakeReplacementGateway(frameFor(snapshot))
                manager.replaceResult(gateway) shouldBe VoiceRewriteReplacementOutcome.Replaced
                gateway.replacedRange shouldBe EditorRange(3, 14)
            }
        }
    }

    test("every editor identity range source security and integrity mismatch blocks mutation") {
        runTest {
            val valid = replacementSnapshot()
            val cases = listOf(
                frameFor(valid).copy(editorSessionId = 8L) to
                    VoiceRewriteTargetVerificationFailure.EDITOR_SESSION_CHANGED,
                frameFor(valid).copy(hostPackage = "other.editor") to
                    VoiceRewriteTargetVerificationFailure.HOST_PACKAGE_CHANGED,
                frameFor(valid).copy(fieldId = 99) to VoiceRewriteTargetVerificationFailure.FIELD_CHANGED,
                frameFor(valid).copy(isRawEditor = true) to VoiceRewriteTargetVerificationFailure.RAW_EDITOR,
                frameFor(valid).copy(isSecureField = true) to VoiceRewriteTargetVerificationFailure.SECURE_FIELD,
                frameFor(valid).copy(selection = EditorRange(4, 14)) to
                    VoiceRewriteTargetVerificationFailure.RANGE_CHANGED,
                frameFor(valid).copy(selectedText = "changed txt") to
                    VoiceRewriteTargetVerificationFailure.SOURCE_CHANGED,
            )
            cases.forEach { (frame, expectedFailure) ->
                val manager = completedReplacementManager(
                    scope = backgroundScope,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    snapshot = valid,
                )
                val gateway = FakeReplacementGateway(frame)
                manager.replaceResult(gateway) shouldBe VoiceRewriteReplacementOutcome.CopyFallback(expectedFailure)
                gateway.replaceCalls shouldBe 0
                manager.state.value.canReplace shouldBe false
                manager.state.value.canCopyResult shouldBe true
                manager.state.value.replacementFailure shouldBe expectedFailure
            }

            val corrupt = valid.copy(integrityHash = "corrupt")
            val corruptManager = completedReplacementManager(
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                snapshot = corrupt,
            )
            val corruptGateway = FakeReplacementGateway(frameFor(valid))
            corruptManager.replaceResult(corruptGateway) shouldBe VoiceRewriteReplacementOutcome.CopyFallback(
                VoiceRewriteTargetVerificationFailure.INTEGRITY_CHANGED,
            )
            corruptGateway.replaceCalls shouldBe 0
        }
    }

    test("selection and commit failures leave source unchanged and never emit success") {
        runTest {
            listOf(
                VoiceRewriteEditorReplaceResult.SelectionFailed to
                    VoiceRewriteTargetVerificationFailure.SELECTION_FAILED,
                VoiceRewriteEditorReplaceResult.CommitFailed to
                    VoiceRewriteTargetVerificationFailure.COMMIT_FAILED,
            ).forEach { (editorResult, expectedFailure) ->
                val snapshot = replacementSnapshot()
                val manager = completedReplacementManager(
                    scope = backgroundScope,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    snapshot = snapshot,
                )
                val gateway = FakeReplacementGateway(frameFor(snapshot), editorResult)
                val originalSource = gateway.sourceText

                manager.replaceResult(gateway) shouldBe VoiceRewriteReplacementOutcome.CopyFallback(expectedFailure)
                gateway.sourceText shouldBe originalSource
                manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT
                manager.state.value.replacementFailure shouldBe expectedFailure
            }
        }
    }

    test("copy fallback uses the existing abstraction and session disposal drops the result") {
        runTest {
            val snapshot = replacementSnapshot()
            val manager = completedReplacementManager(
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                snapshot = snapshot,
            )
            val gateway = FakeReplacementGateway(frameFor(snapshot).copy(selectedText = "changed txt"))
            manager.replaceResult(gateway)
            manager.copyResult(gateway) shouldBe true
            gateway.copiedText shouldBe "rewritten text"

            manager.cancel()
            manager.copyResult(gateway) shouldBe false
            manager.state.value.resultText shouldBe null
        }
    }

    test("replacement is unavailable before a non-empty reviewed result") {
        runTest {
            val snapshot = replacementSnapshot()
            val manager = replacementManager(
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                snapshot = snapshot,
            )
            val gateway = FakeReplacementGateway(frameFor(snapshot))
            manager.replaceResult(gateway) shouldBe VoiceRewriteReplacementOutcome.Unavailable
            gateway.replaceCalls shouldBe 0
        }
    }
})

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun kotlinx.coroutines.test.TestScope.completedReplacementManager(
    scope: kotlinx.coroutines.CoroutineScope,
    dispatcher: CoroutineDispatcher,
    snapshot: VoiceRewriteTargetSnapshot,
): VoiceRewriteSessionManager {
    val manager = replacementManager(scope, dispatcher, snapshot)
    manager.begin()
    runCurrent()
    manager.stopRecording()
    runCurrent()
    return manager
}

private fun replacementManager(
    scope: kotlinx.coroutines.CoroutineScope,
    dispatcher: CoroutineDispatcher,
    snapshot: VoiceRewriteTargetSnapshot,
): VoiceRewriteSessionManager {
    val recorder = object : AudioRecorder {
        private var active = false
        override fun start(): Boolean {
            active = true
            return true
        }
        override fun pause(): Boolean = active
        override fun resume(): Boolean = active
        override fun stopAndRead(): Result<AudioRecording> {
            active = false
            return Result.success(AudioRecording(byteArrayOf(1), 16_000, 1, 500, "audio/test", "temp.test"))
        }
        override fun cancel() {
            active = false
        }
        override fun currentAmplitude(): Float = 0f
    }
    val transcription = object : TranscriptionClient {
        override suspend fun transcribe(recording: AudioRecording): Result<String> =
            Result.success("edit instruction")
    }
    return VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = CloudAiAvailabilityPolicy(
            scope,
            MutableStateFlow(CloudAiEditorSession(7L, false, false)),
        ),
        targetSource = VoiceRewriteTargetSource { VoiceRewriteTargetResolution.Resolved(snapshot) },
        audioSessionCoordinator = AudioSessionCoordinator(),
        audioRecorderProvider = { recorder },
        audioSessionModeProvider = { AudioSessionMode.MOCK },
        microphonePermission = VoiceRewriteMicrophonePermission { true },
        providerConfiguration = object : VoiceRewriteProviderConfigurationSource {
            override fun transcriptionProvider() = VoiceRewriteProviderConfiguration(true, "Audio provider")
            override fun rewriteProvider() = VoiceRewriteProviderConfiguration(true, "Rewrite provider")
        },
        configurationDispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher,
        disclosureStore = object : VoiceRewriteDisclosureStore {
            override fun acknowledgedVersion(): Int = 1
            override fun acknowledge(version: Int) = Unit
        },
        disclosureVersion = 1,
        transcriptionOperation = TranscriptionOnlyOperation(dispatcher),
        instructionTranscriptionClientProvider = { transcription },
        rewriteOperation = VoiceRewriteOperation { _, _ -> Result.success("rewritten text") },
    )
}

private class FakeReplacementGateway(
    private var frame: VoiceRewriteEditorFrame,
    private val replaceResult: VoiceRewriteEditorReplaceResult = VoiceRewriteEditorReplaceResult.Replaced,
) : VoiceRewriteReplacementGateway {
    var replaceCalls = 0
    var replacedRange: EditorRange? = null
    var replacedText: String? = null
    var copiedText: String? = null
    var sourceText: String = frame.selectedText

    override fun currentFrame(): VoiceRewriteEditorFrame = frame

    override fun replace(range: EditorRange, text: String): VoiceRewriteEditorReplaceResult {
        replaceCalls += 1
        replacedRange = range
        replacedText = text
        if (replaceResult is VoiceRewriteEditorReplaceResult.Replaced) sourceText = text
        return replaceResult
    }

    override fun copy(text: String): Boolean {
        copiedText = text
        return true
    }
}

private fun replacementSnapshot(
    scope: VoiceRewriteTargetScope = VoiceRewriteTargetScope.SELECTION,
): VoiceRewriteTargetSnapshot {
    val range = EditorRange(3, 14)
    val source = "source text"
    return VoiceRewriteTargetSnapshot(
        editorSessionId = 7L,
        hostPackage = "test.editor",
        fieldId = 42,
        scope = scope,
        range = range,
        sourceText = source,
        characterCount = 11,
        integrityHash = VoiceRewriteTargetIntegrity.calculate(7L, "test.editor", 42, scope, range, source),
    )
}

private fun frameFor(snapshot: VoiceRewriteTargetSnapshot) = VoiceRewriteEditorFrame(
    editorSessionId = snapshot.editorSessionId,
    hostPackage = snapshot.hostPackage,
    fieldId = snapshot.fieldId,
    isRawEditor = false,
    isSecureField = false,
    selection = snapshot.range,
    selectedText = snapshot.sourceText,
    isKnownEmpty = false,
)
