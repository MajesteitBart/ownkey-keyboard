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

package org.florisboard.lib.prediction.sim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.florisboard.lib.prediction.EngineConfig
import org.florisboard.lib.prediction.PredictionContext
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.SpatialModel
import org.florisboard.lib.prediction.TextFolding
import org.florisboard.lib.prediction.WordBlocklist
import org.florisboard.lib.prediction.WordLanguageModel
import java.io.File
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.system.exitProcess

private const val USAGE = """kbbench offline simulator (see tools/kbbench/sim/README.md)

  --corpus PATH        corpus jsonl (default tools/kbbench/corpus/dev.jsonl)
  --taps PATH[,PATH]   noisy tap plans, one per seed, repeatable (default tools/kbbench/taps_dev_ownkey.json plus
                       tools/kbbench/sim/taps/taps_dev_ownkey_s*.json); noisy metrics are pooled over all plans
  --calib PATH         keyboard calibration (default tools/kbbench/calib/ownkey.json; 'qwerty' = ideal grid)
  --model LANG=PATH    language model, repeatable, primary first (default nl and en 50k frequency lists)
  --primary LANG       move this model to index 0
  --loader EXT=CLASS   register a ModelLoader class for files with extension EXT
  --set KEY=VALUE      EngineConfig override, repeatable
  --touches on|off|both
  --mode clean|noisy|both
  --langs en,nl,mix    sentence languages to run
  --limit N            at most N sentences per language (0 = all)
  --warmup N           untimed warm-up sentences before measuring latency (default 40)
  --details            include per-sentence records in the output
  --literal-checks PATH|none   held-out keep/switch checks (default tools/kbbench/sim/literal_checks.jsonl)
  --blocklist PATH     words never offered (WordBlocklist text format, like the phone's possibly-offensive
                       lists in app/src/main/assets/ime/dict/offensive/), repeatable; default none
  --keep-words PATH    words never autocorrected when typed exactly, and never revised while they stand as typed
                       (the app's own word lists, spec AC-011); first column per line, '#' comments; repeatable
  --keep-words-scope all|current   apply --keep-words to revisions too (all, default) or only to the typed word
  --confusion-sets PATH   confusion groups, one per line ("then than"; '#' comments) plus the Dutch d/dt/t verb
                       endings: a typed word is never autocorrected, or revised while it stands as typed, to a
                       word of its own group (the app's AC-011 rule)
  --keep-floor X       grid: rows whose literal-check keep rate is below X rank last (default 0.95)
  --grid KEY=V1,V2     grid-search values, repeatable (cartesian product; KEY may be 'touches')
  --threads N          parallel grid workers (default 4)
  --objective combined|ksr|wer   grid ranking (combined = KSR - WER)
  --allow-test         permit a single run on the test split (never with --grid or --set)
  --explain CTX|INPUT  print the candidates and the autocorrect decision for INPUT typed after the context words
                       CTX (separated by commas or spaces; key codes), repeatable; nothing else runs. With
                       CTX|INPUT|NEXT also the right-context revision of INPUT once NEXT is committed
  --out PATH           write results JSON
"""

private const val DEFAULT_TAPS = "tools/kbbench/taps_dev_ownkey.json"
private const val EXTRA_SEED_TAPS_DIR = "tools/kbbench/sim/taps"
private const val DEFAULT_LITERAL_CHECKS = "tools/kbbench/sim/literal_checks.jsonl"
/** Differences below this count as ties in grid comparisons. */
private const val Eps = 1e-12

/**
 * Right context for the literal checks under revision: frequent words of each language, the same for every check (not
 * chosen to fit it), so a revision that changes a kept word after one of them is a false revision.
 */
private val REVISION_NEXT_WORDS = mapOf(
    "nl" to listOf("en", "is", "niet", "ook", "de", "dat", "morgen", "zei"),
    "en" to listOf("and", "is", "not", "too", "the", "that", "today", "said"),
)

fun main(args: Array<String>) {
    exitProcess(SimulatorCli.run(args))
}

object SimulatorCli {
    class Options {
        var corpus = "tools/kbbench/corpus/dev.jsonl"
        val taps = ArrayList<String>()
        var calib = "tools/kbbench/calib/ownkey.json"
        val models = ArrayList<Pair<String, String>>()
        var primary: String? = null
        val sets = LinkedHashMap<String, String>()
        var touches = "off"
        var mode = "both"
        var langs = listOf("en", "nl", "mix")
        var limit = 0
        var warmup = 40
        var details = false
        var literalChecks: String? = DEFAULT_LITERAL_CHECKS
        val blocklists = ArrayList<String>()
        val keepWords = ArrayList<String>()
        var keepWordsInRevision = true
        var confusionSets: String? = null
        var keepFloor = 0.95
        val grid = LinkedHashMap<String, List<String>>()
        var threads = 4
        var objective = "combined"
        var allowTest = false
        val explain = ArrayList<String>()
        var out: String? = null
    }

