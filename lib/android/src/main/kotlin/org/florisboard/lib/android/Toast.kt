/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package org.florisboard.lib.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.CurlyArg
import kotlin.math.roundToInt

/**
 * Shows a short toast with specified text.
 *
 * @param text The text to show in the toast popup.
 */
suspend fun Context.showShortToast(text: String): Toast = withContext(Dispatchers.Main.immediate) {
    if (OwnkeyToastBus.tryShowInIme(text, durationMillis = 2_000L)) {
        Toast(this@showShortToast).apply { duration = Toast.LENGTH_SHORT }
    } else {
        makeOwnkeyToast(text, Toast.LENGTH_SHORT).also { it.show() }
    }
}

/**
 * Shows a short toast with the string resource specified by [id].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 */
suspend fun Context.showShortToast(@StringRes id: Int): Toast {
    val text = this.stringRes(id)
    return showShortToast(text)
}

/**
 * Shows a short toast with the string resource specified by [id], additionally curly formatting the string with
 * supplied arguments [args].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 * @param args The curly arguments which will be filled into the string template identified by [id].
 */
suspend fun Context.showShortToast(@StringRes id: Int, vararg args: CurlyArg): Toast {
    val text = this.stringRes(id, *args)
    return showShortToast(text)
}

/**
 * Shows a long toast with specified text.
 *
 * @param text The text to show in the toast popup.
 */
suspend fun Context.showLongToast(text: String): Toast = withContext(Dispatchers.Main.immediate) {
    if (OwnkeyToastBus.tryShowInIme(text, durationMillis = 3_500L)) {
        Toast(this@showLongToast).apply { duration = Toast.LENGTH_LONG }
    } else {
        makeOwnkeyToast(text, Toast.LENGTH_LONG).also { it.show() }
    }
}

private fun Context.makeOwnkeyToast(text: String, duration: Int): Toast {
    val toast = Toast(this)
    val horizontalInset = dp(20)
    val backgroundColor = ownkeyToastBackground(text)
    val background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(backgroundColor)
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(18), dp(10), dp(8), dp(10))
        this.background = background
        layoutParams = ViewGroup.LayoutParams(
            (resources.displayMetrics.widthPixels - horizontalInset * 2).coerceAtLeast(dp(280)),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = dp(12).toFloat()
        }
    }
    val message = TextView(this).apply {
        this.text = text
        setTextColor(Color.BLACK)
        textSize = 13f
        includeFontPadding = false
        maxLines = 3
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        )
    }
    val close = TextView(this).apply {
        this.text = "\u00D7"
        setTextColor(Color.BLACK)
        textSize = 26f
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
        setOnClickListener { toast.cancel() }
    }
    container.addView(message)
    container.addView(close)

    toast.duration = duration
    toast.view = container
    toast.setGravity(Gravity.BOTTOM or Gravity.FILL_HORIZONTAL, 0, dp(28))
    return toast
}

private fun ownkeyToastBackground(text: String): Int {
    val normalized = text.lowercase()
    return when {
        listOf("success", "successfully", "saved", "copied", "inserted", "gelukt", "opgeslagen", "gekopieerd")
            .any { it in normalized } -> Color.rgb(62, 219, 131)
        listOf("error", "failed", "failure", "unable", "missing", "could not", "invalid", "fout", "mislukt")
            .any { it in normalized } -> Color.rgb(255, 122, 122)
        else -> Color.rgb(247, 247, 247)
    }
}

private fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

/**
 * Shows a long toast with the string resource specified by [id].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 */
suspend fun Context.showLongToast(@StringRes id: Int): Toast {
    val text = this.stringRes(id)
    return showLongToast(text)
}

/**
 * Shows a long toast with the string resource specified by [id], additionally curly formatting the string with
 * supplied arguments [args].
 *
 * @param id The string resource id of the text to display. Must not be 0.
 * @param args The curly arguments which will be filled into the string template identified by [id].
 */
suspend fun Context.showLongToast(@StringRes id: Int, vararg args: CurlyArg): Toast {
    val text = this.stringRes(id, *args)
    return showLongToast(text)
}




// These wrappers are temporary, but needed.
// Gradually in the future all event logic will be suspendable, then these wrappers will not be needed anymore.
// DO NOT USE THESE IN SUSPENDABLE CONTEXTS, THIS CAUSES ISSUES

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(text)")
)
fun Context.showShortToastSync(text: String): Toast = runBlocking {
    showShortToast(text)
}

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(id)")
)
fun Context.showShortToastSync(@StringRes id: Int): Toast = runBlocking {
    showShortToast(id)
}

@Deprecated(
    "Use suspend showShortToast instead",
    ReplaceWith("showShortToast(id, *args)")
)
fun Context.showShortToastSync(@StringRes id: Int, vararg args: CurlyArg): Toast = runBlocking {
    showShortToast(id, *args)
}

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(text)")
)
fun Context.showLongToastSync(text: String): Toast = runBlocking {
    showLongToast(text)
}

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(id)")
)
fun Context.showLongToastSync(@StringRes id: Int): Toast = runBlocking {
    showLongToast(id)
}

@Deprecated(
    "Use suspend showLongToast instead",
    ReplaceWith("showLongToast(id, *args)")
)
fun Context.showLongToastSync(@StringRes id: Int, vararg args: CurlyArg): Toast = runBlocking {
    showLongToast(id, *args)
}
