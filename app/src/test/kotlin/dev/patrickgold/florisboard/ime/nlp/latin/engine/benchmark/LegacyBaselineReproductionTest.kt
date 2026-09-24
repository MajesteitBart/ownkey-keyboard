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

package dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark

import dev.patrickgold.florisboard.ime.nlp.latin.engine.LegacyLatinScorer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * The extracted legacy scorer must reproduce the numbers the JavaScript harness measured on 2026-09-24
 * (`.project/projects/autocorrect-engine/research/baseline-2026-09-24.md`). The harness printed one decimal, so
 * values are compared with a 0.06 percentage point tolerance.
 */
class LegacyBaselineReproductionTest : FunSpec({
    val tolerance = 0.06
    val en = BenchmarkData.en()
    val nl = BenchmarkData.nl()
    val legacy = AutocorrectBenchmark(LegacyLatinScorer(), BenchmarkPolicies.default())
    val legacyChat = AutocorrectBenchmark(LegacyLatinScorer(), BenchmarkPolicies.chat())

    // Same draw order as bench.js.
    val generator = HarnessTypoGenerator(42)
    val sEN = generator.buildTypos(en.words, generator.sampleWords(en.words, 1500, tokenWeighted = true))
    val uEN = generator.buildTypos(en.words, generator.sampleWords(en.words, 1500, tokenWeighted = false))
    val sNL = generator.buildTypos(nl.words, generator.sampleWords(nl.words, 1500, tokenWeighted = true))
    val uNL = generator.buildTypos(nl.words, generator.sampleWords(nl.words, 1500, tokenWeighted = false))

    test("real-word error rates match") {
        (100 * sEN.realWordRate) shouldBe (25.8 plusOrMinus tolerance)
        (100 * uEN.realWordRate) shouldBe (6.8 plusOrMinus tolerance)
        (100 * sNL.realWordRate) shouldBe (22.2 plusOrMinus tolerance)
        (100 * uNL.realWordRate) shouldBe (5.8 plusOrMinus tolerance)
    }

    test("EN usage-weighted synthetic typos match") {
        val r = legacy.evaluateTypos("en", BenchmarkData.enOnly(), sEN.pairs, withContext = false)
        r.n shouldBe 1113
        r.rightPct shouldBe (0.0 plusOrMinus tolerance)
        r.wrongPct shouldBe (0.0 plusOrMinus tolerance)
        r.top1Pct shouldBe (67.0 plusOrMinus tolerance)
        r.top3Pct shouldBe (77.8 plusOrMinus tolerance)
        val chat = legacyChat.evaluateTypos("en chat", BenchmarkData.enOnly(), sEN.pairs, withContext = false)
        chat.rightPct shouldBe (0.8 plusOrMinus tolerance)
    }

    test("EN vocabulary-uniform synthetic typos match") {
        val r = legacy.evaluateTypos("en uniform", BenchmarkData.enOnly(), uEN.pairs, withContext = false)
        r.n shouldBe 1398
        r.rightPct shouldBe (0.0 plusOrMinus tolerance)
        r.top1Pct shouldBe (81.0 plusOrMinus tolerance)
        r.top3Pct shouldBe (91.9 plusOrMinus tolerance)
    }

    test("NL synthetic typos match, NL only and NL+EN") {
        val r = legacy.evaluateTypos("nl", BenchmarkData.nlOnly(), sNL.pairs, withContext = false)
        r.n shouldBe 1167
        r.top1Pct shouldBe (66.7 plusOrMinus tolerance)
        r.top3Pct shouldBe (74.0 plusOrMinus tolerance)
        val mixed = legacy.evaluateTypos("nl mixed", BenchmarkData.nlEn(), sNL.pairs, withContext = false)
        mixed.top1Pct shouldBe (60.4 plusOrMinus tolerance)
        mixed.top3Pct shouldBe (71.0 plusOrMinus tolerance)
        mixed.dictPct shouldBe (4.5 plusOrMinus tolerance)
        val enMixed = legacy.evaluateTypos("en mixed", BenchmarkData.nlEn(), sEN.pairs, withContext = false)
        enMixed.top1Pct shouldBe (64.7 plusOrMinus tolerance)
        enMixed.top3Pct shouldBe (75.8 plusOrMinus tolerance)
        enMixed.dictPct shouldBe (2.0 plusOrMinus tolerance)
    }

    test("real-world lists match") {
        val enReal = legacy.evaluateTypos("en real", BenchmarkData.enOnly(), BenchmarkData.pairs("real_en_curated.tsv"), withContext = false)
        enReal.n shouldBe 100
        enReal.top1Pct shouldBe (64.0 plusOrMinus tolerance)
        enReal.top3Pct shouldBe (76.0 plusOrMinus tolerance)
        enReal.dictPct shouldBe (13.0 plusOrMinus tolerance)
        val apostrophes = legacy.evaluateTypos("en apos", BenchmarkData.enOnly(), BenchmarkData.pairs("apostrophes_en.tsv"), withContext = false)
        apostrophes.n shouldBe 14
        apostrophes.dictPct shouldBe (100.0 plusOrMinus tolerance)
        apostrophes.top3Pct shouldBe (0.0 plusOrMinus tolerance)
        val nlReal = legacy.evaluateTypos("nl real", BenchmarkData.nlOnly(), BenchmarkData.pairs("real_nl_curated.tsv"), withContext = false)
        nlReal.n shouldBe 60
        nlReal.top1Pct shouldBe (70.0 plusOrMinus tolerance)
        nlReal.top3Pct shouldBe (88.3 plusOrMinus tolerance)
        nlReal.dictPct shouldBe (8.3 plusOrMinus tolerance)
        val nlRealMixed = legacy.evaluateTypos("nl real mixed", BenchmarkData.nlEn(), BenchmarkData.pairs("real_nl_curated.tsv"), withContext = false)
        nlRealMixed.top1Pct shouldBe (66.7 plusOrMinus tolerance)
        nlRealMixed.top3Pct shouldBe (86.7 plusOrMinus tolerance)
    }

    test("harness out-of-dictionary words are untouched") {
        val r = legacy.evaluateOov("oov", BenchmarkData.nlEn(), BenchmarkData.words("oov_harness.txt"))
        r.n shouldBe 57
        r.inDictionary shouldBe 22
        r.changed shouldBe 0
    }

    test("completion reach matches") {
        // bench.js draws this sample after the four typo sets above.
        var reach = 0
        var oracle = 0
        val sample = generator.sampleWords(en.words, 800, tokenWeighted = false, minLen = 8)
        for (word in sample) {
            val prefix = word.substring(0, 5)
            val top3 = legacy.score(BenchmarkData.enOnly(), prefix, "").take(3).map { it.word }
            if (word in top3) reach++
            val oracleTop3 = en.words.entries
                .filter { it.key.startsWith(prefix) && it.key != prefix }
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
            if (word in oracleTop3) oracle++
        }
        (100.0 * reach / sample.size) shouldBe (53.0 plusOrMinus tolerance)
        (100.0 * oracle / sample.size) shouldBe (79.8 plusOrMinus tolerance)
    }

    test("most aggressive sliders match maxslider.js") {
        val maxGenerator = HarnessTypoGenerator(7)
        val max = AutocorrectBenchmark(LegacyLatinScorer(), BenchmarkPolicies.max())
        val enPairs = maxGenerator.maxSliderSubstitutions(en.words, 1500)
        val nlPairs = maxGenerator.maxSliderSubstitutions(nl.words, 1500)
        val enResult = max.evaluateTypos("en max", BenchmarkData.enOnly(), enPairs, withContext = false)
        val nlResult = max.evaluateTypos("nl max", BenchmarkData.nlOnly(), nlPairs, withContext = false)
        enResult.rightPct shouldBe (2.9 plusOrMinus tolerance)
        enResult.wrongPct shouldBe (0.8 plusOrMinus tolerance)
        nlResult.rightPct shouldBe (2.5 plusOrMinus tolerance)
        nlResult.wrongPct shouldBe (0.9 plusOrMinus tolerance)
    }
})
