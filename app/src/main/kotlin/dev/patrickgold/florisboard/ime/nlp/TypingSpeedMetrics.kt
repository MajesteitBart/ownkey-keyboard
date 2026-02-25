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

package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.BuildConfig
import java.util.ArrayDeque

/**
 * Aggregate-only typing speed metrics for debug/internal baseline measurements.
 *
 * This collector intentionally stores no raw typed content.
 */
object TypingSpeedMetrics {
    private const val SuggestionLatencyWindowSize = 256
    private val lock = Any()

    private val suggestionLatencySamplesMs = ArrayDeque<Long>(SuggestionLatencyWindowSize)
    private var suggestionAcceptCount: Long = 0
    private var top3SuggestionAcceptCount: Long = 0
    private var autoCorrectApplyCount: Long = 0
    private var autoCorrectUndoCount: Long = 0
    private var textInputKeystrokeCount: Long = 0
    private var committedWordCount: Long = 0
    private var hasOpenWord: Boolean = false
    private var enabledOverrideForTests: Boolean? = null

    val isEnabled: Boolean
        get() = enabledOverrideForTests ?: (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE.equals("beta", ignoreCase = true))

    fun recordSuggestionLatency(latencyMs: Long) {
        if (!isEnabled) return
        synchronized(lock) {
            if (suggestionLatencySamplesMs.size >= SuggestionLatencyWindowSize) {
                suggestionLatencySamplesMs.removeFirst()
            }
            suggestionLatencySamplesMs.addLast(latencyMs.coerceAtLeast(0))
        }
    }

    fun recordSuggestionAccepted(candidateIndex: Int?) {
        if (!isEnabled) return
        synchronized(lock) {
            suggestionAcceptCount += 1
            if (candidateIndex in 0..2) {
                top3SuggestionAcceptCount += 1
            }
        }
    }

    fun recordAutoCorrectApplied() {
        if (!isEnabled) return
        synchronized(lock) {
            autoCorrectApplyCount += 1
        }
    }

    fun recordAutoCorrectUndone() {
        if (!isEnabled) return
        synchronized(lock) {
            autoCorrectUndoCount += 1
        }
    }

    fun recordTextInput(text: String) {
        if (!isEnabled || text.isEmpty()) return
        synchronized(lock) {
            textInputKeystrokeCount += 1
            val codePoint = text.codePointAt(0)
            if (isWordCodePoint(codePoint)) {
                hasOpenWord = true
            } else if (hasOpenWord) {
                committedWordCount += 1
                hasOpenWord = false
            }
        }
    }

    fun recordWordCommittedBySuggestion() {
        if (!isEnabled) return
        synchronized(lock) {
            committedWordCount += 1
            hasOpenWord = false
        }
    }

    fun captureSnapshot(): Snapshot {
        synchronized(lock) {
            val latency = suggestionLatencySamplesMs.toList()
            val suggestionP95 = percentile(latency, 0.95)
            return Snapshot(
                suggestionLatencyP95Ms = suggestionP95,
                suggestionLatencySampleCount = latency.size,
                suggestionAcceptCount = suggestionAcceptCount,
                top3SuggestionAcceptCount = top3SuggestionAcceptCount,
                top3AcceptanceRate = safeRate(top3SuggestionAcceptCount, suggestionAcceptCount),
                textInputKeystrokeCount = textInputKeystrokeCount,
                committedWordCount = committedWordCount,
                keystrokesPerWord = safeRate(textInputKeystrokeCount, committedWordCount),
                autoCorrectApplyCount = autoCorrectApplyCount,
                autoCorrectUndoCount = autoCorrectUndoCount,
                falseAutocorrectRatio = safeRate(autoCorrectUndoCount, autoCorrectApplyCount),
                undoAutocorrectFrequency = safeRate(autoCorrectUndoCount, committedWordCount),
            )
        }
    }

    fun resetForTests() {
        synchronized(lock) {
            suggestionLatencySamplesMs.clear()
            suggestionAcceptCount = 0
            top3SuggestionAcceptCount = 0
            autoCorrectApplyCount = 0
            autoCorrectUndoCount = 0
            textInputKeystrokeCount = 0
            committedWordCount = 0
            hasOpenWord = false
        }
    }

    fun setEnabledForTests(enabled: Boolean?) {
        synchronized(lock) {
            enabledOverrideForTests = enabled
        }
    }

    private fun isWordCodePoint(codePoint: Int): Boolean {
        return Character.isLetterOrDigit(codePoint) || codePoint == '\''.code || codePoint == 0x2019
    }

    private fun percentile(values: List<Long>, quantile: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * quantile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx].toDouble()
    }

    private fun safeRate(numerator: Long, denominator: Long): Double {
        if (denominator == 0L) return 0.0
        return numerator.toDouble() / denominator.toDouble()
    }

    data class Snapshot(
        val suggestionLatencyP95Ms: Double,
        val suggestionLatencySampleCount: Int,
        val suggestionAcceptCount: Long,
        val top3SuggestionAcceptCount: Long,
        val top3AcceptanceRate: Double,
        val textInputKeystrokeCount: Long,
        val committedWordCount: Long,
        val keystrokesPerWord: Double,
        val autoCorrectApplyCount: Long,
        val autoCorrectUndoCount: Long,
        val falseAutocorrectRatio: Double,
        val undoAutocorrectFrequency: Double,
    )
}