    fun parse(args: Array<String>): Options {
        val o = Options()
        var i = 0
        fun value(): String = args.getOrNull(++i) ?: throw IllegalArgumentException("missing value for ${args[i - 1]}")
        fun kv(s: String): Pair<String, String> {
            val eq = s.indexOf('=')
            require(eq > 0) { "expected KEY=VALUE, got '$s'" }
            return s.substring(0, eq) to s.substring(eq + 1)
        }
        while (i < args.size) {
            when (val a = args[i]) {
                "--corpus" -> o.corpus = value()
                "--taps" -> o.taps.addAll(value().split(',').map { it.trim() }.filter { it.isNotEmpty() })
                "--calib" -> o.calib = value()
                "--model" -> o.models.add(kv(value()))
                "--primary" -> o.primary = value()
                "--loader" -> kv(value()).let { (ext, cls) -> ModelLoaders.registerClass(ext, cls) }
                "--set" -> kv(value()).let { (k, v) -> o.sets[k] = v }
                "--touches" -> o.touches = value()
                "--mode" -> o.mode = value()
                "--langs" -> o.langs = value().split(',').filter { it.isNotBlank() }
                "--limit" -> o.limit = value().toInt()
                "--warmup" -> o.warmup = value().toInt()
                "--details" -> o.details = true
                "--literal-checks" -> o.literalChecks = value().takeUnless { it == "none" }
                "--blocklist" -> o.blocklists.add(value())
                "--keep-words" -> o.keepWords.add(value())
                "--confusion-sets" -> o.confusionSets = value()
                "--keep-words-scope" -> o.keepWordsInRevision = when (value()) {
                    "all" -> true
                    "current" -> false
                    else -> throw IllegalArgumentException("--keep-words-scope is all or current")
                }
                "--keep-floor" -> o.keepFloor = value().toDouble()
                "--grid" -> kv(value()).let { (k, v) -> o.grid[k] = v.split(',').map { it.trim() } }
                "--threads" -> o.threads = value().toInt()
                "--objective" -> o.objective = value()
                "--allow-test" -> o.allowTest = true
                "--explain" -> o.explain.add(value())
                "--out" -> o.out = value()
                "-h", "--help" -> throw HelpRequested()
                else -> throw IllegalArgumentException("unknown argument '$a'")
            }
            i++
        }
        if (o.models.isEmpty()) {
            o.models.add("nl" to "app/src/main/assets/ime/dict/frequencywords/nl_50k.txt")
            o.models.add("en" to "app/src/main/assets/ime/dict/frequencywords/en_50k.txt")
        }
        if (o.taps.isEmpty()) {
            o.taps.add(DEFAULT_TAPS)
            File(EXTRA_SEED_TAPS_DIR).listFiles { f -> f.name.startsWith("taps_dev_ownkey_s") && f.name.endsWith(".json") }
                ?.sortedBy { it.name }?.forEach { o.taps.add("$EXTRA_SEED_TAPS_DIR/${it.name}") }
        }
        o.primary?.let { p ->
            val idx = o.models.indexOfFirst { it.first == p }
            require(idx >= 0) { "--primary $p has no --model" }
            o.models.add(0, o.models.removeAt(idx))
        }
        require(o.touches in setOf("on", "off", "both")) { "--touches must be on, off or both" }
        require(o.mode in setOf("clean", "noisy", "both")) { "--mode must be clean, noisy or both" }
        return o
    }

    private class HelpRequested : RuntimeException()

    fun run(args: Array<String>): Int {
        val o = try {
            parse(args)
        } catch (_: HelpRequested) {
            println(USAGE)
            return 0
        } catch (e: IllegalArgumentException) {
            System.err.println("error: ${e.message}\n\n$USAGE")
            return 2
        }
        val result = execute(o)
        o.out?.let { path ->
            val f = File(path)
            f.absoluteFile.parentFile?.mkdirs()
            f.writeText(PRETTY.encodeToString(JsonElement.serializer(), toJson(result)) + "\n")
            println("wrote $path")
        }
        return 0
    }

    private fun isTestId(id: String) = "-test-" in id

