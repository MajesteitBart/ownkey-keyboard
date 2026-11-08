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

import dev.patrickgold.florisboard.ime.nlp.personal.PersonalPrediction
import org.florisboard.lib.prediction.Candidate
import org.florisboard.lib.prediction.TextFolding

/**
 * Blends the user's learned phrases ([dev.patrickgold.florisboard.ime.nlp.personal.PersonalNgramStore]) into the
 * engine's next-word predictions, so a phrase the user types often still surfaces in the first slots although the
 * static n-gram model does not know it.
 *
 * Mixture per word: `P(w) = (1 - beta) * P_engine(w) + beta * s(w) / S`, where `s(w)` is the personal score of `w`
 * after the same one or two words (counts, trigram continuations weighted 3x by the store), `S` the sum of all
 * personal scores for this context, and `beta = min(MaxPersonalWeight, S / (S + EvidencePrior))`: one observation
 * gives the personal list a quarter of the mass, three give half, and it never exceeds [MaxPersonalWeight] so the
 * model keeps a share. `P_engine` is the engine posterior over its next-word candidates. The order of equally
 * probable words follows the engine. Pure; no Android APIs.
 *
 * Callers must not call this in private (incognito) sessions: there the personal store is not read at all.
 */
internal object PersonalNextWordBlend {
    const val EvidencePrior = 3.0
    const val MaxPersonalWeight = 0.6

    /** A blended prediction; [candidate] is null for a word only the personal store predicts. */
    data class Prediction(val word: String, val displayForm: String, val prob: Double, val candidate: Candidate?)

    /**
     * @param engine The engine's next-word candidates in display order.
     * @param personal Personal predictions for the same context.
     * @param displayFormOf Written form of a word that only the personal store predicts.
     */
    fun blend(
        engine: List<Candidate>,
        personal: List<PersonalPrediction>,
        limit: Int,
        displayFormOf: (String) -> String = { it },
    ): List<Prediction> {
        val personalScores = LinkedHashMap<String, Int>()
        for (p in personal) {
            val word = TextFolding.normalizeWord(p.word.trim())
            if (p.score <= 0 || !TextFolding.isWordLike(word)) continue
            personalScores[word] = (personalScores[word] ?: 0) + p.score
        }
        val total = personalScores.values.sum().toDouble()
        if (total <= 0.0) {
            return engine.take(limit).map { Prediction(it.word, it.displayForm, it.prob, it) }
        }
        val beta = minOf(MaxPersonalWeight, total / (total + EvidencePrior))
        val out = ArrayList<Pair<Prediction, Int>>(engine.size + personalScores.size)
        val seen = HashSet<String>()
        engine.forEachIndexed { index, c ->
            if (!seen.add(c.word)) return@forEachIndexed
            val s = personalScores[c.word] ?: 0
            out.add(Prediction(c.word, c.displayForm, (1 - beta) * c.prob + beta * s / total, c) to index)
        }
        for ((word, s) in personalScores) {
            if (!seen.add(word)) continue
            out.add(Prediction(word, displayFormOf(word), beta * s / total, null) to engine.size + out.size)
        }
        return out.sortedWith(compareByDescending<Pair<Prediction, Int>> { it.first.prob }.thenBy { it.second })
            .take(limit)
            .map { it.first }
    }
}
