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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Bottom action bar: Black, no top line or elevation, pill buttons. */
@Composable
fun FlorisButtonBar(content: @Composable FlorisButtonBarScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OwnkeyTokens.Black)
            .padding(horizontal = OwnkeyTokens.ScreenInset, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scope = FlorisButtonBarScope(this)
        content(scope)
    }
}

class FlorisButtonBarScope(rowScope: RowScope) : RowScope by rowScope {
    @Composable
    fun ButtonBarButton(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        onClick: () -> Unit,
    ) {
        OwnkeyPillButton(
            label = text,
            onClick = onClick,
            modifier = modifier,
            style = OwnkeyPillStyle.Primary,
            enabled = enabled,
            icon = icon,
        )
    }

    @Composable
    fun ButtonBarTextButton(
        text: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        icon: ImageVector? = null,
        onClick: () -> Unit,
    ) {
        OwnkeyPillButton(
            label = text,
            onClick = onClick,
            modifier = modifier,
            style = OwnkeyPillStyle.Plain,
            enabled = enabled,
            icon = icon,
        )
    }

    @Composable
    fun ButtonBarSpacer() {
        Spacer(modifier = Modifier.weight(1f))
    }
}
