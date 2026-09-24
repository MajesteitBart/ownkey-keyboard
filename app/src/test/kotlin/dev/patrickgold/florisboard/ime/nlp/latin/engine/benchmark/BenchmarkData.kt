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

import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinDictionaryCleanup
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinScoringLanguage
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinText
import dev.patrickgold.florisboard.ime.nlp.latin.engine.LatinWordModel
import java.io.File
import java.util.Locale

/**
 * A misspelling and the word the user meant. [taps] holds simulated tap positions (in key widths) for each typed
 * character when the typo came from the tap-noise generator.
 */
internal data class TypoPair(
    val typed: String,
    val intended: String,
    val taps: List<Tap>? = null,
)

internal data class Tap(val char: Char, val x: Double, val y: Double)

/**
 * [words] is the raw FrequencyWords list, used to sample typo sets. [model] is what the app ships: the built
 * dictionary from `tools/dictionary-build` when present, after [LatinDictionaryCleanup]. [rawModel] reproduces the
 * 2026-09-24 baseline, which ran on the raw list.
 */
internal class BenchmarkLanguage(
    val code: String,
    val words: Map<String, Int>,
    removals: Set<String>,
    builtWords: Map<String, Int>?,
) {
    val shippedWords: Map<String, Int> by lazy { LatinDictionaryCleanup.apply(builtWords ?: words, code, removals) }
    val model: LatinWordModel by lazy { LatinWordModel.build(shippedWords) }
    val rawModel: LatinWordModel by lazy { LatinWordModel.build(words) }
    val locale: Locale = Locale.forLanguageTag(code)

    fun slot(isPrimary: Boolean, raw: Boolean = false) =
        LatinScoringLanguage(code, locale, if (raw) rawModel else model, isPrimary)
}

/**
 * Loads the shipped dictionaries and the benchmark datasets under `src/test/resources/autocorrect/`.
 */
internal object BenchmarkData {
    private val moduleDir: File by lazy {
        listOf(File("."), File("app")).map { it.absoluteFile }.first { dir ->
            File(dir, "src/main/assets/ime/dict/frequencywords").isDirectory
        }
    }

    val dictionaryDir: File get() = File(moduleDir, "src/main/assets/ime/dict/frequencywords")

    private val languages = mutableMapOf<String, BenchmarkLanguage>()

    val removalDir: File get() = File(moduleDir, "src/main/assets/${LatinDictionaryCleanup.RemovalAssetDir}")

    fun removals(code: String): Set<String> {
        val file = File(removalDir, "$code.txt")
        if (!file.isFile) return emptySet()
        return file.bufferedReader().useLines { LatinDictionaryCleanup.parseRemovalList(it) }
    }

    fun language(code: String, dictionaryFile: File = File(dictionaryDir, "${code}_50k.txt")): BenchmarkLanguage {
        return languages.getOrPut("$code:${dictionaryFile.path}") {
            val words = dictionaryFile.bufferedReader().useLines { LatinText.parseFrequencyList(it) }
            val built = File(moduleDir, "src/main/assets/ime/dict/latin/$code.txt").takeIf { it.isFile }
                ?.bufferedReader()?.useLines { LatinText.parseDictionary(it) }
            BenchmarkLanguage(code, words, removals(code), built)
        }
    }

    fun en() = language("en")
    fun nl() = language("nl")

    fun enOnly(raw: Boolean = false) = listOf(en().slot(isPrimary = true, raw = raw))
    fun nlOnly(raw: Boolean = false) = listOf(nl().slot(isPrimary = true, raw = raw))
    fun nlEn(raw: Boolean = false) = listOf(nl().slot(isPrimary = true, raw = raw), en().slot(isPrimary = false, raw = raw))
    fun enNl(raw: Boolean = false) = listOf(en().slot(isPrimary = true, raw = raw), nl().slot(isPrimary = false, raw = raw))

    fun resourceLines(name: String): List<String> {
        val stream = BenchmarkData::class.java.getResourceAsStream("/autocorrect/$name")
            ?: error("Missing benchmark resource autocorrect/$name")
        return stream.bufferedReader().useLines { lines ->
            lines.map { it.trimEnd() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
    }

    fun pairs(name: String): List<TypoPair> {
        return resourceLines(name).map { line ->
            val (typed, intended) = line.split('\t', limit = 2)
            TypoPair(typed, intended)
        }
    }

    fun words(name: String): List<String> = resourceLines(name)

    /** Clean sentences, one per line. */
    fun sentences(name: String): List<String> = resourceLines(name)
}
