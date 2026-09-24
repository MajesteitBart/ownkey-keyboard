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

import dev.patrickgold.florisboard.ime.nlp.latin.AppSpecificAutocorrectProfilePolicy
import dev.patrickgold.florisboard.ime.nlp.latin.AutocorrectAppProfile
import dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectConfig
import dev.patrickgold.florisboard.ime.nlp.latin.HighCertaintyAutocorrectPolicy
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinCurrentWordScorer
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoredCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringHooks
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringRequest
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinText
import java.io.File
import java.util.Locale

internal object BenchmarkPolicies {
    val Default = HighCertaintyAutocorrectConfig()

    fun default() = HighCertaintyAutocorrectPolicy(Default)

    fun chat() = HighCertaintyAutocorrectPolicy(
        AppSpecificAutocorrectProfilePolicy().applyProfile(Default, AutocorrectAppProfile.CHAT)
    )

    /** Every tuning slider at its most aggressive value, in a chat app at 130%. */
    fun max(): HighCertaintyAutocorrectPolicy {
        val base = Default.copy(minConfidence = 0.50, minConfidenceGap = 0.0, minInputLength = 3)
        val profiles = AppSpecificAutocorrectProfilePolicy(
            dev.patrickgold.florisboard.ime.nlp.latin.AppSpecificAutocorrectConfig(chatAggressivenessPercent = 130)
        )
        return HighCertaintyAutocorrectPolicy(profiles.applyProfile(base, AutocorrectAppProfile.CHAT))
    }
}

internal data class TypoSetResult(
    val set: String,
    val n: Int,
    val right: Int,
    val wrong: Int,
    val top1: Int,
    val top3: Int,
    val typedIsDictionaryWord: Int,
    val p50Micros: Long,
    val p95Micros: Long,
    val wrongExamples: List<String>,
    val missExamples: List<String>,
) {
    fun pct(count: Int): Double = if (n == 0) 0.0 else 100.0 * count / n
    val rightPct get() = pct(right)
    val wrongPct get() = pct(wrong)
    val top1Pct get() = pct(top1)
    val top3Pct get() = pct(top3)
    val dictPct get() = pct(typedIsDictionaryWord)
    val precisionPct: Double get() = if (right + wrong == 0) 100.0 else 100.0 * right / (right + wrong)
}

internal data class RealWordResult(
    val set: String,
    val n: Int,
    val first: Int,
    val top3: Int,
    val autoCorrected: Int,
    val misses: List<String>,
) {
    val firstPct: Double get() = if (n == 0) 0.0 else 100.0 * first / n
    val top3Pct: Double get() = if (n == 0) 0.0 else 100.0 * top3 / n
}

internal data class NextWordResult(val set: String, val n: Int, val first: Int, val top3: Int) {
    val firstPct: Double get() = if (n == 0) 0.0 else 100.0 * first / n
    val top3Pct: Double get() = if (n == 0) 0.0 else 100.0 * top3 / n
}

private val NextWordPattern = Regex("[\\p{L}]+(?:['\u2019][\\p{L}]+)*")

internal data class CleanTextResult(
    val set: String,
    val words: Int,
    val falseCorrections: Int,
    val examples: List<String>,
) {
    val perThousand: Double get() = if (words == 0) 0.0 else 1000.0 * falseCorrections / words
}

internal data class OovResult(
    val set: String,
    val n: Int,
    val inDictionary: Int,
    val changed: Int,
    val examples: List<String>,
)

/**
 * Runs a scorer over the benchmark datasets. Scorers see exactly what the provider passes in production, minus
 * user dictionaries and personal n-grams (a fresh install).
 */
