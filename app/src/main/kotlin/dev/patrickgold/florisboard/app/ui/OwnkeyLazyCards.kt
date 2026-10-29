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

package dev.patrickgold.florisboard.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ProvideOwnkeyCardContext

/**
 * Shape of row [index] of [count] when a grouped card is built from separate `LazyColumn` items: the first
 * row gets the card's top corners, the last its bottom corners.
 */
internal fun lazyCardShape(index: Int, count: Int): Shape {
    val radius = 24.dp
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == count - 1) radius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * One row of a grouped card inside a `LazyColumn`: the 2dp black gap above every row but the first, the
 * card corners on the ends, and the card context so rows paint Card.
 */
@Composable
fun LazyCardRow(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (index == 0) 0.dp else OwnkeyTokens.RowGap)
            .clip(lazyCardShape(index, count)),
    ) {
        ProvideOwnkeyCardContext(content)
    }
}

/** Group header for a card built from `LazyColumn` items. [first] drops the 24dp gap above it. */
@Composable
fun LazyCardHeader(text: String, first: Boolean = false) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OwnkeyTokens.RowPaddingHorizontal,
                end = OwnkeyTokens.RowPaddingHorizontal,
                top = if (first) 0.dp else OwnkeyTokens.GroupGap,
                bottom = OwnkeyTokens.GroupHeaderBottom,
            )
            .semantics { heading() },
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OwnkeyTokens.Secondary,
    )
}
