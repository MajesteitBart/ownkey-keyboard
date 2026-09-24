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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelParams
import io.kotest.core.spec.style.FunSpec
import java.io.File
import java.util.Locale

/**
 * Parameter sweep for the noisy-channel scorer. Opt-in: set AUTOCORRECT_TUNE=1. Writes
 * `build/reports/autocorrect-benchmark/tuning.tsv` with one line per configuration.
 */
class NoisyChannelTuningTest : FunSpec({
    test("parameter sweep").config(enabled = System.getenv("AUTOCORRECT_TUNE") == "1") {
        val s = BenchmarkSets
        val enOnly = BenchmarkData.enOnly()
        val nlOnly = BenchmarkData.nlOnly()
        val nlEn = BenchmarkData.nlEn()
        // Smaller slices keep the sweep fast; the full report test measures everything.
        val cleanEn = s.cleanEn.take(400)
        val cleanNl = s.cleanNl.take(400)
        val out = StringBuilder()
        out.appendLine(
            listOf(
                "unknownLogProb", "shortBonus", "subBase", "subDist", "omission", "threshold",
                "tapEnRight", "tapEnWrong", "tapEnUniRight", "tapEnUniWrong", "tapNlRight", "tapNlWrong",
                "tapNlUniRight", "tapNlUniWrong", "tapNlMixRight", "tapNlMixWrong",
                "realEnRight", "realEnWrong", "wikiRight", "wikiWrong", "realNlRight", "realNlWrong",
                "cleanEnPer1k", "cleanNlPer1k", "cleanNlMixPer1k", "oovChanged",
            ).joinToString("\t")
        )
        val unknownValues = System.getenv("TUNE_UNKNOWN")?.split(",")?.map { it.toDouble() } ?: listOf(-13.0, -14.5, -16.0)
        val thresholdValues = System.getenv("TUNE_THRESHOLD")?.split(",")?.map { it.toDouble() } ?: listOf(0.7, 0.8, 0.9)
        val subBaseValues = System.getenv("TUNE_SUBBASE")?.split(",")?.map { it.toDouble() } ?: listOf(3.5)
        val omissionValues = System.getenv("TUNE_OMISSION")?.split(",")?.map { it.toDouble() } ?: listOf(5.0)
        val shortValues = System.getenv("TUNE_SHORT")?.split(",")?.map { it.toDouble() } ?: listOf(1.0)
        for (unknown in unknownValues) for (short in shortValues) for (subBase in subBaseValues)
            for (omission in omissionValues) for (threshold in thresholdValues) {
                val params = NoisyChannelParams(
                    unknownWordLogProb = unknown,
                    shortInputLiteralBonus = short,
                    substitutionBaseCost = subBase,
                    omissionCost = omission,
                )
                val b = AutocorrectBenchmark(
                    NoisyChannelLatinScorer(params),
                    BenchmarkPolicies.default(),
                    settings = AutocorrectSettings(threshold = threshold),
                )
                val r = listOf(
                    b.evaluateTypos("", enOnly, s.tapEnUsage.pairs),
                    b.evaluateTypos("", enOnly, s.tapEnUniform.pairs),
                    b.evaluateTypos("", nlOnly, s.tapNlUsage.pairs),
                    b.evaluateTypos("", nlOnly, s.tapNlUniform.pairs),
                    b.evaluateTypos("", nlEn, s.tapNlUsage.pairs),
                    b.evaluateTypos("", enOnly, s.realEnCurated),
                    b.evaluateTypos("", enOnly, s.realEnWikipedia),
                    b.evaluateTypos("", nlOnly, s.realNlAll),
                )
                val c1 = b.evaluateCleanText("", enOnly, cleanEn)
                val c2 = b.evaluateCleanText("", nlOnly, cleanNl)
                val c3 = b.evaluateCleanText("", nlEn, cleanNl)
                val oov = b.evaluateOov("", nlEn, s.oov)
                val cells = mutableListOf(unknown, short, subBase, params.substitutionDistanceCost, omission, threshold)
                    .map { it.toString() }.toMutableList()
                r.forEach { cells.add(f(it.rightPct)); cells.add(f(it.wrongPct)) }
                cells.add(f(c1.perThousand)); cells.add(f(c2.perThousand)); cells.add(f(c3.perThousand))
                cells.add(oov.changed.toString())
                out.appendLine(cells.joinToString("\t"))
                println(cells.joinToString("\t"))
            }
        val dir = File("build/reports/autocorrect-benchmark").absoluteFile
        dir.mkdirs()
        File(dir, "tuning.tsv").writeText(out.toString())
    }
})

private fun f(value: Double) = String.format(Locale.ROOT, "%.1f", value)