    /** Runs the simulation described by [o] and returns the result tree (maps, lists and numbers). */
    fun execute(o: Options): Map<String, Any?> {
        val sentences = BenchData.loadCorpus(File(o.corpus)).filter { it.lang in o.langs }.let { all ->
            if (o.limit <= 0) all else o.langs.flatMap { l -> all.filter { it.lang == l }.take(o.limit) }
        }
        val splits = sentences.map { it.split }.toSet()
        val plans = if (o.mode != "clean") o.taps.map { BenchData.loadTaps(File(it)) } else emptyList()
        val isTest = "test" in splits || sentences.any { isTestId(it.id) } ||
            plans.any { p -> p.split == "test" || p.sentences.keys.any(::isTestId) }
        if (isTest) {
            require(o.grid.isEmpty()) { "grid search on the test split is not allowed" }
            require(o.sets.isEmpty()) { "--set overrides are not allowed on the test split; tune on dev" }
            require(o.allowTest) { "the corpus or taps are the test split; pass --allow-test for a final (non-tuning) run" }
        }
        val checks = o.literalChecks?.let { BenchData.loadLiteralChecks(File(it)) }
        val blocklist = o.blocklists.fold(WordBlocklist.Empty) { acc, path -> acc + WordBlocklist.parse(File(path).readText()) }
        val blocked: ((String) -> Boolean)? = if (blocklist.size > 0) { w -> w in blocklist } else null
        // App word lists (one word per line, first column; '#' comments), see Simulator.keepWords.
        val keep: Set<String>? = o.keepWords.takeIf { it.isNotEmpty() }?.flatMapTo(HashSet()) { path ->
            File(path).readLines().asSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { TextFolding.normalizeWord(it.split(Regex("\\s+"))[0]) }.toList()
        }
        val confusion = o.confusionSets?.let { ConfusionPairs.load(File(it)) }
        val confusionPair: ((String, String) -> Boolean)? =
            confusion?.let { c -> { typed: String, replacement: String -> c.arePartners(typed, replacement) } }
        val spatial = if (o.calib == "qwerty") SpatialModel.qwerty() else BenchData.loadCalibration(File(o.calib)).spatialModel()

        val modelInfo = ArrayList<MutableMap<String, Any?>>()
        val models = ArrayList<WordLanguageModel>()
        for ((lang, path) in o.models) {
            val t0 = System.nanoTime()
            val m = ModelLoaders.load(lang, File(path))
            val t1 = System.nanoTime()
            models.add(m)
            modelInfo.add(linkedMapOf("lang" to lang, "path" to path, "vocabulary" to m.vocabularySize, "load_ms" to (t1 - t0) / 1e6))
        }
        val t0 = System.nanoTime()
        val baseConfig = EngineConfig().with(o.sets)
        val base = PredictionEngine(models, spatial, baseConfig)
        val lexMs = (System.nanoTime() - t0) / 1e6
        base.lexicons.forEachIndexed { i, lex -> modelInfo[i]["lexicon_keys"] = lex.size }
        println("loaded ${models.joinToString { "${it.language}(${it.vocabularySize})" }}; lexicons built in ${fmt(lexMs)} ms; " +
            "${sentences.size} sentences (${splits.joinToString()}); tap seeds ${plans.joinToString { it.seed }.ifEmpty { "-" }}; " +
            "literal checks ${checks?.size ?: 0}")

        val header = linkedMapOf<String, Any?>(
            "corpus" to o.corpus, "splits" to splits.toList(), "taps" to plans.map { linkedMapOf("path" to it.path, "seed" to it.seed) },
            "calib" to o.calib, "models" to modelInfo, "lexicon_build_ms" to lexMs, "mode" to o.mode, "langs" to o.langs,
            "literal_checks" to o.literalChecks, "blocklists" to o.blocklists.toList(), "blocklist_entries" to blocklist.size,
            "keep_words" to o.keepWords.toList(), "keep_word_entries" to (keep?.size ?: 0),
            "keep_words_in_revision" to o.keepWordsInRevision, "confusion_sets" to o.confusionSets,
        )
        if (o.explain.isNotEmpty()) {
            for (q in o.explain) explain(base, q, blocked)
            return header
        }
        if (o.grid.isNotEmpty()) {
            return header + runGrid(o, base, baseConfig, sentences, plans, checks, blocked, keep, confusionPair)
        }

        val runs = linkedMapOf<String, Any?>()
        for (touch in touchModes(o.touches)) {
            if (o.warmup > 0) warmUp(base, touch, sentences.take(o.warmup), plans.firstOrNull(), o.mode, blocked)
            val r = evaluate(
                base, touch, sentences, plans, o.mode, measureLatency = true, details = o.details, checks = checks,
                isBlocked = blocked, keepWords = keep, keepWordsInRevision = o.keepWordsInRevision,
                confusionPair = confusionPair,
            )
            runs[if (touch) "touches_on" else "touches_off"] = r
            printRun(if (touch) "touches=on" else "touches=off", r)
        }
        return header + linkedMapOf("config" to baseConfig.toMap(), "runs" to runs)
    }

