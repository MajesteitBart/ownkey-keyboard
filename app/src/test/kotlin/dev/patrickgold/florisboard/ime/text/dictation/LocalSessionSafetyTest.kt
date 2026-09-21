package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class LocalSessionSafetyTest : FunSpec({
    test("explicit local and unknown settings cannot fall back to cloud or mock") {
        for (key in listOf(false, true)) for (debug in listOf(false, true)) {
            TranscriptionBackend.resolve("orukeet", key, debug) shouldBe TranscriptionBackend.ORUKEET
            TranscriptionBackend.resolve("future-backend", key, debug) shouldBe TranscriptionBackend.UNAVAILABLE
        }
        TranscriptionBackend.resolve("", true, false) shouldBe TranscriptionBackend.CLOUD
        TranscriptionBackend.resolve("", false, false) shouldBe TranscriptionBackend.EXTERNAL_IME
        TranscriptionBackend.resolve("", false, true) shouldBe TranscriptionBackend.MOCK
    }
    test("snapshot releases resources only once") {
        var releases = 0
        val session = TranscriptionSession(TranscriptionBackend.ORUKEET, NoOpAudioRecorder(), null) { releases++ }
        session.mode shouldBe AudioSessionMode.LOCAL
        session.close(); session.close()
        releases shouldBe 1
    }
    test("invalidation does not wait for recorder startup and rejects its late lease") {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val base = NoOpAudioRecorder()
        val recorder = object : AudioRecorder by base {
            override fun start(): Boolean { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)); return base.start() }
        }
        val coordinator = AudioSessionCoordinator()
        coroutineScope {
            val started = async(Dispatchers.IO) { coordinator.tryStart(AudioSessionOwner.DICTATION, AudioSessionMode.LOCAL, recorder) }
            try {
                withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) } shouldBe true
                withTimeout(1000) { withContext(Dispatchers.IO) { coordinator.invalidate(AudioSessionInvalidation.FIELD_SWITCH) } } shouldBe true
                coordinator.state.value shouldBe null
            } finally { finish.countDown() }
            started.await() shouldBe AudioSessionStartResult.RecorderUnavailable
        }
    }
    test("late file from a cancelled stop is deleted without blocking editor invalidation") {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val file = Files.createTempFile("local-audio-test", ".m4a").toFile()
        val base = NoOpAudioRecorder()
        var releases = 0
        val recorder = object : AudioRecorder by base {
            override fun stopAndRead(): Result<AudioRecording> {
                entered.countDown(); check(finish.await(5, TimeUnit.SECONDS))
                return Result.success(AudioRecording(byteArrayOf(), 16000, 1, 100, "audio/mp4", "test.m4a", file))
            }
        }
        val coordinator = AudioSessionCoordinator()
        val lease = (coordinator.tryStart(AudioSessionOwner.DICTATION, AudioSessionMode.LOCAL, recorder) { releases++ } as AudioSessionStartResult.Started).lease
        try {
            coroutineScope {
                val stopped = async(Dispatchers.IO) { lease.stop() }
                try {
                    withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) } shouldBe true
                    withTimeout(1000) { withContext(Dispatchers.IO) { coordinator.invalidate(AudioSessionInvalidation.FIELD_SWITCH) } } shouldBe true
                } finally { finish.countDown() }
                stopped.await() shouldBe AudioSessionStopResult.Stale
            }
            file.exists() shouldBe false
            releases shouldBe 1
        } finally { file.delete() }
    }
})
