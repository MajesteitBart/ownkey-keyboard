/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.voice

import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.smartbar.quickaction.VoiceActionGestureArbiter
import dev.patrickgold.florisboard.ime.smartbar.quickaction.VoiceActionGestureOutcome
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecorder
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionCoordinator
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionInvalidation
import dev.patrickgold.florisboard.ime.text.dictation.AudioSessionMode
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionOnlyOperation
import dev.patrickgold.florisboard.ime.text.rewrite.CloudAiAvailabilityPolicy
import dev.patrickgold.florisboard.ime.text.rewrite.CloudAiEditorSession
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteDisclosureStore
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEditorFrame
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEditorGateway
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEditorReplaceResult
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteEntryOrigin
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteMicrophonePermission
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteOperation
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewritePipelineFailure
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteProviderConfiguration
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteProviderConfigurationSource
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteReplacementGateway
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteReplacementOutcome
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteSessionManager
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteSessionPhase
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteTargetResolver
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteTargetVerificationFailure
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteUiController
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * Cross-flow coverage intentionally exercises the same pure seams used by Compose and the IME.
 * It does not start Android UI or make network calls: the editor, recorder, providers, disclosure
 * store, routes, and clock are deterministic fakes, while target resolution, session ownership,
 * transcription-only handling, rewrite orchestration, review, and replacement are production code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewriteCrossFlowTest : FunSpec({
    test("long press runs target disclosure recording providers review replacement success and idle") {
        runTest {
            val fixture = VoiceRewriteCrossFlowFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                disclosureAcknowledged = false,
            )
            val gesture = VoiceActionGestureArbiter(longPressTimeoutMs = 500)

            gesture.down(atMs = 0) shouldBe true
            val outcome = gesture.advanceTo(atMs = 500)
            outcome shouldBe VoiceActionGestureOutcome.VOICE_REWRITE
            if (outcome == VoiceActionGestureOutcome.VOICE_REWRITE) {
                fixture.controller.begin(VoiceRewriteEntryOrigin.DICTATION_KEY)
            }
            gesture.up(atMs = 900) shouldBe null
            runCurrent()

            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.DISCLOSURE
            fixture.panelVisible shouldBe true
            fixture.controller.acknowledgeDisclosure()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING

            fixture.controller.pauseRecording() shouldBe true
            fixture.controller.resumeRecording() shouldBe true
            fixture.controller.stopRecording()
            runCurrent()

            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT
            fixture.manager.state.value.recognizedInstruction shouldBe "make it concise"
            fixture.manager.state.value.resultText shouldBe "Concise result"
            fixture.editor.hostText shouldBe "source text"
            fixture.transcription.requests shouldBe 1
            fixture.rewrite.requests shouldContainExactly listOf("source text" to "make it concise")
            fixture.recorder.hasTemporaryData shouldBe false

            fixture.controller.replaceResult() shouldBe VoiceRewriteReplacementOutcome.Replaced
            fixture.editor.hostText shouldBe "Concise result"
            fixture.editor.committedTexts shouldContainExactly listOf("Concise result")
            fixture.controller.finishAfterReplacement()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.READY
            fixture.panelVisible shouldBe false
        }
    }

    test("asynchronous Select All must be visibly confirmed before microphone or provider work") {
        runTest {
            val editor = CrossFlowEditor(
                hostText = "whole field",
                initialSelection = EditorRange.cursor(0),
                selectAllMode = SelectAllMode.ASYNC,
            )
            val fixture = VoiceRewriteCrossFlowFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                editor = editor,
            )

            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.TARGETING
            editor.selectAllRequests shouldBe 1
            fixture.recorder.startCount shouldBe 0
            fixture.transcription.requests shouldBe 0
            fixture.rewrite.requests shouldContainExactly emptyList()

            editor.confirmSelectAll()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RECORDING
            fixture.manager.state.value.targetCharacterCount shouldBe 11
            fixture.recorder.startCount shouldBe 1
        }
    }

    test("selection mutation after review blocks replacement and offers only content-safe copy") {
        runTest {
            val fixture = VoiceRewriteCrossFlowFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fixture.controller.stopRecording()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.RESULT

            fixture.editor.mutateSelection("changed txt")
            fixture.controller.replaceResult() shouldBe VoiceRewriteReplacementOutcome.CopyFallback(
                VoiceRewriteTargetVerificationFailure.SOURCE_CHANGED,
            )
            fixture.editor.hostText shouldBe "changed txt"
            fixture.editor.committedTexts shouldContainExactly emptyList()
            fixture.manager.state.value.canReplace shouldBe false
            fixture.manager.state.value.canCopyResult shouldBe true
            fixture.controller.copyResult() shouldBe true
            fixture.editor.copiedTexts shouldContainExactly listOf("Concise result")
        }
    }

    test("field invalidation cancels delayed transcription and stale completion cannot mutate text") {
        runTest {
            val delayedTranscript = CompletableDeferred<Result<String>>()
            val fixture = VoiceRewriteCrossFlowFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                transcriptionHandler = {
                    withContext(NonCancellable) { delayedTranscript.await() }
                },
            )
            fixture.controller.begin(VoiceRewriteEntryOrigin.DICTATION_KEY)
            runCurrent()
            fixture.controller.stopRecording()
            runCurrent()
            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.TRANSCRIBING

            fixture.controller.invalidate(AudioSessionInvalidation.FIELD_SWITCH)
            fixture.editor.changeField(fieldId = 99, text = "new field")
            delayedTranscript.complete(Result.success("late instruction"))
            runCurrent()

            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.CANCELLED
            fixture.editor.hostText shouldBe "new field"
            fixture.editor.committedTexts shouldContainExactly emptyList()
            fixture.rewrite.requests shouldContainExactly emptyList()
            fixture.recorder.hasTemporaryData shouldBe false
        }
    }

    test("provider failure leaves host text untouched and exposes retry without another recording") {
        runTest {
            val fixture = VoiceRewriteCrossFlowFixture(
                scope = backgroundScope,
                nowMs = { testScheduler.currentTime },
                dispatcher = StandardTestDispatcher(testScheduler),
                rewriteResponses = ArrayDeque(
                    listOf(
                        Result.failure(IllegalStateException("synthetic provider failure")),
                        Result.success("Recovered result"),
                    ),
                ),
            )
            fixture.controller.begin(VoiceRewriteEntryOrigin.REWRITE_HUB)
            runCurrent()
            fixture.controller.stopRecording()
            runCurrent()

            fixture.manager.state.value.phase shouldBe VoiceRewriteSessionPhase.ERROR
            fixture.manager.state.value.pipelineFailure shouldBe VoiceRewritePipelineFailure.REWRITE
            fixture.editor.hostText shouldBe "source text"
            fixture.recorder.startCount shouldBe 1
            fixture.transcription.requests shouldBe 1

            fixture.controller.tryAgain()
            runCurrent()
            fixture.manager.state.value.resultText shouldBe "Recovered result"
            fixture.recorder.startCount shouldBe 1
            fixture.transcription.requests shouldBe 1
            fixture.rewrite.requests.size shouldBe 2
        }
    }
})

