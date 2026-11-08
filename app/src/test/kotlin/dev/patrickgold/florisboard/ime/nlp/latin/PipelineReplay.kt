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

import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.AutoCommitSelector
import dev.patrickgold.florisboard.ime.nlp.WordRevisionTracker
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionBatch
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionRequest
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectTriggerPolicy
import dev.patrickgold.florisboard.ime.nlp.latin.engine.KeyGeometry
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringHooks
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NgramLatinScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.PossiblyOffensiveWords
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.prediction.PredictionContext
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.SpatialModel
import org.florisboard.lib.prediction.TextFolding
import org.florisboard.lib.prediction.TouchPoint
import java.io.File

/**
 * Replays the kbbench noisy typist of the dev split (`tools/kbbench/PROTOCOL.md`, the same tap plans as
 * `tools/kbbench/sim`) through the app's autocorrect pipeline instead of the bare engine: the editor text with
 * auto-capitalised sentence starts, [AutoCommitSelector] and the trigger policy of `NlpManager.autoCommitCandidateFor`,
 * the provider's request and offensive-word filter ([LatinAutocorrectPipeline]), the scorer with main's layers, and the
 * revision path of `KeyboardManager` ([WordRevisionTracker], [LatinAutocorrectPipeline.revise], the editor match).
 * Word error rate, false corrections and fixed typos are computed as the simulator does (folded words), so
 * [engineAlone] and [pipeline] are directly comparable. Only the dev split is ever read.
 */
internal class PipelineReplay(val repoRoot: File) {
    data class Word(val word: String, val pressed: String, val literal: String, val offsets: List<Pair<Double, Double>>)
    data class Sentence(val id: String, val lang: String, val words: List<Word>)
    data class Plan(val seed: String, val sentences: List<Sentence>)
    data class Check(val lang: String, val context: List<String>, val input: String, val expect: String) {
        val isKeep: Boolean get() = TextFolding.normalizeWord(expect) == TextFolding.normalizeWord(input)
    }

