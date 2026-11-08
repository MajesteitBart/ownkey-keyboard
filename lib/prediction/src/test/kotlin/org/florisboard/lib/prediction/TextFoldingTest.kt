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
import io.kotest.matchers.shouldBe

class TextFoldingTest : FunSpec({
    context("keyForm matches bench.py fold()") {
        withData(
            "een" to "een",
            "één" to "een",
            "Één" to "een",
            "don't" to "dont",
            "don’t" to "dont",
            "I'm" to "im",
            "ĳs" to "ijs",
            "IJs" to "ijs",
            "café" to "cafe",
            "naïef" to "naief",
            "geïnteresseerd" to "geinteresseerd",
            "patiënt" to "patient",
            "Zoë" to "zoe",
            "é" to "e",
            "" to "",
        ) { (word, expected) ->
            TextFolding.keyForm(word) shouldBe expected
        }
    }

    test("normalizeWord keeps diacritics and apostrophes") {
        TextFolding.normalizeWord("Don’t") shouldBe "don't"
        TextFolding.normalizeWord("Één") shouldBe "één"
    }

    test("letter and word predicates") {
        TextFolding.isKeyLetters("hello") shouldBe true
        TextFolding.isKeyLetters("hell0") shouldBe false
        TextFolding.isKeyLetters("") shouldBe false
        TextFolding.isWordLike("don't") shouldBe true
        TextFolding.isWordLike("dr.") shouldBe false
        TextFolding.isWordLike("mp3") shouldBe false
        TextFolding.isWordLike("'") shouldBe false
    }
})