    /**
     * Prints the candidates for `context words|input` with each language's log probability in that context; with
     * `context words|input|next` also the right-context revision of the committed word once `next` follows.
     */
    private fun explain(engine: PredictionEngine, query: String, isBlocked: ((String) -> Boolean)?) {
        val parts = query.split('|')
        val ctxWords = if (parts.size >= 2) parts[0].split(' ', ',').filter { it.isNotBlank() }.map { it.lowercase() } else emptyList()
        val input = (if (parts.size >= 2) parts[1] else parts[0]).trim()
        val next = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
        val ctx = PredictionContext(ctxWords, startsSentence = true)
        val r = engine.suggest(ctx, input, isBlocked = isBlocked)
        println("== ${ctxWords.joinToString(" ")} | $input  -> autocorrect ${r.autocorrect?.word ?: "-"}  " +
            "(literal known ${r.isLiteralKnown}, weights ${r.languageWeights.entries.joinToString { "${it.key} ${fmt(it.value)}" }})")
        if (next != null) {
            val committed = r.autocorrect?.word ?: input
            println("   then '$next' after '$committed':")
            engine.explainRevision(ctx, input, committed, next).forEach { println("   $it") }
        }
        val models = engine.lexicons.map { it.model }
        fun lm(word: String): String = models.joinToString(" ") { m ->
            val id = m.wordId(word)
            if (id < 0) "${m.language}:-" else {
                val ids = ctxWords.map { m.wordId(TextFolding.normalizeWord(it)) }
                val p1 = if (ids.isEmpty()) m.bosId else ids.last()
                val p2 = if (ids.size >= 2) ids[ids.size - 2] else if (ids.size == 1) m.bosId else -1
                "${m.language}:${fmt(m.logProb(id, p1, p2))}${if (m.isValidWord(id)) "" else "*"}"
            }
        }
        val shown = (listOfNotNull(r.literal) + r.candidates).distinctBy { it.word to it.kind }.take(14)
        for (c in shown) {
            println(String.format(Locale.ROOT, "  %-14s %-10s lp %7.2f  cost %5.2f  edits %d  lm %s", c.word, c.kind, c.logPosterior, c.editCost, c.edits, lm(c.word)))
        }
    }

    private fun touchModes(s: String): List<Boolean> = when (s) {
        "on" -> listOf(true)
        "off" -> listOf(false)
        else -> listOf(false, true)
    }

    private fun warmUp(
        engine: PredictionEngine,
        touch: Boolean,
        sentences: List<CorpusSentence>,
        plan: TapPlan?,
        mode: String,
        isBlocked: ((String) -> Boolean)?,
    ) {
        val sim = Simulator(engine, touch, isBlocked = isBlocked)
        for (s in sentences) {
            if (mode != "noisy") sim.runClean(s)
            if (mode != "clean") plan?.sentences?.get(s.id)?.let { sim.runNoisy(it) }
        }
    }

