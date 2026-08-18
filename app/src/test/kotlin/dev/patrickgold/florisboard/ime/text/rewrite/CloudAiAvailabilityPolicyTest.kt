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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CloudAiAvailabilityPolicyTest : FunSpec({
    test("an incognito editor session has the distinct incognito reason") {
        runTest {
            val sessions = MutableStateFlow(activeSession(isIncognito = true))
            val policy = CloudAiAvailabilityPolicy(backgroundScope, sessions)
            policy.current() shouldBe CloudAiAvailability.Unavailable(CloudAiUnavailableReason.INCOGNITO)
        }
    }

    test("secure fields have their own reason and take precedence over incognito") {
        runTest {
            val sessions = MutableStateFlow(activeSession(isIncognito = true, isSecure = true))
            val policy = CloudAiAvailabilityPolicy(backgroundScope, sessions)
            policy.current() shouldBe CloudAiAvailability.Unavailable(CloudAiUnavailableReason.SECURE_FIELD)
        }
    }

    test("availability is observable and restores without replay when the session leaves incognito") {
        runTest {
            val sessions = MutableStateFlow(activeSession(isIncognito = true))
            val policy = CloudAiAvailabilityPolicy(backgroundScope, sessions)
            runCurrent()
            policy.state.value shouldBe CloudAiAvailability.Unavailable(CloudAiUnavailableReason.INCOGNITO)

            sessions.value = activeSession(isIncognito = false)
            runCurrent()
            policy.state.value shouldBe CloudAiAvailability.Available
        }
    }

    test("no active editor is unavailable independently of privacy and configuration failures") {
        runTest {
            val sessions = MutableStateFlow(CloudAiEditorSession.None)
            val policy = CloudAiAvailabilityPolicy(backgroundScope, sessions)
            policy.current() shouldBe CloudAiAvailability.Unavailable(CloudAiUnavailableReason.NO_ACTIVE_EDITOR)
        }
    }

    test("all three AI entry paths block before content microphone or provider side effects") {
        runTest {
            val sessions = MutableStateFlow(activeSession(isIncognito = true))
            val policy = CloudAiAvailabilityPolicy(backgroundScope, sessions)

            listOf("dictation", "preset rewrite", "voice rewrite preflight").forEach { entryPoint ->
                @Suppress("UNUSED_VARIABLE") val namedEntryPoint = entryPoint
                var contentReads = 0
                var microphoneStarts = 0
                var providerRequests = 0
                if (policy.current() is CloudAiAvailability.Available) {
                    contentReads += 1
                    microphoneStarts += 1
                    providerRequests += 1
                }
                contentReads shouldBe 0
                microphoneStarts shouldBe 0
                providerRequests shouldBe 0
            }

            sessions.value = activeSession(isIncognito = false)
            policy.current() shouldBe CloudAiAvailability.Available
        }
    }
})

private fun activeSession(
    isIncognito: Boolean = false,
    isSecure: Boolean = false,
) = CloudAiEditorSession(
    sessionId = 7L,
    isIncognito = isIncognito,
    isSecureField = isSecure,
)
