/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.benchmark

internal const val TARGET_PACKAGE = "nl.bartvandermeeren.ownkey.bench"
internal const val OWNKEY_IME_COMPONENT =
    "$TARGET_PACKAGE/dev.patrickgold.florisboard.FlorisImeService"
internal fun batteryHostComponent(applicationId: String) =
    "$applicationId/dev.patrickgold.florisboard.benchmark.BatteryBenchmarkActivity"
internal const val BATTERY_HOST_INPUT_DESCRIPTION = "Ownkey battery benchmark input"