    /** Clean and/or noisy metrics overall and per language, plus the literal checks when given. */
    fun evaluate(
        engine: PredictionEngine,
        useTouches: Boolean,
        sentences: List<CorpusSentence>,
        plans: List<TapPlan>,
        mode: String,
        measureLatency: Boolean,
        details: Boolean,
        checks: List<LiteralCheck>? = null,
        isBlocked: ((String) -> Boolean)? = null,
        keepWords: Set<String>? = null,
        keepWordsInRevision: Boolean = true,
        confusionPair: ((String, String) -> Boolean)? = null,
    ): Map<String, Any?> {
        val sim = Simulator(
            engine, useTouches, isBlocked = isBlocked, keepProposals = details, keepWords = keepWords,
            keepWordsInRevision = keepWordsInRevision, confusionPair = confusionPair,
        )
        val out = linkedMapOf<String, Any?>()
        val langs = sentences.map { it.lang }.distinct()
        if (mode != "noisy") {
            val lat = if (measureLatency) LatencyRecorder() else null
            val rows = sentences.map { sim.runClean(it, lat, keepEvents = details) }
            val m = linkedMapOf<String, Any?>("overall" to Simulator.summarizeClean(rows))
            for (l in langs) m[l] = Simulator.summarizeClean(rows.filter { it.lang == l })
            if (lat != null) m["latency_ms"] = lat.summary()
            if (details) {
                m["sentences"] = rows.map {
                    linkedMapOf("id" to it.id, "taps" to it.taps, "needed" to it.needed, "picks" to it.picksByTyped.toList(), "events" to it.events)
                }
            }
            m["revision_events"] = rows.flatMap { r -> r.revisions.map { e -> revisionJson(r.id, null, e) } }
            if (details) {
                m["revision_proposals"] = rows.flatMap { r -> r.proposals.map { e -> revisionJson(r.id, null, e) + ("lang" to r.lang) } }
            }
            out["clean"] = m
        }
        if (mode != "clean") {
            require(plans.isNotEmpty()) { "noisy mode needs at least one --taps plan" }
            val lat = if (measureLatency) LatencyRecorder() else null
            val reviseLat = if (measureLatency) LatencyRecorder() else null
            val perSeed = plans.map { p ->
                val rows = sentences.mapNotNull { s -> p.sentences[s.id]?.let { sim.runNoisy(it, lat, reviseLat) } }
                require(rows.size == sentences.size) { "${p.path} lacks ${sentences.size - rows.size} corpus sentences" }
                p to rows
            }
            val all = perSeed.flatMap { it.second }
            val m = linkedMapOf<String, Any?>("overall" to Simulator.summarizeNoisy(all))
            for (l in langs) m[l] = Simulator.summarizeNoisy(all.filter { it.lang == l })
            val seeds = perSeed.map { (p, rows) ->
                val s = Simulator.summarizeNoisy(rows)
                linkedMapOf(
                    "seed" to p.seed, "wer" to s["wer"], "false_correction_rate" to s["false_correction_rate"],
                    "typo_fix_rate" to s["typo_fix_rate"], "typo_words" to s["typo_words"],
                )
            }
            m["seeds"] = seeds
            val wers = seeds.map { it["wer"] as Double }
            m["wer_spread"] = wers.max() - wers.min()
            m["wer_sd"] = sd(wers)
            if (lat != null) m["latency_ms"] = lat.summary()
            if (reviseLat != null) m["revise_latency_ms"] = reviseLat.summary()
            if (details) {
                m["sentences"] = perSeed.flatMap { (p, rows) ->
                    rows.map {
                        linkedMapOf("seed" to p.seed, "id" to it.id, "edits" to it.edits, "ref" to it.reference, "literal" to it.literals, "out" to it.output)
                    }
                }
            }
            m["revision_events"] = perSeed.flatMap { (p, rows) -> rows.flatMap { r -> r.revisions.map { e -> revisionJson(r.id, p.seed, e) } } }
            if (details) {
                m["revision_proposals"] = perSeed.flatMap { (p, rows) ->
                    rows.flatMap { r -> r.proposals.map { e -> revisionJson(r.id, p.seed, e) + ("lang" to r.lang) } }
                }
            }
            out["noisy"] = m
        }
        if (checks != null) out["literal_checks"] = literalChecks(engine, sim, checks)
        return out
    }

    private fun revisionJson(id: String, seed: String?, e: RevisionEvent): Map<String, Any?> = linkedMapOf(
        "seed" to seed, "id" to id, "index" to e.index, "before" to e.before, "after" to e.after, "ref" to e.reference,
        "verdict" to when {
            e.isRight -> "right"
            e.isWrong -> "wrong"
            else -> "neutral"
        },
        "margin" to e.proposal?.margin, "right_gain" to e.proposal?.rightGain, "extra_cost" to e.proposal?.extraCost,
        "short" to e.proposal?.isShort,
        "committed" to e.proposal?.committedClass?.name, "passes" to e.proposal?.passes,
    )

