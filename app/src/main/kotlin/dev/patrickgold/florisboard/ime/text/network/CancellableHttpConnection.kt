/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.network

import dev.patrickgold.florisboard.lib.util.AndroidBatteryTraceSink
import dev.patrickgold.florisboard.lib.util.BatteryTraceSink
import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Runs one blocking [HttpURLConnection] request away from the typing-critical thread.
 *
 * [HttpURLConnection] is not coroutine-aware. Registering [HttpURLConnection.disconnect] directly
 * on cancellation gives keyboard-hide, field-switch, and IME-teardown cancellation a way to unblock
 * an active connect/read instead of leaving the radio and an IO thread alive until the full timeout.
 */
internal suspend fun <T> withCancellableHttpConnection(
    traceLabel: String,
    traceSink: BatteryTraceSink = AndroidBatteryTraceSink,
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

        traceSink.beginSection(traceLabel)
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
            traceSink.endSection()
            runCatching { connection.disconnect() }
        }
    }
}
