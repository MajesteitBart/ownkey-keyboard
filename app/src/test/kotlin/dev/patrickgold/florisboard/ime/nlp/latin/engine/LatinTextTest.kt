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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class LatinTextTest : FunSpec({
    fun tokens(text: String) = LatinText.extractWordTokens(text, Locale.ROOT)

    test("closing quotes and dashes do not stick to the word before them") {
        tokens("She said 'hello' to me") shouldBe listOf("she", "said", "hello", "to", "me")
        tokens("Hij zei ‘dag’ en ging") shouldBe listOf("hij", "zei", "dag", "en", "ging")
        tokens("wait- what") shouldBe listOf("wait", "what")
    }

    test("apostrophes and hyphens inside words stay") {
        tokens("don't z'n well-known") shouldBe listOf("don't", "z'n", "well-known")
    }

    test("the word at the end may still be being typed, so it keeps its apostrophe") {
        tokens("I typed don'") shouldBe listOf("i", "typed", "don'")
    }
})