private enum class SelectAllMode {
    SYNC,
    ASYNC,
    UNSUPPORTED,
}

/** Reusable fake for target resolution, editor drift, verified replacement, and copy fallback. */
private class CrossFlowEditor(
    hostText: String = "source text",
    initialSelection: EditorRange = EditorRange(0, hostText.length),
    private val selectAllMode: SelectAllMode = SelectAllMode.SYNC,
    editorSessionId: Long = 7L,
    hostPackage: String = "synthetic.editor",
    fieldId: Int = 42,
    isRawEditor: Boolean = false,
    isSecureField: Boolean = false,
) : VoiceRewriteEditorGateway, VoiceRewriteReplacementGateway {
    var hostText = hostText
        private set
    var selectAllRequests = 0
        private set
    val committedTexts = mutableListOf<String>()
    val copiedTexts = mutableListOf<String>()

    private val frameState = MutableStateFlow(
        frame(
            editorSessionId = editorSessionId,
            hostPackage = hostPackage,
            fieldId = fieldId,
            isRawEditor = isRawEditor,
            isSecureField = isSecureField,
            selection = initialSelection,
        ),
    )

    override val frames = frameState

    override fun currentFrame(): VoiceRewriteEditorFrame = frameState.value

    override fun requestSelectAll(): Boolean {
        selectAllRequests += 1
        return when (selectAllMode) {
            SelectAllMode.UNSUPPORTED -> false
            SelectAllMode.ASYNC -> true
            SelectAllMode.SYNC -> {
                confirmSelectAll()
                true
            }
        }
    }

    fun confirmSelectAll() {
        frameState.value = frameState.value.copy(
            selection = EditorRange(0, hostText.length),
            selectedText = hostText,
            isKnownEmpty = hostText.isEmpty(),
        )
    }

    fun mutateSelection(text: String) {
        hostText = text
        frameState.value = frameState.value.copy(
            selection = EditorRange(0, text.length),
            selectedText = text,
            isKnownEmpty = text.isEmpty(),
        )
    }

    fun changeField(fieldId: Int, text: String) {
        hostText = text
        frameState.value = frameState.value.copy(
            fieldId = fieldId,
            selection = EditorRange(0, text.length),
            selectedText = text,
            isKnownEmpty = text.isEmpty(),
        )
    }

    override fun replace(range: EditorRange, text: String): VoiceRewriteEditorReplaceResult {
        if (frameState.value.selection != range) return VoiceRewriteEditorReplaceResult.SelectionFailed
        hostText = hostText.replaceRange(range.start, range.end, text)
        committedTexts += text
        frameState.value = frameState.value.copy(
            selection = EditorRange.cursor(range.start + text.length),
            selectedText = "",
            isKnownEmpty = hostText.isEmpty(),
        )
        return VoiceRewriteEditorReplaceResult.Replaced
    }

    override fun copy(text: String): Boolean {
        copiedTexts += text
        return true
    }

    private fun frame(
        editorSessionId: Long,
        hostPackage: String,
        fieldId: Int,
        isRawEditor: Boolean,
        isSecureField: Boolean,
        selection: EditorRange,
    ) = VoiceRewriteEditorFrame(
        editorSessionId = editorSessionId,
        hostPackage = hostPackage,
        fieldId = fieldId,
        isRawEditor = isRawEditor,
        isSecureField = isSecureField,
        selection = selection,
        selectedText = if (selection.isSelectionMode) {
            hostText.substring(selection.start, selection.end)
        } else {
            ""
        },
        isKnownEmpty = hostText.isEmpty(),
    )
}

