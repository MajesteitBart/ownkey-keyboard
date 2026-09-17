package dev.patrickgold.florisboard.ime.text.dictation

/** Resolves once per recording. File download/readiness never changes provider selection. */
enum class TranscriptionBackend(val preference: String) {
    CLOUD("cloud"), ORUKEET("orukeet"), EXTERNAL_IME("external"), MOCK("mock"), UNAVAILABLE("unavailable");

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
