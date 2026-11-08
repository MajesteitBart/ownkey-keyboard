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
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.PredictionContext

class PredictionContextMapperTest : FunSpec({
    fun map(text: String, input: String = "", fieldStart: Boolean = true): PredictionContext =
        PredictionContextMapper.map(text, input, fieldStart)

    context("current sentence") {
        test("next word after a sentence typed from the field start") {
            map("Ik ga morgen naar ") shouldBe PredictionContext(listOf("ik", "ga", "morgen", "naar"), startsSentence = true)
        }

        test("the same text inside a longer field does not know its sentence start") {
            // The window starts with a complete word ("Ik" after a space); its sentence start is unknown.
            map(" Ik ga morgen naar ", fieldStart = false) shouldBe
                PredictionContext(listOf("ik", "ga", "morgen", "naar"), startsSentence = false)
        }

        test("a word cut by the window start is dropped") {
            map("rgen naar de ", fieldStart = false) shouldBe PredictionContext(listOf("naar", "de"), startsSentence = false)
        }

        test("the composing word is not context") {
            map("Ik ga morgen naar Amst", input = "Amst") shouldBe
                PredictionContext(listOf("ik", "ga", "morgen", "naar"), startsSentence = true)
        }

        test("a composing word that starts a new sentence") {
            map("Hi. how", input = "how") shouldBe
                PredictionContext(emptyList(), startsSentence = true, languageHintWords = listOf("hi"))
        }

        test("an empty field starts a sentence") {
            map("") shouldBe PredictionContext(emptyList(), startsSentence = true)
            map("Hallo", input = "Hallo") shouldBe PredictionContext(emptyList(), startsSentence = true)
        }

        test("a phantom space after an accepted suggestion makes the last word context") {
            // The suggestion was committed without a trailing space and nothing is composing.
            map("I like") shouldBe PredictionContext(listOf("i", "like"), startsSentence = true)
            map("I like", input = "like") shouldBe PredictionContext(listOf("i"), startsSentence = true)
        }

        test("the cursor inside a word strips the part before the cursor") {
            map("we gaan naar huis", input = "huisje") shouldBe PredictionContext(listOf("we", "gaan", "naar"), startsSentence = true)
        }
    }

    context("tokenisation follows the n-gram training rules") {
        test("apostrophes stay inside words and are normalised, edge apostrophes go") {
            map("I don’t know 'cause ") shouldBe
                PredictionContext(listOf("i", "don't", "know", "cause"), startsSentence = true)
        }

        test("hyphens and punctuation split words") {
            map("een e-mail, of niet ") shouldBe PredictionContext(listOf("een", "e", "mail", "of", "niet"), startsSentence = true)
        }

        test("runs with digits are dropped without breaking the sentence") {
            map("I don't 42 know mp3 ") shouldBe PredictionContext(listOf("i", "don't", "know"), startsSentence = true)
        }

        test("diacritics are kept and case is folded") {
            map("Één keer ÉCHT ") shouldBe PredictionContext(listOf("één", "keer", "écht"), startsSentence = true)
        }
    }

    context("sentence boundaries") {
        test("exclamation and question marks and a single period end a sentence; earlier words are hints") {
            map("Wat leuk! Ik kom ") shouldBe
                PredictionContext(listOf("ik", "kom"), startsSentence = true, languageHintWords = listOf("wat", "leuk"))
            map("Really?! ok ") shouldBe
                PredictionContext(listOf("ok"), startsSentence = true, languageHintWords = listOf("really"))
            map("Hello there. ") shouldBe
                PredictionContext(emptyList(), startsSentence = true, languageHintWords = listOf("hello", "there"))
        }

        test("an ellipsis continues the sentence") {
            map("I don't know... maybe ") shouldBe
                PredictionContext(listOf("i", "don't", "know", "maybe"), startsSentence = true)
            map("wacht… even ") shouldBe PredictionContext(listOf("wacht", "even"), startsSentence = true)
        }

        test("a line break starts a sentence") {
            map("Hoi Sanne\nik ") shouldBe
                PredictionContext(listOf("ik"), startsSentence = true, languageHintWords = listOf("hoi", "sanne"))
        }

        test("a boundary inside a window that does not start at the field start is known") {
            // "eerste" may be cut by the window start and is dropped.
            map("eerste zin. tweede ", fieldStart = false) shouldBe
                PredictionContext(listOf("tweede"), startsSentence = true, languageHintWords = listOf("zin"))
        }

        test("long sentences keep the last words and lose the known sentence start") {
            val letters = (0 until 15).map { "word" + ('a' + it) }
            val result = map(letters.joinToString(" ") + " ")
            result.words shouldBe letters.takeLast(PredictionContextMapper.MaxSentenceWords)
            result.startsSentence shouldBe false
            result.languageHintWords shouldBe letters.take(3)
        }

        test("language hints are capped") {
            val earlier = (0 until 20).joinToString(". ") { "zin" + ('a' + it) }
            val result = map("$earlier. nu ")
            result.words shouldBe listOf("nu")
            result.languageHintWords.size shouldBe PredictionContextMapper.MaxHintWords
            result.languageHintWords.last() shouldBe "zin" + ('a' + 19)
        }
    }
})
