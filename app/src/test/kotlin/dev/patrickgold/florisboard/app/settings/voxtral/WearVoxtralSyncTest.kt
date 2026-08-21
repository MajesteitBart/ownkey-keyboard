/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings.voxtral

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WearVoxtralSyncTest : FunSpec({
    test("Wear sync omits phone-only language sentinels and preserves explicit hints") {
        listOf("", "   ", "auto", "AUTO").forEach { storedHint ->
            wearConfig(storedHint).normalizedForWear().languageHint shouldBe ""
        }
        wearConfig(" nl-NL ").normalizedForWear().languageHint shouldBe "nl-NL"
    }
})

private fun wearConfig(languageHint: String) = WearVoxtralConfig(
    apiKey = "test-key",
    endpointUrl = "https://example.test/transcribe",
    model = "test-model",
    languageHint = languageHint,
)
