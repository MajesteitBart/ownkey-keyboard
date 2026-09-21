package org.ownkey.offline

import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class ModelLifecycleTest {
    private val payload = "pinned-model-content".toByteArray()
    private fun entry() = ModelFile("encoder.onnx", payload.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) })
    private fun release(id: String = "test-v1") = ModelRelease(id, "https://example.invalid/models/$id", listOf(entry()))
    private fun fixture(block: suspend (ModelStore, File) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("ownkey-model-test").toFile()
        try { block(ModelStore(root, listOf(release(), release("test-v2"))), root) }
        finally { root.deleteRecursively() }
    }
    private suspend fun install(store: ModelStore, id: String = "test-v1") {
        store.stagingDirectory(id).mkdirs()
        File(store.stagingDirectory(id), entry().name).writeBytes(payload)
        store.installCandidate(id)
    }

    @Test fun `download and install do not activate and restore distrusts missing files`() = fixture { store, root ->
        install(store)
        assertNull(store.currentId)
        store.commit("test-v1")
        val restarted = ModelStore(root, store.catalog)
        assertNull(restarted.currentId)
        restarted.reconcile()
        assertEquals("test-v1", restarted.currentId)
        File(store.modelDirectory("test-v1"), entry().name).writeText("damaged")
        restarted.reconcile()
        assertNull(restarted.currentId)
        assertFailsWith<LocalAsrException> { restarted.acquire() }
    }

    @Test fun `version lease blocks update promotion and deletion until session completes`() = fixture { store, _ ->
        install(store); store.commit("test-v1")
        val lease = store.acquire()
        install(store, "test-v2")
        assertEquals("test-v1", store.currentId)
        assertEquals(LocalAsrFailure.BUSY, assertFailsWith<LocalAsrException> { store.commit("test-v2") }.reason)
        assertFailsWith<LocalAsrException> { store.removeAll() }
        lease.close(); lease.close()
        store.commit("test-v2")
        store.removeUnusedVersions()
        assertEquals(setOf("test-v2"), store.installedIds)
        store.removeAll()
        assertNull(store.currentId)
    }

    @Test fun `corrupt candidate preserves the previous verified version`() = fixture { store, _ ->
        install(store); store.commit("test-v1")
        store.stagingDirectory("test-v2").mkdirs()
        File(store.stagingDirectory("test-v2"), entry().name).writeBytes(ByteArray(payload.size))
        assertFailsWith<LocalAsrException> { store.installCandidate("test-v2") }
        assertEquals("test-v1", store.acquire().use { it.id })
    }

    @Test fun `full response after a range request replaces rather than appends`() = fixture { store, _ ->
        val staging = store.stagingDirectory("test-v1").apply { mkdirs() }
        File(staging, "encoder.onnx.part").writeBytes(payload.take(5).toByteArray())
        val response = Response(200, payload)
        ModelDownloader(store, { response }, { Long.MAX_VALUE }).download(release()) {}
        assertEquals("bytes=5-", response.getRequestProperty("Range"))
        assertEquals(setOf("test-v1"), store.installedIds)
        assertContentEquals(payload, File(store.modelDirectory("test-v1"), "encoder.onnx").readBytes())
        assertNull(store.currentId)
    }

    @Test fun `matching partial content resumes and verifies complete file`() = fixture { store, _ ->
        val staging = store.stagingDirectory("test-v1").apply { mkdirs() }
        File(staging, "encoder.onnx.part").writeBytes(payload.take(5).toByteArray())
        val response = Response(206, payload.drop(5).toByteArray(), mapOf("Content-Range" to "bytes 5-${payload.lastIndex}/${payload.size}"))
        ModelDownloader(store, { response }, { Long.MAX_VALUE }).download(release()) {}
        assertTrue(response.disconnected)
        assertContentEquals(payload, File(store.modelDirectory("test-v1"), "encoder.onnx").readBytes())
    }

    @Test fun `bad checksum and oversized response never become installed`() = fixture { store, _ ->
        for (body in listOf(ByteArray(payload.size), payload + byteArrayOf(1))) {
            assertFailsWith<LocalAsrException> {
                ModelDownloader(store, { Response(200, body) }, { Long.MAX_VALUE }).download(release()) {}
            }
            assertTrue(store.installedIds.isEmpty())
        }
    }

    @Test fun `low space fails before opening a connection`() = fixture { store, _ ->
        val error = assertFailsWith<LocalAsrException> {
            ModelDownloader(store, { error("Network must not open") }, { 0 }).download(release()) {}
        }
        assertEquals(LocalAsrFailure.INSUFFICIENT_STORAGE, error.reason)
    }

    @Test fun `invalid range is discarded and retries from zero`() = fixture { store, _ ->
        val staging = store.stagingDirectory("test-v1").apply { mkdirs() }
        File(staging, "encoder.onnx.part").writeBytes(payload.take(5).toByteArray())
        val invalid = Response(206, payload.drop(5).toByteArray(), mapOf("Content-Range" to "bytes 4-${payload.lastIndex}/${payload.size}"))
        val complete = Response(200, payload)
        val responses = ArrayDeque(listOf(invalid, complete))
        ModelDownloader(store, { responses.removeFirst() }, { Long.MAX_VALUE }).download(release()) {}
        assertNull(complete.getRequestProperty("Range"))
        assertTrue(responses.isEmpty())
        assertContentEquals(payload, File(store.modelDirectory("test-v1"), "encoder.onnx").readBytes())
    }

    @Test fun `expired range and throttling recover within bounded retries`() = fixture { store, _ ->
        val staging = store.stagingDirectory("test-v1").apply { mkdirs() }
        File(staging, "encoder.onnx.part").writeBytes(payload.take(5).toByteArray())
        val complete = Response(200, payload)
        val responses = ArrayDeque(listOf(Response(416, byteArrayOf()), Response(429, byteArrayOf(), mapOf("Retry-After" to "0")), complete))
        ModelDownloader(store, { responses.removeFirst() }, { Long.MAX_VALUE }).download(release()) {}
        assertNull(complete.getRequestProperty("Range"))
        assertTrue(responses.isEmpty())
        assertNull(store.currentId)
    }

    @Test fun `same-size tampering and symlink payloads fail verification`() = fixture { store, root ->
        install(store); store.commit("test-v1")
        val file = File(store.modelDirectory("test-v1"), entry().name)
        file.writeBytes(ByteArray(payload.size))
        assertFalse(store.verify("test-v1"))
        file.delete()
        val outside = File(root, "outside").apply { writeBytes(payload) }
        Files.createSymbolicLink(file.toPath(), outside.toPath())
        assertFalse(store.verify("test-v1"))
    }

    @Test fun `damaged selected model can be downloaded and activated again`() = fixture { store, _ ->
        install(store); store.commit("test-v1")
        File(store.modelDirectory("test-v1"), entry().name).writeText("broken")
        assertFalse(store.verify("test-v1"))
        assertNull(store.currentId)
        install(store); store.commit("test-v1")
        assertEquals("test-v1", store.currentId)
    }

    @Test fun `symlink model directories and parents cannot bypass verification`() = fixture { store, root ->
        val outside = Files.createTempDirectory("ownkey-model-outside").toFile()
        try {
            File(outside, entry().name).writeBytes(payload)
            val models = File(root, "models").apply { mkdirs() }
            Files.createSymbolicLink(File(models, "test-v1").toPath(), outside.toPath())
            assertFalse(store.verify("test-v1"))
            Files.delete(File(models, "test-v1").toPath())
            models.delete()
            File(outside, "test-v1").mkdirs()
            File(outside, "test-v1/${entry().name}").writeBytes(payload)
            Files.createSymbolicLink(models.toPath(), outside.toPath())
            assertFalse(store.verify("test-v1"))
            Files.delete(models.toPath())
        } finally { outside.deleteRecursively() }
    }

    @Test fun `unknown or unsafe model metadata is rejected`() {
        assertFailsWith<IllegalArgumentException> { ModelFile("encoder.part", 1, "0".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { ModelFile("encoder.etag", 1, "0".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { ModelFile("../encoder", 1, "0".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { ModelRelease("../escape", "https://example.invalid", listOf(entry())) }
        assertFailsWith<IllegalArgumentException> { ModelRelease("v1", "http://example.invalid", listOf(entry())) }
        assertFailsWith<IllegalArgumentException> { ModelRelease("v1", "https://example.invalid", listOf(entry(), entry())) }
        assertNull(ModelDownloader.parseContentRange("bytes 5-7/*"))
        assertNull(ModelDownloader.parseContentRange("bytes 9-7/8"))
        assertNull(ModelDownloader.parseContentRange("bytes 0-9/8"))
        assertEquals(ModelDownloader.ContentRange(5, 9, 10), ModelDownloader.parseContentRange("bytes 5-9/10"))
    }

    private class Response(private val status: Int, private val bytes: ByteArray, private val headers: Map<String, String> = emptyMap()) :
        HttpURLConnection(URL("https://example.invalid/model")) {
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getHeaderField(name: String) = headers[name]
    }
}
