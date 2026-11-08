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

package org.florisboard.lib.prediction

/**
 * Per-input channel costs: `cost(i, c)` is `-ln P(typed position i | intended letter c)` from either the key-code
 * confusion model or the touch point of position [i]. [input] is the typed key form (normally `a`-`z`); edits are
 * counted against it.
 */
class Channel private constructor(
    val input: String,
    private val costs: FloatArray,
    private val floorCost: Float,
    /** Costs come from touch points rather than key codes. */
    val usesTouches: Boolean,
) {
    val length: Int get() = input.length

    /** Cost of explaining typed position [i] by intended letter [c]. */
    fun cost(i: Int, c: Char): Float {
        val k = c - 'a'
        if (k in 0 until SpatialModel.KEYS) return costs[i * SpatialModel.KEYS + k]
        return if (input[i] == c) 0f else floorCost
    }

    /** Cost of reading the input as typed (every position explained by its own letter). */
    val literalCost: Float = (0 until input.length).fold(0f) { acc, i -> acc + cost(i, input[i]) }

    /** `posMin[i]` = lower bound on the cost of explaining typed position `i` by any letter. */
    internal val posMin: FloatArray = FloatArray(input.length) { i ->
        if (SpatialModel.keyIndex(input[i]) < 0) {
            0f
        } else {
            var m = Float.MAX_VALUE
            for (k in 0 until SpatialModel.KEYS) m = minOf(m, costs[i * SpatialModel.KEYS + k])
            m
        }
    }

    /** `suffixMin[i]` = lower bound on the cost of explaining positions `i until length`. */
    internal val suffixMin: FloatArray = FloatArray(input.length + 1).also { s ->
        for (i in input.length - 1 downTo 0) s[i] = s[i + 1] + posMin[i]
    }

    companion object {
        /**
         * Builds the channel for [keyForm]. When [touches] has one point per character, the touch model is used,
         * otherwise the key-code model.
         */
        fun build(keyForm: String, spatial: SpatialModel, touches: List<TouchPoint>?): Channel {
            val n = keyForm.length
            val costs = FloatArray(n * SpatialModel.KEYS)
            val useTouches = touches != null && touches.size == n
            for (i in 0 until n) {
                val off = i * SpatialModel.KEYS
                val t = SpatialModel.keyIndex(keyForm[i])
                when {
                    useTouches && t >= 0 -> spatial.touchCosts(touches[i], costs, off)
                    t >= 0 -> for (k in 0 until SpatialModel.KEYS) costs[off + k] = spatial.keyCost(k, t)
                    else -> costs.fill(spatial.floorCost, off, off + SpatialModel.KEYS)
                }
            }
            return Channel(keyForm, costs, spatial.floorCost, useTouches)
        }
    }
}

/**
 * Output of one [Decoder.decode] call: full-word hits (input consumed exactly at a word end) and completion
 * subtrees (input consumed inside a node; every word below it is a completion with the node's cost).
 */
class DecodeResult {
    var fullCount = 0
        private set
    var fullPos = IntArray(64)
        private set
    var fullCost = FloatArray(64)
        private set
    var fullEdits = IntArray(64)
        private set

    var subtreeCount = 0
        private set
    var subLo = IntArray(64)
        private set
    var subHi = IntArray(64)
        private set
    var subDepth = IntArray(64)
        private set
    var subOwnWord = BooleanArray(64)
        private set
    var subCost = FloatArray(64)
        private set
    var subEdits = IntArray(64)
        private set

    /** Trie nodes whose DP row was computed (search effort). */
    var nodesVisited = 0
        internal set

    fun clear() {
        fullCount = 0
        subtreeCount = 0
        nodesVisited = 0
    }

    internal fun addFull(pos: Int, cost: Float, edits: Int) {
        if (fullCount == fullPos.size) {
            val n = fullPos.size * 2
            fullPos = fullPos.copyOf(n); fullCost = fullCost.copyOf(n); fullEdits = fullEdits.copyOf(n)
        }
        fullPos[fullCount] = pos; fullCost[fullCount] = cost; fullEdits[fullCount] = edits
        fullCount++
    }