internal class AutocorrectBenchmark(
    private val scorer: LatinCurrentWordScorer,
    private val policy: HighCertaintyAutocorrectPolicy,
    private val hooks: LatinScoringHooks = LatinScoringHooks.None,
    private val settings: dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings =
        dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectSettings(),
) {
    suspend fun score(
        languages: List<LatinScoringLanguage>,
        rawInput: String,
        textBeforeSelection: String,
        taps: List<Tap>? = null,
    ): List<LatinScoredCandidate> {
        return scorer.score(
            LatinScoringRequest(
                rawInput = rawInput,
                primaryLocale = languages.first().locale,
                languages = languages,
                textBeforeSelection = textBeforeSelection,
                maxCandidateCount = 8,
                policy = policy,
                taps = taps?.map { dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinTap(it.x, it.y) },
                autocorrect = settings,
            ),
            hooks,
        )
    }

    /**
     * @param withContext When true, the typed word is passed as the text before the cursor, which is what the
     *  editor reports while a word is being composed. The 2026-09-24 harness passed no context.
     */
    suspend fun evaluateTypos(
        set: String,
        languages: List<LatinScoringLanguage>,
        pairs: List<TypoPair>,
        withContext: Boolean = true,
        useTaps: Boolean = false,
    ): TypoSetResult {
        var right = 0
        var wrong = 0
        var top1 = 0
        var top3 = 0
        var dictionaryWords = 0
        val times = LongArray(pairs.size)
        val wrongExamples = mutableListOf<String>()
        val missExamples = mutableListOf<String>()
        pairs.forEachIndexed { index, pair ->
            val normalized = LatinText.normalizeInputWord(pair.typed, languages.first().locale)
            if (languages.any { it.model.isKnown(normalized) }) dictionaryWords++
            val start = System.nanoTime()
            val result = score(
                languages = languages,
                rawInput = pair.typed,
                textBeforeSelection = if (withContext) pair.before + pair.typed else "",
                taps = if (useTaps) pair.taps else null,
            )
            times[index] = System.nanoTime() - start
            val auto = result.firstOrNull { it.isAutoCommit }?.word
            when {
                auto == pair.intended -> right++
                auto != null -> {
                    wrong++
                    if (wrongExamples.size < 8) wrongExamples.add("${pair.typed}->$auto (meant ${pair.intended})")
                }
                missExamples.size < 8 -> {
                    missExamples.add("${pair.typed} [${result.take(3).joinToString(", ") { it.word }}] (meant ${pair.intended})")
                }
            }
            if (result.firstOrNull()?.word == pair.intended) top1++
            if (result.take(3).any { it.word == pair.intended }) top3++
        }
        times.sort()
        return TypoSetResult(
            set = set,
            n = pairs.size,
            right = right,
            wrong = wrong,
            top1 = top1,
            top3 = top3,
            typedIsDictionaryWord = dictionaryWords,
            p50Micros = percentileMicros(times, 0.50),
            p95Micros = percentileMicros(times, 0.95),
            wrongExamples = wrongExamples,
            missExamples = missExamples,
        )
    }

    /**
     * Types every word of every sentence and counts autocorrections that change a correctly written word.
     */
    suspend fun evaluateCleanText(set: String, languages: List<LatinScoringLanguage>, sentences: List<String>): CleanTextResult {
        var words = 0
        var falseCorrections = 0
        val examples = mutableListOf<String>()
        val locale = languages.first().locale
        for (sentence in sentences) {
            var searchFrom = 0
            for (token in sentence.split(' ')) {
                val start = sentence.indexOf(token, searchFrom)
                searchFrom = start + token.length
                val word = token.trim { !it.isLetter() && it != '\'' && it != '’' }
                    .trimEnd('\'', '’')
                if (word.isEmpty() || word.any { !it.isLetter() && it != '\'' && it != '’' && it != '-' }) continue
                words++
                val wordEnd = sentence.indexOf(word, start) + word.length
                val result = score(languages, word, sentence.substring(0, wordEnd))
                val auto = result.firstOrNull { it.isAutoCommit } ?: continue
                if (auto.word != LatinText.normalizeInputWord(word, locale)) {
                    falseCorrections++
                    if (examples.size < 12) examples.add("$word->${auto.text}")
                }
            }
        }
        return CleanTextResult(set, words, falseCorrections, examples)
    }

    /**
     * Types the marked word of each sentence and checks the suggestions. Real-word errors are never autocorrected,
     * so the metric is how often the intended word is the first suggestion.
     */
    suspend fun evaluateRealWords(set: String, languages: List<LatinScoringLanguage>, cases: List<RealWordCase>): RealWordResult {
        var first = 0
        var top3 = 0
        var autoCorrected = 0
        val misses = mutableListOf<String>()
        val locale = languages.first().locale
        for (case in cases) {
            val result = score(languages, case.typed, case.before + case.typed)
            val intended = LatinText.normalizeInputWord(case.intended, locale)
            if (result.firstOrNull()?.word == intended) {
                first++
            } else if (misses.size < 12) {
                misses.add("${case.typed} [${result.take(3).joinToString(", ") { it.word }}] (meant ${case.intended})")
            }
            if (result.take(3).any { it.word == intended }) top3++
            val auto = result.firstOrNull { it.isAutoCommit }
            if (auto != null && auto.word != LatinText.normalizeInputWord(case.typed, locale)) autoCorrected++
        }
        return RealWordResult(set, cases.size, first, top3, autoCorrected, misses)
    }

    /**
     * Walks through each sentence and asks [predict] for the next word after every word boundary. Counts how often
     * the word that follows is the first prediction or among the first three.
     */
    fun evaluateNextWord(
        set: String,
        languages: List<LatinScoringLanguage>,
        sentences: List<String>,
        predict: (languages: List<LatinScoringLanguage>, textBefore: String) -> List<String>,
    ): NextWordResult {
        var n = 0
        var first = 0
        var top3 = 0
        val locale = languages.first().locale
        for (sentence in sentences) {
            for (match in NextWordPattern.findAll(sentence).drop(1)) {
                val actual = LatinText.normalizeInputWord(match.value, locale)
                n++
                val predictions = predict(languages, sentence.substring(0, match.range.first))
                if (predictions.firstOrNull() == actual) first++
                if (predictions.take(3).contains(actual)) top3++
            }
        }
        return NextWordResult(set, n, first, top3)
    }

    /** Types each word after a short neutral prefix, the way a name or term appears mid-sentence. */
    suspend fun evaluateOov(set: String, languages: List<LatinScoringLanguage>, words: List<String>): OovResult {
        var inDictionary = 0
        var changed = 0
        val examples = mutableListOf<String>()
        val locale = languages.first().locale
        for (word in words) {
            val normalized = LatinText.normalizeInputWord(word, locale)
            if (languages.any { it.model.isKnown(normalized) }) inDictionary++
            val result = score(languages, word, "ok $word")
            val auto = result.firstOrNull { it.isAutoCommit } ?: continue
            if (auto.word != normalized) {
                changed++
                if (examples.size < 12) examples.add("$word->${auto.text}")
            }
        }
        return OovResult(set, words.size, inDictionary, changed, examples)
    }

    private fun percentileMicros(sortedNanos: LongArray, p: Double): Long {
        if (sortedNanos.isEmpty()) return 0
        val index = ((sortedNanos.size - 1) * p).toInt()
        return sortedNanos[index] / 1000
    }
}

