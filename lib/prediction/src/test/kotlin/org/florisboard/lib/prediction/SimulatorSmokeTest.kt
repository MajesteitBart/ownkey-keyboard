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

package org.florisboard.lib.prediction

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.florisboard.lib.prediction.sim.BenchText
import org.florisboard.lib.prediction.sim.CorpusSentence
import org.florisboard.lib.prediction.sim.Simulator
import org.florisboard.lib.prediction.sim.SimulatorCli
import org.florisboard.lib.prediction.sim.TapSentence
import org.florisboard.lib.prediction.sim.TapWord
import java.io.File

class SimulatorSmokeTest : FunSpec({
    fun opts(vararg extra: String): SimulatorCli.Options {
        val root = TestModels.repoRoot.path
        return SimulatorCli.parse(
            arrayOf(
                "--corpus", "$root/tools/kbbench/corpus/dev.jsonl",
                "--taps", "$root/tools/kbbench/taps_dev_ownkey.json",
                "--calib", "$root/tools/kbbench/calib/ownkey.json",
                "--model", "nl=$root/app/src/main/assets/ime/dict/frequencywords/nl_50k.txt",
                "--model", "en=$root/app/src/main/assets/ime/dict/frequencywords/en_50k.txt",
                "--literal-checks", "$root/tools/kbbench/sim/literal_checks.jsonl",
                "--limit", "3", "--warmup", "0", *extra,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun metric(r: Map<String, Any?>, run: String, mode: String, key: String): Double {
        val m = ((r["runs"] as Map<String, Any?>)[run] as Map<String, Any?>)[mode] as Map<String, Any?>
        return (m["overall"] as Map<String, Any?>)[key] as Double
    }

    test("runs both typists on a few dev sentences with and without touches") {
        val r = SimulatorCli.execute(opts("--touches", "both"))
        for (run in listOf("touches_off", "touches_on")) {
            metric(r, run, "clean", "ksr") shouldBeGreaterThan 0.2
            metric(r, run, "noisy", "wer") shouldBeLessThan metric(r, run, "noisy", "literal_wer") + 1e-9
        }
        SimulatorCli.toJson(r).toString().contains("\"ksr\"") shouldBe true
        @Suppress("UNCHECKED_CAST")
        val checks = ((r["runs"] as Map<String, Any?>)["touches_off"] as Map<String, Any?>)["literal_checks"] as Map<String, Any?>
        (checks["keep_n"] as Int) shouldBeGreaterThan 100
        (checks["switch_n"] as Int) shouldBeGreaterThan 10
    }

    test("noisy metrics pool several tap seeds and report their spread") {
        val root = TestModels.repoRoot.path
        // opts() already passes the s1 plan.
        val r = SimulatorCli.execute(opts("--mode", "noisy", "--taps", "$root/tools/kbbench/sim/taps/taps_dev_ownkey_s2.json"))
        @Suppress("UNCHECKED_CAST")
        val noisy = ((r["runs"] as Map<String, Any?>)["touches_off"] as Map<String, Any?>)["noisy"] as Map<String, Any?>
        (noisy["seeds"] as List<*>).size shouldBe 2
        val corpus = org.florisboard.lib.prediction.sim.BenchData.loadCorpus(File("$root/tools/kbbench/corpus/dev.jsonl"))
        val words = listOf("en", "nl", "mix").sumOf { l -> corpus.filter { it.lang == l }.take(3).sumOf { it.words.size } }
        ((noisy["overall"] as Map<*, *>)["words"] as Int) shouldBe 2 * words
        (noisy["wer_spread"] as Double) shouldBeGreaterThan -1e-12
    }

    test("grid mode ranks combinations") {
        @Suppress("UNCHECKED_CAST")
        val grid = SimulatorCli.execute(opts("--grid", "deltaKnown=3,6", "--threads", "2", "--mode", "noisy"))["grid"] as List<Map<String, Any?>>
        grid.size shouldBe 2
    }

    test("refuses to tune on the test split") {
        // Stand-ins labelled as the test split; the real test split is never read here.
        val dir = kotlin.io.path.createTempDirectory("kbbench-test-split").toFile()
        try {
            val corpus = File(dir, "test.jsonl").apply {
                writeText("""{"id": "en-test-000", "split": "test", "lang": "en", "words": ["hello"]}""" + "\n")
            }
            // Split field says dev, but the id pattern gives the test split away.
            val disguised = File(dir, "disguised.jsonl").apply {
                writeText("""{"id": "en-test-000", "split": "dev", "lang": "en", "words": ["hello"]}""" + "\n")
            }
            val devCorpus = File(dir, "dev.jsonl").apply {
                writeText("""{"id": "en-dev-000", "split": "dev", "lang": "en", "words": ["hello"]}""" + "\n")
            }
            val word = """{"word": "hello", "pressed": "hello", "literal": "hello", "offsets": [[0,0],[0,0],[0,0],[0,0],[0,0]]}"""
            val testTaps = File(dir, "taps_test.json").apply {
                writeText("""{"split": "test", "seed": "s1", "sentences": [{"id": "en-dev-000", "lang": "en", "words": [$word]}]}""")
            }
            shouldThrow<IllegalArgumentException> {
                SimulatorCli.execute(opts("--corpus", corpus.path, "--grid", "alpha=1,2", "--mode", "clean"))
            }
            shouldThrow<IllegalArgumentException> { SimulatorCli.execute(opts("--corpus", corpus.path, "--mode", "clean")) }
            shouldThrow<IllegalArgumentException> { SimulatorCli.execute(opts("--corpus", disguised.path, "--mode", "clean")) }
            shouldThrow<IllegalArgumentException> {
                SimulatorCli.execute(opts("--corpus", devCorpus.path, "--taps", testTaps.path, "--mode", "noisy"))
            }
            // A final test run takes the shipped configuration only.
            shouldThrow<IllegalArgumentException> {
                SimulatorCli.execute(opts("--corpus", corpus.path, "--mode", "clean", "--allow-test", "--set", "alpha=2"))
            }
            SimulatorCli.execute(opts("--corpus", corpus.path, "--mode", "clean", "--allow-test", "--literal-checks", "none"))
        } finally {
            dir.deleteRecursively()
        }
    }

    test("clean typist counts taps like bench.py") {
        // One-word sentence: "the" is predicted before the first letter -> 1 tap instead of 4.
        val engine = PredictionEngine(listOf(TestModels.model("en", "the" to 1000, "a" to 10)))
        val r = Simulator(engine, useTouches = false).runClean(CorpusSentence("x", "dev", "en", listOf("the")))
        r.taps shouldBe 1
        r.needed shouldBe 4
        r.nextWordHits shouldBe 1
        // The noisy typist keeps the literal when nothing fires and scores folded words.
        val noisy = Simulator(engine, useTouches = false).runNoisy(
            TapSentence("x", "en", listOf(TapWord("The", "the", "thw", listOf(0.0 to 0.0, 0.0 to 0.0, 0.4 to 0.0)))),
        )
        noisy.typoWords shouldBe 1
        BenchText.fold(noisy.output.single()) shouldBe "the"
    }

    test("the noisy typist applies revisions of the previous word as the phone does") {
        val model = TestNgramModel(
            "nl",
            unigrams = mapOf("ik" to 100_000L, "ok" to 300L, "ga" to 8_000L, "dat" to 20_000L),
            bigrams = mapOf((TestNgramModel.BOS to "ik") to 6_000L, ("ik" to "ga") to 3_000L, ("ok" to "dat") to 50L),
        )
        val centre = listOf(0.0 to 0.0, 0.0 to 0.0)
        val sentence = TapSentence("x", "nl", listOf(TapWord("ik", "ok", "ok", centre), TapWord("ga", "ga", "ga", centre)))
        val engine = PredictionEngine(listOf(model))
        val revised = Simulator(engine, useTouches = false).runNoisy(sentence)
        revised.output shouldBe listOf("ik", "ga")
        revised.revisions.single().isRight shouldBe true
        Simulator.summarizeNoisy(listOf(revised))["revisions_right"] shouldBe 1
        val kept = Simulator(engine.withConfig(engine.config.copy(reviseEnabled = false)), useTouches = false).runNoisy(sentence)
        kept.output shouldBe listOf("ok", "ga")
        kept.revisions shouldBe emptyList()
        // The clean typist picks or types both words right: nothing to revise.
        val clean = Simulator(engine, useTouches = false).runClean(CorpusSentence("y", "dev", "nl", listOf("ik", "ga")))
        clean.revisions shouldBe emptyList()
    }
})