/** Recorder fixture tracks both single-session calls and synthetic temporary-data cleanup. */
private class CrossFlowRecorder : AudioRecorder {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var cancelCount = 0
        private set
    var hasTemporaryData = false
        private set
    var currentLevel = 0f
    private var recording = false

    override fun start(): Boolean {
        if (recording) return false
        recording = true
        hasTemporaryData = true
        startCount += 1
        return true
    }

    override fun pause(): Boolean = recording

    override fun resume(): Boolean = recording

    override fun stopAndRead(): Result<AudioRecording> {
        if (!recording) return Result.failure(IllegalStateException("not recording"))
        recording = false
        hasTemporaryData = false
        stopCount += 1
        return Result.success(
            AudioRecording(
                bytes = byteArrayOf(1, 2, 3),
                sampleRateHz = 16_000,
                channelCount = 1,
                durationMs = 800,
                mimeType = "audio/test",
                fileName = "synthetic-recording.test",
            ),
        )
    }

    override fun cancel() {
        if (recording || hasTemporaryData) cancelCount += 1
        recording = false
        hasTemporaryData = false
    }

    override fun currentAmplitude(): Float = currentLevel
}

private class CrossFlowTranscriptionClient(
    private val responses: ArrayDeque<Result<String>>,
    private val handler: (suspend (AudioRecording) -> Result<String>)? = null,
) : TranscriptionClient {
    var requests = 0
        private set

    override suspend fun transcribe(recording: AudioRecording): Result<String> {
        requests += 1
        return handler?.invoke(recording) ?: responses.removeFirst()
    }
}

