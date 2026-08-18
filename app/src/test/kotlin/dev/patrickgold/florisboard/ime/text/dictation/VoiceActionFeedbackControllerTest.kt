/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceActionFeedbackControllerTest : FunSpec({
    test("success is explicit after processing and resets at nine hundred milliseconds") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(1) shouldBe true
            controller.success(1) shouldBe false
            controller.processing(1) shouldBe true
            controller.success(1) shouldBe true
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.SUCCESS

            advanceTimeBy(899)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.SUCCESS
            advanceTimeBy(1)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE
        }
    }

    test("error is typed announced once retryable immediately and resets after five seconds") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(3)
            controller.error(3, VoiceActionErrorReason.TRANSCRIPTION) shouldBe true
            val announcementId = controller.state.value.announcementId
            controller.state.value.errorReason shouldBe VoiceActionErrorReason.TRANSCRIPTION

            advanceTimeBy(4_999)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.ERROR
            controller.state.value.announcementId shouldBe announcementId
            advanceTimeBy(1)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE
            controller.state.value.announcementId shouldBe announcementId

            controller.begin(4) shouldBe true
            controller.error(4, VoiceActionErrorReason.RECORDING)
            controller.begin(5) shouldBe true
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.RECORDING
            advanceTimeBy(5_000)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.RECORDING
        }
    }

    test("newer outcome cancels an older reset timer") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(8)
            controller.processing(8)
            controller.success(8)
            advanceTimeBy(400)
            controller.error(8, VoiceActionErrorReason.EDITOR_COMMIT)

            advanceTimeBy(500)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.ERROR
            advanceTimeBy(4_500)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE
        }
    }

    test("cancellation and disposal never create success or reset newer state") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(11)
            controller.processing(11)
            controller.cancel(11) shouldBe true
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE

            controller.begin(12)
            controller.error(12, VoiceActionErrorReason.EMPTY_AUDIO)
            controller.dispose()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE
            controller.begin(13) shouldBe false
            advanceTimeBy(5_000)
            runCurrent()
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.IDLE
        }
    }

    test("stale sessions cannot publish terminal outcomes into a newer session") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(20)
            controller.cancel(20)
            controller.begin(21)

            controller.success(20) shouldBe false
            controller.error(20, VoiceActionErrorReason.TRANSCRIPTION) shouldBe false
            controller.state.value.sessionId shouldBe 21L
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.RECORDING
        }
    }

    test("late phase callbacks cannot revive a cancelled or terminal session") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(30)
            controller.cancel(30) shouldBe true
            controller.processing(30) shouldBe false

            controller.begin(31)
            controller.processing(31)
            controller.success(31)
            controller.resume(31) shouldBe false
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.SUCCESS

            controller.begin(32)
            controller.error(32, VoiceActionErrorReason.TRANSCRIPTION)
            controller.begin(32) shouldBe false
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.ERROR
        }
    }

    test("standalone processing supports rewrite retry and active audio feedback wins over warnings") {
        runTest {
            val controller = VoiceActionFeedbackController(this)
            controller.begin(40) shouldBe true
            controller.standaloneError(VoiceActionErrorReason.AUDIO_SESSION_BUSY) shouldBe 0L
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.RECORDING

            controller.processing(40) shouldBe true
            controller.error(40, VoiceActionErrorReason.REWRITE) shouldBe true
            val retrySessionId = controller.beginStandaloneProcessing()
            retrySessionId shouldBe -1L
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.PROCESSING
            controller.success(retrySessionId) shouldBe true
            controller.state.value.phase shouldBe VoiceActionFeedbackPhase.SUCCESS
        }
    }
})
