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

internal data class HighCertaintyAutocorrectConfig(
    val enabled: Boolean = true,
    val minConfidence: Double = 0.88,
    val minConfidenceGap: Double = 0.12,
    val minInputLength: Int = 4,
    val maxAutoCorrectEditDistance: Int = 1,
)

internal class HighCertaintyAutocorrectPolicy(
    private val config: HighCertaintyAutocorrectConfig = HighCertaintyAutocorrectConfig(),
) {
    fun shouldAutoCommit(
        normalizedInput: String,
        candidateWord: String,
        candidateEditDistance: Int,
        candidateConfidence: Double,
        runnerUpConfidence: Double?,
        hasExactInputMatch: Boolean,
    ): Boolean {
        if (!config.enabled) return false
        if (hasExactInputMatch) return false
        if (normalizedInput.length < config.minInputLength) return false
        if (candidateWord == normalizedInput) return false
        if (candidateEditDistance < 1 || candidateEditDistance > config.maxAutoCorrectEditDistance) return false
        if (candidateConfidence < config.minConfidence) return false

        val confidenceGap = runnerUpConfidence?.let { candidateConfidence - it } ?: 1.0
        if (confidenceGap < config.minConfidenceGap) return false

        return true
    }
}

