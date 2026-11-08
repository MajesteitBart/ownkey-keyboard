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

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.random.Random

class DecoderTest : FunSpec({
    val config = EngineConfig()
    val decoder = Decoder(config)
    val spatial = SpatialModel.qwerty()

    fun fullHits(lex: Lexicon, input: String, maxEdits: Int = 2): Map<String, Pair<Float, Int>> {
        val out = DecodeResult()
        decoder.decode(lex, Channel.build(input, spatial, null), maxEdits, out)
        return (0 until out.fullCount).associate { lex.keyForm(out.fullPos[it]) to (out.fullCost[it] to out.fullEdits[it]) }
    }

    context("full-word corrections are found with the expected edit count") {
        val en = Lexicon.build(TestModels.en)
        val nl = Lexicon.build(TestModels.nl)
        withData(
            nameFn = { "${it.first.second} -> ${it.first.third}" },
            Triple(en, "teh", "the") to 1, // transposition
            Triple(en, "becuase", "because") to 1, // transposition
            Triple(en, "helo", "hello") to 1, // omitted repeated letter
            Triple(en, "helllo", "hello") to 1, // double tap
            Triple(en, "wprld", "world") to 1, // neighbour substitution
            Triple(en, "hwllo", "hello") to 1,
            Triple(nl, "gistren", "gisteren") to 1, // omission
            Triple(nl, "morgne", "morgen") to 1,
            Triple(en, "becuse", "because") to 1,
            Triple(en, "accomodate", "accommodate") to 1,
        ) { (case, edits) ->
            val (lex, input, target) = case
            val hits = fullHits(lex, input, if (input.length <= 3) 1 else 2)
            hits.keys shouldContain target
            hits[target]!!.second shouldBe edits
        }
    }

    test("exact input costs only the match cost and has zero edits") {
        val en = Lexicon.build(TestModels.en)
        val hits = fullHits(en, "hello")
        hits["hello"]!!.second shouldBe 0
        hits["hello"]!!.first shouldBeLessThan 1.5f
    }

    test("a neighbour substitution is cheaper than a distant one") {
        val lex = Lexicon.build(TestModels.model("en", "cat" to 10))
        val near = fullHits(lex, "cst", 1)["cat"]!!
        val far = fullHits(lex, "cpt", 1)["cat"]!!
        near.second shouldBe 1
        far.second shouldBe 1
        near.first shouldBeLessThan far.first
        near.first shouldBeLessThan 5f
    }

    test("completions: the input consumed inside a node yields its subtree") {
        val en = Lexicon.build(TestModels.en)
        val out = DecodeResult()
        decoder.decode(en, Channel.build("consi", spatial, null), 2, out)
        out.subtreeCount shouldBeGreaterThan 0
        val cheapest = (0 until out.subtreeCount).minByOrNull { out.subCost[it] }!!
        val lo = out.subLo[cheapest]
        val hi = out.subHi[cheapest]
        en.keyForm(lo).startsWith("consi") shouldBe true
        val buf = IntArray(10)
        val n = en.topByUnigram(lo, hi, 10, buf)
        val words = buf.take(n).map { en.keyForm(it) }
        words shouldContainAll listOf("consider", "considered", "considering", "consistent")
    }

    test("short inputs with one edit still reach transpositions and the search stays small") {
        val en = Lexicon.build(TestModels.en)
        val out = DecodeResult()
        decoder.decode(en, Channel.build("teh", spatial, null), 1, out)
        val words = (0 until out.fullCount).map { en.keyForm(out.fullPos[it]) }
        words shouldContain "the"
        words shouldContain "ten"
        out.nodesVisited shouldNotBe 0
        (out.nodesVisited < 20_000) shouldBe true
    }

    test("a transposition right after a hopeless row is still found") {
        // Omitted first letter plus a transposition: row "he" alone is over budget, the swap at "her" is not.
        val nl = Lexicon.build(TestModels.nl)
        fullHits(nl, "eirnneringen").keys shouldContain "herinneringen"
    }

    test("matches a brute-force DP with the same operations on random lexicons") {
        val rnd = Random(7)
        val alphabet = "abcdeilnorst"
        fun randomWord(maxLen: Int) = String(CharArray(1 + rnd.nextInt(maxLen)) { alphabet[rnd.nextInt(alphabet.length)] })
        val words = HashSet<String>()
        while (words.size < 2000) words.add(randomWord(8))
        val lex = Lexicon.build(UnigramListModel.fromCounts("xx", words.map { it to 1L + rnd.nextInt(1000) }))
        val maxCost = config.maxChannelCost
        var missing = 0
        var extra = 0
        var costDiff = 0
        val examples = ArrayList<String>()
        repeat(600) {
            val input = randomWord(7)
            val touches = if (rnd.nextBoolean()) {
                input.map { c -> spatial.centre(c).let { TouchPoint(it.x + rnd.nextDouble(-0.6, 0.6), it.y + rnd.nextDouble(-0.6, 0.6)) } }
            } else {
                null
            }
            val channel = Channel.build(input, spatial, touches)
            val e = if (input.length <= 3) 1 else 2
            val out = DecodeResult()
            decoder.decode(lex, channel, e, out)
            val got = (0 until out.fullCount).associate { lex.keyForm(out.fullPos[it]) to out.fullCost[it].toDouble() }
            for (w in words) {
                val ref = referenceCost(config, w, input, channel, e)
                if (abs(ref - maxCost) < 1e-3) continue // float rounding at the budget edge
                val g = got[w]
                when {
                    ref <= maxCost && g == null -> { missing++; if (examples.size < 5) examples.add("missing $input -> $w ($ref)") }
                    ref > maxCost && g != null -> { extra++; if (examples.size < 5) examples.add("extra $input -> $w ($g)") }
                    g != null && abs(ref - g) > 1e-3 -> { costDiff++; if (examples.size < 5) examples.add("cost $input -> $w: $g vs $ref") }
                }
            }
        }
        examples shouldBe emptyList()
        (missing + extra + costDiff) shouldBe 0
    }

    test("touch points override the key-code channel") {
        val lex = Lexicon.build(TestModels.model("en", "of" to 100, "if" to 100))
        val i = spatial.centre('i')
        val o = spatial.centre('o')
        // The keyboard reported "if", but the first touch landed next to the o/i boundary on the o side.
        val touches = listOf(TouchPoint((i.x + o.x) / 2 + 0.05, i.y), spatial.centre('f'))
        val out = DecodeResult()
        decoder.decode(lex, Channel.build("if", spatial, touches), 1, out)
        val hits = (0 until out.fullCount).associate { lex.keyForm(out.fullPos[it]) to out.fullCost[it] }
        hits["of"]!! shouldBeLessThan hits["if"]!!
    }
})

