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

package dev.patrickgold.florisboard.ime.clipboard

internal data class ClipboardCleanupPolicy(
    val oldItemsEnabled: Boolean,
    val oldItemsAfterMinutes: Int,
    val sensitiveItemsEnabled: Boolean,
    val sensitiveItemsAfterSeconds: Int,
)

internal data class ClipboardCleanupPlan(
    val history: ClipboardHistory,
    val policy: ClipboardCleanupPolicy,
)

internal data class ClipboardCleanupItem(
    val creationTimestampMs: Long,
    val isPinned: Boolean,
    val isSensitive: Boolean,
)

/** Pure next-expiry calculation; `null` means no cleanup wake-up needs to be scheduled. */
internal object ClipboardCleanupScheduler {
    const val RetryDelayMs = 60_000L
    const val MaxRapidRetries = 5
    const val RecoveryDelayMs = 6 * 60 * 60 * 1_000L

    fun retryDelayMs(consecutiveRetries: Int): Long =
        if (consecutiveRetries <= MaxRapidRetries) RetryDelayMs else RecoveryDelayMs

    fun nextDelayMs(
        items: List<ClipboardCleanupItem>,
        policy: ClipboardCleanupPolicy,
        nowMs: Long,
    ): Long? {
        val nextOldExpiry = if (policy.oldItemsEnabled) {
            items.asSequence()
                .filterNot(ClipboardCleanupItem::isPinned)
                .map { it.creationTimestampMs + policy.oldItemsAfterMinutes.coerceAtLeast(0) * 60_000L }
                .minOrNull()
        } else {
            null
        }
        val nextSensitiveExpiry = if (policy.sensitiveItemsEnabled) {
            items.asSequence()
                .filter(ClipboardCleanupItem::isSensitive)
                .map { it.creationTimestampMs + policy.sensitiveItemsAfterSeconds.coerceAtLeast(0) * 1_000L }
                .minOrNull()
        } else {
            null
        }
        return listOfNotNull(nextOldExpiry, nextSensitiveExpiry)
            .minOrNull()
            ?.let { (it - nowMs).coerceAtLeast(0L) }
    }

    fun isExpired(
        item: ClipboardCleanupItem,
        policy: ClipboardCleanupPolicy,
        nowMs: Long,
    ): Boolean {
        val oldItemExpired = policy.oldItemsEnabled && !item.isPinned &&
            item.creationTimestampMs <= nowMs - policy.oldItemsAfterMinutes.coerceAtLeast(0) * 60_000L
        val sensitiveItemExpired = policy.sensitiveItemsEnabled && item.isSensitive &&
            item.creationTimestampMs <= nowMs - policy.sensitiveItemsAfterSeconds.coerceAtLeast(0) * 1_000L
        return oldItemExpired || sensitiveItemExpired
    }
}
