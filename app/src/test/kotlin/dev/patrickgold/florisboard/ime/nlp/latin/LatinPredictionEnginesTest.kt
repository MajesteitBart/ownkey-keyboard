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

package dev.patrickgold.florisboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.florisboard.lib.prediction.UnigramListModel
import org.florisboard.lib.prediction.WordLanguageModel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class LatinPredictionEnginesTest : FunSpec({
    // Loads run on a test dispatcher that only advances in runAll(): prepare everything a test awaits first.
    timeout = 60_000
    fun model(language: String): WordLanguageModel = when (language) {
        "nl" -> UnigramListModel.fromCounts("nl", listOf("de" to 500L, "het" to 400L, "ik" to 300L, "heb" to 200L))
        else -> UnigramListModel.fromCounts("en", listOf("the" to 500L, "you" to 400L, "have" to 300L, "hello" to 200L))
    }

    // The provider's scope is a supervisor scope: a failed load must not cancel the others.
    class Loads {
        private val dispatcher = StandardTestDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        fun runAll() = dispatcher.scheduler.advanceUntilIdle()
    }

    test("never waits: nothing is ready until the load ran, then the engine is built") {
        val loads = Loads()
        val engines = LatinPredictionEngines(loads.scope) { model(it) }
        engines.engineIfReady(listOf("nl", "en")).shouldBeNull()
        engines.modelIfReady("nl").shouldBeNull()
        engines.hasFailed(listOf("nl", "en")) shouldBe false
        loads.runAll()
        val engine = engines.engineIfReady(listOf("nl", "en")).shouldNotBeNull()
        engine.languages shouldBe listOf("nl", "en")
        engines.modelIfReady("nl").shouldNotBeNull()
    }

    test("every language is loaded and sorted once; all language orders share them") {
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val loads = Loads()
        val engines = LatinPredictionEngines(loads.scope) { language ->
            counts.getOrPut(language) { AtomicInteger() }.incrementAndGet()
            model(language)
        }
        engines.prepare(listOf("nl", "en"))
        engines.prepare(listOf("en", "nl"))
        engines.prepare(listOf("en"))
        loads.runAll()
        counts.mapValues { it.value.get() } shouldBe mapOf("nl" to 1, "en" to 1)
        val nlEn = engines.engineIfReady(listOf("nl", "en")).shouldNotBeNull()
        val enNl = engines.engineIfReady(listOf("en", "nl")).shouldNotBeNull()
        val en = engines.engineIfReady(listOf("en")).shouldNotBeNull()
        enNl.lexicons[0] shouldBeSameInstanceAs nlEn.lexicons[1]
        enNl.lexicons[1] shouldBeSameInstanceAs nlEn.lexicons[0]
        en.lexicons[0] shouldBeSameInstanceAs nlEn.lexicons[1]
    }

    test("models load one at a time, the primary language first") {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val order = Collections.synchronizedList(ArrayList<String>())
        val engines = LatinPredictionEngines(scope) { language ->
            maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            Thread.sleep(50)
            order.add(language)
            active.decrementAndGet()
            model(language)
        }
        try {
            engines.awaitEngine(listOf("nl", "en")).shouldNotBeNull()
            maxActive.get() shouldBe 1
            order shouldBe listOf("nl", "en")
        } finally {
            scope.cancel()
        }
    }

    test("a model that fails to load fails its engines, not the other language") {
        val loads = Loads()
        val engines = LatinPredictionEngines(loads.scope) { language ->
            if (language == "nl") error("broken asset") else model(language)
        }
        engines.engineIfReady(listOf("nl", "en")).shouldBeNull()
        engines.prepare(listOf("en"))
        loads.runAll()
        engines.hasFailed(listOf("nl", "en")) shouldBe true
        engines.engineIfReady(listOf("nl", "en")).shouldBeNull()
        engines.awaitEngine(listOf("nl", "en")).shouldBeNull()
        engines.awaitModel("nl").shouldBeNull()
        engines.awaitModel("en").shouldNotBeNull()
        engines.awaitEngine(listOf("en")).shouldNotBeNull()
        engines.hasFailed(listOf("en")) shouldBe false
    }

    test("an Error such as OutOfMemoryError while loading is reported as a failure, never rethrown") {
        val loads = Loads()
        val engines = LatinPredictionEngines(loads.scope) { language ->
            if (language == "en") throw OutOfMemoryError("test") else model(language)
        }
        engines.prepare(listOf("en"))
        loads.runAll()
        engines.hasFailed(listOf("en")) shouldBe true
        engines.awaitEngine(listOf("en")).shouldBeNull()
        engines.awaitModel("en").shouldBeNull()
        engines.engineIfReady(listOf("en")).shouldBeNull()
        engines.modelIfReady("en").shouldBeNull()
    }

    // The bundled Dutch model, memory-mapped like the provider maps the uncompressed asset.
    val nlAsset = File("src/main/assets/ime/dict/ngram/nl.bin").takeIf { it.isFile }

    test("an n-gram model is read in place from the mapped buffer, without opening the stream")
        .config(enabled = nlAsset != null) {
        val mapped = RandomAccessFile(nlAsset!!, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
        var opened = false
        val model = LatinPredictionEngines.loadNgramModel(
            "nl",
            map = { mapped },
            open = {
                opened = true
                nlAsset.inputStream()
            },
        )
        opened shouldBe false
        model.language shouldBe "nl"
        model.ngram.isDirect shouldBe true
        model.ngram.fileBytes.toLong() shouldBe nlAsset.length()
        model.wordId("afspraak") shouldNotBe -1
    }

    test("an n-gram asset that cannot be mapped (stored compressed) is read from the stream instead")
        .config(enabled = nlAsset != null) {
        val failures = ArrayList<Exception>()
        val model = LatinPredictionEngines.loadNgramModel(
            "nl",
            map = { throw FileNotFoundException("it is probably compressed") },
            open = { nlAsset!!.inputStream() },
            onMapFailed = { failures.add(it) },
            heapBudget = { nlAsset!!.length() },
        )
        failures.size shouldBe 1
        model.ngram.isDirect shouldBe false
        model.ngram.fileBytes.toLong() shouldBe nlAsset!!.length()
        model.wordId("afspraak") shouldNotBe -1
    }

    test("the heap copy is refused when the model does not fit in the heap budget, so the caller falls back")
        .config(enabled = nlAsset != null) {
        var failures = 0
        val result = runCatching {
            LatinPredictionEngines.loadNgramModel(
                "nl",
                map = { throw FileNotFoundException("it is probably compressed") },
                open = { nlAsset!!.inputStream() },
                onMapFailed = { failures++ },
                heapBudget = { nlAsset!!.length() - 1 },
            )
        }
        failures shouldBe 1
        (result.exceptionOrNull() is IllegalStateException) shouldBe true
        // The engine of a language that failed this way is never built: the provider uses the frequency lists.
        val loads = Loads()
        val engines = LatinPredictionEngines(loads.scope) { throw result.exceptionOrNull()!! }
        engines.prepare(listOf("nl"))
        loads.runAll()
        engines.hasFailed(listOf("nl")) shouldBe true
        engines.engineIfReady(listOf("nl")).shouldBeNull()
    }

    test("the heap budget keeps the reserve share of the heap limit free") {
        val budget = LatinPredictionEngines.heapCopyBudget()
        val runtime = Runtime.getRuntime()
        (budget >= 0) shouldBe true
        (budget <= runtime.maxMemory() - (runtime.maxMemory() * LatinPredictionEngines.HeapCopyReserveFraction).toLong()) shouldBe true
    }

    test("a mapped buffer that is no model fails without reading the stream") {
        var opened = false
        val result = runCatching {
            LatinPredictionEngines.loadNgramModel(
                "nl",
                map = { ByteBuffer.allocateDirect(64) },
                open = {
                    opened = true
                    ByteArrayInputStream(ByteArray(0))
                },
            )
        }
        result.isFailure shouldBe true
        opened shouldBe false
    }
})
