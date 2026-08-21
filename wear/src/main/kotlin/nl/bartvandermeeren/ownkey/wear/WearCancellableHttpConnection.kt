package nl.bartvandermeeren.ownkey.wear

import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Keeps blocking watch networking off the UI thread and disconnects the socket on cancellation. */
internal suspend fun <T> withCancellableWearHttpConnection(
    openConnection: () -> HttpURLConnection,
    execute: (HttpURLConnection) -> T,
): T = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        if (!continuation.isActive) return@suspendCancellableCoroutine

        val connection = try {
            openConnection()
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            runCatching { connection.disconnect() }
        }
        if (!continuation.isActive) {
            runCatching { connection.disconnect() }
            return@suspendCancellableCoroutine
        }

        WearBatteryTrace.beginRequest()
        try {
            val result = execute(connection)
            if (continuation.isActive) {
                continuation.resume(result)
            }
        } catch (error: Throwable) {
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        } finally {
            WearBatteryTrace.endRequest()
            runCatching { connection.disconnect() }
        }
    }
}
