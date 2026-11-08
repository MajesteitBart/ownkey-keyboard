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

import dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectPolicy
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.AutocorrectBenchmark
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkData
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkPolicies
import dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark.BenchmarkSets
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Locale

/**
 * The n-gram scorer on the shipped models: spec acceptance scenarios, main's rules on top of the engine (AC-011, chat
 * shorthand, protected words, offensive words, pronoun case), the input mapping (taps, context) and the fallback.
 */
class NgramLatinScorerTest : FunSpec({
    val scorer = NgramTestEngines.scorer()
    val benchmark = AutocorrectBenchmark(scorer, BenchmarkPolicies.default())

    suspend fun autoCorrection(
        languages: List<LatinScoringLanguage>,
        typed: String,
        before: String = "",
        hooks: LatinScoringHooks = LatinScoringHooks.None,
        settings: AutocorrectSettings = AutocorrectSettings(),
        blocked: Set<String> = emptySet(),
        taps: List<LatinTap>? = null,
        with: NgramLatinScorer = scorer,
    ): String? {
        val request = LatinScoringRequest(
            rawInput = typed,
            primaryLocale = languages.first().locale,
            languages = languages,
            textBeforeSelection = before + typed,
            maxCandidateCount = 8,
            policy = HighCertaintyAutocorrectPolicy(),
            autocorrect = settings,
            blockedWords = blocked,
            taps = taps,
        )
        return with.score(request, hooks).firstOrNull { it.isAutoCommit }?.text
    }

    test("the engine serves English, Dutch and Dutch with English, nothing else") {
        scorer.isReady(BenchmarkData.enOnly()) shouldBe true
        scorer.isReady(BenchmarkData.nlEn()) shouldBe true
        scorer.engineLanguagesOf(BenchmarkData.nlEn()) shouldBe listOf("nl", "en")
        val german = LatinScoringLanguage("de", Locale.GERMAN, BenchmarkData.en().model, isPrimary = true)
        scorer.isReady(listOf(german)) shouldBe false
        scorer.isReady(BenchmarkData.nlEn() + german.copy(isPrimary = false)) shouldBe false
    }

    test("without an engine every request goes to the noisy-channel scorer") {
        val noisy = NoisyChannelLatinScorer()
        val withoutEngine = NgramLatinScorer(engineFor = { null }, fallback = noisy)
        val reference = AutocorrectBenchmark(noisy, BenchmarkPolicies.default())
        val fallback = AutocorrectBenchmark(withoutEngine, BenchmarkPolicies.default())
        for ((typed, languages) in listOf("becuase" to BenchmarkData.enOnly(), "mischien" to BenchmarkData.nlEn())) {
            fallback.score(languages, typed, typed) shouldBe reference.score(languages, typed, typed)
        }
        withoutEngine.predictNextWords(BenchmarkData.nlOnly(), "ik ", true, 3).shouldBeNull()
    }

    test("AC-001, AC-009: typos and a dropped final letter are fixed") {
        autoCorrection(BenchmarkData.enOnly(), "becuase") shouldBe "because"
        autoCorrection(BenchmarkData.enOnly(), "becaus") shouldBe "because"
        autoCorrection(BenchmarkData.enOnly(), "teh") shouldBe "the"
        autoCorrection(BenchmarkData.enOnly(), "thnaks") shouldBe "thanks"
    }

    test("AC-002: Dutch typos, also on the NL+EN subtype") {
        autoCorrection(BenchmarkData.nlEn(), "mischien") shouldBe "misschien"
        autoCorrection(BenchmarkData.nlEn(), "vergaderign") shouldBe "vergadering"
        autoCorrection(BenchmarkData.nlOnly(), "bedakt") shouldBe "bedankt"
    }

    test("AC-003: product names and jargon stay as typed") {
        autoCorrection(BenchmarkData.nlEn(), "Voxtral", "ik gebruik ").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "webhook", "check de ").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "Lieke", "ik zag ").shouldBeNull()
    }

    test("AC-006: dont becomes don't, and FR-09's ambiguous words stay") {
        autoCorrection(BenchmarkData.enOnly(), "dont", "I ") shouldBe "don't"
        for (word in listOf("its", "cant", "well", "were", "ill", "shell")) {
            withClue(word) { autoCorrection(BenchmarkData.enOnly(), word, "the ").shouldBeNull() }
        }
    }

    test("AC-010, AC-011: a confusion-pair word is never replaced by its partner, also when the context prefers it") {
        autoCorrection(BenchmarkData.enOnly(), "then", "I would rather ").shouldBeNull()
        for ((languages, cases) in listOf(
            BenchmarkData.enOnly() to BenchmarkSets.realWordEn,
            BenchmarkData.nlOnly() to BenchmarkSets.realWordNl,
            BenchmarkData.nlEn() to BenchmarkSets.realWordNl,
        )) {
            for (case in cases) {
                val typed = LatinText.normalizeDictionaryWord(case.typed)
                val partners = languages.flatMap { language -> ConfusionSets.alternatives(language.language, typed) { true } }
                val correction = autoCorrection(languages, case.typed, case.before)?.let { LatinText.normalizeDictionaryWord(it) }
                withClue("${case.before}[${case.typed}] -> $correction") { (correction == null || correction !in partners) shouldBe true }
            }
        }
    }

    test("the devtools rules: every dictionary word kept, or the engine alone") {
        val strict = AutocorrectBenchmark(NgramTestEngines.scorer(realWordRule = RealWordRule.DICTIONARY_WORDS), BenchmarkPolicies.default())
        strict.evaluateRealWords("real words", BenchmarkData.enOnly(), BenchmarkSets.realWordEn).autoCorrected shouldBe 0
        autoCorrection(BenchmarkData.nlOnly(), "ok", "wat denk ", with = NgramTestEngines.scorer(realWordRule = RealWordRule.DICTIONARY_WORDS))
            .shouldBeNull()
        val engineOnly = AutocorrectBenchmark(NgramTestEngines.scorer(realWordRule = RealWordRule.NONE), BenchmarkPolicies.default())
        engineOnly.evaluateRealWords("real words", BenchmarkData.enOnly(), BenchmarkSets.realWordEn).autoCorrected shouldNotBe 0
    }

    test("chat shorthand, acronyms and apostrophe words stay as typed") {
        autoCorrection(BenchmarkData.nlEn(), "idd").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "wss", "ik ga ").shouldBeNull()
        autoCorrection(BenchmarkData.enOnly(), "idk").shouldBeNull()
        autoCorrection(BenchmarkData.enOnly(), "API", "the ").shouldBeNull()
        autoCorrection(BenchmarkData.enOnly(), "didn't").shouldBeNull()
    }

    test("chat shorthand that collides with a common word in context is corrected; two-letter typos are fixed") {
        // "ok" at the start of a Dutch sentence before "heb" is a slip for "ik" (revision, right context).
        val revised = scorer.revise(
            BenchmarkData.nlEn(), "", true, "Ok", "Ok", "heb", null, null, AutocorrectSettings(), { false }, emptySet(),
        )
        revised?.text shouldBe "Ik"
        // Two-letter slips of very common words: no length limit on top of the engine's own.
        autoCorrection(BenchmarkData.nlEn(), "dw", "ik zie ") shouldBe "de"
        // The noisy-channel scorer's three-letter minimum, which the first integration applied, kept it as typed.
        val withLengthLimit = NgramLatinScorer(
            engineFor = { NgramTestEngines.engineFor(it) }, fallback = NoisyChannelLatinScorer(), noisyChannelLengthLimit = true,
        )
        autoCorrection(BenchmarkData.nlEn(), "dw", "ik zie ", with = withLengthLimit).shouldBeNull()
    }

    test("revision keeps a word the user typed and kept three times, like the space decision does") {
        val geometry = KeyGeometry.QwertyPhone
        val o = geometry.center('o')!!
        val i = geometry.center('i')!!
        val k = geometry.center('k')!!
        // "Ok" with the O tap leaning towards the I key, at the start of a Dutch sentence, followed by "heb".
        val taps = listOf(LatinTap(o.first - 0.45 * (o.first - i.first), o.second), LatinTap(k.first, k.second))
        fun revise(times: Int) = scorer.revise(
            BenchmarkData.nlEn(), "", true, "Ok", "Ok", "heb", taps, geometry, AutocorrectSettings(), { false }, emptySet(),
            timesTyped = { word -> if (word == "ok") times else 0 },
        )
        revise(0)?.text shouldBe "Ik"
        revise(3).shouldBeNull()
    }

    test("never-correct and user dictionary words stay as typed") {
        val protected = object : LatinScoringHooks by LatinScoringHooks.None {
            override fun isBlockedByUserPreference(normalizedWord: String) = normalizedWord == "becuase"
            override fun isUserDictionaryWord(normalizedWord: String) = normalizedWord == "vergaderign"
        }
        autoCorrection(BenchmarkData.enOnly(), "becuase", hooks = protected).shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "vergaderign", hooks = protected).shouldBeNull()
    }

    test("a word the user kept three times gets a bonus for keeping it") {
        val kept = object : LatinScoringHooks by LatinScoringHooks.None {
            override fun timesTyped(normalizedWord: String) = if (normalizedWord == "thijs") 5 else 0
        }
        autoCorrection(BenchmarkData.nlEn(), "thijs", "ik zag ", hooks = kept).shouldBeNull()
    }

    test("autocorrect off keeps every word and still suggests") {
        val off = AutocorrectSettings(enabled = false)
        autoCorrection(BenchmarkData.enOnly(), "becuase", settings = off).shouldBeNull()
        benchmark.score(BenchmarkData.enOnly(), "becuase", "becuase").map { it.word } shouldContain "because"
    }

    test("a typo is never corrected into a possibly offensive word") {
        val blocked = BenchmarkData.offensiveWords("en") + BenchmarkData.offensiveWords("nl") + setOf("fuck*", "shit*")
        for (typed in listOf("fuxk", "shjt", "fuckimg")) {
            val correction = autoCorrection(BenchmarkData.enOnly(), typed, blocked = blocked)
            withClue("$typed -> $correction") {
                (correction == null || !PossiblyOffensiveWords.isBlocked(correction.lowercase(), blocked)) shouldBe true
            }
        }
    }

    test("a lowercase English pronoun becomes I") {
        autoCorrection(BenchmarkData.enOnly(), "i", "yesterday ") shouldBe "I"
        autoCorrection(BenchmarkData.enOnly(), "im", "yes ") shouldBe "I'm"
    }

    test("input case carries over to the correction; all caps reads as an acronym (FR-06)") {
        autoCorrection(BenchmarkData.enOnly(), "Becuase") shouldBe "Because"
        autoCorrection(BenchmarkData.enOnly(), "Becuase", "Yes. ") shouldBe "Because"
        autoCorrection(BenchmarkData.enOnly(), "BECUASE").shouldBeNull()
        autoCorrection(BenchmarkData.nlEn(), "Thijs", "ik zag ").shouldBeNull()
    }

    test("missed spaces come from the noisy-channel splits") {
        autoCorrection(BenchmarkData.enOnly(), "thisis", "I think ") shouldBe "this is"
    }

    test("taps are read in the frame of the key geometry") {
        val geometry = KeyGeometry.QwertyPhone
        fun at(ch: Char, dx: Double = 0.0): LatinTap = geometry.center(ch)!!.let { LatinTap(it.first + dx, it.second) }
        // "teh" tapped squarely on its keys is still the swapped "the".
        autoCorrection(BenchmarkData.enOnly(), "teh", taps = listOf(at('t'), at('e'), at('h'))) shouldBe "the"
        // "thr" with the last tap at the left edge of the r key, towards e: "the".
        autoCorrection(BenchmarkData.enOnly(), "thr", "I saw ", taps = listOf(at('t'), at('h'), at('r', -0.45))) shouldBe "the"
    }

    test("the user's word pairs move their words up") {
        val withPair = object : LatinScoringHooks by LatinScoringHooks.None {
            override suspend fun personalContinuationScore(previousWord: String, candidateWord: String): Double {
                return if (previousWord == "the" && candidateWord == "thing") 1.0 else 0.0
            }
        }
        val plain = benchmark.score(BenchmarkData.enOnly(), "thi", "I like the thi").map { it.word }
        val personal = AutocorrectBenchmark(scorer, BenchmarkPolicies.default(), withPair)
            .score(BenchmarkData.enOnly(), "thi", "I like the thi").map { it.word }
        val index = personal.indexOf("thing")
        index shouldNotBe -1
        if (plain.indexOf("thing") >= 0) index shouldBeLessThanOrEqual plain.indexOf("thing")
    }

    test("AC-012: after 'ik' the predictions are Dutch words") {
        val predictions = scorer.predictNextWords(BenchmarkData.nlOnly(), "ik ", true, 3).shouldNotBeNull().map { it.word }
        predictions.size shouldBe 3
        predictions shouldNotContain "the"
        println("after 'ik': $predictions")
    }

    test("revision: a confusion-pair word that stands as typed is never revised to its partner (AC-011)") {
        fun revise(with: NgramLatinScorer, typed: String, committed: String, next: String) = with.revise(
            languages = BenchmarkData.nlEn(),
            textBeforeWord = "",
            textStartsAtFieldStart = true,
            typed = typed,
            committed = committed,
            next = next,
            taps = null,
            geometry = null,
            settings = AutocorrectSettings(),
            isProtected = { false },
            blockedWords = emptySet(),
        )
        val engineOnly = NgramTestEngines.scorer(realWordRule = RealWordRule.NONE)
        val strict = NgramTestEngines.scorer(realWordRule = RealWordRule.DICTIONARY_WORDS)
        for ((typed, next) in listOf("then" to "i", "than" to "we", "your" to "welcome", "word" to "ik", "wordt" to "het")) {
            val revised = revise(scorer, typed, typed, next)?.word
            val partners = listOf("en", "nl").flatMap { ConfusionSets.alternatives(it, typed) { true } }
            withClue("$typed -> $revised (engine alone: ${revise(engineOnly, typed, typed, next)?.word})") {
                (revised == null || revised !in partners) shouldBe true
            }
        }
        // The stricter rule keeps every correctly spelled word as typed.
        revise(strict, "ok", "ok", "heb").shouldBeNull()
        // Autocorrect off: nothing is revised.
        scorer.revise(
            BenchmarkData.nlEn(), "", true, "ok", "ok", "heb", null, null, AutocorrectSettings(enabled = false), { false }, emptySet(),
        ).shouldBeNull()
    }
})
