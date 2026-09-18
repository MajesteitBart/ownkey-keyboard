package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TranscriptionBackendTest : FunSpec({
    test("system voice input and an unavailable provider leave the speech dictionary unused") {
        TranscriptionBackend.entries.associateWith { it.usesSpeechDictionary } shouldBe mapOf(
            TranscriptionBackend.CLOUD to true,
            TranscriptionBackend.ORUKEET to true,
            TranscriptionBackend.EXTERNAL_IME to false,
            TranscriptionBackend.MOCK to true,
            TranscriptionBackend.UNAVAILABLE to false,
        )
    }

    test("a release install without a cloud key resolves to system voice input, which shows the notice") {
        val backend = TranscriptionBackend.resolve(preference = "", hasCloudKey = false, debug = false)
        backend shouldBe TranscriptionBackend.EXTERNAL_IME
        backend.usesSpeechDictionary shouldBe false
    }
})
