package org.ownkey.offline

/** Stable diagnostic codes only: native messages, paths, causes and user content stay private. */
internal object InferenceDiagnostics {
    fun failureCode(error: Throwable): String = when (error) {
        is OutOfMemoryError -> "OUT_OF_MEMORY"
        is LinkageError -> "NATIVE_LIBRARY"
        else -> "RUNTIME"
    }
}
