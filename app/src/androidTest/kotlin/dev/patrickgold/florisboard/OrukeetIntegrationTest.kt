package dev.patrickgold.florisboard

import android.app.ActivityManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.dictation.AudioRecording
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ownkey.offline.*

/** Opt-in: tools/orukeet-probe/integration.py provisions only the pinned public fixture and weights. */
@RunWith(AndroidJUnit4::class)
class OrukeetIntegrationTest {
    @Test fun downloadJobPreservesTransportConsentWithoutRejectingMeteredWifiOrVpn() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 34)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val job = dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads.createJob(context, "test", false)
        val request = requireNotNull(job.requiredNetwork)
        assertTrue(request.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))
        assertTrue(request.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        assertFalse(request.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))
        assertFalse(request.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        assertFalse(request.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
        assertEquals(64L * 1024, job.minimumNetworkChunkBytes)
        val any = dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloadNetwork.request(true)
        assertTrue(any.transportTypes.isEmpty())
        assertFalse(any.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
    }

    @Test fun downloadConsentCannotAuthorizeAnotherModelVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val record = File(context.noBackupFilesDir, "ai-models/transfer-request")
        val previous = record.takeIf { it.exists() }?.readBytes()
        try {
            record.parentFile!!.mkdirs()
            record.writeText("retired-model:token")
            assertFalse(dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads.authorized(context, "token"))
            record.writeText("${ModelCatalog.current.id}:token")
            assertTrue(dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads.authorized(context, "token"))
            assertFalse(dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads.authorized(context, "different-token"))
        } finally { if (previous == null) record.delete() else record.writeBytes(previous) }
    }

    @Test fun scheduledDownloadDoesNotActivateOrChangeCloudRoute() = runBlocking {
        assumeTrue("Opt-in network fixture", InstrumentationRegistry.getArguments().getString("download") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as FlorisApplication
        if (InstrumentationRegistry.getArguments().getString("meteredWifi") == "true") {
            val connectivity = app.getSystemService(android.net.ConnectivityManager::class.java)
            val capabilities = requireNotNull(connectivity.getNetworkCapabilities(connectivity.activeNetwork))
            assertTrue(capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))
            assertFalse("Test requires Android to mark Wi-Fi metered", capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
            val oldRequest = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).build()
            assertFalse("Reproduce the original eligibility failure", oldRequest.canBeSatisfiedBy(capabilities))
            assertTrue(dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloadNetwork.request(false).canBeSatisfiedBy(capabilities))
            assertTrue(dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloadNetwork.allows(capabilities, false))
        }
        val prefs by FlorisPreferenceStore
        app.preferenceStoreLoaded.first { it }
        val controller = app.offlineDictation.value
        controller.state.first { it.phase != ModelPhase.CHECKING }
        val previous = prefs.voxtral.dictationBackend.get()
        try {
            controller.delete()
            prefs.voxtral.dictationBackend.set("cloud")
            app.startActivity(android.content.Intent(app, dev.patrickgold.florisboard.app.FlorisAppActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            delay(2000)
            dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads.schedule(app, allowMobileData = false)
            withTimeout(600_000) { controller.state.first { ModelCatalog.current.id in it.installed || it.error != null } }
            assertNull(controller.state.value.error)
            assertNull(controller.store.currentId)
            assertEquals("cloud", prefs.voxtral.dictationBackend.get())
            assertEquals(RuntimeState.UNLOADED, controller.runtime.state.value)
        } finally { prefs.voxtral.dictationBackend.set(previous) }
    }

    @Test fun nativeLifecycleAndCrashRecovery() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FlorisApplication
        val fixture = File(app.noBackupFilesDir, "orukeet-test/sample.wav")
        assumeTrue("Provision the public fixture with integration.py", fixture.isFile)
        val prefs by FlorisPreferenceStore
        app.preferenceStoreLoaded.first { it }
        val previous = prefs.voxtral.dictationBackend.get()
        val previousSaved = prefs.voxtral.previousDictationBackend.get()
        val controller = app.offlineDictation.value
        withTimeout(60_000) { controller.state.first { it.phase != ModelPhase.CHECKING } }
        assertTrue(controller.compatible)
        assertEquals(setOf(ModelCatalog.current.id), controller.store.installedIds)
        assertFalse(controller.store.hasLeases)
        val mainPid = Process.myPid()
        fun servicePid(): Int? = app.getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.processName == app.packageName + InferenceService.PROCESS_SUFFIX }?.pid
        fun memory(pid: Int): org.json.JSONObject {
            val status = File("/proc/$pid/status").readText()
            fun value(key: String): Long = Regex("(?m)^$key:\\s+(\\d+)").find(status)!!.groupValues[1].toLong()
            return org.json.JSONObject().put("rss_kib", value("VmRSS")).put("high_water_rss_kib", value("VmHWM"))
        }
        val metrics = org.json.JSONObject().put("api", android.os.Build.VERSION.SDK_INT)
            .put("page_bytes", android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE))
            .put("runtime_aar_sha256", InstrumentationRegistry.getArguments().getString("runtimeSha256"))
        suspend fun transcribe(source: File = fixture): Result<String> = controller.session().use { session ->
            val owned = File(app.noBackupFilesDir, "ai-audio/test.wav").apply { parentFile!!.mkdirs(); source.copyTo(this, true) }
            AudioRecording(bytes = byteArrayOf(), sampleRateHz = 16000, channelCount = 1, durationMs = 7435, mimeType = "audio/wav", fileName = "test.wav", file = owned).use { session.client!!.transcribe(it) }
        }
        try {
            prefs.voxtral.dictationBackend.set("cloud")
            assertEquals(RuntimeState.UNLOADED, controller.runtime.state.value)
            controller.activate()
            assertTrue(controller.selected)
            assertEquals(RuntimeState.READY, controller.runtime.state.value)
            assertNotEquals(mainPid, servicePid())
            metrics.put("activation_service", memory(requireNotNull(servicePid()))).put("activation_main", memory(mainPid))
            val expected = transcribe().getOrThrow()
            assertTrue(expected.isNotBlank())
            metrics.put("after_inference_service", memory(requireNotNull(servicePid()))).put("after_inference_main", memory(mainPid))
            File(app.noBackupFilesDir, "orukeet-runtime-metrics.json").writeText(metrics.toString())
            val aac = File(fixture.parentFile, "sample.m4a")
            PublicFixtureAudio.encodeAac(fixture, aac)
            assertEquals(expected, transcribe(aac).getOrThrow())
            assertFalse(File(app.noBackupFilesDir, "ai-audio/test.wav").exists())
            assertFalse(controller.store.hasLeases)
            val held = controller.session()
            try {
                try { controller.activate(); fail("An active version lease must block updates") }
                catch (e: LocalAsrException) { assertEquals(LocalAsrFailure.BUSY, e.reason) }
            } finally { held.close() }
            // Observe the progress callback, then kill during native work, not after completion.
            val death = launch(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(20_000) { controller.runtime.state.first { it == RuntimeState.TRANSCRIBING } }
                Process.killProcess(requireNotNull(servicePid()))
            }
            val failed = transcribe()
            death.join()
            assertEquals(LocalAsrFailure.PROCESS_DIED, (failed.exceptionOrNull() as LocalAsrException).reason)
            assertEquals(mainPid, Process.myPid())
            assertTrue(controller.selected) // Crash cannot switch to cloud.
            assertFalse(controller.store.hasLeases)
            assertEquals(expected, transcribe().getOrThrow())
            controller.runtime.unload()
            val cancel = launch(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(20_000) { controller.runtime.state.first { it == RuntimeState.LOADING } }
                controller.runtime.unload()
            }
            assertTrue(transcribe().isFailure)
            cancel.join()
            assertFalse(controller.store.hasLeases)
            assertEquals(expected, transcribe().getOrThrow())
            val soakSeconds = (InstrumentationRegistry.getArguments().getString("soakSeconds")?.toLong() ?: 0).coerceIn(0, 900)
            if (soakSeconds > 0) {
                val readings = org.json.JSONArray()
                val started = android.os.SystemClock.elapsedRealtime()
                var cycle = 0
                while (android.os.SystemClock.elapsedRealtime() - started < soakSeconds * 1000) {
                    assertEquals(expected, transcribe(aac).getOrThrow())
                    val ids = intArrayOf(mainPid, requireNotNull(servicePid()))
                    val memory = app.getSystemService(ActivityManager::class.java).getProcessMemoryInfo(ids)
                    readings.put(org.json.JSONObject().put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started)
                        .put("main_pss_kib", memory[0].totalPss).put("inference_pss_kib", memory[1].totalPss))
                    if (++cycle % 6 == 0) InstrumentationRegistry.getInstrumentation().sendStatus(2,
                        android.os.Bundle().apply { putString("soak_progress", "completed cycles: $cycle") })
                    delay(10_000)
                }
                File(app.noBackupFilesDir, "orukeet-soak.json").writeText(org.json.JSONObject()
                    .put("api", android.os.Build.VERSION.SDK_INT).put("abi", android.os.Build.SUPPORTED_ABIS[0])
                    .put("duration_seconds", soakSeconds).put("cycles", cycle).put("readings", readings).toString())
            }
            controller.deactivate()
            assertEquals("cloud", prefs.voxtral.dictationBackend.get())
            assertEquals(RuntimeState.UNLOADED, controller.runtime.state.value)
            assertTrue(controller.store.installedIds.isNotEmpty())
            controller.delete()
            assertTrue(controller.store.installedIds.isEmpty())
            assertNull(controller.store.currentId)
            assertEquals("cloud", prefs.voxtral.dictationBackend.get())
        } finally {
            controller.runtime.unload()
            prefs.voxtral.dictationBackend.set(previous)
            prefs.voxtral.previousDictationBackend.set(previousSaved)
            fixture.parentFile!!.deleteRecursively()
        }
    }
}