    internal fun addSubtree(lo: Int, hi: Int, depth: Int, ownWord: Boolean, cost: Float, edits: Int) {
        if (subtreeCount == subLo.size) {
            val n = subLo.size * 2
            subLo = subLo.copyOf(n); subHi = subHi.copyOf(n); subDepth = subDepth.copyOf(n)
            subOwnWord = subOwnWord.copyOf(n); subCost = subCost.copyOf(n); subEdits = subEdits.copyOf(n)
        }
        subLo[subtreeCount] = lo; subHi[subtreeCount] = hi; subDepth[subtreeCount] = depth
        subOwnWord[subtreeCount] = ownWord; subCost[subtreeCount] = cost; subEdits[subtreeCount] = edits
        subtreeCount++
    }
}

/**
 * Weighted edit-distance search of a typed key form over a [Lexicon]'s virtual trie.
 *
 * One DP row per trie node holds, for typed prefix length `i` and edit count `e`, the cheapest cost of aligning
 * the node's prefix with `input[0, i)`. Operations: match or substitution (spatial cost; counts as an edit when
 * the typed letter differs), omission of an intended letter (cheaper when it repeats the previous letter),
 * insertion of an extra typed letter (generic, or a repeat tap aimed at the previous intended letter plus its
 * spatial cost) and adjacent transposition (plus the spatial cost of both letters). Edits at the first letter pay
 * [EngineConfig.firstLetterPenalty] (substitutions only in key-code mode). Rows are limited to the band
 * `|i - depth| <= maxEdits`, and a subtree is pruned when no cell, plus a lower bound for the remaining input,
 * stays within [EngineConfig.maxChannelCost], and no transposition from the parent row (which skips this row) can
 * either.
 *
 * Stateless apart from the configuration; safe to share between threads.
 */
class Decoder(private val config: EngineConfig) {
    private val omission = config.omissionCost.toFloat()
    private val repeatOmission = config.repeatOmissionCost.toFloat()
    private val insertion = config.insertionCost.toFloat()
    private val repeatInsertion = config.repeatInsertionCost.toFloat()
    private val transposition = config.transpositionCost.toFloat()
    private val firstPenalty = config.firstLetterPenalty.toFloat()
    private val maxCost = config.maxChannelCost.toFloat()

    /** Decodes [channel] against [lexicon] with at most [maxEdits] edits into [out] (cleared first). */
    fun decode(lexicon: Lexicon, channel: Channel, maxEdits: Int, out: DecodeResult) {
        out.clear()
        if (channel.length == 0 || lexicon.size == 0) return
        Search(lexicon, channel, maxEdits.coerceAtLeast(0), out).run()
    }

