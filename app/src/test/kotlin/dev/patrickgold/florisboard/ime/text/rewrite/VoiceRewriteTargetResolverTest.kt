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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRewriteTargetResolverTest : FunSpec({
    test("existing selection resolves exactly without Select All") {
        runTest {
            val gateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange(6, 11), selectedText = "world"))
            val result = VoiceRewriteTargetResolver(gateway).resolve() as VoiceRewriteTargetResolution.Resolved

            gateway.selectAllRequests shouldBe 0
            result.snapshot.scope shouldBe VoiceRewriteTargetScope.SELECTION
            result.snapshot.range shouldBe EditorRange(6, 11)
            result.snapshot.sourceText shouldBe "world"
            result.snapshot.characterCount shouldBe 5
            result.snapshot.integrityHash shouldMatch Regex("[0-9a-f]{64}")
        }
    }

    test("Select All waits for asynchronous confirmed selected text") {
        runTest {
            val gateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(4)))
            val resolution = async { VoiceRewriteTargetResolver(gateway).resolve() }
            runCurrent()

            gateway.selectAllRequests shouldBe 1
            resolution.isCompleted shouldBe false
            gateway.current.value = frame(
                selection = EditorRange(11, 0),
                selectedText = "hello world",
            )

            val result = resolution.await() as VoiceRewriteTargetResolution.Resolved
            result.snapshot.scope shouldBe VoiceRewriteTargetScope.WHOLE_FIELD
            result.snapshot.range shouldBe EditorRange(0, 11)
            result.snapshot.sourceText shouldBe "hello world"
        }
    }

    test("Select All synchronous confirmation is not missed") {
        runTest {
            val selected = frame(selection = EditorRange(0, 11), selectedText = "hello world")
            val gateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(2))) {
                current.value = selected
                true
            }

            val result = VoiceRewriteTargetResolver(gateway).resolve() as VoiceRewriteTargetResolution.Resolved
            result.snapshot.scope shouldBe VoiceRewriteTargetScope.WHOLE_FIELD
            result.snapshot.sourceText shouldBe "hello world"
        }
    }

    test("Select All unsupported and timed out are distinct") {
        runTest {
            val unsupportedGateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(2))) { false }
            VoiceRewriteTargetResolver(unsupportedGateway).resolve() shouldBe
                VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECT_ALL_UNSUPPORTED)

            val timeoutGateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(2)))
            val timedOut = async { VoiceRewriteTargetResolver(timeoutGateway).resolve() }
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            timedOut.await() shouldBe
                VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECT_ALL_TIMED_OUT)
        }
    }

    test("unsafe and invalid editor states return typed failures without Select All") {
        runTest {
            val cases = listOf(
                frame(sessionId = 0L) to VoiceRewriteTargetFailure.NO_ACTIVE_EDITOR,
                frame(raw = true) to VoiceRewriteTargetFailure.RAW_EDITOR,
                frame(secure = true) to VoiceRewriteTargetFailure.SECURE_FIELD,
                frame(selection = EditorRange.Unspecified) to VoiceRewriteTargetFailure.INVALID_SELECTION,
                frame(selection = EditorRange.cursor(0), knownEmpty = true) to VoiceRewriteTargetFailure.EMPTY_TARGET,
                frame(selection = EditorRange(0, 5), selectedText = "cut") to
                    VoiceRewriteTargetFailure.SELECTED_TEXT_UNAVAILABLE,
            )

            cases.forEach { (editorFrame, expected) ->
                val gateway = FakeVoiceRewriteEditorGateway(editorFrame)
                VoiceRewriteTargetResolver(gateway).resolve() shouldBe VoiceRewriteTargetResolution.Rejected(expected)
                gateway.selectAllRequests shouldBe 0
            }
        }
    }

    test("Unicode character limit counts code points rather than UTF-16 units") {
        runTest {
            val twoEmoji = "😀😀"
            val allowed = FakeVoiceRewriteEditorGateway(
                frame(selection = EditorRange(0, twoEmoji.length), selectedText = twoEmoji),
            )
            val result = VoiceRewriteTargetResolver(allowed, maxCharacters = 2).resolve()
                as VoiceRewriteTargetResolution.Resolved
            result.snapshot.characterCount shouldBe 2

            val rejected = FakeVoiceRewriteEditorGateway(
                frame(selection = EditorRange(0, 3), selectedText = "abc"),
            )
            VoiceRewriteTargetResolver(rejected, maxCharacters = 2).resolve() shouldBe
                VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.TARGET_TOO_LONG)
        }
    }

    test("focus or input-session changes while awaiting Select All are rejected") {
        runTest {
            val gateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(3)))
            val resolution = async { VoiceRewriteTargetResolver(gateway).resolve() }
            runCurrent()
            gateway.current.value = frame(
                sessionId = 8L,
                packageName = "other.editor",
                fieldId = 99,
                selection = EditorRange(0, 11),
                selectedText = "hello world",
            )

            resolution.await() shouldBe
                VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EDITOR_SESSION_CHANGED)
        }
    }

    test("whole-field resolution never reads surrounding or inferred text") {
        runTest {
            val gateway = FakeVoiceRewriteEditorGateway(frame(selection = EditorRange.cursor(5)))
            val resolution = async { VoiceRewriteTargetResolver(gateway).resolve() }
            runCurrent()
            gateway.current.value = frame(selection = EditorRange(0, 11), selectedText = "")

            resolution.await() shouldBe
                VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECTED_TEXT_UNAVAILABLE)
        }
    }
})

private class FakeVoiceRewriteEditorGateway(
    initial: VoiceRewriteEditorFrame,
    private val selectAll: FakeVoiceRewriteEditorGateway.() -> Boolean = { true },
) : VoiceRewriteEditorGateway {
    val current = MutableStateFlow(initial)
    var selectAllRequests = 0
        private set

    override val frames = current

    override fun currentFrame(): VoiceRewriteEditorFrame = current.value

    override fun requestSelectAll(): Boolean {
        selectAllRequests += 1
        return selectAll()
    }
}

private fun frame(
    sessionId: Long = 7L,
    packageName: String = "test.editor",
    fieldId: Int = 42,
    raw: Boolean = false,
    secure: Boolean = false,
    selection: EditorRange = EditorRange.cursor(5),
    selectedText: String = "",
    knownEmpty: Boolean = false,
) = VoiceRewriteEditorFrame(
    editorSessionId = sessionId,
    hostPackage = packageName,
    fieldId = fieldId,
    isRawEditor = raw,
    isSecureField = secure,
    selection = selection,
    selectedText = selectedText,
    isKnownEmpty = knownEmpty,
)
