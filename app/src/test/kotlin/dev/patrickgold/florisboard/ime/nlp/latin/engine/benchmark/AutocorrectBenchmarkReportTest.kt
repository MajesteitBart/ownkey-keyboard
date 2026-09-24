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
        val report = runFullBenchmark(
            "Noisy-channel scorer, default settings",
            AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default()),
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
        report.oovResults.forEach { result ->
            withClue(result.set) { result.changed shouldBeLessThanOrEqual 3 }
        }
    }
})
