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
import io.kotest.matchers.ints.shouldBeGreaterThan

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
})
