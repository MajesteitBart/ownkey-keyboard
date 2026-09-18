package dev.patrickgold.florisboard.ime.text.dictation

enum class SpeechDictionaryUse { APPLIED, SYSTEM_VOICE_INPUT, NOT_SET_UP }

/** Resolves once per recording. File download/readiness never changes provider selection. */
enum class TranscriptionBackend(val preference: String) {
    CLOUD("cloud"), ORUKEET("orukeet"), EXTERNAL_IME("external"), MOCK("mock"), UNAVAILABLE("unavailable");

    /**
     * Whether dictation through this backend uses the speech dictionary, for the notice on the dictionary
     * screen. System voice input inserts its own text, so Ownkey never sees a transcript to hint, correct
     * or clean. A provider that cannot dictate yet (no API key, no local model, unknown preference) is
     * reported separately, because the fix is to finish the setup, not to switch away from system input.
     */
    fun speechDictionaryUse(hasCloudKey: Boolean, localModelReady: Boolean): SpeechDictionaryUse = when (this) {
        EXTERNAL_IME -> SpeechDictionaryUse.SYSTEM_VOICE_INPUT
        UNAVAILABLE -> SpeechDictionaryUse.NOT_SET_UP
        CLOUD -> if (hasCloudKey) SpeechDictionaryUse.APPLIED else SpeechDictionaryUse.NOT_SET_UP
        ORUKEET -> if (localModelReady) SpeechDictionaryUse.APPLIED else SpeechDictionaryUse.NOT_SET_UP
        MOCK -> SpeechDictionaryUse.APPLIED
    }

    companion object {
        fun resolve(preference: String, hasCloudKey: Boolean, debug: Boolean): TranscriptionBackend = when (preference) {
            ORUKEET.preference -> ORUKEET // Missing models fail closed; never resolve to cloud.
            CLOUD.preference -> CLOUD
            EXTERNAL_IME.preference -> EXTERNAL_IME
            "" -> if (hasCloudKey) CLOUD else if (debug) MOCK else EXTERNAL_IME
            else -> UNAVAILABLE
        }
    }
}

class TranscriptionSession(
    val backend: TranscriptionBackend,
    val recorder: AudioRecorder,
    val client: TranscriptionClient?,
    /** Dictionary snapshot taken at recording start; edits during a recording affect the next one. */
    val dictionary: dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionarySnapshot? = null,
    private val release: () -> Unit = {},
) : AutoCloseable {
    val mode: AudioSessionMode get() = when (backend) {
        TranscriptionBackend.ORUKEET -> AudioSessionMode.LOCAL
        TranscriptionBackend.CLOUD -> AudioSessionMode.CONFIGURED_PROVIDER
        else -> AudioSessionMode.MOCK
    }
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}