    /**
     * Keep rate over checks that must keep the typed word (and over those whose key form no lexicon contains, which
     * only the unknown-word hypothesis protects), switch rate over checks that must change spelling, failures. The
     * revision keep rate types each keep check followed by every word of [REVISION_NEXT_WORDS] for its language and
     * counts the pairs whose word is still as expected after a right-context revision.
     */
    private fun literalChecks(engine: PredictionEngine, sim: Simulator, checks: List<LiteralCheck>): Map<String, Any?> {
        val results = checks.map { c -> c to sim.runLiteralCheck(c) }
        fun ok(c: LiteralCheck, out: String) = TextFolding.normalizeWord(out) == TextFolding.normalizeWord(c.expect)
        fun rate(rs: List<Pair<LiteralCheck, String>>) = if (rs.isEmpty()) null else rs.count { (c, w) -> ok(c, w) }.toDouble() / rs.size
        val keep = results.filter { it.first.isKeep }
        val unknown = keep.filter { (c, _) -> engine.lexicons.none { it.find(TextFolding.keyForm(c.input)) >= 0 } }
        val switch = results.filterNot { it.first.isKeep }
        val revised = keep.flatMap { (c, _) ->
            REVISION_NEXT_WORDS[c.lang].orEmpty().map { next -> Triple(c, next, sim.runLiteralCheckWithNext(c, next)) }
        }
        // Pairs whose word was kept without the next word but changed by its revision.
        val falseRevisions = revised.filter { (c, _, out) -> !ok(c, out) && ok(c, sim.runLiteralCheck(c)) }
        // Every proposal for a kept word, applied or not, for offline threshold sweeps.
        val proposals = keep.flatMap { (c, _) ->
            REVISION_NEXT_WORDS[c.lang].orEmpty().mapNotNull { next ->
                val (committed, p) = sim.literalCheckProposal(c, next)
                p?.takeIf { ok(c, committed) && !ok(c, it.candidate.word) }?.let {
                    linkedMapOf(
                        "check" to "${c.kind}: ${c.context.joinToString(" ")} | ${c.input} $next", "after" to it.candidate.word,
                        "margin" to it.margin, "right_gain" to it.rightGain, "extra_cost" to it.extraCost, "short" to it.isShort,
                        "committed" to it.committedClass.name, "passes" to it.passes,
                    )
                }
            }
        }
        return linkedMapOf(
            "revision_proposals" to proposals,
            "revision_keep_n" to revised.size,
            "revision_keep_rate" to if (revised.isEmpty()) null else revised.count { (c, _, w) -> ok(c, w) }.toDouble() / revised.size,
            "revision_false_n" to falseRevisions.size,
            "revision_failures" to falseRevisions.map { (c, next, w) ->
                "${c.kind}: ${c.context.joinToString(" ")} | ${c.input} $next -> $w (expected ${c.expect})"
            },
            "n" to results.size,
            "keep_n" to keep.size, "keep_rate" to rate(keep),
            "keep_unknown_n" to unknown.size, "keep_unknown_rate" to rate(unknown),
            "switch_n" to switch.size, "switch_rate" to rate(switch),
            "by_kind" to results.groupBy { it.first.kind }.mapValues { (_, rs) -> rate(rs) },
            "failures" to results.filterNot { (c, w) -> ok(c, w) }.map { (c, w) ->
                "${c.kind}: ${c.context.joinToString(" ")} | ${c.input} -> $w (expected ${c.expect})"
            },
        )
    }