    private inner class Search(
        private val lex: Lexicon,
        private val ch: Channel,
        private val e: Int,
        private val out: DecodeResult,
    ) {
        private val n = ch.length
        private val input = ch.input
        private val w = e + 1
        private val maxDepth = n + e
        private val rows = Array(maxDepth + 1) { FloatArray((n + 1) * w) }
        private val path = CharArray(maxDepth + 1)
        private val suffixMin = ch.suffixMin
        private val posMin = ch.posMin
        private var cellCost = 0f
        private var cellEdits = 0

        fun run() {
            val root = rows[0]
            root.fill(INF)
            root[0] = 0f
            for (i in 1..minOf(n, e)) {
                root[i * w + i] = root[(i - 1) * w + i - 1] + insertion + (if (i == 1) firstPenalty else 0f)
            }
            visit(0, 0, lex.size, INF)
        }

        private fun bestAtEnd(row: FloatArray) {
            var best = INF
            var edits = 0
            val base = n * w
            for (k in 0 until w) {
                if (row[base + k] < best) {
                    best = row[base + k]
                    edits = k
                }
            }
            cellCost = best
            cellEdits = edits
        }

        private fun visit(d: Int, lo: Int, hi: Int, ancestorCompletion: Float) {
            val row = rows[d]
            val ownWord = lex.keyLength(lo) == d
            var ancestor = ancestorCompletion
            if (d > 0) {
                bestAtEnd(row)
                if (cellCost <= maxCost) {
                    if (ownWord) out.addFull(lo, cellCost, cellEdits)
                    if (cellCost < ancestor - 1e-4f) {
                        if (hi - lo > (if (ownWord) 1 else 0)) out.addSubtree(lo, hi, d, ownWord, cellCost, cellEdits)
                        ancestor = cellCost
                    }
                }
            }
            if (d >= maxDepth) return
            var start = if (ownWord) lo + 1 else lo
            while (start < hi) {
                val c = lex.charAt(start, d)
                val end = lex.upperBoundChar(start, hi, d, c)
                if (computeRow(d + 1, c)) visit(d + 1, start, end, ancestor)
                start = end
            }
        }

        /** Fills the row for depth [d] whose last intended letter is [c]; false if the subtree can be pruned. */
        private fun computeRow(d: Int, c: Char): Boolean {
            out.nodesVisited++
            val cur = rows[d]
            val p1 = rows[d - 1]
            val p2 = if (d >= 2) rows[d - 2] else null
            cur.fill(INF)
            path[d - 1] = c
            val pc = if (d >= 2) path[d - 2] else '\u0000'
            val omit = (if (d >= 2 && c == pc) repeatOmission else omission) + (if (d == 1) firstPenalty else 0f)
            val iLo = maxOf(0, d - e)
            val iHi = minOf(n, d + e)
            var rowMin = INF
            for (i in iLo..iHi) {
                val subCost: Float
                val subEdit: Int
                val insCost: Float
                if (i >= 1) {
                    subCost = ch.cost(i - 1, c)
                    subEdit = if (input[i - 1] == c) 0 else 1
                    insCost = minOf(insertion, repeatInsertion + subCost) + (if (i == 1) firstPenalty else 0f)
                } else {
                    subCost = INF
                    subEdit = 0
                    insCost = INF
                }
                var transCost = INF
                var transEdit = 0
                if (p2 != null && i >= 2 && c != pc) {
                    transCost = transposition + ch.cost(i - 2, c) + ch.cost(i - 1, pc) + (if (d == 2) firstPenalty else 0f)
                    transEdit = 1 + (if (input[i - 2] != c) 1 else 0) + (if (input[i - 1] != pc) 1 else 0)
                }
                // With touch points the spatial likelihood already is the evidence for the first letter.
                val subPenalty = if (d == 1 && i == 1 && subEdit == 1 && !ch.usesTouches) firstPenalty else 0f
                val base = i * w
                for (k in 0..e) {
                    var best = INF
                    if (k >= 1) {
                        val v = p1[base + k - 1] + omit
                        if (v < best) best = v
                    }
                    if (i >= 1) {
                        if (k >= subEdit) {
                            val v = p1[base - w + k - subEdit] + subCost + subPenalty
                            if (v < best) best = v
                        }
                        if (k >= 1) {
                            val v = cur[base - w + k - 1] + insCost
                            if (v < best) best = v
                        }
                    }
                    if (k >= transEdit && transCost < INF) {
                        val v = p2!![base - 2 * w + k - transEdit] + transCost
                        if (v < best) best = v
                    }
                    cur[base + k] = best
                    val bound = best + suffixMin[i]
                    if (bound < rowMin) rowMin = bound
                }
            }
            if (rowMin <= maxCost) return true
            return transpositionCanReachChild(d, c, p1)
        }

        /**
         * True when a transposition at depth `d + 1` could stay within budget. It reads row `d - 1` ([p1]) and
         * skips row [d], so a hopeless row [d] does not prove that its subtree is hopeless. The child's transposition
         * explains typed position `i + 1` by [c] (this node's letter) and position `i` by the child's letter.
         */
        private fun transpositionCanReachChild(d: Int, c: Char, p1: FloatArray): Boolean {
            if (e == 0 || d >= maxDepth) return false
            val iLo = maxOf(0, d - 1 - e)
            val iHi = minOf(n - 2, d - 1 + e)
            for (i in iLo..iHi) {
                val tail = transposition + posMin[i] + ch.cost(i + 1, c) + suffixMin[i + 2]
                val base = i * w
                // A transposition is at least one edit, so the parent cell must leave room for it.
                for (k in 0 until e) {
                    if (p1[base + k] + tail <= maxCost) return true
                }
            }
            return false
        }
    }

    companion object {
        private const val INF = 1e9f
    }
}
