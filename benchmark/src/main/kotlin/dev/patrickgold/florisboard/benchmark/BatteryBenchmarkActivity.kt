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

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout

/** Stable, content-free host field for measuring the Ownkey IME outside its settings activity. */
class BatteryBenchmarkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = EditText(this).apply {
            contentDescription = BATTERY_HOST_INPUT_DESCRIPTION
            hint = "Focus field for Ownkey power profiling"
            minLines = 6
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(FrameLayout(this).apply { addView(input) })
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        input.requestFocus()
        input.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(
                input,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }
    }
}