    private fun runGrid(
        o: Options,
        base: PredictionEngine,
        baseConfig: EngineConfig,
        sentences: List<CorpusSentence>,
        plans: List<TapPlan>,
        checks: List<LiteralCheck>?,
        isBlocked: ((String) -> Boolean)?,
        keepWords: Set<String>? = null,
        confusionPair: ((String, String) -> Boolean)? = null,
    ): Map<String, Any?> {
        var combos: List<Map<String, String>> = listOf(emptyMap())
        for ((k, values) in o.grid) combos = combos.flatMap { c -> values.map { v -> c + (k to v) } }
        fun touchesOf(combo: Map<String, String>) = combo["touches"]?.let { it == "on" } ?: (o.touches == "on")
        println("grid: ${combos.size} combinations on ${o.threads} threads, ${plans.size} tap seed(s), keep floor ${fmt(o.keepFloor)}")
        val pool = Executors.newFixedThreadPool(o.threads.coerceAtLeast(1))
        try {
            fun submit(touches: Boolean, cfg: EngineConfig) = pool.submit(Callable {
                evaluate(
                    base.withConfig(cfg), touches, sentences, plans, o.mode, measureLatency = false, details = false,
                    checks = checks, isBlocked = isBlocked, keepWords = keepWords,
                    keepWordsInRevision = o.keepWordsInRevision, confusionPair = confusionPair,
                )
            })
            // The current configuration per touch mode: rows are judged by their paired gain over it on every seed.
            val baselines = combos.map(::touchesOf).distinct().associateWith { submit(it, baseConfig) }
            val futures = combos.map { combo -> combo to submit(touchesOf(combo), baseConfig.with(combo - "touches")) }
            val baseRows = baselines.mapValues { (_, f) -> f.get() }
            val rows = futures.map { (combo, f) ->
                val r = f.get()
                val baseRow = baseRows.getValue(touchesOf(combo))
                val obj = seedObjectives(r, o.objective)
                val baseObj = seedObjectives(baseRow, o.objective)
                val deltas = obj.indices.map { obj[it] - baseObj[it] }
                val keep = (r["literal_checks"] as? Map<*, *>)?.get("keep_rate") as? Double
                val meetsFloor = keep == null || keep >= o.keepFloor
                // The clean KSR is one deterministic number shared by every seed, so it is compared on its own; only
                // the noisy WER varies by seed and gets the sign test (paired by seed).
                val ksr = metric(r, "clean", "ksr")
                val baseKsr = metric(baseRow, "clean", "ksr")
                val ksrDelta = if (ksr != null && baseKsr != null) ksr - baseKsr else null
                val werGains = seedWers(r)?.let { w -> seedWers(baseRow)?.let { b -> w.indices.map { b[it] - w[it] } } }
                val beats = meetsFloor && if (o.objective == "ksr" || werGains == null) {
                    (ksrDelta ?: 0.0) > Eps
                } else {
                    werGains.min() > Eps && (ksrDelta ?: 0.0) >= -Eps
                }
                linkedMapOf(
                    "params" to combo, "objective" to obj.average(), "ksr" to ksr,
                    "wer" to metric(r, "noisy", "wer"), "wer_spread" to (r["noisy"] as? Map<*, *>)?.get("wer_spread"),
                    "keep_rate" to keep, "meets_keep_floor" to meetsFloor,
                    "delta_vs_base" to deltas.average(), "delta_min" to deltas.min(), "delta_max" to deltas.max(),
                    "ksr_delta" to ksrDelta,
                    "wer_seeds_better" to werGains?.count { it > Eps }, "wer_seeds_worse" to werGains?.count { it < -Eps },
                    // Within the floor and: noisy WER lower on every seed with KSR not lower, or (objective ksr or no
                    // noisy typist) KSR higher.
                    "beats_base" to beats,
                ) + r
            }.sortedWith(
                compareByDescending<Map<String, Any?>> { it["meets_keep_floor"] as Boolean }.thenByDescending { it["objective"] as Double },
            )
            for ((touches, r) in baseRows) printGridRow("BASE touches=${if (touches) "on" else "off"}", r, null)
            println(String.format(Locale.ROOT, "%-80s %7s %7s %6s %6s %6s %6s %6s %6s %8s %5s %5s %13s %6s %6s",
                "params", "ksr", "wer", "±seed", "fcr", "fix", "keep", "kUnk", "switch", "ΔKSR", "seeds", "beats", "rev r/w/n",
                "cleanF", "keepRv"))
            for (row in rows) {
                printGridRow((row["params"] as Map<*, *>).entries.joinToString(" ") { "${it.key}=${it.value}" }, row, row)
            }
            return linkedMapOf(
                "config" to baseConfig.toMap(), "objective" to o.objective, "keep_floor" to o.keepFloor,
                "baseline" to baseRows.mapKeys { (t, _) -> if (t) "touches_on" else "touches_off" }, "grid" to rows,
            )
        } finally {
            pool.shutdown()
        }
    }

    private fun printGridRow(label: String, r: Map<String, Any?>, row: Map<String, Any?>?) {
        val noisy = (r["noisy"] as? Map<*, *>)
        val clean = (r["clean"] as? Map<*, *>)?.get("overall") as? Map<*, *>
        val checks = r["literal_checks"] as? Map<*, *>
        val n = noisy?.get("overall") as? Map<*, *>
        val ksrDelta = row?.get("ksr_delta") as? Double
        val better = row?.get("wer_seeds_better") as? Int
        val worse = row?.get("wer_seeds_worse") as? Int
        val rev = n?.let { "${it["revisions_right"]}/${it["revisions_wrong"]}/${it["revisions"]}" } ?: "-"
        println(String.format(
            Locale.ROOT, "%-80s %7s %7s %6s %6s %6s %6s %6s %6s %8s %5s %5s %13s %6s %6s",
            label.take(80), pct(clean?.get("ksr")), pct(n?.get("wer")), pct(noisy?.get("wer_spread")),
            pct(n?.get("false_correction_rate")), pct(n?.get("typo_fix_rate")), pct(checks?.get("keep_rate")),
            pct(checks?.get("keep_unknown_rate")), pct(checks?.get("switch_rate")),
            ksrDelta?.let { String.format(Locale.ROOT, "%+.3f%%", it * 100) } ?: "-",
            if (better != null && worse != null) "+$better/-$worse" else "-",
            row?.let { if (it["beats_base"] == true) "yes" else "no" } ?: "",
            rev, clean?.get("false_revisions") ?: "-", pct(checks?.get("revision_keep_rate")),
        ))
    }

