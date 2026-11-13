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

package dev.patrickgold.florisboard.ime.window

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/*
 * With gesture navigation on Android 13 and later, Android draws the hide-keyboard arrow and the keyboard switcher
 * inside the keyboard's own window, in a view of the class NavigationBarFrame at the bottom of the decor view. Stock
 * Android reports the same height as a caption bar inset, so the safe drawing insets already keep the keys clear of
 * the buttons. Some builds report less. On Motorola phones with Android 15 the buttons were reported over the bottom
 * key row, where a tap reaches either the button or the key. Measuring the frame itself keeps the keys above the
 * buttons on those builds too.
 */

private const val NavigationBarFrameClassName = "NavigationBarFrame"

/**
 * Overlaps above this are ignored. The platform frame is 48dp tall. On Android 15 it can briefly be as tall as the
 * whole window before its first layout, and padding the keyboard by that would push the keys off the screen.
 */
private const val MaxNavigationBarFrameOverlapDp = 96

/** The bottom inset of the navigation buttons inside the keyboard window, measured by [ImeRootWindow]. */
val LocalImeNavigationBarFrameInsets = compositionLocalOf { WindowInsets(0, 0, 0, 0) }

/**
 * The safe drawing insets, extended so content stays above the navigation buttons that Android draws inside the
 * keyboard window. Use it instead of [WindowInsets.Companion.safeDrawing] for keyboard content at the bottom edge.
 */
val WindowInsets.Companion.imeSafeDrawing: WindowInsets
    @Composable get() = safeDrawing.union(LocalImeNavigationBarFrameInsets.current)

/**
 * Measures how far the navigation buttons Android draws inside the keyboard window reach into [LocalView]. The
 * value updates after every layout of the window, which covers the frame being added, resized, shown or hidden.
 */
@Composable
fun rememberImeNavigationBarFrameInsets(): WindowInsets {
    val view = LocalView.current
    var bottomPx by remember(view) { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            bottomPx = navigationBarFrameBottomInset(view)
        }
        val observer = view.viewTreeObserver
        observer.addOnGlobalLayoutListener(listener)
        bottomPx = navigationBarFrameBottomInset(view)
        onDispose {
            // A view that was detached in the meantime has a new observer; the old one is no longer alive.
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(listener)
            } else {
                view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
    }
    return remember(bottomPx) { WindowInsets(0, 0, 0, bottomPx) }
}

private fun navigationBarFrameBottomInset(view: View): Int {
    val decor = view.rootView as? ViewGroup ?: return 0
    var frame: View? = null
    for (i in 0 until decor.childCount) {
        val child = decor.getChildAt(i)
        if (child.javaClass.simpleName == NavigationBarFrameClassName) {
            frame = child
            break
        }
    }
    if (frame == null || !frame.isShown) return 0
    val location = IntArray(2)
    frame.getLocationInWindow(location)
    val frameTop = location[1]
    view.getLocationInWindow(location)
    val viewBottom = location[1] + view.height
    val maxOverlap = (MaxNavigationBarFrameOverlapDp * view.resources.displayMetrics.density).toInt()
    return navigationBarFrameOverlap(frameTop, frame.height, viewBottom, maxOverlap)
}

/**
 * How many pixels at the bottom of a view a navigation bar frame covers. A frame that is empty, sits below the view
 * or reaches more than [maxOverlap] into it counts as zero, so the caller falls back to the reported insets.
 */
internal fun navigationBarFrameOverlap(frameTop: Int, frameHeight: Int, viewBottom: Int, maxOverlap: Int): Int {
    if (frameHeight <= 0) return 0
    val overlap = viewBottom - frameTop
    return if (overlap in 1..maxOverlap) overlap else 0
}
