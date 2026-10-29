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


package org.florisboard.lib.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp

/** Height of the collapsed title bar, without the status bar above it. */
val FlorisAppBarCollapsedHeight = 64.dp

private val ExpandedTitleRowMinHeight = 48.dp
private val ExpandedTitleBottomSlack = 14.dp
private val TitleHorizontalInsets = 32.dp
private const val TwoLineTitleFontScale = 1.5f
private const val MinLargeTitleFontSizeSp = 20f
private const val LargeTitleFontSizeStepSp = 1f

private val WordSeparator = Regex("""\s+""")

/** The expanded title's style and line count after fitting it to the bar. */
private class LargeTitleFit(val style: TextStyle, val lines: Int)

/**
 * Shrinks the large title in 1sp steps, from its role size down to 20sp, until every word fits on a line and
 * the whole title fits in [maxLines]. Checking each word keeps the line breaker from splitting one; at 20sp an
 * ellipsis is the last resort.
 */
private fun fitLargeTitle(
    measurer: TextMeasurer,
    title: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxLines: Int,
): LargeTitleFit {
    val roleSize = style.fontSize.value
    val words = title.split(WordSeparator).filter { it.isNotEmpty() }
    var size = roleSize
    while (true) {
        val scale = size / roleSize
        val sized = style.copy(
            fontSize = size.sp,
            lineHeight = if (style.lineHeight.isSpecified) (style.lineHeight.value * scale).sp else style.lineHeight,
        )
        val wordsFit = words.all { word ->
            measurer.measure(text = word, style = sized, maxLines = 1, softWrap = false).size.width <= maxWidthPx
        }
        val layout = measurer.measure(
            text = title,
            style = sized,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        if ((wordsFit && !layout.hasVisualOverflow) || size <= MinLargeTitleFontSizeSp) {
            return LargeTitleFit(sized, layout.lineCount.coerceIn(1, maxLines))
        }
        size = (size - LargeTitleFontSizeStepSp).coerceAtLeast(MinLargeTitleFontSizeSp)
    }
}

/**
 * Large title bar: Black in both states, 112dp expanded, collapses on scroll. A long or large-font title
 * shrinks before it wraps, never breaks inside a word, and at font scales of 1.5 and up may take two lines;
 * the bar grows to fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlorisAppBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit = { },
    scrollBehavior: TopAppBarScrollBehavior
) {
    val density = LocalDensity.current
    val largeTitleStyle = MaterialTheme.typography.headlineMedium
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val textMeasurer = rememberTextMeasurer()
    val maxLines = if (density.fontScale >= TwoLineTitleFontScale) 2 else 1
    val fit = remember(title, largeTitleStyle, screenWidth, density, maxLines) {
        val maxWidthPx = with(density) { (screenWidth - TitleHorizontalInsets).roundToPx() }.coerceAtLeast(1)
        fitLargeTitle(textMeasurer, title, largeTitleStyle, maxWidthPx, maxLines)
    }
    val lineHeight: Dp = with(density) {
        if (fit.style.lineHeight.isSpecified) fit.style.lineHeight.toDp() else fit.style.fontSize.toDp()
    }
    val expandedHeight = FlorisAppBarCollapsedHeight +
        max(ExpandedTitleRowMinHeight, lineHeight * fit.lines + ExpandedTitleBottomSlack)

    LargeTopAppBar(
        title = {
            // The bar renders this slot twice: large when expanded, small when collapsed.
            val isExpandedTitle = LocalTextStyle.current.fontSize == largeTitleStyle.fontSize
            Text(
                modifier = Modifier.semantics { heading() },
                text = title,
                fontSize = if (isExpandedTitle) fit.style.fontSize else LocalTextStyle.current.fontSize,
                lineHeight = if (isExpandedTitle) fit.style.lineHeight else LocalTextStyle.current.lineHeight,
                overflow = TextOverflow.Ellipsis,
                maxLines = if (isExpandedTitle) fit.lines else 1,
            )
        },
        navigationIcon = navigationIcon ?: {},
        actions = actions,
        expandedHeight = expandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = OwnkeyTokens.Black,
            scrolledContainerColor = OwnkeyTokens.Black,
            navigationIconContentColor = OwnkeyTokens.Label,
            titleContentColor = OwnkeyTokens.Label,
            actionIconContentColor = OwnkeyTokens.Label,
        ),
        scrollBehavior = scrollBehavior,
    )
}
