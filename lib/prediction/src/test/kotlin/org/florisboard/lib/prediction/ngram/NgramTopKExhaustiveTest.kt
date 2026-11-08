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

package org.florisboard.lib.prediction.ngram

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * `topNext` and `topCompletions` against an exhaustive ranking of the whole vocabulary by `logProb`, on both shipped
 * models, for a sample of unigram, bigram and trigram contexts. A frequent word that is an explicit but unlikely
 * successor of the context once came back with its higher unigram-backoff score ("the" -> and, is, in).
 */
class NgramTopKExhaustiveTest : FunSpec({
    fun assetFile(lang: String): File? = listOfNotNull(
        System.getProperty("ownkey.repoRoot")?.let { File(it, "app/src/main/assets/ime/dict/ngram/$lang.bin") },
        File("../../app/src/main/assets/ime/dict/ngram/$lang.bin"),
        File("app/src/main/assets/ime/dict/ngram/$lang.bin"),
    ).firstOrNull { it.isFile }

    /** The best [k] ids of [ids] by `logProb` in the context, ties by lower id, as `topNext` orders them. */
    fun exhaustive(m: NgramModel, ids: IntRange, prev1: Int, prev2: Int, k: Int): List<NgramCandidate> {
        val all = ArrayList<NgramCandidate>(ids.count())
        for (id in ids) all.add(NgramCandidate(id, m.logProb(id, prev1, prev2)))
        return all.sortedWith(compareByDescending<NgramCandidate> { it.logProb }.thenBy { it.wordId }).take(k)
    }

    val contexts = mapOf(
        "en" to listOf("", "the", "i", "you", "to", "of", "and", "a", "it's", "going", "i am", "want to", "of the", "in the"),
        "nl" to listOf("", "de", "ik", "je", "het", "een", "van", "en", "naar", "ik ben", "ga je", "op de", "dat is"),
    )
    val prefixes = mapOf("en" to listOf("t", "th", "w", "go", "ab"), "nl" to listOf("d", "ge", "wa", "m", "zi"))

    for (lang in listOf("en", "nl")) {
        val file = assetFile(lang)
        test("$lang topNext and topCompletions equal the exhaustive ranking").config(enabled = file != null) {
            val m = NgramModel.load(file!!)
            for (context in contexts.getValue(lang)) {
                val words = context.split(' ').filter { it.isNotEmpty() }
                val prev1 = if (words.isEmpty()) NgramModel.BOS_ID else m.wordId(words.last())
                val prev2 = when {
                    words.size >= 2 -> m.wordId(words[words.size - 2])
                    words.size == 1 -> NgramModel.BOS_ID
                    else -> NgramModel.UNKNOWN_ID
                }
                for (k in listOf(3, 10)) {
                    withClue("$lang topNext('$context', $k)") {
                        m.topNext(prev1, prev2, k) shouldBe exhaustive(m, 1 until m.vocabularySize, prev1, prev2, k)
                    }
                }
                for (prefix in prefixes.getValue(lang)) {
                    val range = m.prefixRange(prefix)
                    withClue("$lang topCompletions('$prefix', '$context')") {
                        m.topCompletions(prefix, prev1, prev2, 5) shouldBe exhaustive(m, range, prev1, prev2, 5)
                    }
                }
            }
        }
    }
})
