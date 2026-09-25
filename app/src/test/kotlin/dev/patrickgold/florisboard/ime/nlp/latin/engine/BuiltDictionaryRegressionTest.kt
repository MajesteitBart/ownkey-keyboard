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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Checks that the built dictionaries (T-007) keep what other features rely on.
 */
class BuiltDictionaryRegressionTest : FunSpec({
    fun spearman(a: List<Double>, b: List<Double>): Double {
        fun ranks(values: List<Double>): DoubleArray {
            val order = values.indices.sortedBy { values[it] }
            val r = DoubleArray(values.size)
            order.forEachIndexed { rank, index -> r[index] = rank.toDouble() }
            return r
        }
        val ra = ranks(a)
        val rb = ranks(b)
        val n = a.size.toDouble()
        val d2 = ra.indices.sumOf { (ra[it] - rb[it]) * (ra[it] - rb[it]) }
        return 1 - 6 * d2 / (n * (n * n - 1))
    }

    test("glide keeps the frequency order of common words") {
        // Glide ranks gesture candidates with frequency / maxFrequency from the same model.
        for (code in listOf("en", "nl")) {
            val language = BenchmarkData.language(code)
            val raw = language.rawModel
            val built = language.model
            val common = raw.words.entries.sortedByDescending { it.value }.take(5000).map { it.key }
                .filter { built.isKnown(it) }
            val rawScores = common.map { raw.words.getValue(it).toDouble() / raw.maxFrequency }
            val builtScores = common.map { built.words.getValue(it).toDouble() / built.maxFrequency }
            val rho = spearman(rawScores, builtScores)
            println("$code: ${common.size} of the top 5,000 kept, Spearman $rho")
            rho shouldBeGreaterThanOrEqual 0.99
            common.size shouldBeGreaterThan 4500
        }
    }

    test("languages without a built dictionary still load the legacy data.json") {
        val file = File(BenchmarkData.dictionaryDir.parentFile, "data.json")
        val raw = Json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), file.readText())
        val words = raw.mapKeys { LatinText.normalizeDictionaryWord(it.key) }.filterKeys { it.isNotBlank() }
        val model = LatinWordModel.build(words)
        model.words.size shouldBeGreaterThan 10_000
        model.isKnown("the").shouldBeTrue()
    }

    test("the built format round-trips counts within 1 percent") {
        val parsed = LatinText.parseDictionary(
            sequenceOf("# ownkey-latin-dictionary v1 format=log100", "# note", "the\t${Math.round(100 * Math.log(22_748_278.0))}")
        )
        val count = parsed.getValue("the").toDouble()
        (count / 22_748_278.0) shouldBeGreaterThanOrEqual 0.99
        (count / 22_748_278.0) shouldBeLessThanOrEqual 1.01
        LatinText.parseDictionary(sequenceOf("hello 42")) shouldBe mapOf("hello" to 42)
    }

    test("built models have fewer words than the raw models") {
        // The heap and build time are printed for information only: a heap delta around System.gc() is too noisy to
        // assert on. The device measurement is in the T-007 evidence log; LatinBigramModelTest gates the bigram heap.
        fun measure(build: () -> LatinWordModel): Pair<Long, Long> {
            System.gc()
            val runtime = Runtime.getRuntime()
            val before = runtime.totalMemory() - runtime.freeMemory()
            val start = System.nanoTime()
            val model = build()
            val millis = (System.nanoTime() - start) / 1_000_000
            System.gc()
            val bytes = runtime.totalMemory() - runtime.freeMemory() - before
            check(model.words.isNotEmpty())
            return bytes to millis
        }
        for (code in listOf("en", "nl")) {
            val language = BenchmarkData.language(code)
            val (rawBytes, rawMs) = measure { LatinWordModel.build(language.words) }
            val (builtBytes, builtMs) = measure { LatinWordModel.build(language.shippedWords) }
            println("$code: raw ${language.words.size} words ${rawBytes / 1_000_000} MB ${rawMs} ms; built ${language.shippedWords.size} words ${builtBytes / 1_000_000} MB ${builtMs} ms")
            (language.shippedWords.size < language.words.size).shouldBeTrue()
        }
    }
})
