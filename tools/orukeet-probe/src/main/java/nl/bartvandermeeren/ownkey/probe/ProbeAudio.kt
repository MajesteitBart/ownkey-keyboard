package nl.bartvandermeeren.ownkey.probe

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded public-fixture conversion for the experiment; no microphone or user recordings. */
object ProbeAudio {
    fun readWav(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE")
        var cursor = 12
        var pcm: ByteArray? = null
        var formatOk = false
        while (cursor + 8 <= bytes.size) {
            val tag = String(bytes, cursor, 4)
            val size = buffer.getInt(cursor + 4)
            require(size >= 0 && cursor.toLong() + 8 + size <= bytes.size)
            if (tag == "fmt ") {
                require(size >= 16)
                formatOk = buffer.getShort(cursor + 8).toInt() == 1 &&
                    buffer.getShort(cursor + 10).toInt() == 1 &&
                    buffer.getInt(cursor + 12) == 16000 && buffer.getShort(cursor + 22).toInt() == 16
            }
            if (tag == "data") pcm = bytes.copyOfRange(cursor + 8, cursor + 8 + size)
            cursor += 8 + size + (size % 2)
        }
        require(formatOk && pcm != null)
        return pcmFloats(pcm)
    }

    private fun pcmFloats(pcm: ByteArray): FloatArray {
        require(pcm.size % 2 == 0 && pcm.size <= 16000 * 2 * 31)
        val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(shorts.remaining()) { shorts.get() / 32768f }
    }

    fun encodeAac(wav: File, output: File) {
        val samples = readWav(wav.readBytes())
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { pcm.putShort((it * 32768).toInt().coerceIn(-32768, 32767).toShort()) }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            codec.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputEnded = false
            var outputEnded = false
            var track = -1
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!outputEnded) {
                check(System.nanoTime() < deadline) { "AAC encode timeout" }
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index)!!
                        val count = minOf(input.capacity(), pcm.array().size - offset)
                        input.put(pcm.array(), offset, count)
                        val pts = offset.toLong() / 2 * 1_000_000 / 16000
                        offset += count
                        inputEnded = offset == pcm.array().size
                        codec.queueInputBuffer(index, 0, count, pts,
                            if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start(); muxerStarted = true
                    }
                    in 0..Int.MAX_VALUE -> {
                        val encoded = codec.getOutputBuffer(index)!!
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(muxerStarted)
                            encoded.position(info.offset); encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(track, encoded, info)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    fun decodeAac(fd: FileDescriptor): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(fd)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0); codec.start()
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            val deadline = System.nanoTime() + 30_000_000_000L
            while (!outputEnded) {
                check(System.nanoTime() < deadline) { "AAC decode timeout" }
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val count = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        inputEnded = count < 0
                        codec.queueInputBuffer(index, 0, maxOf(count, 0),
                            if (inputEnded) 0 else extractor.sampleTime,
                            if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!inputEnded) extractor.advance()
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actual = codec.outputFormat
                        require(actual.getInteger(MediaFormat.KEY_SAMPLE_RATE) == 16000 &&
                            actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1)
                    }
                    in 0..Int.MAX_VALUE -> {
                        val output = codec.getOutputBuffer(index)!!
                        output.position(info.offset); output.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size); output.get(chunk); pcm.write(chunk)
                        require(pcm.size() <= 16000 * 2 * 31)
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
            return pcmFloats(pcm.toByteArray())
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
}
