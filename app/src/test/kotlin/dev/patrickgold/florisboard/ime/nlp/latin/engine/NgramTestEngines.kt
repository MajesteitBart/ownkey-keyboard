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

import dev.patrickgold.florisboard.ime.nlp.latin.LatinDictionaryAssets
import org.florisboard.lib.prediction.Lexicon
import org.florisboard.lib.prediction.PredictionEngine
import org.florisboard.lib.prediction.ngram.NgramWordModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The shipped n-gram models for JVM tests, memory-mapped from `src/main/assets` like the phone maps the APK asset.
 * Lexicons are built once per test JVM and shared by every engine.
 */
internal object NgramTestEngines {
    private val moduleDir: File by lazy {
        listOf(File("."), File("app")).map { it.absoluteFile }.first { dir ->
            File(dir, "src/main/assets/ime/dict/ngram").isDirectory
        }
    }

    private val lexicons = ConcurrentHashMap<String, Lexicon>()
    private val engines = ConcurrentHashMap<List<String>, PredictionEngine>()

    private fun lexicon(language: String): Lexicon = lexicons.getOrPut(language) {
        val path = requireNotNull(LatinDictionaryAssets.NgramModels[language]) { "no n-gram model for $language" }
        Lexicon.build(NgramWordModel.load(File(moduleDir, "src/main/assets/$path"), language))
    }

    /** The engine for [languages] (primary first), or null when one of them has no model. */
    fun engineFor(languages: List<String>): PredictionEngine? {
        if (languages.isEmpty() || languages.any { it !in LatinDictionaryAssets.NgramModels }) return null
        return engines.getOrPut(languages) { PredictionEngine.fromLexicons(languages.map { lexicon(it) }) }
    }

    /** An n-gram scorer over the shipped models that falls back to [fallback]. */
    fun scorer(
        fallback: NoisyChannelLatinScorer = NoisyChannelLatinScorer(),
        realWordRule: RealWordRule = RealWordRule.CONFUSION_PAIRS,
    ) = NgramLatinScorer(
        engineFor = { engineFor(it) },
        fallback = fallback,
        realWordRule = { realWordRule },
    )
}
