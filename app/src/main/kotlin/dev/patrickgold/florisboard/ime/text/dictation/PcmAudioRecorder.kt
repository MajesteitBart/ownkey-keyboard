/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.dictation

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import dev.patrickgold.florisboard.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max

/**
 * Records raw 16 kHz mono 16-bit PCM into a file under `noBackupFilesDir/ai-audio/`, for live
 * dictation. The inference process reads the same file while it grows, so every chunk is written
 * straight through and [onSamples] reports how much is on disk.
 */
class PcmAudioRecorder(
    private val context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : AudioRecorder {
    /** Called on the recording thread after each chunk is written, with the total sample count. */
    @Volatile var onSamples: ((Long) -> Unit)? = null

    /**
     * Called on the recording thread when capture stops working; Stop then fails as well. A failure that
     * happened before the callback was set is delivered when it is set, possibly twice in a race.
     */
    @Volatile var onFailure: (() -> Unit)? = null
        set(value) {
            field = value
            if (value != null && failed) value()
        }
    @Volatile private var failed = false

    @Volatile var file: File? = null
        private set

    private val written = AtomicLong()
    val samplesWritten: Long get() = written.get()

    private var record: AudioRecord? = null
    private var output: FileOutputStream? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var peak = 0f
    private var startedAtMs: Long? = null
    private var pausedAtMs: Long? = null
    private var pausedDurationMs = 0L

    @SuppressLint("MissingPermission") // The dictation manager checks the microphone permission first.
    override fun start(): Boolean {
        if (startedAtMs != null) return false
        if (BuildConfig.DEBUG) TestMicrophone.find(context)?.let { return startFrom(it) }
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        val target = runCatching {
            File.createTempFile("dictation_", ".pcm", File(context.noBackupFilesDir, "ai-audio").apply { mkdirs() })
        }.getOrElse { return false }
        val audio = try {
            // Tuned for recognition: most phones skip noise suppression and automatic gain here.
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minimum, SAMPLE_RATE * 2),
            )
        } catch (_: Exception) {
            target.delete()
            return false
        }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release(); target.delete()
            return false
        }
        val stream = runCatching { FileOutputStream(target) }.getOrElse { audio.release(); target.delete(); return false }
        runCatching { audio.startRecording() }
        if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            // Another app holds the microphone.
            audio.release(); stream.close(); target.delete()
            return false
        }
        record = audio
        output = stream
        file = target
        written.set(0)
        failed = false
        running = true
        paused = false
        startedAtMs = nowMs()
        pausedAtMs = null
        pausedDurationMs = 0L
        thread = Thread({ capture(audio, stream) }, "ownkey-mic").apply { start() }
        return true
    }

    /**
     * Debug builds only: plays a recording instead of the microphone, in real time, then silence, so a
     * live dictation can be tested end to end on an emulator.
     */
    private fun startFrom(samples: ShortArray): Boolean {
        val target = runCatching {
            File.createTempFile("dictation_", ".pcm", File(context.noBackupFilesDir, "ai-audio").apply { mkdirs() })
        }.getOrElse { return false }
        val stream = runCatching { FileOutputStream(target) }.getOrElse { target.delete(); return false }
        output = stream
        file = target
        written.set(0)
        failed = false
        running = true
        paused = false
        startedAtMs = nowMs()
        pausedAtMs = null
        pausedDurationMs = 0L
        thread = Thread({
            val bytes = ByteArray(CHUNK * 2)
            var position = 0
            val started = System.nanoTime()
            var chunks = 0L
            while (running) {
                if (paused) { Thread.sleep(20); continue }
                var loudest = 0
                for (i in 0 until CHUNK) {
                    val value = if (position < samples.size) samples[position++].toInt() else 0
                    bytes[2 * i] = value.toByte()
                    bytes[2 * i + 1] = (value shr 8).toByte()
                    loudest = max(loudest, abs(value))
                }
                chunks++
                val due = started + chunks * 100_000_000L
                val wait = (due - System.nanoTime()) / 1_000_000L
                if (wait > 0) Thread.sleep(wait)
                if (!running) break
                if (runCatching { stream.write(bytes) }.isFailure) {
                    fail()
                    break
                }
                peak = max(peak, loudest / 32_767f)
                onSamples?.invoke(written.addAndGet(CHUNK.toLong()))
            }
        }, "ownkey-test-mic").apply { start() }
        return true
    }

    private fun capture(audio: AudioRecord, stream: FileOutputStream) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val samples = ShortArray(CHUNK)
        val bytes = ByteArray(CHUNK * 2)
        while (running) {
            if (paused) {
                Thread.sleep(20)
                continue
            }
            val count = audio.read(samples, 0, CHUNK)
            if (count < 0) {
                // Pausing stops the recorder under a blocked read; anything else is a lasting error.
                if (!running || paused) continue
                fail()
                break
            }
            if (count == 0) {
                Thread.sleep(10)
                continue
            }
            var loudest = 0
            for (i in 0 until count) {
                val value = samples[i].toInt()
                bytes[2 * i] = value.toByte()
                bytes[2 * i + 1] = (value shr 8).toByte()
                loudest = max(loudest, abs(value))
            }
            if (!running) break
            try {
                stream.write(bytes, 0, count * 2)
            } catch (_: Exception) {
                fail()
                break
            }
            peak = max(peak, loudest / 32_767f)
            onSamples?.invoke(written.addAndGet(count.toLong()))
        }
    }

    private fun fail() {
        failed = true
        onFailure?.invoke()
    }

    override fun pause(): Boolean {
        if (thread == null || paused) return false
        paused = true
        record?.let { runCatching { it.stop() } }
        pausedAtMs = nowMs()
        return true
    }

    override fun resume(): Boolean {
        val pausedAt = pausedAtMs ?: return false
        record?.let { audio ->
            runCatching { audio.startRecording() }
            if (audio.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
        }
        pausedDurationMs += (nowMs() - pausedAt).coerceAtLeast(0L)
        pausedAtMs = null
        paused = false
        return true
    }

    override fun stopAndRead(): Result<AudioRecording> {
        val target = file ?: return Result.failure(IllegalStateException("No active recording session"))
        val startedAt = startedAtMs ?: nowMs()
        pausedAtMs?.let { pausedDurationMs += (nowMs() - it).coerceAtLeast(0L) }
        release()
        val durationMs = (nowMs() - startedAt - pausedDurationMs).coerceAtLeast(0L)
        if (failed || written.get() == 0L) {
            target.delete()
            return Result.failure(IllegalStateException(if (failed) "Microphone capture failed." else "Recording was too short or unavailable."))
        }
        return Result.success(
            AudioRecording(
                bytes = ByteArray(0),
                sampleRateHz = SAMPLE_RATE,
                channelCount = 1,
                durationMs = durationMs,
                mimeType = "audio/pcm",
                fileName = "recording.pcm",
                file = target,
            ),
        )
    }

    override fun cancel() {
        val target = file
        release()
        target?.delete()
    }

    override fun currentAmplitude(): Float {
        if (thread == null || paused) return 0f
        return peak.also { peak = 0f }
    }

    private fun release() {
        running = false
        val audio = record
        record = null
        runCatching { audio?.stop() }
        thread?.let { runCatching { it.join(1_000) } }
        thread = null
        runCatching { audio?.release() }
        runCatching { output?.close() }
        output = null
        file = null
        startedAtMs = null
        pausedAtMs = null
        pausedDurationMs = 0L
        onSamples = null
        onFailure = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** 100 ms per write. */
        private const val CHUNK = SAMPLE_RATE / 10
    }
}

/** Debug builds: `no_backup/test-mic.wav` (16 kHz mono 16-bit) replaces the microphone while it exists. */
private object TestMicrophone {
    fun find(context: Context): ShortArray? {
        val wav = File(context.noBackupFilesDir, "test-mic.wav")
        if (!wav.isFile) return null
        val bytes = runCatching { wav.readBytes() }.getOrNull() ?: return null
        // Find the "data" chunk; the header before it varies between tools.
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = (bytes[offset + 4].toInt() and 0xff) or ((bytes[offset + 5].toInt() and 0xff) shl 8) or
                ((bytes[offset + 6].toInt() and 0xff) shl 16) or ((bytes[offset + 7].toInt() and 0xff) shl 24)
            if (id == "data") {
                val start = offset + 8
                val count = minOf(size, bytes.size - start) / 2
                return ShortArray(count) { i ->
                    ((bytes[start + 2 * i].toInt() and 0xff) or (bytes[start + 2 * i + 1].toInt() shl 8)).toShort()
                }
            }
            offset += 8 + size + (size and 1)
        }
        return null
    }
}
