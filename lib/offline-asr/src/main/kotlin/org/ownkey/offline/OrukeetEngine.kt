package org.ownkey.offline

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/** Only instantiated in the inference process. Never release concurrently with decode. */
internal class OrukeetEngine(directory: File) : AutoCloseable {
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
            ),
        ),
    )

    fun transcribe(samples: FloatArray): String {
        require(samples.size <= 16000 * 31)
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, 16000)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally { stream.release() }
    }

    override fun close() = recognizer.release()
}
