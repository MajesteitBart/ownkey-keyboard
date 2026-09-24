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

package dev.patrickgold.florisboard.ime.nlp.latin.engine.benchmark

import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinText

/**
 * The fixed datasets every scorer is measured on. Synthetic sets use fixed seeds, so results are comparable
 * between runs and between scorers.
 *
 * Words are sampled from the original FrequencyWords lists (`*_50k.txt`) even after the shipped dictionaries
 * change, so later dictionary work cannot move the goalposts.
 */
internal object BenchmarkSets {
    const val TapTyposPerSet = 1500

    private fun tapSet(seed: Long, language: BenchmarkLanguage, tokenWeighted: Boolean): TapNoiseTypoGenerator.Generated {
        val generator = TapNoiseTypoGenerator(seed)
        val sample = generator.sampleWords(language.words, 40_000, tokenWeighted)
        return generator.buildTypos(language.words, sample, TapTyposPerSet)
    }

    val tapEnUsage by lazy { tapSet(1001, BenchmarkData.en(), tokenWeighted = true) }
    val tapEnUniform by lazy { tapSet(1002, BenchmarkData.en(), tokenWeighted = false) }
    val tapNlUsage by lazy { tapSet(1003, BenchmarkData.nl(), tokenWeighted = true) }
    val tapNlUniform by lazy { tapSet(1004, BenchmarkData.nl(), tokenWeighted = false) }

    val harnessEnUsage by lazy {
        val g = HarnessTypoGenerator(42)
        g.buildTypos(BenchmarkData.en().words, g.sampleWords(BenchmarkData.en().words, 1500, tokenWeighted = true))
    }

    val realEnCurated by lazy { BenchmarkData.pairs("real_en_curated.tsv") }
    val realEnWikipedia by lazy { BenchmarkData.pairs("real_en_wikipedia.tsv") }
    val apostrophesEn by lazy { BenchmarkData.pairs("apostrophes_en.tsv") }
    val realNlCurated by lazy { BenchmarkData.pairs("real_nl_curated.tsv") }
    val realNlExtra by lazy { BenchmarkData.pairs("real_nl_extra.tsv") }
    val realNlAll by lazy { realNlCurated + realNlExtra }

    val contextSentencesEn by lazy { BenchmarkData.sentences("context_en.txt") }
    val contextSentencesNl by lazy { BenchmarkData.sentences("context_nl.txt") }
    val contextEn by lazy { TapNoiseTypoGenerator(2001).buildContextTypos(BenchmarkData.en().words, contextSentencesEn) }
    val contextNl by lazy { TapNoiseTypoGenerator(2002).buildContextTypos(BenchmarkData.nl().words, contextSentencesNl) }
    val realWordEn by lazy { BenchmarkData.realWords("realword_en.tsv") }
    val realWordNl by lazy { BenchmarkData.realWords("realword_nl.tsv") }

    val cleanEn by lazy { BenchmarkData.sentences("clean_en.txt") }
    val cleanNl by lazy { BenchmarkData.sentences("clean_nl.txt") }
    val oov by lazy { BenchmarkData.words("oov.txt") }
}

/**
 * Runs every dataset against one scorer configuration and returns the filled report.
 */
