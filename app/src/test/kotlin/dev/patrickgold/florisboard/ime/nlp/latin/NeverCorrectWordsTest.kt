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
import io.kotest.matchers.shouldBe

class NeverCorrectWordsTest : FunSpec({
    test("suppression entries are deduplicated and moved to the front") {
        val words = NeverCorrectWords.Empty
            .suppress(normalizedWord = "teh", language = "en")
            .suppress(normalizedWord = "spelng", language = "en")
            .suppress(normalizedWord = "teh", language = "en")

        words.entries.shouldContainExactly(
            NeverCorrectWordEntry(word = "teh", language = "en"),
            NeverCorrectWordEntry(word = "spelng", language = "en"),
        )
    }

    test("contains only matches the scoped language entry") {
        val words = NeverCorrectWords.Empty
            .suppress(normalizedWord = "teh", language = "en")
            .suppress(normalizedWord = "teh", language = "nl")

        words.contains(normalizedWord = "teh", languages = setOf("en")) shouldBe true
        words.contains(normalizedWord = "teh", languages = setOf("nl")) shouldBe true
        words.contains(normalizedWord = "teh", languages = setOf("de")) shouldBe false
    }

    test("remove deletes only the targeted scoped entry") {
        val words = NeverCorrectWords.Empty
            .suppress(normalizedWord = "teh", language = "en")
            .suppress(normalizedWord = "teh", language = "nl")
            .remove(normalizedWord = "teh", language = "en")

        words.entries.shouldContainExactly(
            NeverCorrectWordEntry(word = "teh", language = "nl"),
        )
    }
})
