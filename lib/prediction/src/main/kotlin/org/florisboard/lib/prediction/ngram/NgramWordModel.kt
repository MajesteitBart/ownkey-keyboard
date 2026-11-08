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

package org.florisboard.lib.prediction.ngram

import org.florisboard.lib.prediction.ScoredWord
import org.florisboard.lib.prediction.WordLanguageModel
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * [WordLanguageModel] view of a binary trigram [NgramModel] (`OKNG` v1 or v2, see `tools/lm/FORMAT.md`), for example
 * the bundled `app/src/main/assets/ime/dict/ngram/<lang>.bin`.
 *
 * Ids are the model's word ids: [bosId] is [NgramModel.BOS_ID] (0) and is never predicted, unknown words are -1.
 * Probabilities are widened from `Float` to `Double`. [displayForm] is [NgramModel.casedForm] ("I'm", "Amsterdam",
 * "iPhone"). [isValidWord] and [isListedWord] are [NgramModel.isValid] and [NgramModel.isListed] (for v1 files every
 * word is valid and none is listed). Immutable and thread-safe, like the model.
 *
 * @param language Language tag reported to the engine; defaults to the tag in the model metadata.
 */
class NgramWordModel(
    val ngram: NgramModel,
    override val language: String = ngram.language,
) : WordLanguageModel {
    override val vocabularySize: Int get() = ngram.vocabularySize

    override val bosId: Int get() = NgramModel.BOS_ID

    override val usesContext: Boolean get() = true

    override fun wordId(word: String): Int = ngram.wordId(word)

    override fun word(id: Int): String = ngram.word(id)

    override fun isCapitalized(id: Int): Boolean = ngram.isCapitalized(id)

    override fun displayForm(id: Int): String = ngram.casedForm(id)

    override fun isValidWord(id: Int): Boolean = ngram.isValid(id)

    override fun isListedWord(id: Int): Boolean = ngram.isListed(id)

    override fun unigramLogProb(id: Int): Double = ngram.unigramLogProb(id).toDouble()

    override fun logProb(id: Int, prev1: Int, prev2: Int): Double = ngram.logProb(id, prev1, prev2).toDouble()

    override fun topNext(prev1: Int, prev2: Int, limit: Int): List<ScoredWord> =
        ngram.topNext(prev1, prev2, limit).map { ScoredWord(it.wordId, it.logProb.toDouble()) }

    companion object {
        /**
         * Loads a model from [input] (one heap copy of the file; the caller closes it). [language] overrides the
         * metadata tag; a file larger than [maxBytes] throws [IllegalStateException] before the copy is allocated.
         * Prefer a memory-mapped buffer or a [File], which keep the file off the Java heap.
         */
        @JvmOverloads
        fun load(input: InputStream, language: String? = null, maxBytes: Long = Long.MAX_VALUE): NgramWordModel =
            wrap(NgramModel.load(input, maxBytes), language)

        /**
         * Loads a model that reads [buffer] in place (zero copy; see [NgramModel.load]), for example a read-only
         * mapping of an uncompressed asset. [language] overrides the metadata tag.
         */
        fun load(buffer: ByteBuffer, language: String? = null): NgramWordModel = wrap(NgramModel.load(buffer), language)

        /** Memory-maps [file] read-only and loads the model from it (zero copy). [language] overrides the metadata tag. */
        fun load(file: File, language: String? = null): NgramWordModel = wrap(NgramModel.load(file), language)

        private fun wrap(model: NgramModel, language: String?) = NgramWordModel(model, language ?: model.language)
    }
}
