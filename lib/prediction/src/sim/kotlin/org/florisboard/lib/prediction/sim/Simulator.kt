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

import org.florisboard.lib.prediction.PredictionContext
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.RevisionProposal
import org.florisboard.lib.prediction.SuggestResult
import org.florisboard.lib.prediction.Candidate
import org.florisboard.lib.prediction.TextFolding
import org.florisboard.lib.prediction.TouchPoint

/** Collects `suggest()` wall times. Not thread-safe; use one per thread. */
class LatencyRecorder {
    private var values = LongArray(4096)
    var count = 0
        private set

    fun add(nanos: Long) {
        if (count == values.size) values = values.copyOf(values.size * 2)
        values[count++] = nanos
    }

    fun addAll(other: LatencyRecorder) {
        for (i in 0 until other.count) add(other.values[i])
    }

    /** Percentiles in milliseconds. */
    fun summary(): Map<String, Double> {
        if (count == 0) return emptyMap()
        val sorted = values.copyOf(count).also { it.sort() }
        fun pct(p: Double) = sorted[((count - 1) * p).toInt()] / 1e6
        return linkedMapOf(
            "n" to count.toDouble(), "mean" to sorted.average() / 1e6, "p50" to pct(0.50), "p90" to pct(0.90),
            "p95" to pct(0.95), "p99" to pct(0.99), "max" to sorted.last() / 1e6,
        )
    }
}

/**
 * One right-context revision ([PredictionEngine.revise]): word [index] changed from [before] to [after] once the next
 * word was committed; [reference] is the intended word.
 */
data class RevisionEvent(
    val index: Int,
    val before: String,
    val after: String,
    val reference: String,
    /** The engine's proposal (also for proposals that did not pass, see [NoisySentence.proposals]). */
    val proposal: RevisionProposal? = null,
) {
    private val ref get() = BenchText.fold(reference)

    /** The word was wrong and is now right. */
    val isRight: Boolean get() = BenchText.fold(before) != ref && BenchText.fold(after) == ref

    /** The word was right and is now wrong. */
    val isWrong: Boolean get() = BenchText.fold(before) == ref && BenchText.fold(after) != ref
}

/** Clean-typist outcome of one sentence. */
data class CleanSentence(
    val id: String,
    val lang: String,
    val taps: Int,
    val needed: Int,
    val words: Int,
    val nextWordHits: Int,
    /** Picks by number of letters typed before the pick (index 0 = next-word prediction). */
    val picksByTyped: IntArray,
    val desyncs: Int,
    val events: List<String>,
    /** Revisions of correctly typed words (each changes a word the typist typed right, unless it undoes a desync). */
    val revisions: List<RevisionEvent> = emptyList(),
    /** Every revision proposal, applied or not (with `keepProposals`). */
    val proposals: List<RevisionEvent> = emptyList(),
)

/** Noisy-typist outcome of one sentence. */
data class NoisySentence(
    val id: String,
    val lang: String,
    val words: Int,
    val edits: Int,
    val typedCorrect: Int,
    val falseCorrections: Int,
    val typoWords: Int,
    val typosFixed: Int,
    val reference: List<String>,
    val output: List<String>,
    val literals: List<String>,
    val revisions: List<RevisionEvent> = emptyList(),
    /** Every revision the engine proposed, applied ([RevisionProposal.passes]) or not, as if it were applied. */
    val proposals: List<RevisionEvent> = emptyList(),
)

/**
 * Replays the kbbench typists of `tools/kbbench/PROTOCOL.md` against a [PredictionEngine] instead of a phone.
 *
 * The clean typist reads the first three [SuggestResult.displaySlots] before every letter (and before the first
 * one), taps a slot that folds equal to the target when that saves at least one tap (phantom space: the next
 * word's prediction follows immediately), otherwise types the next letter and finally taps space, which applies
 * the autocorrect. A correctly typed word that autocorrect changes counts as a desync; the intended word is kept
 * as context. The noisy typist types each word's literal letters from the taps file and keeps the autocorrect
 * result (or the literal) as context for the next word.
 *
 * Right-context revision ([PredictionEngine.revise], on unless [org.florisboard.lib.prediction.EngineConfig.reviseEnabled]
 * is off) runs as on the phone: when a word typed letter by letter is ended with space and the word before it was
 * typed letter by letter too, the word before may change. The noisy typist keeps the revised word as context for the
 * following words; the clean typist only counts revisions (a revision of a correctly typed word is a false revision),
 * and never revises a word it picked from the strip or the word before a pick.
 *
 * @param useTouches Pass touch points to the engine: key centres for the clean typist, centre plus the recorded
 *  offset for the noisy typist.
 * @param isBlocked Words the engine must not offer ([PredictionEngine.suggest]), as the phone's possibly-offensive
 *  word filter (on by default) does; null blocks nothing.
 */
