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

package dev.patrickgold.florisboard.ime.nlp.latin.engine

import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.AutocorrectBenchmark
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkPolicies
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkSets
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * T-017: words the user typed and kept several times stop being corrected; common typos do not.
 */
class LearnedWordsTest : FunSpec({
    fun hooks(timesTyped: Int) = object : LatinScoringHooks {
        override fun isUserDictionaryWord(normalizedWord: String) = false
        override fun isBlockedByUserPreference(normalizedWord: String) = false
        override suspend fun personalContinuationScore(previousWord: String, candidateWord: String) = 0.0
        override fun timesTyped(normalizedWord: String) = timesTyped
    }

    test("names and terms typed three times before are kept") {
        val words = BenchmarkSets.oov.map { it.lowercase() }
        for ((prefix, languages) in listOf(
            "talk to " to BenchmarkData.enOnly(), "I went to the " to BenchmarkData.enOnly(),
            "ik ga naar de " to BenchmarkData.nlOnly(), "" to BenchmarkData.nlEn(),
        )) {
            val learned = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default(), hooks = hooks(3))
            val result = learned.evaluateOov("learned", languages, words, prefix = prefix)
            println("learned [$prefix]: ${result.changed} ${result.examples}")
            result.changed shouldBeLessThanOrEqual 1
        }
    }

    test("twice is not enough, and a typo of a very common word is still fixed after three times") {
        val twice = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default(), hooks = hooks(2))
        twice.score(BenchmarkData.enOnly(), "thijs", "and thijs").firstOrNull { it.isAutoCommit }?.word shouldBe "this"
        val often = AutocorrectBenchmark(NoisyChannelLatinScorer(), BenchmarkPolicies.default(), hooks = hooks(3))
        often.score(BenchmarkData.enOnly(), "thijs", "and thijs").firstOrNull { it.isAutoCommit }.shouldBeNull()
        often.score(BenchmarkData.enOnly(), "teh", "and teh").firstOrNull { it.isAutoCommit }?.word shouldBe "the"
    }
})
