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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.AutocorrectStrength
import dev.patrickgold.florisboard.ime.nlp.latin.engine.NoisyChannelLatinScorer
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * Each strength level must meet its precision floor on the usage-weighted tap-noise sets, keep wrong corrections on
 * the rare-word (vocabulary-uniform) sets under a cap, and fix at least 10 points more typos (average of EN, NL and
 * NL+EN) than the level below it.
 */
class AutocorrectStrengthCalibrationTest : FunSpec({
    val precisionFloor = mapOf(
        AutocorrectStrength.GENTLE to 99.0,
        AutocorrectStrength.NORMAL to 97.0,
        AutocorrectStrength.STRONG to 95.0,
    )
    val rareWordWrongCap = mapOf(
        AutocorrectStrength.GENTLE to 0.5,
        AutocorrectStrength.NORMAL to 1.5,
        AutocorrectStrength.STRONG to 3.5,
    )

    test("strength levels meet their floors and step up in recall") {
        val s = BenchmarkSets
        val averageRecall = mutableMapOf<AutocorrectStrength, Double>()
        for (strength in AutocorrectStrength.entries) {
            val b = AutocorrectBenchmark(
                NoisyChannelLatinScorer(),
                BenchmarkPolicies.default(),
                settings = strength.toSettings(enabled = true),
            )
            val usage = listOf(
                b.evaluateTypos("EN", BenchmarkData.enOnly(), s.tapEnUsage.pairs),
                b.evaluateTypos("NL", BenchmarkData.nlOnly(), s.tapNlUsage.pairs),
                b.evaluateTypos("NL+EN", BenchmarkData.nlEn(), s.tapNlUsage.pairs),
            )
            val rare = listOf(
                b.evaluateTypos("EN rare", BenchmarkData.enOnly(), s.tapEnUniform.pairs),
                b.evaluateTypos("NL rare", BenchmarkData.nlOnly(), s.tapNlUniform.pairs),
            )
            usage.forEach { r ->
                println("$strength ${r.set}: right ${r.rightPct} wrong ${r.wrongPct} precision ${r.precisionPct}")
                withClue("$strength ${r.set} precision") { r.precisionPct shouldBeGreaterThanOrEqual precisionFloor.getValue(strength) }
            }
            rare.forEach { r ->
                println("$strength ${r.set}: right ${r.rightPct} wrong ${r.wrongPct}")
                withClue("$strength ${r.set} wrong rate") { r.wrongPct shouldBeLessThanOrEqual rareWordWrongCap.getValue(strength) }
            }
            averageRecall[strength] = usage.map { it.rightPct }.average()
        }
        val gentle = averageRecall.getValue(AutocorrectStrength.GENTLE)
        val normal = averageRecall.getValue(AutocorrectStrength.NORMAL)
        val strong = averageRecall.getValue(AutocorrectStrength.STRONG)
        println("average recall: gentle $gentle normal $normal strong $strong")
        withClue("normal over gentle") { (normal - gentle) shouldBeGreaterThanOrEqual 10.0 }
        withClue("strong over normal") { (strong - normal) shouldBeGreaterThanOrEqual 10.0 }
    }

    test("legacy confidence pref maps to the nearest level") {
        AutocorrectStrength.fromLegacyMinConfidencePercent(88) shouldBe AutocorrectStrength.NORMAL
        AutocorrectStrength.fromLegacyMinConfidencePercent(99) shouldBe AutocorrectStrength.GENTLE
        AutocorrectStrength.fromLegacyMinConfidencePercent(95) shouldBe AutocorrectStrength.GENTLE
        AutocorrectStrength.fromLegacyMinConfidencePercent(75) shouldBe AutocorrectStrength.STRONG
        AutocorrectStrength.fromLegacyMinConfidencePercent(50) shouldBe AutocorrectStrength.STRONG
    }
})
