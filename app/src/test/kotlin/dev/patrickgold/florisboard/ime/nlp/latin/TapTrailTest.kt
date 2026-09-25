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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class TapTrailTest : FunSpec({
    beforeTest { TapTrail.clear() }

    fun type(text: String) = text.forEach { TapTrail.record(it.code, 0f, 0f) }

    test("taps are handed out only when they spell the word") {
        type("hello")
        TapTrail.tapsFor("hello").shouldNotBeNull().size shouldBe 5
        TapTrail.tapsFor("Hello").shouldNotBeNull().size shouldBe 5
        TapTrail.tapsFor("llo").shouldNotBeNull().size shouldBe 3
        TapTrail.tapsFor("help").shouldBeNull()
        TapTrail.tapsFor("ahello").shouldBeNull()
    }

    test("backspace removes the last tap") {
        type("thw")
        TapTrail.removeLast()
        type("e")
        TapTrail.tapsFor("the").shouldNotBeNull().size shouldBe 3
    }

    test("word boundaries and characters outside words") {
        type("don't")
        TapTrail.tapsFor("don't").shouldNotBeNull().size shouldBe 5
        TapTrail.tapsFor("don’t").shouldNotBeNull().size shouldBe 5
        TapTrail.record('.'.code, 0f, 0f)
        TapTrail.tapsFor("don't").shouldNotBeNull()
        TapTrail.clear()
        TapTrail.tapsFor("don't").shouldBeNull()
    }

    test("text that was not tapped gets no taps") {
        type("xy")
        // "hey" was glided or pasted; only "y" was tapped last, so the word does not match.
        TapTrail.tapsFor("hey").shouldBeNull()
    }

    test("the trail keeps a bounded number of taps") {
        type("a".repeat(100))
        TapTrail.tapsFor("a".repeat(48)).shouldNotBeNull()
        TapTrail.tapsFor("a".repeat(49)).shouldBeNull()
    }
})
