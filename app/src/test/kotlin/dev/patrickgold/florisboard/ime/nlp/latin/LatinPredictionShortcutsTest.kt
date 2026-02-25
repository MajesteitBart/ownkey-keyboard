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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThan
import kotlin.system.measureNanoTime

class LatinPredictionShortcutsTest : FunSpec({
    test("prefix lookup returns highest-frequency matches in order") {
        val shortcuts = LatinPredictionShortcuts(
            words = mapOf(
                "hello" to 100,
                "help" to 80,
                "helium" to 60,
                "hero" to 75,
                "helmet" to 50,
                "zebra" to 40,
            ),
            maxPrefixDepth = 3,
            prefixPoolSize = 8,
            fallbackPoolSize = 8,
        )

        val candidates = shortcuts.lookupPrefixCandidates(input = "hel", maxCount = 3)

        candidates.map { it.word } shouldContainExactly listOf("hello", "help", "helium")
    }

    test("fallback candidates are frequency sorted and exclude previous word") {
        val shortcuts = LatinPredictionShortcuts(
            words = mapOf(
                "the" to 1200,
                "and" to 1100,
                "to" to 1000,
                "of" to 900,
                "in" to 800,
            ),
            fallbackPoolSize = 5,
        )

        val candidates = shortcuts.fallbackCandidates(previousWord = "and", maxCount = 3)

        candidates.map { it.word } shouldContainExactly listOf("the", "to", "of")
    }

    test("benchmark scenario keeps top-3 lookup latency p95 under 50 ms") {
        val words = buildMap {
            for (i in 0 until 20_000) {
                put("word$i", 20_000 - i)
            }
            for (i in 0 until 10_000) {
                put("th${'a' + (i % 26)}token$i", 15_000 - i)
                put("pre${'a' + (i % 26)}dict$i", 10_000 - i)
            }
        }
        val shortcuts = LatinPredictionShortcuts(
            words = words,
            maxPrefixDepth = 3,
            prefixPoolSize = 64,
            fallbackPoolSize = 64,
        )
        val inputs = buildList {
            repeat(50) {
                addAll(listOf("t", "th", "tha", "the", "p", "pr", "pre"))
            }
        }

        val samplesMs = inputs.map { input ->
            measureNanoTime {
                val top3 = shortcuts.lookupPrefixCandidates(input = input, maxCount = 3)
                top3.size shouldBeLessThan 4
            } / 1_000_000.0
        }.sorted()
        val p95 = samplesMs[((samplesMs.size - 1) * 0.95).toInt()]

        p95 shouldBeLessThan 50.0
    }
})
