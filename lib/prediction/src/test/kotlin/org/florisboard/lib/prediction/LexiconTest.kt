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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class LexiconTest : FunSpec({
    fun Lexicon.group(keyForm: String): List<String> {
        val pos = find(keyForm)
        if (pos < 0) return emptyList()
        return (0 until groupSize(pos)).map { model.word(groupId(pos, it)) }
    }

    test("words are grouped by key form, best unigram first") {
        val lex = Lexicon.build(TestModels.nl)
        lex.group("een").take(3) shouldContainExactly listOf("een", "één", "eén")
        val small = Lexicon.build(TestModels.model("en", "dont" to 10, "don't" to 1000, "done" to 50))
        small.group("dont") shouldContainExactly listOf("don't", "dont")
        small.size shouldBe 2
    }

    test("key forms are sorted and every id maps back to its position") {
        val lex = Lexicon.build(TestModels.en)
        for (p in 1 until lex.size) (lex.keyForm(p - 1) < lex.keyForm(p)) shouldBe true
        for (p in 0 until lex.size) {
            for (g in 0 until lex.groupSize(p)) lex.positionOf(lex.groupId(p, g)) shouldBe p
        }
        lex.find("hello") shouldBeGreaterThan -1
        lex.find("hellooo") shouldBe -1
    }

    test("prefixRange covers exactly the words with that prefix") {
        val lex = Lexicon.build(TestModels.en)
        val range = lex.prefixRange("consi")
        val expected = (0 until lex.size).filter { lex.keyForm(it).startsWith("consi") }
        range.toList() shouldBe expected
        lex.prefixRange("zzzzq").isEmpty() shouldBe true
    }

    test("topByUnigram equals brute force on random ranges") {
        val lex = Lexicon.build(TestModels.en)
        val rnd = Random(7)
        val out = IntArray(20)
        repeat(300) {
            val a = rnd.nextInt(lex.size)
            val b = minOf(lex.size, a + 1 + rnd.nextInt(if (rnd.nextBoolean()) 40 else 20_000))
            val k = 1 + rnd.nextInt(20)
            val n = lex.topByUnigram(a, b, k, out)
            val brute = (a until b).sortedWith(compareByDescending<Int> { lex.bestUnigram(it) }.thenBy { it }).take(k)
            out.take(n) shouldContainExactly brute
        }
    }

    test("topByUnigram on a prefix gives the most frequent completions") {
        val lex = Lexicon.build(TestModels.en)
        val r = lex.prefixRange("consi")
        val out = IntArray(3)
        val n = lex.topByUnigram(r.first, r.last + 1, 3, out)
        out.take(n).map { lex.keyForm(it) } shouldContainExactlyInAnyOrder listOf("consider", "considered", "considering")
    }
})
