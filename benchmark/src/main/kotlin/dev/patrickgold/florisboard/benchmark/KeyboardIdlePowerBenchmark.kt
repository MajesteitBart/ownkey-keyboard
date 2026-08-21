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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.PowerMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures a visible-but-idle Ownkey IME session and emits one Perfetto trace per iteration.
 *
 * Energy rails are system-wide, not exact per-app accounting. The fixed `Ownkey.*` trace spans in
 * the app make manual voice/network runs attributable inside the same system trace.
 */
@LargeTest
@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class KeyboardIdlePowerBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun keyboardIdle() {
        val benchmarkApplicationId = InstrumentationRegistry.getInstrumentation().context.packageName
        assumeTrue(
            "High-precision energy metrics require supported physical power rails",
            PowerMetric.deviceSupportsHighPrecisionTracking(),
        )
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(PowerMetric(PowerMetric.Type.Energy())),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            iterations = 3,
            startupMode = null,
            setupBlock = {
                pressHome()
                device.executeShellCommand("ime enable $OWNKEY_IME_COMPONENT")
                device.executeShellCommand("ime set $OWNKEY_IME_COMPONENT")
                device.executeShellCommand(
                    "am start -W -n ${batteryHostComponent(benchmarkApplicationId)}",
                )
                check(
                    device.wait(
                        Until.hasObject(By.desc(BATTERY_HOST_INPUT_DESCRIPTION)),
                        5_000L,
                    ),
                ) { "Battery benchmark host field did not appear" }
                device.findObject(By.desc(BATTERY_HOST_INPUT_DESCRIPTION)).click()
                device.waitForIdle()
            },
        ) {
            Thread.sleep(10_000L)
        }
    }
}
