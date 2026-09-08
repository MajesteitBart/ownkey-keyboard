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

package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun join(before: String, transcript: String, after: String = "") =
    DictationInsertionSpacing.join(transcript = transcript, textBefore = before, textAfter = after)

class DictationInsertionSpacingTest : FunSpec({
    test("a phrase dictated after sentence punctuation gets the missing space") {
        join("Let's demo the keyboard.", "When I press longer") shouldBe " When I press longer"
        join("Really?", "Yes") shouldBe " Yes"
        join("Wait…", "then") shouldBe " then"
    }

    test("a phrase dictated after a word gets one separating space") {
        join("hello", "world") shouldBe " world"
        join("version 2", "is out") shouldBe " is out"
    }

    test("existing whitespace before the cursor is never doubled") {
        join("hello ", "world") shouldBe "world"
        join("hello\n", "world") shouldBe "world"
        join("hello\t", "world") shouldBe "world"
        join("hello", " world") shouldBe " world"
    }

    test("an empty field or unavailable context inserts the transcript unchanged") {
        join("", "Hello there") shouldBe "Hello there"
    }

    test("the transcript never changes outside its own boundaries") {
        val before = "keep me."
        val after = "and me"
        val joined = DictationInsertionSpacing.join("new words", before, after)
        joined.startsWith(" new words") shouldBe true
        joined.endsWith("new words ") shouldBe true
        joined.contains(before) shouldBe false
        joined.contains(after) shouldBe false
    }

    test("punctuation that attaches to the previous word gets no space before it") {
        join("hello", ", world") shouldBe ", world"
        join("hello", ".") shouldBe "."
        join("hello", "!") shouldBe "!"
        join("(note", ")") shouldBe ")"
    }

    test("opening brackets and quotes are not followed by a space") {
        join("(", "note") shouldBe "note"
        join("he said \"", "hi") shouldBe "hi"
        join("[", "sic") shouldBe "sic"
        join("“", "quoted") shouldBe "quoted"
    }

    test("closing quotes and brackets are followed by a space") {
        join("he said \"hi\"", "then left") shouldBe " then left"
        join("(note)", "done") shouldBe " done"
        join("”", "next") shouldBe " next"
    }

    test("inserting in the middle of text separates both sides") {
        join("Hello ", "there", "world") shouldBe "there "
        join("Hello", "there", " world") shouldBe " there"
        join("Hello", "there", "world") shouldBe " there "
        join("Hello", "there", ".") shouldBe " there"
        join("Hello ", "there", ", world") shouldBe "there"
    }

    test("inserting at the beginning of text only separates the trailing side") {
        join("", "Hello", "world") shouldBe "Hello "
        join("", "Hello", " world") shouldBe "Hello"
    }

    test("replacing a selection uses the text around the selection") {
        // "one [two] three" with "two" selected and replaced by "2".
        join("one ", "2", " three") shouldBe "2"
        // "one[two]three" with "two" selected.
        join("one", "2", "three") shouldBe " 2 "
    }

    test("scripts without inter-word spaces get no space on either side") {
        join("你好", "世界") shouldBe "世界"
        join("こんにちは", "世界") shouldBe "世界"
        join("สวัสดี", "ครับ") shouldBe "ครับ"
        join("hello", "世界") shouldBe "世界"
        join("你好", "world") shouldBe "world"
        join("你好", "世界", "你好") shouldBe "世界"
    }

    test("an empty transcript is returned untouched") {
        join("hello", "") shouldBe ""
    }
})