private class CrossFlowRewriteOperation(
    private val responses: ArrayDeque<Result<String>>,
    private val handler: (suspend (String, String) -> Result<String>)? = null,
) : VoiceRewriteOperation {
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
        requests += sourceText to instruction
        return handler?.invoke(sourceText, instruction) ?: responses.removeFirst()
    }
}

private class CrossFlowDisclosureStore(acknowledgedVersion: Int) : VoiceRewriteDisclosureStore {
    var acknowledgedVersion = acknowledgedVersion
        private set

    override fun acknowledgedVersion(): Int = acknowledgedVersion

    override fun acknowledge(version: Int) {
        acknowledgedVersion = version
    }
}

private class VoiceRewriteCrossFlowFixture(
    scope: CoroutineScope,
    nowMs: () -> Long,
    dispatcher: CoroutineDispatcher,
    val editor: CrossFlowEditor = CrossFlowEditor(),
    disclosureAcknowledged: Boolean = true,
    transcriptionResponses: ArrayDeque<Result<String>> = ArrayDeque(
        listOf(Result.success("make it concise")),
    ),
    rewriteResponses: ArrayDeque<Result<String>> = ArrayDeque(
        listOf(Result.success("Concise result")),
    ),
    transcriptionHandler: (suspend (AudioRecording) -> Result<String>)? = null,
    rewriteHandler: (suspend (String, String) -> Result<String>)? = null,
) {
    private val editorSession = MutableStateFlow(
        CloudAiEditorSession(
            sessionId = editor.currentFrame().editorSessionId,
            isIncognito = false,
            isSecureField = false,
        ),
    )
    private val availabilityPolicy = CloudAiAvailabilityPolicy(scope, editorSession)
    val recorder = CrossFlowRecorder()
    val transcription = CrossFlowTranscriptionClient(transcriptionResponses, transcriptionHandler)
    val rewrite = CrossFlowRewriteOperation(rewriteResponses, rewriteHandler)
    private val providers = object : VoiceRewriteProviderConfigurationSource {
        override fun transcriptionProvider() = VoiceRewriteProviderConfiguration(true, "Synthetic ASR")
        override fun rewriteProvider() = VoiceRewriteProviderConfiguration(true, "Synthetic rewrite")
    }
    private val disclosureStore = CrossFlowDisclosureStore(if (disclosureAcknowledged) 1 else 0)
    val manager = VoiceRewriteSessionManager(
        scope = scope,
        availabilityPolicy = availabilityPolicy,
        targetSource = VoiceRewriteTargetResolver(editor),
        audioSessionCoordinator = AudioSessionCoordinator(nowMs),
        audioRecorderProvider = { recorder },
        audioSessionModeProvider = { AudioSessionMode.CONFIGURED_PROVIDER },
        microphonePermission = VoiceRewriteMicrophonePermission { true },
        providerConfiguration = providers,
        configurationDispatcher = dispatcher,
        disclosureStore = disclosureStore,
        disclosureVersion = 1,
        transcriptionOperation = TranscriptionOnlyOperation(dispatcher),
        instructionTranscriptionClientProvider = { transcription },
        rewriteOperation = rewrite,
        nowMs = nowMs,
    )
    var panelVisible = false
        private set
    val controller = VoiceRewriteUiController(
        scope = scope,
        sessionManager = manager,
        availabilityPolicy = availabilityPolicy,
        providerConfiguration = providers,
        selectionCharacterCount = { editor.currentFrame().selectedText.length.takeIf { it > 0 } },
        replacementGateway = { editor },
        setPanelVisible = { panelVisible = it },
        openAiSettingsRoute = {},
        openIncognitoSettingRoute = {},
    )
}
