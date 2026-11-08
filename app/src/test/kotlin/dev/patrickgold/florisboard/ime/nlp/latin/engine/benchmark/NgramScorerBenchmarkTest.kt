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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinCurrentWordScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramTestEngines
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.RealWordRule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/**
 * The full in-repo benchmark of the autocorrect rebuild (PR #17) for the n-gram scorer next to the noisy-channel
 * scorer, plus the n-gram engine without the AC-011 confusion-pair rule (devtools "engine only"). Writes
 * `ngram.md`, `ngram-real-words.md` and `compare-ngram-noisy-channel.md` to `app/build/reports/autocorrect-benchmark/`.
 * The comparison is a report; AC-011 is enforced in NgramLatinScorerTest.
 */
class NgramScorerBenchmarkTest : FunSpec({
    fun nextWords(scorer: NgramLatinScorer): suspend (List<dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage>, String) -> List<String> =
        { languages, text -> scorer.predictNextWords(languages, text, true, 3)?.map { it.word }.orEmpty() }

    // Three full benchmark runs and the strength sets: far longer than Kotest's default 10 minutes.
    test("n-gram scorer report next to the noisy-channel scorer").config(timeout = 90.minutes) {
        val noisy = NoisyChannelLatinScorer()
        val ngram = NgramTestEngines.scorer(fallback = NoisyChannelLatinScorer())
        val realWords = NgramTestEngines.scorer(fallback = NoisyChannelLatinScorer(), realWordRule = RealWordRule.NONE)

        val noisyReport = runFullBenchmark(
            "Noisy-channel scorer, default settings",
            AutocorrectBenchmark(noisy, BenchmarkPolicies.default()),
            predictNextWord = { languages, text -> noisy.predictNextWords(languages, languages.first().locale, text, 3).map { it.word } },
        )
        val ngramReport = runFullBenchmark(
            "N-gram scorer, default settings",
            AutocorrectBenchmark(ngram, BenchmarkPolicies.default()),
            predictNextWord = nextWords(ngram),
        )
        ngramReport.write("ngram.md").length().toInt() shouldBeGreaterThan 0
        val realWordReport = runFullBenchmark(
            "N-gram scorer, engine only (devtools; no AC-011 confusion-pair rule)",
            AutocorrectBenchmark(realWords, BenchmarkPolicies.default()),
            predictNextWord = nextWords(realWords),
        )
        realWordReport.write("ngram-real-words.md")

        val strengths = AutocorrectStrength.entries.flatMap { strength ->
            listOf("noisy channel" to noisy, "n-gram" to ngram).map { (name, scorer) ->
                strengthRow(name, scorer, strength)
            }
        }
        val comparison = renderComparison(noisyReport, ngramReport, realWordReport, strengths)
        val dir = File("build/reports/autocorrect-benchmark").absoluteFile.also { it.mkdirs() }
        File(dir, "compare-ngram-noisy-channel.md").writeText(comparison)
        println(comparison)

        // AC-011 as written (no confusion-pair word replaced by its partner) is enforced per case in NgramLatinScorerTest;
        // a replacement by a word outside the pair is the engine's call and only reported here.
        ngramReport.realWordResults.size shouldBeGreaterThan 0
    }
})

private class StrengthRow(val scorer: String, val strength: AutocorrectStrength, val usage: List<TypoSetResult>, val rare: List<TypoSetResult>)

private suspend fun strengthRow(name: String, scorer: LatinCurrentWordScorer, strength: AutocorrectStrength): StrengthRow {
    val s = BenchmarkSets
    val b = AutocorrectBenchmark(scorer, BenchmarkPolicies.default(), settings = strength.toSettings(enabled = true))
    val usage = listOf(
        b.evaluateTypos("EN", BenchmarkData.enOnly(), s.tapEnUsage.pairs),
        b.evaluateTypos("NL", BenchmarkData.nlOnly(), s.tapNlUsage.pairs),
        b.evaluateTypos("NL+EN", BenchmarkData.nlEn(), s.tapNlUsage.pairs),
    )
    val rare = listOf(
        b.evaluateTypos("EN rare", BenchmarkData.enOnly(), s.tapEnUniform.pairs),
        b.evaluateTypos("NL rare", BenchmarkData.nlOnly(), s.tapNlUniform.pairs),
    )
    return StrengthRow(name, strength, usage, rare)
}

private fun f(value: Double, decimals: Int = 1) = String.format(Locale.ROOT, "%.${decimals}f", value)