internal suspend fun runFullBenchmark(
    title: String,
    benchmark: AutocorrectBenchmark,
    useTaps: Boolean = false,
    predictNextWord: suspend (List<LatinScoringLanguage>, String) -> List<String> = { languages, text -> frequencyOnlyNextWords(languages, text) },
): BenchmarkReport {
    val report = BenchmarkReport(title)
    val s = BenchmarkSets
    report.note(
        "Tap-noise real-word rates (excluded from sets): EN usage ${pct(s.tapEnUsage.realWordRate)}, " +
            "EN uniform ${pct(s.tapEnUniform.realWordRate)}, NL usage ${pct(s.tapNlUsage.realWordRate)}, " +
            "NL uniform ${pct(s.tapNlUniform.realWordRate)}, context EN ${pct(s.contextEn.realWordRate)}, " +
            "context NL ${pct(s.contextNl.realWordRate)}"
    )
    val enOnly = BenchmarkData.enOnly()
    val nlOnly = BenchmarkData.nlOnly()
    val nlEn = BenchmarkData.nlEn()
    report.add(benchmark.evaluateTypos("tap EN usage, EN", enOnly, s.tapEnUsage.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap EN uniform, EN", enOnly, s.tapEnUniform.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap NL usage, NL", nlOnly, s.tapNlUsage.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap NL uniform, NL", nlOnly, s.tapNlUniform.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap NL usage, NL+EN", nlEn, s.tapNlUsage.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap EN usage, NL+EN", nlEn, s.tapEnUsage.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("tap NL uniform, NL+EN", nlEn, s.tapNlUniform.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("harness EN usage, EN (optimistic)", enOnly, s.harnessEnUsage.pairs))
    report.add(benchmark.evaluateTypos("real EN curated, EN", enOnly, s.realEnCurated))
    report.add(benchmark.evaluateTypos("real EN Wikipedia, EN", enOnly, s.realEnWikipedia))
    report.add(benchmark.evaluateTypos("EN missing apostrophes, EN", enOnly, s.apostrophesEn))
    report.add(benchmark.evaluateTypos("real NL curated, NL", nlOnly, s.realNlCurated))
    report.add(benchmark.evaluateTypos("real NL extra, NL", nlOnly, s.realNlExtra))
    report.add(benchmark.evaluateTypos("real NL all, NL+EN", nlEn, s.realNlAll))
    report.add(benchmark.evaluateTypos("context EN, EN", enOnly, s.contextEn.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("context NL, NL", nlOnly, s.contextNl.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("context NL, NL+EN", nlEn, s.contextNl.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("context EN, NL+EN", nlEn, s.contextEn.pairs, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("context EN, EN, words before removed", enOnly, s.contextEn.pairs.map { it.copy(before = "") }, useTaps = useTaps))
    report.add(benchmark.evaluateTypos("context NL, NL, words before removed", nlOnly, s.contextNl.pairs.map { it.copy(before = "") }, useTaps = useTaps))
    report.add(benchmark.evaluateRealWords("real-word EN, EN", enOnly, s.realWordEn))
    report.add(benchmark.evaluateRealWords("real-word NL, NL", nlOnly, s.realWordNl))
    report.add(benchmark.evaluateRealWords("real-word NL, NL+EN", nlEn, s.realWordNl))
    report.add(benchmark.evaluateNextWord("next word EN, EN", enOnly, s.contextSentencesEn, predictNextWord))
    report.add(benchmark.evaluateNextWord("next word NL, NL", nlOnly, s.contextSentencesNl, predictNextWord))
    report.add(benchmark.evaluateNextWord("next word NL, NL+EN", nlEn, s.contextSentencesNl, predictNextWord))
    report.add(benchmark.evaluateNextWord("next word EN, EN, frequency only", enOnly, s.contextSentencesEn) { l, t -> frequencyOnlyNextWords(l, t) })
    report.add(benchmark.evaluateNextWord("next word NL, NL, frequency only", nlOnly, s.contextSentencesNl) { l, t -> frequencyOnlyNextWords(l, t) })
    report.add(benchmark.evaluateCleanText("clean EN, EN", enOnly, s.cleanEn))
    report.add(benchmark.evaluateCleanText("clean NL, NL", nlOnly, s.cleanNl))
    report.add(benchmark.evaluateCleanText("clean EN, NL+EN", nlEn, s.cleanEn))
    report.add(benchmark.evaluateCleanText("clean NL, NL+EN", nlEn, s.cleanNl))
    report.add(benchmark.evaluateOov("oov.txt, NL+EN", nlEn, s.oov))
    report.add(benchmark.evaluateOov("oov.txt, EN", enOnly, s.oov))
    return report
}

/**
 * What the keyboard predicts today when neither the text field nor the personal n-grams have a match: the most
 * frequent words of the primary language, whatever came before.
 */
internal fun frequencyOnlyNextWords(languages: List<LatinScoringLanguage>, textBefore: String): List<String> {
    val primary = languages.first()
    val previous = LatinText.extractWordTokens(textBefore, primary.locale).lastOrNull().orEmpty()
    return primary.model.predictionShortcuts.fallbackCandidates(previous, 3).map { it.word }
}

private fun pct(value: Double) = String.format(java.util.Locale.ROOT, "%.1f%%", 100 * value)
