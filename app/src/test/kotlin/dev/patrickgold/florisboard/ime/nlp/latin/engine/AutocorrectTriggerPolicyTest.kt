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

import dev.patrickgold.florisboard.ime.editor.InputAttributes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class AutocorrectTriggerPolicyTest : FunSpec({
    test("space and sentence punctuation end a word") {
        listOf(" ", ".", ",", "!", "?", "‽", ";", ":").forEach { AutocorrectTriggerPolicy.isTrigger(it).shouldBeTrue() }
    }

    test("apostrophes, hyphens, digits and symbols continue a word") {
        listOf("'", "’", "-", "1", "@", "/", "#", "_", "(", "\"", "a", "").forEach {
            AutocorrectTriggerPolicy.isTrigger(it).shouldBeFalse()
        }
    }

    test("plain words are correctable") {
        listOf("teh", "becuase", "Mischien", "isn't", "e-mail", "(helo", "\"thnaks").forEach {
            AutocorrectTriggerPolicy.isCorrectableToken(it).shouldBeTrue()
        }
    }

    test("addresses, URLs, paths, handles and numbers are not correctable") {
        listOf(
            "bart@example", "www", "www.exampel", "https://exampel", "exampel.com", "e.g", "a/b", "#tag",
            "snake_case", "abc123", "", "(",
        ).forEach { AutocorrectTriggerPolicy.isCorrectableToken(it).shouldBeFalse() }
    }

    test("a single letter before a period is an abbreviation or a list marker") {
        AutocorrectTriggerPolicy.isCorrectableToken("i", trigger = ".").shouldBeFalse()
        AutocorrectTriggerPolicy.isCorrectableToken("i", trigger = " ").shouldBeTrue()
        AutocorrectTriggerPolicy.isCorrectableToken("teh", trigger = ".").shouldBeTrue()
    }

    test("a trailing dot does not make a word a domain") {
        AutocorrectTriggerPolicy.isCorrectableToken("Dr.").shouldBeTrue()
    }

    test("token before cursor stops at whitespace") {
        AutocorrectTriggerPolicy.tokenBeforeCursor("send it to bart@exampl") shouldBe "bart@exampl"
        AutocorrectTriggerPolicy.tokenBeforeCursor("hello teh") shouldBe "teh"
        AutocorrectTriggerPolicy.tokenBeforeCursor("hello ") shouldBe ""
        AutocorrectTriggerPolicy.tokenBeforeCursor("") shouldBe ""
    }

    test("autocorrect is off in password, e-mail, URL and name fields") {
        listOf(
            InputAttributes.Variation.PASSWORD,
            InputAttributes.Variation.VISIBLE_PASSWORD,
            InputAttributes.Variation.WEB_PASSWORD,
            InputAttributes.Variation.EMAIL_ADDRESS,
            InputAttributes.Variation.WEB_EMAIL_ADDRESS,
            InputAttributes.Variation.URI,
            InputAttributes.Variation.PERSON_NAME,
        ).forEach { variation ->
            AutocorrectTriggerPolicy.allowsField(variation, flagTextNoSuggestions = false, isRichInputEditor = true)
                .shouldBeFalse()
        }
    }

    test("autocorrect is on in message fields unless the app opts out") {
        listOf(
            InputAttributes.Variation.NORMAL,
            InputAttributes.Variation.SHORT_MESSAGE,
            InputAttributes.Variation.LONG_MESSAGE,
            InputAttributes.Variation.EMAIL_SUBJECT,
            InputAttributes.Variation.WEB_EDIT_TEXT,
        ).forEach { variation ->
            AutocorrectTriggerPolicy.allowsField(variation, flagTextNoSuggestions = false, isRichInputEditor = true)
                .shouldBeTrue()
            AutocorrectTriggerPolicy.allowsField(variation, flagTextNoSuggestions = true, isRichInputEditor = true)
                .shouldBeFalse()
            AutocorrectTriggerPolicy.allowsField(variation, flagTextNoSuggestions = false, isRichInputEditor = false)
                .shouldBeFalse()
        }
    }
})