    /** Totals of one run: [edits] word edits over [words] reference words, as in the simulator's noisy summary. */
    data class Totals(
        var words: Int = 0,
        var edits: Int = 0,
        var typedCorrect: Int = 0,
        var falseCorrections: Int = 0,
        var typos: Int = 0,
        var fixed: Int = 0,
        var revisions: Int = 0,
    ) {
        val wer: Double get() = if (words == 0) 0.0 else 100.0 * edits / words
        fun add(o: Totals) {
            words += o.words; edits += o.edits; typedCorrect += o.typedCorrect; falseCorrections += o.falseCorrections
            typos += o.typos; fixed += o.fixed; revisions += o.revisions
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    val plans: List<Plan> by lazy {
        val files = listOf(File(repoRoot, "tools/kbbench/taps_dev_ownkey.json")) +
            (File(repoRoot, "tools/kbbench/sim/taps").listFiles { f -> f.name.startsWith("taps_dev_") }?.sortedBy { it.name }.orEmpty())
        files.map { loadPlan(it) }
    }

    private val calibration by lazy { json.parseToJsonElement(File(repoRoot, "tools/kbbench/calib/ownkey.json").readText()).jsonObject }
    private val keysPx: Map<Char, Pair<Double, Double>> by lazy {
        calibration.getValue("keys").jsonObject.entries.associate { (k, v) ->
            k.single() to (v.jsonArray[0].jsonPrimitive.double to v.jsonArray[1].jsonPrimitive.double)
        }
    }
    private val pitch: Pair<Double, Double> by lazy {
        calibration.getValue("pitch").jsonArray.let { it[0].jsonPrimitive.double to it[1].jsonPrimitive.double }
    }

    /** The engine's spatial model of the calibrated layout, as the simulator builds it. */
    val spatial: SpatialModel by lazy { SpatialModel.fromKeyCentres(keysPx, pitch.first, pitch.second) }

    /** The layout as the keyboard measures it ([KeyGeometry.fromPixels], `KeyboardGeometrySource`). */
    private val measured: KeyGeometry.Measured by lazy {
        requireNotNull(KeyGeometry.fromPixels(keysPx.mapValues { (_, p) -> p.first.toFloat() to p.second.toFloat() }))
    }
    val geometry: KeyGeometry get() = measured.geometry

    val literalChecks: List<Check> by lazy {
        File(repoRoot, "tools/kbbench/sim/literal_checks.jsonl").readLines().filter { it.isNotBlank() }.map { line ->
            val o = json.parseToJsonElement(line).jsonObject
            Check(
                o.getValue("lang").jsonPrimitive.content,
                o.getValue("context").jsonPrimitive.content.split(' ').filter { it.isNotBlank() },
                o.getValue("input").jsonPrimitive.content,
                o.getValue("expect").jsonPrimitive.content,
            )
        }
    }

    private fun loadPlan(file: File): Plan {
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val split = root["split"]?.jsonPrimitive?.content
        require(split == "dev" && "test" !in file.name) { "only dev tap plans are replayed: ${file.name}" }
        val sentences = root.getValue("sentences").jsonArray.map { s ->
            val o = s.jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            require("-test-" !in id) { "test sentence $id in a dev plan" }
            Sentence(id, o.getValue("lang").jsonPrimitive.content, o.getValue("words").jsonArray.map { w ->
                val wo = w.jsonObject
                Word(
                    wo.getValue("word").jsonPrimitive.content,
                    wo.getValue("pressed").jsonPrimitive.content,
                    wo.getValue("literal").jsonPrimitive.content,
                    wo.getValue("offsets").jsonArray.map { p -> p.jsonArray[0].jsonPrimitive.double to p.jsonArray[1].jsonPrimitive.double },
                )
            })
        }
        return Plan(root["seed"]?.jsonPrimitive?.content ?: file.nameWithoutExtension, sentences)
    }

    /** Engine touch points: key centre plus offset, in key pitches (simulator `BenchData.touchesFor`). */
    private fun touchesFor(w: Word): List<TouchPoint> = w.pressed.mapIndexed { i, c ->
        val centre = spatial.centre(c)
        TouchPoint(centre.x + w.offsets[i].first, centre.y + w.offsets[i].second)
    }

    /** The taps the keyboard records ([TapTrail]): the same points in pixels, divided by the measured key pitch. */
    private fun tapsFor(w: Word): List<LatinTap> = w.pressed.mapIndexed { i, c ->
        val (x, y) = keysPx.getValue(c)
        val px = x + w.offsets[i].first * pitch.first
        val py = y + w.offsets[i].second * pitch.second
        LatinTap(px / measured.unit, py / measured.unit)
    }

    private fun fold(s: String) = TextFolding.keyForm(s.trim())
    private fun typedForm(word: String) = fold(word).filter { it in 'a'..'z' }

    /** The folded output words of the last replayed sentence, for diagnostics. */
    var lastOutput: List<String> = emptyList()
        private set

    private fun score(ref: List<String>, hyp: List<String>, words: List<Word>, revisions: Int): Totals {
        lastOutput = hyp
        val t = Totals(words = ref.size, edits = wordEditDistance(ref, hyp), revisions = revisions)
        for ((i, w) in words.withIndex()) {
            val right = hyp.getOrNull(i) == ref[i]
            if (w.literal == typedForm(w.word)) {
                t.typedCorrect++
                if (!right) t.falseCorrections++
            } else {
                t.typos++
                if (right) t.fixed++
            }
        }
        return t
    }

    /** The simulator's noisy typist on the bare engine (`Simulator.runNoisy`). */
    fun engineAlone(engine: PredictionEngine, sentence: Sentence, touches: Boolean, blocked: Set<String>): Totals {
        val isBlocked: ((String) -> Boolean)? = if (blocked.isEmpty()) null else { w -> PossiblyOffensiveWords.isBlocked(w, blocked) }
        val outputs = ArrayList<String>()
        var revisions = 0
        var previousTouches: List<TouchPoint>? = null
        for ((i, w) in sentence.words.withIndex()) {
            val t = if (touches) touchesFor(w) else null
            val res = engine.suggest(PredictionContext(outputs.toList(), startsSentence = true), w.literal, t, isBlocked = isBlocked)
            outputs.add(res.autocorrect?.word ?: w.literal)
            if (i > 0) {
                val prevCtx = PredictionContext(outputs.subList(0, i - 1).toList(), startsSentence = true)
                val p = engine.proposeRevision(prevCtx, sentence.words[i - 1].literal, outputs[i - 1], outputs[i], previousTouches, isBlocked = isBlocked)
                if (p != null && p.passes && p.candidate.word != outputs[i - 1]) {
                    outputs[i - 1] = p.candidate.word
                    revisions++
                }
            }
            previousTouches = t
        }
        return score(sentence.words.map { fold(it.word) }, outputs.map { fold(it) }, sentence.words, revisions)
    }

    /**
     * The app pipeline for one sentence typed into an empty field: each word's letters (the first one capitalised, as
     * the editor's sentence auto-capitalisation does), then space.
     */
    suspend fun pipeline(
        scorer: NgramLatinScorer,
        languages: List<LatinScoringLanguage>,
        sentence: Sentence,
        touches: Boolean,
        blocked: Set<String>,
        settings: AutocorrectSettings = AutocorrectStrength.NORMAL.toSettings(enabled = true),
        hooks: LatinScoringHooks = LatinScoringHooks.None,
        autoCapitalize: Boolean = true,
    ): Totals {
        var text = ""
        val tracker = WordRevisionTracker()
        var revisions = 0
        val session = 1L
        for ((i, w) in sentence.words.withIndex()) {
            val typed = if (autoCapitalize && i == 0) w.literal.replaceFirstChar { it.uppercaseChar() } else w.literal
            val taps = if (touches) tapsFor(w) else null
            val textBefore = text + typed
            val committed = commitOnSpace(scorer, languages, typed, textBefore, taps, settings, hooks, blocked) ?: typed
            text += "$committed "
            val content = EditorContent(text, 0, EditorRange.cursor(text.length), EditorRange.Unspecified, EditorRange.Unspecified)
            // KeyboardManager: letters typed on the keyboard always have a tap trail; key-code runs pass no positions.
            val request = tracker.onWordEndedWithSpace(session, typed, committed, taps ?: emptyList(), geometry, content)
                ?: continue
            val revised = LatinAutocorrectPipeline.revise(scorer, request, languages, settings, { false }, blocked) ?: continue
            if (!request.matches(content) || revised.text == request.committed) continue
            text = text.substring(0, request.wordStart) + revised.text + text.substring(request.wordStart + request.committed.length)
            tracker.onRevisionApplied(request, revised.text)
            revisions++
        }
        val outputs = text.trim().split(' ').filter { it.isNotEmpty() }
        return score(sentence.words.map { fold(it.word) }, outputs.map { fold(it) }, sentence.words, revisions)
    }

    /** `NlpManager.autoCommitCandidateFor` on space: trigger policy, [AutoCommitSelector], provider decision. */
    suspend fun commitOnSpace(
        scorer: NgramLatinScorer,
        languages: List<LatinScoringLanguage>,
        typed: String,
        textBefore: String,
        taps: List<LatinTap>?,
        settings: AutocorrectSettings,
        hooks: LatinScoringHooks,
        blocked: Set<String>,
    ): String? {
        if (!settings.enabled) return null
        if (!AutocorrectTriggerPolicy.isCorrectableToken(AutocorrectTriggerPolicy.tokenBeforeCursor(textBefore), " ")) return null
        val request = LatinAutocorrectPipeline.autoCommitRequest(
            typed, languages, textBefore, true, settings, HighCertaintyAutocorrectPolicy(), geometry, taps, blocked,
        )
        val decided = LatinAutocorrectPipeline.decideAutoCommit(scorer, request, hooks)
        val selection = AutoCommitSelector.select(
            request = WordSuggestionRequest(typed, subtypeId = 1L, inputSessionId = 1L, isPrivateSession = false, textBefore = ""),
            batch = WordSuggestionBatch.Empty,
        ) { decided?.let { WordSuggestionCandidate(text = it.text, confidence = it.confidence, isEligibleForAutoCommit = true) } }
        return selection.candidate?.text?.toString()
    }

    private fun wordEditDistance(a: List<String>, b: List<String>): Int {
        val d = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.size) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.size]
    }
}
