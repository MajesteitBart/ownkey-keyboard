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

package dev.patrickgold.florisboard.ime.nlp.latin

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringHooks
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramTestEngines
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.PossiblyOffensiveWords
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import org.florisboard.lib.prediction.TextFolding
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/** Shared by the replay test and the one-off sweeps: the repository, the offensive lists, the engine, the report. */
internal object ReplaySupport {
    val repoRoot: File by lazy {
        listOf(File("."), File("..")).map { it.absoluteFile.normalize() }.first { File(it, "tools/kbbench/sim").isDirectory }
    }
    val replay by lazy { PipelineReplay(repoRoot) }

    /** Every list the app loads (`ime/dict/offensive/`*.txt). */
    val blocked: Set<String> by lazy {
        File(repoRoot, "app/src/main/assets/${PossiblyOffensiveWords.AssetDir}").listFiles { f -> f.extension == "txt" }.orEmpty()
            .flatMapTo(HashSet()) { f -> f.bufferedReader().useLines { PossiblyOffensiveWords.parse(it) } }
    }

    /** The phone's default subtype for the benchmark: Dutch with English, on main's shipped word lists. */
    val languages by lazy { BenchmarkData.nlEn() }

    fun f(v: Double, d: Int = 3) = String.format(Locale.ROOT, "%.${d}f", v)

    class Row(val name: String, val perLang: Map<String, PipelineReplay.Totals>) {
        val overall = PipelineReplay.Totals().also { t -> perLang.values.forEach { t.add(it) } }
    }

    suspend fun runPipeline(name: String, scorer: NgramLatinScorer, touches: Boolean, hooks: LatinScoringHooks = LatinScoringHooks.None): Row {
        val per = LinkedHashMap<String, PipelineReplay.Totals>()
        for (plan in replay.plans) for (s in plan.sentences) {
            per.getOrPut(s.lang) { PipelineReplay.Totals() }.add(replay.pipeline(scorer, languages, s, touches, blocked, hooks = hooks))
        }
        return Row(name, per)
    }

    fun runEngine(touches: Boolean): Row {
        val engine = NgramTestEngines.engineFor(listOf("nl", "en"))!!.withGeometry(replay.spatial)
        val per = LinkedHashMap<String, PipelineReplay.Totals>()
        for (plan in replay.plans) for (s in plan.sentences) {
            per.getOrPut(s.lang) { PipelineReplay.Totals() }.add(replay.engineAlone(engine, s, touches, blocked))
        }
        return Row("engine alone (simulator)", per)
    }

    /** Literal checks through the pipeline's space decision: keep rate, switch rate and the failed keeps. */
    suspend fun literalChecks(scorer: NgramLatinScorer): Triple<Double, Double, List<String>> {
        var keep = 0; var keepOk = 0; var switch = 0; var switchOk = 0
        val failures = ArrayList<String>()
        val settings = AutocorrectStrength.NORMAL.toSettings(enabled = true)
        for (c in replay.literalChecks) {
            val before = if (c.context.isEmpty()) "" else c.context.joinToString(" ") + " "
            val out = replay.commitOnSpace(scorer, languages, c.input, before + c.input, null, settings, LatinScoringHooks.None, blocked)
                ?: c.input
            val ok = TextFolding.normalizeWord(out) == TextFolding.normalizeWord(c.expect)
            if (c.isKeep) {
                keep++; if (ok) keepOk++ else failures.add("${c.context.joinToString(" ")} | ${c.input} -> $out")
            } else {
                switch++; if (ok) switchOk++
            }
        }
        return Triple(100.0 * keepOk / keep, 100.0 * switchOk / switch, failures)
    }

    fun table(title: String, rows: List<Row>): String = buildString {
        appendLine("### $title")
        appendLine()
        val langs = rows.first().perLang.keys.toList()
        appendLine("| Run | WER overall | " + langs.joinToString(" | ") { "WER $it" } + " | False corrections | Typos fixed | Revisions |")
        appendLine("| --- | --- | " + langs.joinToString(" | ") { "---" } + " | --- | --- | --- |")
        for (r in rows) {
            appendLine(
                "| ${r.name} | ${f(r.overall.wer)}% | " + langs.joinToString(" | ") { "${f(r.perLang.getValue(it).wer)}%" } +
                    " | ${r.overall.falseCorrections} | ${r.overall.fixed} / ${r.overall.typos} | ${r.overall.revisions} |"
            )
        }
        appendLine()
    }

    fun write(name: String, text: String) {
        val dir = File(repoRoot, "app/build/reports/autocorrect-benchmark").also { it.mkdirs() }
        File(dir, name).writeText(text)
        println(text)
    }
}

/**
 * The full app pipeline on the kbbench dev split next to the engine alone (the simulator's noisy typist), per language,
 * with key codes and with taps. Writes `pipeline-replay.md` to `app/build/reports/autocorrect-benchmark/`. The floor
 * keeps main's layers from costing the engine's quality again: the pipeline may be at most 0.1 percentage points worse.
 */
class LatinPipelineReplayTest : FunSpec({
    test("the app pipeline matches the engine alone on the dev split").config(timeout = 60.minutes) {
        val s = ReplaySupport
        val scorer = NgramTestEngines.scorer(fallback = NoisyChannelLatinScorer())
        val report = StringBuilder("# Pipeline replay on the kbbench dev split (5 tap seeds, Dutch + English subtype)\n\n")
        for (touches in listOf(false, true)) {
            val engine = s.runEngine(touches)
            val pipeline = s.runPipeline("app pipeline (default)", scorer, touches)
            report.append(s.table(if (touches) "With taps" else "Key codes", listOf(engine, pipeline)))
            withClue("pipeline WER ${pipeline.overall.wer} vs engine ${engine.overall.wer}, taps=$touches") {
                pipeline.overall.wer shouldBeLessThanOrEqual engine.overall.wer + 0.1
            }
        }
        val (keep, switch, failures) = s.literalChecks(scorer)
        report.append("Literal checks through the space decision: keep ${s.f(keep, 1)}%, switch ${s.f(switch, 1)}%.\n\n")
        report.append("Kept words changed: ${failures.joinToString("; ")}\n")
        s.write("pipeline-replay.md", report.toString())
    }
})
