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

package org.florisboard.lib.prediction.sim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.prediction.SpatialModel
import org.florisboard.lib.prediction.TextFolding
import org.florisboard.lib.prediction.TouchPoint
import org.florisboard.lib.prediction.UnigramListModel
import org.florisboard.lib.prediction.WordLanguageModel
import org.florisboard.lib.prediction.ngram.NgramWordModel
import java.io.File

/** One benchmark sentence from `tools/kbbench/corpus/<split>.jsonl`. */
data class CorpusSentence(val id: String, val split: String, val lang: String, val words: List<String>)

/** The noisy typist's plan for one word (see `tools/kbbench/export_taps.py`). */
data class TapWord(
    /** Intended word as in the corpus. */
    val word: String,
    /** Letters the typist aimed at, after a possible cognitive slip. */
    val pressed: String,
    /** Letters the keyboard's hit test reports. */
    val literal: String,
    /** Tap offsets from the centre of each pressed key, in key pitches. */
    val offsets: List<Pair<Double, Double>>,
)

data class TapSentence(val id: String, val lang: String, val words: List<TapWord>)

/** One noisy tap plan file (one seed of `export_taps.py`) with its header. */
data class TapPlan(val path: String, val split: String, val seed: String, val sentences: Map<String, TapSentence>)

/**
 * A held-out literal check (`tools/kbbench/sim/literal_checks.jsonl`, not benchmark data): [input] typed exactly
 * after [context] must come out as [expect] once the word ends. `expect == input` means the word must be kept
 * (names, slang, real words with an apostrophe or diacritic sibling); otherwise it must switch to that spelling.
 */
data class LiteralCheck(val kind: String, val lang: String, val context: List<String>, val input: String, val expect: String) {
    val isKeep: Boolean get() = TextFolding.normalizeWord(expect) == TextFolding.normalizeWord(input)
}

/** Keyboard calibration (`tools/kbbench/calib/<keyboard>.json`): letter centres and pitch in pixels. */
data class Calibration(val keys: Map<Char, Pair<Double, Double>>, val pitchX: Double, val pitchY: Double) {
    fun spatialModel(): SpatialModel = SpatialModel.fromKeyCentres(keys, pitchX, pitchY)
}

/** Benchmark normalisation helpers mirroring `tools/kbbench/bench.py`. */
object BenchText {
    /** `fold()` of bench.py. */
    fun fold(s: String): String = TextFolding.keyForm(s.trim())

    /** `typed_form()` of bench.py: the letters a-z of the folded word. */
    fun typedForm(word: String): String = fold(word).filter { it in 'a'..'z' }
}

object BenchData {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadCorpus(file: File): List<CorpusSentence> = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { line ->
        val o = json.parseToJsonElement(line).jsonObject
        CorpusSentence(
            id = o.str("id"),
            split = o["split"]?.jsonPrimitive?.content ?: "",
            lang = o.str("lang"),
            words = o["words"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    fun loadTaps(file: File): TapPlan {
        val root = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val sentences = root["sentences"]!!.jsonArray.associate { s ->
            val o = s.jsonObject
            val words = o["words"]!!.jsonArray.map { w ->
                val wo = w.jsonObject
                TapWord(
                    word = wo.str("word"),
                    pressed = wo.str("pressed"),
                    literal = wo.str("literal"),
                    offsets = (wo["offsets"] as JsonArray).map { p ->
                        val a = p.jsonArray
                        a[0].jsonPrimitive.double to a[1].jsonPrimitive.double
                    },
                )
            }
            o.str("id") to TapSentence(o.str("id"), o.str("lang"), words)
        }
        val split = root["split"]?.jsonPrimitive?.content ?: ""
        val seed = root["seed"]?.jsonPrimitive?.content ?: file.nameWithoutExtension
        return TapPlan(file.path, split, seed, sentences)
    }

    fun loadLiteralChecks(file: File): List<LiteralCheck> =
        file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.map { line ->
            val o = json.parseToJsonElement(line).jsonObject
            LiteralCheck(
                kind = o.str("kind"),
                lang = o.str("lang"),
                context = o.str("context").split(' ').filter { it.isNotBlank() },
                input = o.str("input"),
                expect = o.str("expect"),
            )
        }

    fun loadCalibration(file: File): Calibration {
        val o = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val keys = o["keys"]!!.jsonObject.entries.associate { (k, v) ->
            val a = v.jsonArray
            k.single() to (a[0].jsonPrimitive.double to a[1].jsonPrimitive.double)
        }
        val pitch = o["pitch"]!!.jsonArray
        return Calibration(keys, pitch[0].jsonPrimitive.double, pitch[1].jsonPrimitive.double)
    }

    /** Touch points of a noisy word: centre of each pressed key plus its offset, in key-pitch units. */
    fun touchesFor(word: TapWord, spatial: SpatialModel): List<TouchPoint> = word.pressed.mapIndexed { i, c ->
        val centre = spatial.centre(c)
        val (dx, dy) = word.offsets[i]
        TouchPoint(centre.x + dx, centre.y + dy)
    }

    private fun JsonObject.str(key: String): String = requireNotNull(this[key]) { "missing '$key'" }.jsonPrimitive.content
}

/**
 * Loads a [WordLanguageModel] from a file. Loaders are chosen by file extension; `.txt` frequency lists
 * ([UnigramListModel]) and `.bin` binary n-gram models ([NgramWordModel]) are built in. Other formats can be plugged
 * in with [ModelLoaders.register] or, from the command line, with `--loader <ext>=<class name>` naming a class with a
 * no-argument constructor that implements this interface.
 */
fun interface ModelLoader {
    fun load(language: String, file: File): WordLanguageModel
}

object ModelLoaders {
    private val loaders = LinkedHashMap<String, ModelLoader>()

    init {
        register("txt") { language, file -> UnigramListModel.fromFile(language, file) }
        register("bin") { language, file -> NgramWordModel.load(file, language) }
    }

    @Synchronized
    fun register(extension: String, loader: ModelLoader) {
        loaders[extension.lowercase().removePrefix(".")] = loader
    }

    /** Registers the class [className] (a [ModelLoader] with a no-argument constructor) for [extension]. */
    fun registerClass(extension: String, className: String) {
        val loader = Class.forName(className).getDeclaredConstructor().newInstance() as ModelLoader
        register(extension, loader)
    }

    @Synchronized
    fun load(language: String, file: File): WordLanguageModel {
        val ext = file.extension.lowercase()
        val loader = loaders[ext] ?: throw IllegalArgumentException(
            "no model loader for '.$ext' (${file.path}); known: ${loaders.keys}. Register one with --loader $ext=<class>",
        )
        return loader.load(language, file)
    }
}