/**
 * Collects results and writes a markdown report to `build/reports/autocorrect-benchmark/`.
 */
internal class BenchmarkReport(private val title: String) {
    val typoResults = mutableListOf<TypoSetResult>()
    val cleanResults = mutableListOf<CleanTextResult>()
    val oovResults = mutableListOf<OovResult>()
    val realWordResults = mutableListOf<RealWordResult>()
    val nextWordResults = mutableListOf<NextWordResult>()
    private val notes = mutableListOf<String>()

    fun add(result: TypoSetResult) = result.also { typoResults.add(it) }
    fun add(result: CleanTextResult) = result.also { cleanResults.add(it) }
    fun add(result: OovResult) = result.also { oovResults.add(it) }
    fun add(result: RealWordResult) = result.also { realWordResults.add(it) }
    fun add(result: NextWordResult) = result.also { nextWordResults.add(it) }
    fun note(text: String) = notes.add(text)

    fun render(): String = buildString {
        appendLine("# $title")
        appendLine()
        notes.forEach { appendLine("- $it") }
        if (notes.isNotEmpty()) appendLine()
        appendLine("| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        typoResults.forEach { r ->
            appendLine(
                "| ${r.set} | ${r.n} | ${f(r.rightPct)} | ${f(r.wrongPct)} | ${f(r.precisionPct)} | ${f(r.top1Pct)} | " +
                    "${f(r.top3Pct)} | ${f(r.dictPct)} | ${r.p50Micros} | ${r.p95Micros} |"
            )
        }
        if (cleanResults.isNotEmpty()) {
            appendLine()
            appendLine("| Clean text | Words | False corrections | Per 1,000 | Examples |")
            appendLine("| --- | --- | --- | --- | --- |")
            cleanResults.forEach { r ->
                appendLine("| ${r.set} | ${r.words} | ${r.falseCorrections} | ${f(r.perThousand, 2)} | ${r.examples.joinToString(", ")} |")
            }
        }
        if (oovResults.isNotEmpty()) {
            appendLine()
            appendLine("| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |")
            appendLine("| --- | --- | --- | --- | --- |")
            oovResults.forEach { r ->
                appendLine("| ${r.set} | ${r.n} | ${r.inDictionary} | ${r.changed} | ${r.examples.joinToString(", ")} |")
            }
        }
        if (realWordResults.isNotEmpty()) {
            appendLine()
            appendLine("| Real-word errors | n | Intended first | Intended in top 3 | Autocorrected |")
            appendLine("| --- | --- | --- | --- | --- |")
            realWordResults.forEach { r ->
                appendLine("| ${r.set} | ${r.n} | ${f(r.firstPct)} | ${f(r.top3Pct)} | ${r.autoCorrected} |")
            }
        }
        if (nextWordResults.isNotEmpty()) {
            appendLine()
            appendLine("| Next word | Positions | First prediction | In first 3 |")
            appendLine("| --- | --- | --- | --- |")
            nextWordResults.forEach { r ->
                appendLine("| ${r.set} | ${r.n} | ${f(r.firstPct)} | ${f(r.top3Pct)} |")
            }
        }
        appendLine()
        appendLine("## Examples")
        realWordResults.forEach { r ->
            if (r.misses.isNotEmpty()) appendLine("- ${r.set}, intended word not first: ${r.misses.joinToString("; ")}")
        }
        typoResults.forEach { r ->
            if (r.wrongExamples.isNotEmpty()) appendLine("- ${r.set}, wrong: ${r.wrongExamples.joinToString("; ")}")
            if (r.missExamples.isNotEmpty()) appendLine("- ${r.set}, missed: ${r.missExamples.joinToString("; ")}")
        }
    }

    fun write(fileName: String): File {
        val dir = File("build/reports/autocorrect-benchmark").absoluteFile
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(render())
        println(render())
        return file
    }

    private fun f(value: Double, decimals: Int = 1) = String.format(Locale.ROOT, "%.${decimals}f", value)
}
