package org.ownkey.offline

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Disk operations run on the caller's IO dispatcher; leases never hash or open large files. */
class ModelStore(val root: File, val catalog: List<ModelRelease> = ModelCatalog.trusted) {
    private val lock = Any()
    private val verified = mutableSetOf<String>()
    private val leases = mutableMapOf<String, Int>()
    private var current: String? = null
    private val pointer get() = File(root, "current")
    fun modelDirectory(id: String): File = File(root, "models/${release(id).id}")
    fun stagingDirectory(id: String): File = File(root, "staging/${release(id).id}")
    fun release(id: String): ModelRelease = catalog.firstOrNull { it.id == id }
        ?: throw LocalAsrException(LocalAsrFailure.MODEL_DAMAGED)
    val currentId: String? get() = synchronized(lock) { current?.takeIf { it in verified } }
    val installedIds: Set<String> get() = synchronized(lock) { verified.toSet() }
    val hasLeases: Boolean get() = synchronized(lock) { leases.values.any { it > 0 } }

    val hasStoredData: Boolean get() = listOf("models", "staging").any { File(root, it).list()?.isNotEmpty() == true }

    suspend fun reconcile() {
        root.mkdirs()
        val valid = catalog.filter { verifyDirectory(modelDirectory(it.id), it) }.map { it.id }.toSet()
        val stored = runCatching { pointer.readText().trim() }.getOrNull()
        synchronized(lock) {
            verified.clear(); verified.addAll(valid)
            current = stored?.takeIf { it in valid }
        }
        // A missing/restored pointer is not a selection or download action.
    }

    suspend fun verify(id: String): Boolean {
        val valid = verifyDirectory(modelDirectory(id), release(id))
        synchronized(lock) {
            if (valid) verified.add(id) else {
                verified.remove(id)
                if (current == id) current = null
            }
        }
        return valid
    }

    suspend fun verifyDirectory(directory: File, release: ModelRelease): Boolean {
        if (!directory.isDirectory) return false
        val rootPath = root.toPath().toAbsolutePath().normalize()
        var path = directory.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(rootPath)) return false
        while (path.startsWith(rootPath)) {
            if (Files.isSymbolicLink(path)) return false
            path = path.parent ?: break
        }
        return release.files.all { matches(File(directory, it.name), it) }
    }

    suspend fun matches(file: File, expected: ModelFile): Boolean {
        try {
            if (Files.isSymbolicLink(file.toPath()) || !file.isFile || file.length() != expected.bytes || file.canonicalFile != File(file.parentFile!!.canonicalFile, file.name)) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(65536).use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == expected.sha256
        } catch (_: java.io.IOException) { return false }
    }

    /** Promote files without changing the selected backend or current model pointer. */
    suspend fun installCandidate(id: String) {
        val release = release(id)
        val staging = stagingDirectory(id)
        if (!verifyDirectory(staging, release)) throw LocalAsrException(LocalAsrFailure.INTEGRITY)
        synchronized(lock) {
            val destination = modelDirectory(id)
            if (leases.getOrDefault(id, 0) > 0 || current == id) throw LocalAsrException(LocalAsrFailure.BUSY)
            if (destination.exists() && !destination.deleteRecursively()) throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
            destination.parentFile!!.mkdirs()
            if (!staging.renameTo(destination)) throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
            verified.add(id)
        }
    }

    /** Caller unloads the old runtime and smoke-tests the candidate before committing this pointer. */
    fun commit(id: String) = synchronized(lock) {
        if (leases.values.any { it > 0 }) throw LocalAsrException(LocalAsrFailure.BUSY)
        if (id !in verified) throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
        atomicWrite(pointer, id)
        current = id
    }

    fun acquire(id: String? = currentId): ModelLease = synchronized(lock) {
        if (id == null || id !in verified) throw LocalAsrException(LocalAsrFailure.MODEL_MISSING)
        leases[id] = leases.getOrDefault(id, 0) + 1
        ModelLease(id, modelDirectory(id)) {
            synchronized(lock) { leases[id] = (leases.getOrDefault(id, 1) - 1).coerceAtLeast(0) }
        }
    }

    fun removeAll() = synchronized(lock) {
        if (leases.values.any { it > 0 }) throw LocalAsrException(LocalAsrFailure.BUSY)
        // Remove the trusted pointer first; interruption can leave only unselected files.
        if (pointer.exists() && !pointer.delete()) throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
        current = null; verified.clear()
        if (!File(root, "models").deleteRecursively() || !File(root, "staging").deleteRecursively()) {
            throw LocalAsrException(LocalAsrFailure.DOWNLOAD)
        }
    }

    fun removeUnusedVersions() = synchronized(lock) {
        catalog.filter { it.id != current && leases.getOrDefault(it.id, 0) == 0 }.forEach {
            if (modelDirectory(it.id).deleteRecursively()) verified.remove(it.id)
        }
    }

    companion object {
        fun atomicWrite(file: File, text: String) {
            file.parentFile?.mkdirs()
            val partial = File(file.path + ".new")
            FileOutputStream(partial).use { it.write(text.toByteArray()); it.fd.sync() }
            try {
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (atomicFailure: java.io.IOException) {
                try {
                    Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (replacementFailure: java.io.IOException) {
                    atomicFailure.addSuppressed(replacementFailure)
                    throw atomicFailure
                }
            }
        }
    }
}

class ModelLease internal constructor(val id: String, val directory: File, private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}
