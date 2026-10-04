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

    test("saving a cloud key stores a non-cloud choice for an undecided install, in every build") {
        TranscriptionBackend.choiceToKeepOnKeySave("", hasCloudKey = false) shouldBe TranscriptionBackend.EXTERNAL_IME
        // Without it, a blank preference would resolve to cloud dictation once the key exists.
        TranscriptionBackend.resolve("", hasCloudKey = true, debug = true) shouldBe TranscriptionBackend.CLOUD
        TranscriptionBackend.resolve("", hasCloudKey = true, debug = false) shouldBe TranscriptionBackend.CLOUD
    }

    test("saving a cloud key leaves an explicit choice and an existing key alone") {
        TranscriptionBackend.choiceToKeepOnKeySave("orukeet", hasCloudKey = false) shouldBe null
        TranscriptionBackend.choiceToKeepOnKeySave("external", hasCloudKey = false) shouldBe null
        TranscriptionBackend.choiceToKeepOnKeySave("cloud", hasCloudKey = false) shouldBe null
        TranscriptionBackend.choiceToKeepOnKeySave("", hasCloudKey = true) shouldBe null
    }

    test("a cloud endpoint must use http or https, in any letter case") {
        VoxtralRelayTranscriptionClient.hasHttpScheme("https://api.mistral.ai/v1/audio/transcriptions") shouldBe true
        VoxtralRelayTranscriptionClient.hasHttpScheme("http://localhost:8000/v1/audio/transcriptions") shouldBe true
        VoxtralRelayTranscriptionClient.hasHttpScheme("HTTPS://api.mistral.ai/v1/audio/transcriptions") shouldBe true
        VoxtralRelayTranscriptionClient.hasHttpScheme("api.example.com/v1/audio") shouldBe false
    }
})
