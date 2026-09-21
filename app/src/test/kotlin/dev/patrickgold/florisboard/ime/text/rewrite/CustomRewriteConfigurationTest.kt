package dev.patrickgold.florisboard.ime.text.rewrite

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CustomRewriteConfigurationTest : FunSpec({
    test("incomplete custom configuration fails preflight without default routing") {
        for ((endpoint, model) in listOf("" to "", "https://custom.invalid/rewrite" to "", "" to "custom-model")) {
            val client = LlmRewriteClient(
                apiKeyProvider = { "fixture-key" }, endpointUrlProvider = { endpoint }, modelProvider = { model },
                providerIdProvider = { LlmRewriteProviders.Custom }, maxRetryAttempts = 0,
            )
            client.rewriteWithVoiceInstruction("fixture text", "shorten").exceptionOrNull()?.message shouldBe
                "Configure the custom rewrite endpoint and model first."
        }
    }
})