/**
 * Cheapest alignment cost of intended [word] with typed [input] under at most [maxEdits] edits: a plain DP over
 * the same operations and costs as [Decoder], without trie, band or pruning.
 */
private fun referenceCost(config: EngineConfig, word: String, input: String, ch: Channel, maxEdits: Int): Double {
    val inf = 1e9
    val m = word.length
    val n = input.length
    val first = config.firstLetterPenalty
    val dp = Array(m + 1) { Array(n + 1) { DoubleArray(maxEdits + 1) { inf } } }
    dp[0][0][0] = 0.0
    for (i in 1..minOf(n, maxEdits)) dp[0][i][i] = dp[0][i - 1][i - 1] + config.insertionCost + if (i == 1) first else 0.0
    for (d in 1..m) {
        val c = word[d - 1]
        val pc = if (d >= 2) word[d - 2] else '\u0000'
        val omit = (if (d >= 2 && c == pc) config.repeatOmissionCost else config.omissionCost) + if (d == 1) first else 0.0
        for (i in 0..n) for (k in 0..maxEdits) {
            var best = inf
            if (k >= 1) best = minOf(best, dp[d - 1][i][k - 1] + omit)
            if (i >= 1) {
                val sub = ch.cost(i - 1, c).toDouble()
                val se = if (input[i - 1] == c) 0 else 1
                val sp = if (d == 1 && i == 1 && se == 1 && !ch.usesTouches) first else 0.0
                if (k >= se) best = minOf(best, dp[d - 1][i - 1][k - se] + sub + sp)
                val ins = minOf(config.insertionCost, config.repeatInsertionCost + sub) + if (i == 1) first else 0.0
                if (k >= 1) best = minOf(best, dp[d][i - 1][k - 1] + ins)
            }
            if (d >= 2 && i >= 2 && c != pc) {
                val tc = config.transpositionCost + ch.cost(i - 2, c) + ch.cost(i - 1, pc) + if (d == 2) first else 0.0
                val te = 1 + (if (input[i - 2] != c) 1 else 0) + (if (input[i - 1] != pc) 1 else 0)
                if (k >= te) best = minOf(best, dp[d - 2][i - 2][k - te] + tc)
            }
            dp[d][i][k] = best
        }
    }
    return dp[m][n].min()
}
