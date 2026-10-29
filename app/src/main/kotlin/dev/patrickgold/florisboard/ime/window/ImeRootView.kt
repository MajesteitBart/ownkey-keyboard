/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import org.florisboard.lib.compose.ProvideLocalizedResources

/**
 * Provides the [ImeWindowController] instance this composition tree is associated with.
 */
val LocalWindowController = staticCompositionLocalOf<ImeWindowController> {
    error("This composition local provider is only available within an IME view")
}

/**
 * The main entry point and bridge between the IME dialog view and the composables. It will fill the maximum area
 * available within the accompanying dialog view, and also draw under system bars.
 *
 * The layout direction will be forced to [LayoutDirection.Ltr], to ensure the window positioning logic's left/right
 * corresponds to the physical left/right. For UI components that need to conform to the actual system layout
 * direction, the UI components should be wrapped with [org.florisboard.lib.compose.ProvideActualLayoutDirection].
 *
 * @see ImeRootWindow
 */
@SuppressLint("ViewConstructor")
class ImeRootView(val ims: FlorisImeService) : AbstractComposeView(ims) {
    init {
        isHapticFeedbackEnabled = true
        layoutParams = LayoutParams(
            /* width = */ LayoutParams.MATCH_PARENT,
            /* height = */ LayoutParams.MATCH_PARENT,
        )
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(
            LocalInputFeedbackController provides ims.inputFeedbackController,
            LocalWindowController provides ims.windowController,
        ) {
            ProvideLocalizedResources(
                resourcesContext = ims.resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                ProvideImeFontScale {
                    FlorisImeTheme {
                        ImeRootWindow()
                    }
                }
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence? {
        return this::class.simpleName
    }
}

/**
 * The largest system font scale the keyboard follows. Keys and the toolbar have a fixed height, so larger text
 * would run into the hint labels and push labels such as `?123` out of their keys.
 */
internal const val ImeMaxFontScale = 1.3f

internal fun imeFontScale(systemFontScale: Float): Float = systemFontScale.coerceAtMost(ImeMaxFontScale)

/**
 * Caps the font scale for everything the keyboard draws. Below the cap the platform density is passed on as is;
 * above it, the platform's own scaling curve for [ImeMaxFontScale] is used. It is resolved once per configuration,
 * so key presses and recompositions do no extra work.
 */
@Composable
private fun ProvideImeFontScale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val imeDensity = remember(context, configuration, density) {
        val fontScale = imeFontScale(density.fontScale)
        if (fontScale == density.fontScale) {
            density
        } else {
            val capped = Configuration(configuration).apply { this.fontScale = fontScale }
            Density(context.createConfigurationContext(capped))
        }
    }
    CompositionLocalProvider(LocalDensity provides imeDensity, content = content)
}
