package dev.patrickgold.florisboard.ime.text.dictation.offline

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.dictation.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ownkey.offline.*

enum class ModelPhase { CHECKING, IDLE, WAITING_FOR_NETWORK, DOWNLOADING, VERIFYING, ACTIVATING, REMOVING }
data class LocalModelState(
    val phase: ModelPhase = ModelPhase.CHECKING,
    val installed: Set<String> = emptySet(),
    val currentId: String? = null,
    val hasStoredData: Boolean = false,
    val progress: DownloadProgress? = null,
    val transferPhase: ModelPhase? = null,
    val allowMobileData: Boolean = false,
    val error: LocalAsrFailure? = null,
)

class OfflineDictationController(private val app: FlorisApplication) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = Mutex()
    private val downloadLock = Mutex()
    val store = ModelStore(File(app.noBackupFilesDir, "ai-models"))
    val runtime = InferenceConnection(app)
    private val _state = MutableStateFlow(LocalModelState())
    val state: StateFlow<LocalModelState> = _state
    @Volatile private var transfer: Job? = null
    private val initialized = scope.async {
        app.preferenceStoreLoaded.first { it }
        store.reconcile()
        publish()
    }
    // Experimental internal builds can gather phone evidence. Public support requires a validated device policy.
    val compatible: Boolean get() = BuildConfig.ORUKEET_INTERNAL &&
        (Build.SUPPORTED_ABIS.contains("arm64-v8a") || (BuildConfig.DEBUG && Build.SUPPORTED_ABIS.contains("x86_64")))
    val selected: Boolean get() = prefs.voxtral.dictationBackend.get() == TranscriptionBackend.ORUKEET.preference
    val ready: Boolean get() = compatible && store.currentId != null && state.value.phase !in setOf(ModelPhase.ACTIVATING, ModelPhase.REMOVING)

    private fun publish(phase: ModelPhase = ModelPhase.IDLE, error: LocalAsrFailure? = null) {
        _state.update { it.copy(phase = phase, installed = store.installedIds, currentId = store.currentId, hasStoredData = store.hasStoredData, error = error) }
    }
    private fun transferState(phase: ModelPhase?, progress: DownloadProgress? = null, error: LocalAsrFailure? = null) {
        _state.update { it.copy(transferPhase = phase, progress = progress, error = error, installed = store.installedIds, hasStoredData = store.hasStoredData) }
    }
    fun waitingForNetwork(allowMobileData: Boolean) {
        _state.update { it.copy(allowMobileData = allowMobileData) }
        transferState(ModelPhase.WAITING_FOR_NETWORK)
    }
    fun downloadFailed() = transferState(null, error = LocalAsrFailure.DOWNLOAD)
    fun reportError(reason: LocalAsrFailure) = publish(error = reason)

    suspend fun download(
        open: (java.net.URL) -> java.net.HttpURLConnection = { it.openConnection() as java.net.HttpURLConnection },
        onProgress: (DownloadProgress) -> Unit,
    ) {
        initialized.await()
        if (!compatible) throw LocalAsrException(LocalAsrFailure.UNSUPPORTED)
        downloadLock.lock()
        transfer = currentCoroutineContext()[Job]
        try {
            transferState(ModelPhase.DOWNLOADING)
            if (ModelCatalog.current.id !in store.installedIds) {
                ModelDownloader(store, open).download(ModelCatalog.current) {
                    transferState(if (it.verifying) ModelPhase.VERIFYING else ModelPhase.DOWNLOADING, it)
                    onProgress(it)
                }
            }
            transferState(null) // Never selects a route or promotes the runtime here.
        } catch (error: CancellationException) {
            transferState(null); throw error
        } catch (error: Exception) {
            transferState(null, error = (error as? LocalAsrException)?.reason ?: LocalAsrFailure.DOWNLOAD)
            throw error
        } finally { transfer = null; downloadLock.unlock() }
    }

    fun cancelDownload() { transfer?.cancel(); transferState(null) }

    suspend fun activate(id: String = ModelCatalog.current.id) = withContext(Dispatchers.IO) {
        initialized.await()
        lifecycle.withLock {
            if (!compatible) throw LocalAsrException(LocalAsrFailure.UNSUPPORTED)
            if (store.hasLeases) throw LocalAsrException(LocalAsrFailure.BUSY)
            publish(ModelPhase.ACTIVATING)
            try {
                if (!store.verify(id)) throw LocalAsrException(LocalAsrFailure.MODEL_DAMAGED)
                runtime.unload()
                runtime.load(id)
                currentCoroutineContext().ensureActive()
                store.commit(id)
                if (!selected) {
                    prefs.voxtral.previousDictationBackend.set(prefs.voxtral.dictationBackend.get())
                    prefs.voxtral.dictationBackend.set(TranscriptionBackend.ORUKEET.preference)
                }
                store.removeUnusedVersions()
                publish()
            } catch (cancel: CancellationException) {
                withContext(NonCancellable) { runtime.unload() }; publish(); throw cancel
            } catch (error: Exception) {
                runtime.unload()
                publish(error = (error as? LocalAsrException)?.reason ?: LocalAsrFailure.RUNTIME)
                throw error
            }
        }
    }

    suspend fun deactivate() = withContext(Dispatchers.IO) {
        initialized.await()
        lifecycle.withLock {
            invalidateVoice()
            runtime.unload()
            if (selected) prefs.voxtral.dictationBackend.set(prefs.voxtral.previousDictationBackend.get())
            publish()
        }
    }

    suspend fun selectBackend(backend: TranscriptionBackend) = withContext(Dispatchers.IO) {
        require(backend in setOf(TranscriptionBackend.CLOUD, TranscriptionBackend.EXTERNAL_IME))
        initialized.await()
        lifecycle.withLock {
            invalidateVoice()
            runtime.unload()
            prefs.voxtral.dictationBackend.set(backend.preference)
            publish()
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        initialized.await()
        lifecycle.withLock {
            ModelDownloads.cancel(app)
            downloadLock.withLock {
                publish(ModelPhase.REMOVING)
                if (selected || store.hasLeases) invalidateVoice()
                runtime.unload()
                if (selected) prefs.voxtral.dictationBackend.set(prefs.voxtral.previousDictationBackend.get())
                try { store.removeAll(); publish() }
                catch (error: Exception) { publish(error = LocalAsrFailure.BUSY); throw error }
            }
        }
    }

    private suspend fun invalidateVoice() = withContext(Dispatchers.Main.immediate) {
        if (app.voxtralDictationManager.isInitialized()) app.voxtralDictationManager.value.invalidateSession(AudioSessionInvalidation.OWNER_CANCELLED)
        if (app.voiceRewriteSessionManager.isInitialized()) app.voiceRewriteSessionManager.value.cancel()
    }

    /**
     * [vocabulary] is encoded once here and travels unchanged with every request of this session.
     * An empty list keeps the greedy decoder; words switch the inference process to beam search.
     */
    suspend fun session(
        vocabulary: List<String> = emptyList(),
        dictionary: dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionarySnapshot? = null,
    ): TranscriptionSession {
        initialized.await()
        val hotwords = HotwordTransport.encode(vocabulary).hotwords
        return lifecycle.withLock {
            if (!ready) throw LocalAsrException(if (compatible) LocalAsrFailure.MODEL_MISSING else LocalAsrFailure.UNSUPPORTED)
            val model = store.acquire()
            TranscriptionSession(
                backend = TranscriptionBackend.ORUKEET,
                recorder = MediaRecorderAudioRecorder(app, fileBacked = true),
                client = object : TranscriptionClient {
                    override suspend fun transcribe(recording: AudioRecording): Result<String> {
                        return try {
                            val file = recording.file ?: throw LocalAsrException(LocalAsrFailure.AUDIO)
                            if (recording.durationMs > ModelCatalog.RECORDING_CAP_MS + 1000) throw LocalAsrException(LocalAsrFailure.AUDIO)
                            val text = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { audio ->
                                runtime.transcribe(model.id, audio, hotwords)
                            }
                            Result.success(text)
                        } catch (cancel: CancellationException) { throw cancel
                        } catch (error: Exception) { Result.failure(error) }
                    }
                },
                dictionary = dictionary,
                release = model::close,
            )
        }
    }
}

fun Context.offlineDictation(): OfflineDictationController = (applicationContext as FlorisApplication).offlineDictation.value