class Simulator(
    private val engine: PredictionEngine,
    private val useTouches: Boolean,
    private val slotsVisible: Int = 3,
    private val isBlocked: ((String) -> Boolean)? = null,
    /** Keep every revision proposal of the noisy typist ([NoisySentence.proposals]) for offline threshold sweeps. */
    private val keepProposals: Boolean = false,
    /**
     * Words of an app dictionary (`--keep-words`): typed exactly they are never autocorrected, and never revised while
     * they stand as typed, like the Ownkey app's rule for words of its own word lists (spec AC-011). Null: off.
     */
    private val keepWords: Set<String>? = null,
    /** Apply [keepWords] to revisions too; false: only to the word being typed. */
    private val keepWordsInRevision: Boolean = true,
    /**
     * Confusion pairs (`--confusion-sets`): true when the typed word and the replacement are two sides of one pair
     * ("then", "than"). Like the Ownkey app (spec AC-011), such a typed word is never autocorrected to its partner, and
     * never revised to it while it stands as typed. Null: off.
     */
    private val confusionPair: ((typed: String, replacement: String) -> Boolean)? = null,
) {
    private val spatial = engine.spatialModel
    private val isProtected: ((String) -> Boolean)? = keepWords?.let { words -> { w: String -> w in words } }

    /** The autocorrect of [res] for the typed [letters], unless it swaps one side of a confusion pair for the other. */
    private fun autocorrectOf(res: SuggestResult?, letters: String): Candidate? {
        val ac = res?.autocorrect ?: return null
        val pair = confusionPair ?: return ac
        return if (pair(TextFolding.normalizeWord(letters.trim()), ac.word)) null else ac
    }

    /** Whether revision [p] of [typed], committed as [committed], applies (see [confusionPair]). */
    private fun revisionApplies(p: RevisionProposal, typed: String, committed: String): Boolean {
        if (!p.passes) return false
        val pair = confusionPair ?: return true
        val norm = TextFolding.normalizeWord(typed.trim())
        if (norm != TextFolding.normalizeWord(committed.trim())) return true
        return !pair(norm, p.candidate.word)
    }

    /** The protection of a revision of [typed], committed as [committed]: only a word that stands as typed. */
    private fun revisionProtection(typed: String, committed: String): ((String) -> Boolean)? {
        val words = keepWords ?: return null
        if (!keepWordsInRevision) return null
        if (TextFolding.normalizeWord(typed.trim()) != TextFolding.normalizeWord(committed.trim())) return null
        return { w -> w in words }
    }

    private inline fun timed(latency: LatencyRecorder?, block: () -> SuggestResult): SuggestResult {
        if (latency == null) return block()
        val t0 = System.nanoTime()
        val r = block()
        latency.add(System.nanoTime() - t0)
        return r
    }

    fun runClean(sentence: CorpusSentence, latency: LatencyRecorder? = null, keepEvents: Boolean = false): CleanSentence {
        var taps = 0
        var needed = 0
        var nextWordHits = 0
        var desyncs = 0
        val picks = IntArray(PICK_BUCKETS)
        val events = ArrayList<String>()
        val revisions = ArrayList<RevisionEvent>()
        val proposals = ArrayList<RevisionEvent>()
        val context = ArrayList<String>()
        // The previous word when it was typed letter by letter and ended with space: its context, letters and output.
        var previous: Triple<PredictionContext, String, String>? = null
        for ((index, word) in sentence.words.withIndex()) {
            val target = BenchText.fold(word)
            val letters = BenchText.typedForm(word)
            needed += letters.length + 1
            var typed = 0
            var picked = false
            var last: SuggestResult? = null
            val ctx = PredictionContext(context.toList(), startsSentence = true)
            while (true) {
                val remaining = letters.length - typed + 1
                val prefix = letters.substring(0, typed)
                val touches = if (useTouches) prefix.map { spatial.centre(it) } else null
                val res = timed(latency) { engine.suggest(ctx, prefix, touches, isProtected = isProtected, isBlocked = isBlocked) }
                val slots = res.displaySlots(8).take(slotsVisible)
                val hit = slots.indexOfFirst { BenchText.fold(it.word) == target }
                if (hit >= 0 && remaining > 1) {
                    taps++
                    picked = true
                    picks[minOf(typed, PICK_BUCKETS - 1)]++
                    if (typed == 0) nextWordHits++
                    break
                }
                if (typed == letters.length) {
                    last = res
                    break
                }
                taps++
                typed++
            }
            if (!picked) {
                taps++
                val ac = autocorrectOf(last, letters)
                if (ac != null && BenchText.fold(ac.word) != target) {
                    desyncs++
                    if (keepEvents) events.add("desync: $letters -> ${ac.word}")
                }
                val output = ac?.word ?: letters
                previous?.let { (prevCtx, prevLetters, prevOutput) ->
                    val prevTouches = if (useTouches) prevLetters.map { spatial.centre(it) } else null
                    val p = engine.proposeRevision(prevCtx, prevLetters, prevOutput, output, prevTouches, revisionProtection(prevLetters, prevOutput), isBlocked)
                    if (p != null && p.candidate.word != prevOutput) {
                        val event = RevisionEvent(index - 1, prevOutput, p.candidate.word, sentence.words[index - 1], p)
                        if (keepProposals) proposals.add(event)
                        if (revisionApplies(p, prevLetters, prevOutput)) {
                            revisions.add(event)
                            if (keepEvents) events.add("revision: $prevOutput -> ${p.candidate.word} (next $output)")
                        }
                    }
                }
                previous = Triple(ctx, letters, output)
            } else {
                previous = null
            }
            context.add(word.lowercase())
        }
        return CleanSentence(
            sentence.id, sentence.lang, taps, needed, sentence.words.size, nextWordHits, picks, desyncs, events, revisions,
            proposals,
        )
    }

    fun runNoisy(
        sentence: TapSentence,
        latency: LatencyRecorder? = null,
        reviseLatency: LatencyRecorder? = null,
    ): NoisySentence {
        val outputs = ArrayList<String>()
        val revisions = ArrayList<RevisionEvent>()
        val proposals = ArrayList<RevisionEvent>()
        var previousTouches: List<TouchPoint>? = null
        for ((i, w) in sentence.words.withIndex()) {
            val touches: List<TouchPoint>? = if (useTouches) BenchData.touchesFor(w, spatial) else null
            val ctx = PredictionContext(outputs.toList(), startsSentence = true)
            val res = timed(latency) { engine.suggest(ctx, w.literal, touches, isProtected = isProtected, isBlocked = isBlocked) }
            outputs.add(autocorrectOf(res, w.literal)?.word ?: w.literal)
            if (i > 0) {
                // Space after word i: the phone may now change word i - 1 (its context is the text before it).
                val prevCtx = PredictionContext(outputs.subList(0, i - 1).toList(), startsSentence = true)
                val prev = sentence.words[i - 1]
                val t0 = System.nanoTime()
                val p = engine.proposeRevision(prevCtx, prev.literal, outputs[i - 1], outputs[i], previousTouches, revisionProtection(prev.literal, outputs[i - 1]), isBlocked)
                reviseLatency?.add(System.nanoTime() - t0)
                if (p != null && p.candidate.word != outputs[i - 1]) {
                    val event = RevisionEvent(i - 1, outputs[i - 1], p.candidate.word, prev.word, p)
                    if (keepProposals) proposals.add(event)
                    if (revisionApplies(p, prev.literal, outputs[i - 1])) {
                        revisions.add(event)
                        outputs[i - 1] = p.candidate.word
                    }
                }
            }
            previousTouches = touches
        }
        val ref = sentence.words.map { BenchText.fold(it.word) }
        val hyp = outputs.map { BenchText.fold(it) }
        var typedCorrect = 0
        var falseCorr = 0
        var typos = 0
        var fixed = 0
        for ((i, w) in sentence.words.withIndex()) {
            if (w.literal == BenchText.typedForm(w.word)) {
                typedCorrect++
                if (hyp[i] != ref[i]) falseCorr++
            } else {
                typos++
                if (hyp[i] == ref[i]) fixed++
            }
        }
        return NoisySentence(
            sentence.id, sentence.lang, ref.size, wordEditDistance(ref, hyp), typedCorrect, falseCorr, typos, fixed,
            ref, outputs, sentence.words.map { it.literal }, revisions, proposals,
        )
    }

    /**
     * The word the noisy rule leaves for [check] (autocorrect target or the literal). Always typed with key codes:
     * centre touches would make the literal look more certain than real taps do, so this is the conservative case.
     */
    fun runLiteralCheck(check: LiteralCheck): String {
        val ctx = PredictionContext(check.context.map { it.lowercase() }, startsSentence = true)
        val res = engine.suggest(ctx, check.input, isProtected = isProtected, isBlocked = isBlocked)
        return autocorrectOf(res, check.input)?.word ?: check.input
    }

    /**
     * [runLiteralCheck] followed by the word [next] and space: the word the phone leaves after a possible right-context
     * revision. Key codes, like [runLiteralCheck].
     */
    fun runLiteralCheckWithNext(check: LiteralCheck, next: String): String = literalCheckProposal(check, next)
        .let { (committed, p) -> p?.takeIf { revisionApplies(it, check.input, committed) }?.candidate?.word ?: committed }

    /** The committed word of [check] and the revision proposal once [next] follows. */
    fun literalCheckProposal(check: LiteralCheck, next: String): Pair<String, RevisionProposal?> {
        val ctx = PredictionContext(check.context.map { it.lowercase() }, startsSentence = true)
        val committed = runLiteralCheck(check)
        return committed to engine.proposeRevision(ctx, check.input, committed, next, isProtected = revisionProtection(check.input, committed), isBlocked = isBlocked)
    }

    companion object {
        const val PICK_BUCKETS = 6

        /** Word-level Levenshtein distance (bench.py `word_edit_distance`). */
        fun wordEditDistance(ref: List<String>, hyp: List<String>): Int {
            val m = hyp.size
            val d = IntArray(m + 1) { it }
            for (i in 1..ref.size) {
                var prev = d[0]
                d[0] = i
                for (j in 1..m) {
                    val cur = d[j]
                    d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (ref[i - 1] != hyp[j - 1]) 1 else 0)
                    prev = cur
                }
            }
            return d[m]
        }

        fun summarizeClean(rows: List<CleanSentence>): Map<String, Any> {
            val taps = rows.sumOf { it.taps }
            val needed = rows.sumOf { it.needed }
            val words = rows.sumOf { it.words }
            val nw = rows.sumOf { it.nextWordHits }
            val picks = IntArray(PICK_BUCKETS)
            rows.forEach { r -> r.picksByTyped.forEachIndexed { i, v -> picks[i] += v } }
            return linkedMapOf(
                "sentences" to rows.size, "words" to words, "taps" to taps, "needed" to needed,
                "ksr" to 1.0 - taps.toDouble() / maxOf(needed, 1), "next_word_hits" to nw,
                "next_word_hit_rate" to nw.toDouble() / maxOf(words, 1), "picks_by_letters_typed" to picks.toList(),
                "desyncs" to rows.sumOf { it.desyncs },
                "revisions" to rows.sumOf { it.revisions.size },
                "false_revisions" to rows.sumOf { r -> r.revisions.count { it.isWrong } },
            )
        }

        fun summarizeNoisy(rows: List<NoisySentence>): Map<String, Any> {
            val words = rows.sumOf { it.words }
            val edits = rows.sumOf { it.edits }
            val ok = rows.sumOf { it.typedCorrect }
            val typos = rows.sumOf { it.typoWords }
            return linkedMapOf(
                "sentences" to rows.size, "words" to words, "edits" to edits, "wer" to edits.toDouble() / maxOf(words, 1),
                "literal_wer" to typos.toDouble() / maxOf(words, 1), "typed_correct_words" to ok,
                "false_corrections" to rows.sumOf { it.falseCorrections },
                "false_correction_rate" to rows.sumOf { it.falseCorrections }.toDouble() / maxOf(ok, 1),
                "typo_words" to typos, "typos_fixed" to rows.sumOf { it.typosFixed },
                "typo_fix_rate" to rows.sumOf { it.typosFixed }.toDouble() / maxOf(typos, 1),
                "revisions" to rows.sumOf { it.revisions.size },
                "revisions_right" to rows.sumOf { r -> r.revisions.count { it.isRight } },
                "revisions_wrong" to rows.sumOf { r -> r.revisions.count { it.isWrong } },
            )
        }
    }
}
