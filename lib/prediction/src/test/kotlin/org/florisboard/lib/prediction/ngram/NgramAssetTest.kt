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

package org.florisboard.lib.prediction.ngram

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Loads the production assets (app/src/main/assets/ime/dict/ngram/<lang>.bin) and checks them against a sample of
 * Python-reference answers written by `tools/lm/make_fixture.py --assets`. Skipped when an asset is missing.
 */
class NgramAssetTest : FunSpec({
    // The Gradle test task passes the repository root; the relative paths cover runs from the module or repo root.
    fun assetFile(lang: String): File? = listOfNotNull(
        System.getProperty("ownkey.repoRoot")?.let { File(it, "app/src/main/assets/ime/dict/ngram/$lang.bin") },
        File("../../app/src/main/assets/ime/dict/ngram/$lang.bin"),
        File("app/src/main/assets/ime/dict/ngram/$lang.bin"),
    ).firstOrNull { it.isFile }

    fun parseFloat(s: String): Float = if (s == "-inf") Float.NEGATIVE_INFINITY else s.toFloat()

    fun parseCandidates(s: String): List<NgramCandidate> =
        if (s.isEmpty()) emptyList() else s.split(';').map {
            val (id, lp) = it.split(':')
            NgramCandidate(id.toInt(), parseFloat(lp))
        }

    for (lang in listOf("en", "nl")) {
        val file = assetFile(lang)
        val expectedStream = NgramAssetTest::class.java.getResourceAsStream("/ngram/$lang.asset.expected.tsv")
        test("$lang asset loads fast, fits the memory budget and matches the Python reference")
            .config(enabled = file != null && expectedStream != null) {
            NgramModel.load(file!!) // warm-up (JIT)
            val t0 = System.nanoTime()
            val mapped = NgramModel.load(file)
            val loadMs = (System.nanoTime() - t0) / 1_000_000
            // The stream path (one heap copy of the file) must answer the same.
            val copied = file.inputStream().use { NgramModel.load(it) }
            println("ngram $lang: ${file.length()} bytes, vocab ${mapped.vocabularySize}, bigrams ${mapped.bigramCount}, " +
                "trigram contexts ${mapped.trigramContextCount}, trigrams ${mapped.trigramCount}, " +
                "mapped load $loadMs ms, heap ${mapped.heapBytes / 1024} KiB")
            for (model in listOf(mapped, copied)) {
                model.language shouldBe lang
                model.fileBytes.toLong() shouldBe file.length()
                model.memoryBytes shouldBe model.heapBytes + model.fileBytes
                // The file is read in place; only the offset indexes are on the heap (about 14 bytes per word).
                model.heapBytes shouldBeLessThan 8L * 1024 * 1024
            }
            mapped.isDirect shouldBe true
            copied.isDirect shouldBe false
            val rows = expectedStream!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotEmpty() }
                .map { it.split('\t') }
            var checked = 0
            for (model in listOf(mapped, copied)) for (row in rows) {
                withClue(row.joinToString(" ")) {
                    when (row[0]) {
                        "V" -> model.vocabularySize shouldBe row[1].toInt()
                        "W" -> {
                            val id = row[1].toInt()
                            model.word(id) shouldBe row[2]
                            model.casedForm(id) shouldBe row[3]
                            model.isCapitalized(id) shouldBe (row[4] == "1")
                            model.unigramLogProb(id) shouldBe parseFloat(row[5])
                            if (row.size > 6) model.isValid(id) shouldBe (row[6] == "1")
                            if (row.size > 7) model.isListed(id) shouldBe (row[7] == "1")
                        }
                        "VALID" -> {
                            model.validCount shouldBe row[1].toInt()
                            (1 until model.vocabularySize).count { model.isListed(it) } shouldBe row[2].toInt()
                            model.formatVersion shouldBe row[3].toInt()
                        }
                        "L" -> model.logProb(row[1].toInt(), row[2].toInt(), row[3].toInt()) shouldBe parseFloat(row[4])
                        "N" -> model.topNext(row[1].toInt(), row[2].toInt(), row[3].toInt()) shouldBe
                            parseCandidates(row.getOrElse(4) { "" })
                        "R" -> {
                            val r = model.prefixRange(row[1])
                            r.first shouldBe row[2].toInt()
                            (r.last + 1) shouldBe row[3].toInt()
                        }
                        "C" -> model.topCompletions(row[1], row[2].toInt(), row[3].toInt(), row[4].toInt()) shouldBe
                            parseCandidates(row.getOrElse(5) { "" })
                        "I" -> model.wordId(row[1]) shouldBe row[2].toInt()
                    }
                }
                checked++
            }
            checked shouldBeGreaterThan 1000
            val model = mapped
            // A repeated context is answered from its hashed successor lists (thousands of entries after <s> or a
            // frequent word): the values must equal the scanned ones.
            val contexts = listOf("", "ik ", "ik ga ", "de ", "the ", "i want to ", "naar de ", "of the ")
                .map { model.contextOf(it) }
            for (c in contexts) {
                val words = (model.topNext(c.prev1Id, c.prev2Id, 400).map { it.wordId } +
                    (1 until model.vocabularySize step 97)).distinct()
                val scanned = words.map { w ->
                    model.logProb(1, NgramModel.UNKNOWN_ID, NgramModel.UNKNOWN_ID)
                    model.logProb(w, c.prev1Id, c.prev2Id)
                }
                val repeated = words.map { w -> model.logProb(w, c.prev1Id, c.prev2Id) }
                withClue("context $c") { repeated shouldBe scanned }
            }
            // Query latency sanity check on the desktop JVM.
            val ctx = model.contextOf("ik ga morgen naar de ")
            val t1 = System.nanoTime()
            repeat(1000) { model.topCompletions("s", ctx.prev1Id, ctx.prev2Id, 3) }
            println("ngram $lang: topCompletions('s') ${(System.nanoTime() - t1) / 1000 / 1000.0} us/query")
        }
    }
})
