/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.network

import dev.patrickgold.florisboard.lib.util.BatteryTraceSink
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

class CancellableHttpConnectionTest : FunSpec({
    test("request leaves the typing thread and cancellation disconnects with a balanced trace") {
        val connection = BlockingHttpConnection()
        val trace = RecordingBatteryTraceSink()
        val typingDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "typing-critical-test")
        }.asCoroutineDispatcher()
        val requestScope = CoroutineScope(SupervisorJob() + typingDispatcher)
        try {
            lateinit var callerThread: String
            val request = requestScope.async {
                callerThread = Thread.currentThread().name
                withCancellableHttpConnection(
                    traceLabel = "fixed-test-label",
                    traceSink = trace,
                    openConnection = { connection },
                ) { activeConnection ->
                    (activeConnection as BlockingHttpConnection).blockUntilDisconnected()
                }
            }

            withContext(kotlinx.coroutines.Dispatchers.IO) {
                connection.entered.await(2, TimeUnit.SECONDS) shouldBe true
            }
            request.cancelAndJoin()

            callerThread.startsWith("typing-critical-test") shouldBe true
            (connection.executionThread != null) shouldBe true
            (connection.executionThread != callerThread) shouldBe true
            (connection.disconnectCalls.get() > 0) shouldBe true
            trace.events shouldContainExactly listOf(
                "begin:fixed-test-label",
                "end",
            )
        } finally {
            requestScope.cancel()
            typingDispatcher.close()
        }
    }

    test("successful request closes its connection and trace once") {
        val connection = ImmediateHttpConnection()
        val trace = RecordingBatteryTraceSink()

        withCancellableHttpConnection(
            traceLabel = "fixed-test-label",
            traceSink = trace,
            openConnection = { connection },
        ) { "ok" } shouldBe "ok"

        connection.disconnectCalls.get() shouldBe 1
        trace.events shouldContainExactly listOf("begin:fixed-test-label", "end")
    }
})

private class BlockingHttpConnection : HttpURLConnection(URL("https://localhost")) {
    val entered = CountDownLatch(1)
    private val disconnected = CountDownLatch(1)
    val disconnectCalls = AtomicInteger(0)
    @Volatile var executionThread: String? = null

    fun blockUntilDisconnected(): String {
        executionThread = Thread.currentThread().name
        entered.countDown()
        if (!disconnected.await(5, TimeUnit.SECONDS)) {
            throw IllegalStateException("Cancellation did not disconnect the request")
        }
        throw IOException("Disconnected")
    }

    override fun disconnect() {
        disconnectCalls.incrementAndGet()
        disconnected.countDown()
    }

    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
}

private class ImmediateHttpConnection : HttpURLConnection(URL("https://localhost")) {
    val disconnectCalls = AtomicInteger(0)

    override fun disconnect() {
        disconnectCalls.incrementAndGet()
    }

    override fun usingProxy(): Boolean = false
    override fun connect() = Unit
}

private class RecordingBatteryTraceSink : BatteryTraceSink {
    val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun beginSection(label: String) {
        events += "begin:$label"
    }

    override fun endSection() {
        events += "end"
    }

    override fun beginAsyncSection(label: String, cookie: Int) = Unit
    override fun endAsyncSection(label: String, cookie: Int) = Unit
}
