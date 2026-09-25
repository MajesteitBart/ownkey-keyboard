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
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual

/**
 * Writes the full benchmark report for each scorer to `app/build/reports/autocorrect-benchmark/`.
 */
class AutocorrectBenchmarkReportTest : FunSpec({
    test("legacy scorer report") {
        val report = runFullBenchmark(
            "Legacy scorer, default profile",
            AutocorrectBenchmark(LegacyLatinScorer(), BenchmarkPolicies.default()),
        )
        report.write("legacy.md").length().toInt() shouldBeGreaterThan 0
    }

    test("noisy-channel scorer report and Phase 1 floors") {
        val scorer = NoisyChannelLatinScorer()
        val report = runFullBenchmark(
            "Noisy-channel scorer, default settings",
            AutocorrectBenchmark(scorer, BenchmarkPolicies.default()),
            predictNextWord = { languages, text ->
                scorer.predictNextWords(languages, languages.first().locale, text, 3).map { it.word }
            },
        )
        report.write("noisy-channel.md").length().toInt() shouldBeGreaterThan 0

        // Regression floors, set just below the values measured when T-004 passed (see spec.md, Phase 1 gates).
        val typos = report.typoResults.associateBy { it.set }
        typos.getValue("tap EN usage, EN").rightPct shouldBeGreaterThanOrEqual 65.0
        typos.getValue("tap NL usage, NL").rightPct shouldBeGreaterThanOrEqual 60.0
        typos.getValue("tap NL usage, NL+EN").rightPct shouldBeGreaterThanOrEqual 55.0
        typos.getValue("real EN curated, EN").rightPct shouldBeGreaterThanOrEqual 60.0
        typos.getValue("real NL all, NL+EN").rightPct shouldBeGreaterThanOrEqual 75.0
        typos.getValue("tap EN usage, EN").top1Pct shouldBeGreaterThanOrEqual 85.0
        typos.getValue("tap NL usage, NL").top1Pct shouldBeGreaterThanOrEqual 85.0
        typos.values.filter { it.right + it.wrong >= 10 }.forEach { result ->
            withClue(result.set) { result.precisionPct shouldBeGreaterThanOrEqual 97.0 }
        }
        report.cleanResults.forEach { result ->
            withClue(result.set) { result.perThousand shouldBeLessThanOrEqual 0.5 }
        }
        report.oovResults.filter { it.set in setOf("oov.txt, NL+EN", "oov.txt, EN") }.forEach { result ->
            withClue(result.set) { result.changed shouldBeLessThanOrEqual 3 }
        }
        // Lowercase names and terms: caps at the values measured after T-012, so context cannot quietly add more.
        report.oovResults.filter { "lowercase" in it.set }.forEach { result ->
            withClue(result.set) { result.changed shouldBeLessThanOrEqual 10 }
        }

        // Phase 3 floors, just below the values measured when T-012 passed.
        typos.getValue("context EN, EN").rightPct shouldBeGreaterThanOrEqual 75.0
        typos.getValue("context NL, NL").rightPct shouldBeGreaterThanOrEqual 66.0
        typos.getValue("context NL, NL+EN").rightPct shouldBeGreaterThanOrEqual 65.0
        typos.getValue("context EN, NL+EN").rightPct shouldBeGreaterThanOrEqual 73.0
        val realWords = report.realWordResults.associateBy { it.set }
        realWords.getValue("real-word EN, EN").firstPct shouldBeGreaterThanOrEqual 60.0
        realWords.getValue("real-word NL, NL").firstPct shouldBeGreaterThanOrEqual 45.0
        report.realWordResults.forEach { result ->
            withClue("${result.set} is never autocorrected") { result.autoCorrected shouldBeLessThanOrEqual 0 }
        }
        report.cleanResults.forEach { result ->
            withClue("${result.set}: correctly typed word replaced as first suggestion") {
                result.firstChangedPct shouldBeLessThanOrEqual 2.0
            }
        }
        // Phase 4 floors (T-015), with tap positions: just below the values measured when the touch model landed.
        typos.getValue("tap EN usage, EN, with taps").rightPct shouldBeGreaterThanOrEqual 78.0
        typos.getValue("tap NL usage, NL, with taps").rightPct shouldBeGreaterThanOrEqual 77.0
        typos.getValue("tap NL usage, NL+EN, with taps").rightPct shouldBeGreaterThanOrEqual 73.0
        typos.getValue("context NL, NL, with taps").rightPct shouldBeGreaterThanOrEqual 73.0
        // T-016: two-edit candidates bring the intended word first often enough with taps.
        typos.getValue("tap EN usage, EN, with taps").top1Pct shouldBeGreaterThanOrEqual 92.0
        typos.getValue("tap NL usage, NL, with taps").top1Pct shouldBeGreaterThanOrEqual 92.0
        typos.values.filter { "with taps" in it.set && it.right + it.wrong >= 10 }.forEach { result ->
            withClue(result.set) { result.precisionPct shouldBeGreaterThanOrEqual 98.0 }
        }
        report.cleanResults.filter { "with taps" in it.set }.forEach { result ->
            withClue(result.set) { result.perThousand shouldBeLessThanOrEqual 0.3 }
        }
        // T-019: missed spaces.
        typos.getValue("run-together EN, EN").top1Pct shouldBeGreaterThanOrEqual 95.0
        typos.getValue("run-together NL, NL").top1Pct shouldBeGreaterThanOrEqual 70.0
        typos.getValue("run-together EN, EN").rightPct shouldBeGreaterThanOrEqual 75.0
        typos.getValue("run-together NL, NL").rightPct shouldBeGreaterThanOrEqual 65.0
        // T-013: word-pair predictions beat the frequency-only list they replace.
        val nextWord = report.nextWordResults.associateBy { it.set }
        for (language in listOf("EN", "NL")) {
            withClue("next word $language") {
                nextWord.getValue("next word $language, $language").top3Pct shouldBeGreaterThanOrEqual
                    nextWord.getValue("next word $language, $language, frequency only").top3Pct + 10.0
            }
        }
    }
})
