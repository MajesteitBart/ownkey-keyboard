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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import dev.patrickgold.jetpref.material.ui.JetPrefTextFieldAppearance

/**
 * A read-only field that opens a picker. The field only draws [item]; a layer on top takes the taps and the
 * whole control is one button for accessibility, so quick taps and TalkBack both reach [onClick].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlorisDropdownLikeButton(
    item: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onClick: () -> Unit = { },
    appearance: JetPrefTextFieldAppearance = OwnkeyTextFieldDefaults.jetPrefAppearance(),
) {
    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopStart)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = item
                onClick {
                    onClick()
                    true
                }
            },
    ) {
        JetPrefTextField(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            value = item,
            onValueChange = {},
            enabled = true,
            readOnly = true,
            isError = isError,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = true,
                    modifier = Modifier.rotate(90f), //Arrow to the right
                )
            },
            appearance = appearance,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(OwnkeyTokens.FieldShape)
                .clickable(onClick = onClick)
                .clearAndSetSemantics { },
        )
    }
}
