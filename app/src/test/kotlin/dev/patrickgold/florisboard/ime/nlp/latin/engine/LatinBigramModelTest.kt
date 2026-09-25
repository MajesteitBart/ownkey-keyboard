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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.math.exp
import kotlin.math.ln

class LatinBigramModelTest : FunSpec({
    val sample = LatinBigramModel.parse(
        sequenceOf(
            "# ownkey-latin-bigrams v2 format=log100",
            "# language=nl",
            "@words 6",
            "<s>", "ben", "heb", "hij", "ik", "wil",
            "@pairs",
            "0\t3 100 1 200",
            "4\t1 300 1 250 3 100",
            "@notpredicted",
            "hij",
        )
    )

    test("pair counts and probabilities") {
        sample.count("ik", "ben") shouldBe (exp(3.0) plusOrMinus 1e-9)
        sample.count("ik", "zag") shouldBe 0.0
        sample.count("jij", "bent") shouldBe 0.0
        sample.totalAfter("ik") shouldBe ((exp(3.0) + exp(2.5) + exp(1.0)) plusOrMinus 1e-4)
        sample.distinctAfter("ik") shouldBe 3
        sample.logProbability("ik", "heb")!! shouldBe (ln(exp(2.5) / sample.totalAfter("ik")) plusOrMinus 1e-6)
        sample.logProbability("ik", "zag").shouldBeNull()
        sample.count(LatinBigramModel.SentenceStart, "hij") shouldBe (exp(1.0) plusOrMinus 1e-9)
    }

    test("successors come most frequent first") {
        sample.successors("ik", 2).map { it.first } shouldBe listOf("ben", "heb")
        sample.successors("ik", 10).map { it.first } shouldBe listOf("ben", "heb", "wil")
        sample.successors("jij", 3) shouldBe emptyList()
        sample.successors(LatinBigramModel.SentenceStart, 2).map { it.first } shouldBe listOf("ik", "hij")
        sample.successors(LatinBigramModel.SentenceStart, 2, predictableOnly = true).map { it.first } shouldBe listOf("ik")
    }

    test("malformed files are rejected") {
        shouldThrow<IllegalArgumentException> { LatinBigramModel.parse(sequenceOf("@words 1", "ik", "@pairs")) }
        shouldThrow<IllegalArgumentException> {
            LatinBigramModel.parse(sequenceOf("# ownkey-latin-bigrams v1 format=log100", "ik\tben 300"))
        }
        shouldThrow<IllegalArgumentException> {
            LatinBigramModel.parse(sequenceOf("# ownkey-latin-bigrams v2 format=log100", "@words 2", "ik", "ben", "@pairs"))
        }
        shouldThrow<IllegalArgumentException> {
            LatinBigramModel.parse(sequenceOf("# ownkey-latin-bigrams v2 format=log100", "@words 2", "ben", "ik", "@pairs", "1\t5 100"))
        }
    }

    test("a list cut off before its pairs is rejected, not loaded without context") {
        val header = "# ownkey-latin-bigrams v2 format=log100"
        shouldThrow<IllegalArgumentException> { LatinBigramModel.parse(sequenceOf(header, "@words 2", "ben", "ik", "@pairs")) }
        shouldThrow<IllegalArgumentException> { LatinBigramModel.parse(sequenceOf(header, "@words 2", "ben", "ik")) }
        LatinBigramModel.parse(sequenceOf(header)).isEmpty() shouldBe true
    }

    test("shipped bigram lists load, fit the heap budget and know common pairs") {
        var totalBytes = 0L
        for (code in listOf("en", "nl")) {
            val file = File(BenchmarkData.dictionaryDir.parentFile, "latin/$code.bigrams.txt")
            System.gc()
            val runtime = Runtime.getRuntime()
            val before = runtime.totalMemory() - runtime.freeMemory()
            val start = System.nanoTime()
            val model = file.bufferedReader().useLines { LatinBigramModel.parse(it) }
            val millis = (System.nanoTime() - start) / 1_000_000
            System.gc()
            val bytes = runtime.totalMemory() - runtime.freeMemory() - before
            totalBytes += bytes
            println("$code bigrams: ${model.pairCount} pairs, ${bytes / 1_000_000} MB, $millis ms")
            model.pairCount shouldBeGreaterThan 50_000
        }
        println("bigram heap, EN and NL together: ${totalBytes / 1_000_000} MB")
        (totalBytes / 1_000_000.0) shouldBeLessThan 8.0

        val en = BenchmarkData.en().bigrams
        en.successors("rather", 1).first().first shouldBe "than"
        val nl = BenchmarkData.nl().bigrams
        nl.successors("ik", 5).map { it.first } shouldContainAll listOf("ben", "heb")
        // Held-out and benchmark sentences never enter the counts, but common pairs do.
        (nl.count("ik", "ben") > 0.0) shouldBe true
    }
})
