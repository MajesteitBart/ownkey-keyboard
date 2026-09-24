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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LatinDictionaryCleanupTest : FunSpec({
    test("removal lists only name words that are in the raw lists") {
        for (code in listOf("en", "nl")) {
            val language = BenchmarkData.language(code)
            val removals = BenchmarkData.removals(code)
            withClue("$code removals not in the raw list") {
                removals.filter { it !in language.words }.shouldBeEmpty()
            }
        }
    }

    test("no removed word survives in the shipped model") {
        for (code in listOf("en", "nl")) {
            val language = BenchmarkData.language(code)
            BenchmarkData.removals(code).forEach { word ->
                withClue("$code:$word") { language.model.isKnown(word).shouldBeFalse() }
            }
            language.shippedWords.keys.filter { '`' in it || '�' in it }.shouldBeEmpty()
        }
    }

    test("English keeps only real single-letter words") {
        BenchmarkData.en().shippedWords.keys.filter { it.length == 1 }.sorted() shouldBe listOf("a", "i", "k", "u", "x")
    }

    test("apostrophe-less forms and accepted variants stay") {
        BenchmarkData.en().model.isKnown("dont").shouldBeTrue()
        BenchmarkData.nl().model.isKnown("kado").shouldBeTrue()
        BenchmarkData.nl().model.isKnown("ongelofelijk").shouldBeTrue()
    }

    test("comments and blank lines are ignored in removal lists") {
        LatinDictionaryCleanup.parseRemovalList(sequenceOf("# note", "", "  Untill ", "wich")) shouldBe setOf("untill", "wich")
    }
})
