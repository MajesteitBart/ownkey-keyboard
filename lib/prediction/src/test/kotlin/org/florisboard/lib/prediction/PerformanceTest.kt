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

package org.florisboard.lib.prediction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.florisboard.lib.prediction.sim.BenchData
import org.florisboard.lib.prediction.sim.BenchText
import java.util.Locale
import kotlin.random.Random

/**
 * Load time and `suggest()` latency with two 150k-word languages. The bundled lists have 50k words, so each is
 * padded with 100k pseudo-words from a character trigram model of the real words (so the trie has realistic
 * branching). Thresholds are loose because the machine is shared; the printed numbers are the measurement.
 */
class PerformanceTest : FunSpec({
    fun synthesize(base: UnigramListModel, extra: Int, seed: Int): UnigramListModel {
        val words = (0 until base.vocabularySize).map { base.word(it) }
        val counts = words.indices.map { words[it] to (1_000_000_000.0 * kotlin.math.exp(base.unigramLogProb(it))).toLong().coerceAtLeast(2) }
        val next = HashMap<String, MutableList<Char>>()
        for (w in words) {
            val s = "^^" + TextFolding.keyForm(w) + "$"
            for (i in 2 until s.length) next.getOrPut(s.substring(i - 2, i)) { ArrayList() }.add(s[i])
        }
        val rnd = Random(seed)
        val seen = HashSet(words)
        val out = ArrayList(counts)
        var made = 0
        while (made < extra) {
            val sb = StringBuilder("^^")
            while (sb.length < 24) {
                val c = next[sb.substring(sb.length - 2)]?.let { it[rnd.nextInt(it.size)] } ?: '$'
                if (c == '$') break
                sb.append(c)
            }
            val w = sb.substring(2)
            if (w.length >= 2 && seen.add(w)) {
                made++
                out.add(w to (1 + 2L * extra / (made + 50)))
            }
        }
        return UnigramListModel.fromCounts(base.language, out)
    }

    test("150k-word lexicons load fast and suggest() stays within budget") {
        val nl = synthesize(TestModels.nl, 100_000, 1)
        val en = synthesize(TestModels.en, 100_000, 2)
        nl.vocabularySize shouldBeGreaterThan 145_000

        val buildTimes = (0 until 4).map {
            val t0 = System.nanoTime()
            Lexicon.build(nl)
            (System.nanoTime() - t0) / 1e6
        }
        println(String.format(Locale.ROOT, "lexicon build (%d words): cold %.1f ms, warm %.1f ms", nl.vocabularySize, buildTimes[0], buildTimes.drop(1).min()))

        val engine = PredictionEngine(listOf(nl, en))
        val corpus = BenchData.loadCorpus(TestModels.repoFile("tools/kbbench/corpus/dev.jsonl"))
        val taps = BenchData.loadTaps(TestModels.repoFile("tools/kbbench/taps_dev_ownkey.json")).sentences
        val calls = ArrayList<Pair<PredictionContext, String>>()
        for (s in corpus.filterIndexed { i, _ -> i % 2 == 0 }) {
            val words = s.words.map { it.lowercase() }
            for ((wi, w) in words.withIndex()) {
                val ctx = PredictionContext(words.take(wi))
                val letters = BenchText.typedForm(w)
                for (k in 0..letters.length) calls.add(ctx to letters.substring(0, k))
                taps[s.id]?.words?.getOrNull(wi)?.let { calls.add(ctx to it.literal) }
            }
        }
        for ((ctx, input) in calls.take(3000)) engine.suggest(ctx, input)
        val times = calls.map { (ctx, input) ->
            val t0 = System.nanoTime()
            engine.suggest(ctx, input)
            (System.nanoTime() - t0) / 1e6
        }.sorted()
        fun pct(p: Double) = times[((times.size - 1) * p).toInt()]
        println(String.format(Locale.ROOT, "suggest() over %d calls, nl+en 150k each: p50 %.2f ms, p95 %.2f ms, p99 %.2f ms, max %.2f ms",
            times.size, pct(0.5), pct(0.95), pct(0.99), times.last()))
        buildTimes.drop(1).min() shouldBeLessThan 1000.0
        pct(0.95) shouldBeLessThan 30.0
    }
})
