package org.ownkey.offline

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class DownloadProgress(val completed: Long, val total: Long, val verifying: Boolean = false)

/** One bounded transfer at a time. No keys, mutable manifests, archives or native code downloads. */
class ModelDownloader(
    private val store: ModelStore,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val availableBytes: () -> Long = { store.root.usableSpace },
) {
    suspend fun download(release: ModelRelease, progress: (DownloadProgress) -> Unit) = withContext(Dispatchers.IO) {
        val staging = store.stagingDirectory(release.id).apply { mkdirs() }
        var complete = 0L
        for (entry in release.files) {
            currentCoroutineContext().ensureActive()
            val target = File(staging, entry.name)
            if (store.matches(target, entry)) {
                complete += entry.bytes
                progress(DownloadProgress(complete, release.bytes))
                continue
            }
            target.delete()
            val partial = File(staging, entry.name + ".part")
            if (partial.length() > entry.bytes) partial.delete()
            var attempt = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val remaining = release.bytes - complete - partial.length()
                if (availableBytes() < remaining + ModelCatalog.RESERVE_BYTES) {
                    throw LocalAsrException(LocalAsrFailure.INSUFFICIENT_STORAGE)
                }
                try {
                    if (partial.length() != entry.bytes) {
                        transfer(URL(release.baseUrl + "/" + entry.name), partial, entry.bytes) { bytes ->
                            progress(DownloadProgress(complete + bytes, release.bytes))
                        }
                    }
                    progress(DownloadProgress(complete + partial.length(), release.bytes, verifying = true))
                    if (!store.matches(partial, entry)) {
                        partial.delete()
                        throw LocalAsrException(LocalAsrFailure.INTEGRITY)
                    }
                    if (!partial.renameTo(target)) throw IOException("Cannot finalize model file")
                    File(partial.path + ".etag").delete()
                    break
                } catch (cancel: CancellationException) { throw cancel
                } catch (error: Exception) {
                    if (error is LocalAsrException || ++attempt >= 3) throw error
                    val retry = (error as? RetryableHttp)?.delayMs ?: (1000L shl attempt)
                    delay(retry.coerceIn(1000, 60_000))
                }
            }
            complete += entry.bytes
        }
        progress(DownloadProgress(complete, release.bytes, verifying = true))
        store.installCandidate(release.id)
    }

    private suspend fun transfer(url: URL, partial: File, expected: Long, progress: (Long) -> Unit) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val connection = open(url).apply {
                connectTimeout = 15_000; readTimeout = 15_000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "Ownkey-model-download/1")
            }
            val offset = partial.length()
            val validator = File(partial.path + ".etag")
            val etag = runCatching { validator.readText() }.getOrNull()?.takeIf { it.startsWith('"') && it.endsWith('"') }
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                if (etag != null) connection.setRequestProperty("If-Range", etag)
            }
            continuation.invokeOnCancellation { runCatching { connection.disconnect() } }
            try {
                if (!continuation.isActive) return@suspendCancellableCoroutine
                val status = connection.responseCode
                if (connection.url.protocol != "https") throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
                if (status == 429 || status in 500..599 || status == 408) {
                    throw RetryableHttp(connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1000) ?: 2000)
                }
                if (status == 416) {
                    partial.delete(); validator.delete()
                    throw IOException("Range no longer available")
                }
                if (status !in setOf(200, 206)) throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
                val responseTag = connection.getHeaderField("ETag")
                val append = status == 206
                if (append) {
                    val range = parseContentRange(connection.getHeaderField("Content-Range"))
                    if (range == null || range.first != offset || range.last != expected - 1 || range.total != expected ||
                        (etag != null && responseTag != null && etag != responseTag)) {
                        partial.delete(); validator.delete()
                        throw IOException("Incompatible range response")
                    }
                }
                val beginning = if (append) offset else 0L
                val length = connection.contentLengthLong
                if (length >= 0 && length != expected - beginning) throw LocalAsrException(LocalAsrFailure.INTEGRITY)
                if (responseTag != null && responseTag.startsWith('"') && !responseTag.startsWith("W/")) {
                    ModelStore.atomicWrite(validator, responseTag)
                } else validator.delete()
                var written = beginning
                connection.inputStream.use { source ->
                    FileOutputStream(partial, append).use { target ->
                        val buffer = ByteArray(65536)
                        var lastReport = 0L
                        while (continuation.isActive) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (written + count > expected) throw LocalAsrException(LocalAsrFailure.INTEGRITY)
                            if (availableBytes() < ModelCatalog.RESERVE_BYTES + count) {
                                throw LocalAsrException(LocalAsrFailure.INSUFFICIENT_STORAGE)
                            }
                            target.write(buffer, 0, count); written += count
                            if (System.nanoTime() - lastReport > 200_000_000) {
                                progress(written); lastReport = System.nanoTime()
                            }
                        }
                        target.fd.sync()
                    }
                }
                if (written != expected) throw IOException("Incomplete model file")
                if (continuation.isActive) continuation.resume(Unit)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally { connection.disconnect() }
        }
    }

    private class RetryableHttp(val delayMs: Long) : IOException()

    data class ContentRange(val first: Long, val last: Long, val total: Long)
    companion object {
        fun parseContentRange(value: String?): ContentRange? {
            val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(value ?: "") ?: return null
            val values = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
            return ContentRange(values[0], values[1], values[2]).takeIf { it.first <= it.last && it.last < it.total }
        }
    }
}