private fun renderComparison(
    noisy: BenchmarkReport,
    ngram: BenchmarkReport,
    realWords: BenchmarkReport,
    strengths: List<StrengthRow>,
): String = buildString {
    appendLine("# Autocorrect benchmark: noisy-channel vs n-gram scorer")
    appendLine()
    appendLine("Right / wrong: share of the set autocorrected to the intended word / to another word. Prec.: right / (right + wrong).")
    appendLine("\"N-gram RW\" is the n-gram engine without the AC-011 confusion-pair rule (devtools \"Engine only\").")
    appendLine()
    appendLine("| Set | n | Noisy right | Noisy wrong | Noisy prec. | Noisy top-1 | N-gram right | N-gram wrong | N-gram prec. | N-gram top-1 | N-gram RW right | N-gram RW wrong | N-gram p95 us |")
    appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    val ngramTypos = ngram.typoResults.associateBy { it.set }
    val rwTypos = realWords.typoResults.associateBy { it.set }
    for (a in noisy.typoResults) {
        val b = ngramTypos[a.set] ?: continue
        val c = rwTypos[a.set]
        appendLine(
            "| ${a.set} | ${a.n} | ${f(a.rightPct)} | ${f(a.wrongPct)} | ${f(a.precisionPct)} | ${f(a.top1Pct)} | " +
                "${f(b.rightPct)} | ${f(b.wrongPct)} | ${f(b.precisionPct)} | ${f(b.top1Pct)} | " +
                "${c?.let { f(it.rightPct) } ?: "-"} | ${c?.let { f(it.wrongPct) } ?: "-"} | ${b.p95Micros} |"
        )
    }
    appendLine()
    appendLine("| Clean text | Words | Noisy false / 1,000 | N-gram false / 1,000 | N-gram RW false / 1,000 | Noisy known not first | N-gram known not first |")
    appendLine("| --- | --- | --- | --- | --- | --- | --- |")
    val ngramClean = ngram.cleanResults.associateBy { it.set }
    val rwClean = realWords.cleanResults.associateBy { it.set }
    for (a in noisy.cleanResults) {
        val b = ngramClean[a.set] ?: continue
        val c = rwClean[a.set]
        appendLine(
            "| ${a.set} | ${a.words} | ${f(a.perThousand, 2)} | ${f(b.perThousand, 2)} | ${c?.let { f(it.perThousand, 2) } ?: "-"} | " +
                "${f(a.firstChangedPct, 2)}% | ${f(b.firstChangedPct, 2)}% |"
        )
    }
    appendLine()
    appendLine("| Out-of-dictionary set | n | Noisy changed | N-gram changed | N-gram RW changed |")
    appendLine("| --- | --- | --- | --- | --- |")
    val ngramOov = ngram.oovResults.associateBy { it.set }
    val rwOov = realWords.oovResults.associateBy { it.set }
    for (a in noisy.oovResults) {
        val b = ngramOov[a.set] ?: continue
        appendLine("| ${a.set} | ${a.n} | ${a.changed} | ${b.changed} | ${rwOov[a.set]?.changed ?: "-"} |")
    }
    appendLine()
    appendLine("| Real-word errors | n | Noisy intended first | N-gram intended first | Noisy autocorrected | N-gram autocorrected | N-gram RW autocorrected |")
    appendLine("| --- | --- | --- | --- | --- | --- | --- |")
    val ngramReal = ngram.realWordResults.associateBy { it.set }
    val rwReal = realWords.realWordResults.associateBy { it.set }
    for (a in noisy.realWordResults) {
        val b = ngramReal[a.set] ?: continue
        appendLine("| ${a.set} | ${a.n} | ${f(a.firstPct)} | ${f(b.firstPct)} | ${a.autoCorrected} | ${b.autoCorrected} | ${rwReal[a.set]?.autoCorrected ?: "-"} |")
    }
    appendLine()
    appendLine("| Next word | Positions | Noisy first | Noisy first 3 | N-gram first | N-gram first 3 |")
    appendLine("| --- | --- | --- | --- | --- | --- |")
    val ngramNext = ngram.nextWordResults.associateBy { it.set }
    for (a in noisy.nextWordResults) {
        val b = ngramNext[a.set] ?: continue
        if ("frequency only" in a.set) continue
        appendLine("| ${a.set} | ${a.n} | ${f(a.firstPct)} | ${f(a.top3Pct)} | ${f(b.firstPct)} | ${f(b.top3Pct)} |")
    }
    appendLine()
    appendLine("| Strength | Scorer | Usage right EN / NL / NL+EN | Usage precision EN / NL / NL+EN | Rare wrong EN / NL |")
    appendLine("| --- | --- | --- | --- | --- |")
    for (row in strengths) {
        appendLine(
            "| ${row.strength} | ${row.scorer} | ${row.usage.joinToString(" / ") { f(it.rightPct) }} | " +
                "${row.usage.joinToString(" / ") { f(it.precisionPct) }} | ${row.rare.joinToString(" / ") { f(it.wrongPct) }} |"
        )
    }
}
