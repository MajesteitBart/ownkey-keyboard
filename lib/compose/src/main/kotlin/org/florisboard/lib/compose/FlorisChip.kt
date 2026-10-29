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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Pill chip on Raised with no border. Selected chips are white with black text. */
@Composable
fun FlorisChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = OwnkeyTokens.PillShape,
    leadingIcons: List<ImageVector> = listOf(),
    trailingIcons: List<ImageVector> = listOf(),
) {
    val disabledAlpha = OwnkeyTokens.DisabledAlpha
    InputChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = null,
        colors = InputChipDefaults.inputChipColors(
            containerColor = OwnkeyTokens.Raised,
            labelColor = OwnkeyTokens.Label,
            leadingIconColor = OwnkeyTokens.Label,
            trailingIconColor = OwnkeyTokens.Label,
            disabledContainerColor = OwnkeyTokens.Raised.copy(alpha = disabledAlpha),
            disabledLabelColor = OwnkeyTokens.Label.copy(alpha = disabledAlpha),
            disabledLeadingIconColor = OwnkeyTokens.Label.copy(alpha = disabledAlpha),
            disabledTrailingIconColor = OwnkeyTokens.Label.copy(alpha = disabledAlpha),
            selectedContainerColor = OwnkeyTokens.Label,
            selectedLabelColor = OwnkeyTokens.Black,
            selectedLeadingIconColor = OwnkeyTokens.Black,
            selectedTrailingIconColor = OwnkeyTokens.Black,
            disabledSelectedContainerColor = OwnkeyTokens.Label.copy(alpha = disabledAlpha),
        ),
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.ownkeyFocusRing(shape),
        leadingIcon = {
            Row {
                for (leadingIcon in leadingIcons) {
                    Icon(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        imageVector = leadingIcon,
                        contentDescription = null,
                    )
                }
            }
        },
        trailingIcon = {
            Row {
                for (trailingIcon in trailingIcons) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(16.dp),
                        imageVector = trailingIcon,
                        contentDescription = null,
                    )
                }
            }
        }
    )
}
