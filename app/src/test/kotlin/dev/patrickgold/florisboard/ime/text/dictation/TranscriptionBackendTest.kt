package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TranscriptionBackendTest : FunSpec({
    test("a provider that is set up uses the speech dictionary") {
        TranscriptionBackend.CLOUD.speechDictionaryUse(hasCloudKey = true, localModelReady = false) shouldBe SpeechDictionaryUse.APPLIED
        TranscriptionBackend.ORUKEET.speechDictionaryUse(hasCloudKey = false, localModelReady = true) shouldBe SpeechDictionaryUse.APPLIED
        TranscriptionBackend.MOCK.speechDictionaryUse(hasCloudKey = false, localModelReady = false) shouldBe SpeechDictionaryUse.APPLIED
    }

    test("system voice input never uses the speech dictionary, whatever else is configured") {
        TranscriptionBackend.EXTERNAL_IME.speechDictionaryUse(hasCloudKey = true, localModelReady = true) shouldBe
            SpeechDictionaryUse.SYSTEM_VOICE_INPUT
    }

    test("a provider that cannot dictate yet is reported as not set up, not as system voice input") {
        TranscriptionBackend.CLOUD.speechDictionaryUse(hasCloudKey = false, localModelReady = true) shouldBe SpeechDictionaryUse.NOT_SET_UP
        TranscriptionBackend.ORUKEET.speechDictionaryUse(hasCloudKey = true, localModelReady = false) shouldBe SpeechDictionaryUse.NOT_SET_UP
        TranscriptionBackend.UNAVAILABLE.speechDictionaryUse(hasCloudKey = true, localModelReady = true) shouldBe SpeechDictionaryUse.NOT_SET_UP
    }

    test("only a cloud provider without a key stops dictation before recording") {
        TranscriptionBackend.CLOUD.lacksApiKey(hasCloudKey = false) shouldBe true
        TranscriptionBackend.CLOUD.lacksApiKey(hasCloudKey = true) shouldBe false
        TranscriptionBackend.entries.filter { it != TranscriptionBackend.CLOUD }.forEach { backend ->
            backend.lacksApiKey(hasCloudKey = false) shouldBe false
        }
    }

    test("a release install without a cloud key resolves to system voice input, which shows the notice") {
        val backend = TranscriptionBackend.resolve(preference = "", hasCloudKey = false, debug = false)
        backend shouldBe TranscriptionBackend.EXTERNAL_IME
        backend.speechDictionaryUse(hasCloudKey = false, localModelReady = false) shouldBe SpeechDictionaryUse.SYSTEM_VOICE_INPUT
    }
})