    /** The objective per tap seed (the clean KSR is deterministic and shared by all seeds). */
    private fun seedObjectives(r: Map<String, Any?>, objective: String): List<Double> {
        val ksr = metric(r, "clean", "ksr")
        val wers = ((r["noisy"] as? Map<*, *>)?.get("seeds") as? List<*>)?.map { (it as Map<*, *>)["wer"] as Double }
        return when {
            objective == "ksr" || wers == null -> listOf(ksr ?: 0.0)
            objective == "wer" -> wers.map { -it }
            else -> wers.map { (ksr ?: 0.0) - it }
        }
    }

    /** Noisy WER per tap seed, or null without a noisy typist. */
    private fun seedWers(r: Map<String, Any?>): List<Double>? =
        ((r["noisy"] as? Map<*, *>)?.get("seeds") as? List<*>)?.map { (it as Map<*, *>)["wer"] as Double }

    private fun metric(r: Map<String, Any?>, part: String, key: String): Double? =
        ((r[part] as? Map<*, *>)?.get("overall") as? Map<*, *>)?.get(key) as? Double

    private fun sd(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val mean = xs.average()
        return sqrt(xs.sumOf { (it - mean) * (it - mean) } / (xs.size - 1))
    }

    private fun printRun(label: String, r: Map<String, Any?>) {
        val clean = r["clean"] as? Map<*, *>
        val noisy = r["noisy"] as? Map<*, *>
        println("== $label")
        for (part in listOf("overall", "en", "nl", "mix")) {
            val c = clean?.get(part) as? Map<*, *>
            val n = noisy?.get(part) as? Map<*, *>
            if (c == null && n == null) continue
            println(String.format(
                Locale.ROOT, "  %-8s KSR %s  next-word %s  desyncs %s | WER %s (literal %s)  false-corr %s  typo-fix %s",
                part, pct(c?.get("ksr")), pct(c?.get("next_word_hit_rate")), c?.get("desyncs") ?: "-",
                pct(n?.get("wer")), pct(n?.get("literal_wer")), pct(n?.get("false_correction_rate")), pct(n?.get("typo_fix_rate")),
            ))
        }
        for (part in listOf("overall", "en", "nl", "mix")) {
            val c = clean?.get(part) as? Map<*, *>
            val n = noisy?.get(part) as? Map<*, *>
            if (c == null && n == null) continue
            println(String.format(
                Locale.ROOT, "  %-8s revisions noisy %s (right %s, wrong %s) | clean %s (false %s)",
                part, n?.get("revisions") ?: "-", n?.get("revisions_right") ?: "-", n?.get("revisions_wrong") ?: "-",
                c?.get("revisions") ?: "-", c?.get("false_revisions") ?: "-",
            ))
        }
        (noisy?.get("seeds") as? List<*>)?.let { seeds ->
            println("  WER per seed: " + seeds.joinToString("  ") { s -> (s as Map<*, *>).let { "${it["seed"]} ${pct(it["wer"])}" } } +
                "  (spread ${pct(noisy["wer_spread"])}, sd ${pct(noisy["wer_sd"])})")
        }
        (r["literal_checks"] as? Map<*, *>)?.let { lc ->
            println("  literal checks: keep ${pct(lc["keep_rate"])} (n=${lc["keep_n"]}), unknown-only ${pct(lc["keep_unknown_rate"])} " +
                "(n=${lc["keep_unknown_n"]}), switch ${pct(lc["switch_rate"])} (n=${lc["switch_n"]}), with a next word " +
                "${pct(lc["revision_keep_rate"])} (n=${lc["revision_keep_n"]}, false revisions ${lc["revision_false_n"]})")
        }
        for ((name, part, key) in listOf(Triple("clean", clean, "latency_ms"), Triple("noisy", noisy, "latency_ms"), Triple("revise", noisy, "revise_latency_ms"))) {
            val lat = part?.get(key) as? Map<*, *> ?: continue
            println("  latency $name: p50 ${fmt(lat["p50"])} ms  p95 ${fmt(lat["p95"])} ms  p99 ${fmt(lat["p99"])} ms  max ${fmt(lat["max"])} ms  (n=${(lat["n"] as Double).toInt()})")
        }
    }

    private fun pct(v: Any?): String = (v as? Double)?.let { String.format(Locale.ROOT, "%.2f%%", it * 100) } ?: "-"

    private fun fmt(v: Any?): String = (v as? Double)?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "-"

    private val PRETTY = Json { prettyPrint = true }

    fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is JsonElement -> v
        is String -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
        is Iterable<*> -> JsonArray(v.map { toJson(it) })
        is IntArray -> JsonArray(v.map { JsonPrimitive(it) })
        else -> JsonPrimitive(v.toString())
    }
}
