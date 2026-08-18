/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardCleanupSchedulerTest : FunSpec({
    val nowMs = 2_000_000L

    test("disabled cleanup and empty history schedule no wake-up") {
        ClipboardCleanupScheduler.nextDelayMs(
            items = listOf(cleanupItem(createdAtMs = 0L)),
            policy = cleanupPolicy(oldEnabled = false, sensitiveEnabled = false),
            nowMs = nowMs,
        ) shouldBe null

        ClipboardCleanupScheduler.nextDelayMs(
            items = emptyList(),
            policy = cleanupPolicy(oldEnabled = true, sensitiveEnabled = true),
            nowMs = nowMs,
        ) shouldBe null
    }

    test("scheduler wakes once for the earliest enabled real expiry") {
        val items = listOf(
            cleanupItem(createdAtMs = nowMs - 19 * 60_000L),
            cleanupItem(createdAtMs = nowMs - 15_000L, sensitive = true),
        )

        ClipboardCleanupScheduler.nextDelayMs(
            items = items,
            policy = cleanupPolicy(
                oldEnabled = true,
                oldAfterMinutes = 20,
                sensitiveEnabled = true,
                sensitiveAfterSeconds = 20,
            ),
            nowMs = nowMs,
        ) shouldBe 5_000L
    }

    test("pinned ordinary items do not schedule old-item cleanup") {
        ClipboardCleanupScheduler.nextDelayMs(
            items = listOf(cleanupItem(createdAtMs = 0L, pinned = true)),
            policy = cleanupPolicy(oldEnabled = true),
            nowMs = nowMs,
        ) shouldBe null
    }

    test("expired items schedule immediately and preserve sensitive pinned cleanup") {
        val policy = cleanupPolicy(
            oldEnabled = true,
            oldAfterMinutes = 20,
            sensitiveEnabled = true,
            sensitiveAfterSeconds = 20,
        )
        val pinnedSensitive = cleanupItem(
            createdAtMs = nowMs - 21_000L,
            pinned = true,
            sensitive = true,
        )

        ClipboardCleanupScheduler.nextDelayMs(
            items = listOf(pinnedSensitive),
            policy = policy,
            nowMs = nowMs,
        ) shouldBe 0L
        ClipboardCleanupScheduler.isExpired(pinnedSensitive, policy, nowMs) shouldBe true
    }

    test("a backward clock jump produces a new future wake-up instead of ending cleanup") {
        val item = cleanupItem(createdAtMs = 1_000_000L, sensitive = true)
        val policy = cleanupPolicy(sensitiveEnabled = true, sensitiveAfterSeconds = 20)

        ClipboardCleanupScheduler.nextDelayMs(
            items = listOf(item),
            policy = policy,
            nowMs = 1_015_000L,
        ) shouldBe 5_000L
        ClipboardCleanupScheduler.isExpired(item, policy, nowMs = 900_000L) shouldBe false
        ClipboardCleanupScheduler.nextDelayMs(
            items = listOf(item),
            policy = policy,
            nowMs = 900_000L,
        ) shouldBe 120_000L
    }

    test("rapid cleanup retries park on a long recovery delay without becoming terminal") {
        (1 until ClipboardCleanupScheduler.MaxRapidRetries).forEach { retry ->
            ClipboardCleanupScheduler.retryDelayMs(retry) shouldBe ClipboardCleanupScheduler.RetryDelayMs
        }
        ClipboardCleanupScheduler.retryDelayMs(
            ClipboardCleanupScheduler.MaxRapidRetries,
        ) shouldBe ClipboardCleanupScheduler.RecoveryDelayMs
        ClipboardCleanupScheduler.retryDelayMs(
            ClipboardCleanupScheduler.MaxRapidRetries + 1,
        ) shouldBe ClipboardCleanupScheduler.RecoveryDelayMs
    }
})

private fun cleanupItem(
    createdAtMs: Long,
    pinned: Boolean = false,
    sensitive: Boolean = false,
) = ClipboardCleanupItem(
    creationTimestampMs = createdAtMs,
    isPinned = pinned,
    isSensitive = sensitive,
)

private fun cleanupPolicy(
    oldEnabled: Boolean = false,
    oldAfterMinutes: Int = 20,
    sensitiveEnabled: Boolean = false,
    sensitiveAfterSeconds: Int = 20,
) = ClipboardCleanupPolicy(
    oldItemsEnabled = oldEnabled,
    oldItemsAfterMinutes = oldAfterMinutes,
    sensitiveItemsEnabled = sensitiveEnabled,
    sensitiveItemsAfterSeconds = sensitiveAfterSeconds,
)
