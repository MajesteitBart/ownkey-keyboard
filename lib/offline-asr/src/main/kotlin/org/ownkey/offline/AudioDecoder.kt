package org.ownkey.offline

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes the bounded recording directly from its descriptor, entirely in the inference process. */
internal object AudioDecoder {
    fun decode(fd: FileDescriptor): FloatArray {
        if (Os.fstat(fd).st_size !in 1..4_000_000L) throw LocalAsrException(LocalAsrFailure.AUDIO)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(fd)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw LocalAsrException(LocalAsrFailure.AUDIO)
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION) && inputFormat.getLong(MediaFormat.KEY_DURATION) > 31_000_000) {
                throw LocalAsrException(LocalAsrFailure.AUDIO)
            }
            codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            codec.configure(inputFormat, null, null, 0); codec.start()
            var rate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var ended = false
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!ended) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val count = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        ended = count < 0
                        codec.queueInputBuffer(index, 0, count.coerceAtLeast(0), if (ended) 0 else extractor.sampleTime,
                            if (ended) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (!ended) extractor.advance()
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else AudioFormat.ENCODING_PCM_16BIT
                        require(rate in 8000..48000 && channels in 1..2 && encoding == AudioFormat.ENCODING_PCM_16BIT)
                    }
                    in 0..Int.MAX_VALUE -> {
                        if (pcm.size() + info.size > 48_000 * 2 * 2 * 31) throw LocalAsrException(LocalAsrFailure.AUDIO)
                        val output = codec.getOutputBuffer(index)!!
                        output.position(info.offset); output.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size); output.get(chunk); pcm.write(chunk)
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return pcm16ToMono16k(pcm.toByteArray(), rate, channels)
                        }
                    }
                }
            }
            throw LocalAsrException(LocalAsrFailure.TIMEOUT)
        } catch (error: LocalAsrException) { throw error
        } catch (_: Exception) { throw LocalAsrException(LocalAsrFailure.AUDIO)
        } finally {
            codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
            extractor.release()
        }
    }

    internal fun pcm16ToMono16k(bytes: ByteArray, rate: Int, channels: Int): FloatArray {
        require(rate in 8000..48000 && channels in 1..2 && bytes.size % (2 * channels) == 0)
        val frames = bytes.size / (2 * channels)
        require(frames <= rate * 31)
        if (frames == 0) return FloatArray(0)
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val mono = FloatArray(frames) {
            var sum = 0f
            repeat(channels) { sum += shorts.get() / 32768f }
            sum / channels
        }
        if (rate == 16000) return mono
        return FloatArray((frames.toLong() * 16000 / rate).toInt()) { i ->
            val source = i.toDouble() * rate / 16000
            val first = source.toInt().coerceAtMost(mono.lastIndex)
            val next = (first + 1).coerceAtMost(mono.lastIndex)
            mono[first] + (mono[next] - mono[first]) * (source - first).toFloat()
        }
    }
}
