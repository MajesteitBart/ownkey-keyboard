package org.ownkey.offline

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * Decoder profile of a loaded engine. Greedy search is the baseline for ordinary dictation; the beam
 * profile is created only when a request carries vocabulary, because hotwords need modified beam
 * search with the BPE vocabulary. Only one engine is resident at a time.
 */
enum class DecoderProfile { GREEDY, BEAM_HOTWORDS }

/** Only instantiated in the inference process. Never release concurrently with decode. */
internal class OrukeetEngine(directory: File, val profile: DecoderProfile = DecoderProfile.GREEDY) : AutoCloseable {
    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 128),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(directory, "encoder.int8.onnx").path,
                    decoder = File(directory, "decoder.int8.onnx").path,
                    joiner = File(directory, "joiner.int8.onnx").path,
                ),
                tokens = File(directory, "tokens.txt").path,
                modelType = "nemo_transducer", numThreads = 2, provider = "cpu", debug = false,
                modelingUnit = if (profile == DecoderProfile.BEAM_HOTWORDS) "bpe" else "",
                bpeVocab = if (profile == DecoderProfile.BEAM_HOTWORDS) File(directory, "bpe.vocab").path else "",
            ),
            // Windows reference values. Mobile accuracy and cost still need the physical-device probe.
            decodingMethod = if (profile == DecoderProfile.BEAM_HOTWORDS) "modified_beam_search" else "greedy_search",
            maxActivePaths = 4,
            hotwordsScore = 1.5f,
        ),
    )

    fun transcribe(samples: FloatArray, hotwords: String = ""): String = decode(samples, hotwords) { it.text.trim() }

    /** Live dictation: the words with their start times, in seconds from the start of [samples]. */
    fun transcribeWords(samples: FloatArray, hotwords: String = ""): List<TimedWord> =
        decode(samples, hotwords) { LiveWindow.words(it.tokens, it.timestamps, samples.size / 16000.0) }

    private fun <T> decode(samples: FloatArray, hotwords: String, read: (OfflineRecognizerResult) -> T): T {
        require(samples.size <= 16000 * 31)
        val stream = if (hotwords.isNotEmpty() && profile == DecoderProfile.BEAM_HOTWORDS) {
            recognizer.createStream(hotwords)
        } else {
            recognizer.createStream()
        }
        return try {
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            read(recognizer.getResult(stream))
        } finally { stream.release() }
    }

    override fun close() = recognizer.release()
}
