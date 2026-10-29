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

package dev.patrickgold.florisboard.app.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.chipColors
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.LocalOwnkeyContentPadding
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

@Composable
fun ThirdPartyLicensesScreen() = FlorisScreen {
    title = stringRes(R.string.about__third_party_licenses__title)
    scrollable = false
    iconSpaceReserved = false

    val lazyListState = rememberLazyListState()
    val libs by produceLibraries()

    content {
        LibrariesContainer(
            libraries = libs,
            modifier = Modifier
                .fillMaxSize()
                .florisScrollbar(lazyListState, isVertical = true),
            contentPadding = LocalOwnkeyContentPadding.current,
            colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = OwnkeyTokens.Black,
                libraryContentColor = OwnkeyTokens.Label,
                versionChipColors = LibraryDefaults.chipColors(OwnkeyTokens.Card, OwnkeyTokens.Secondary),
                licenseChipColors = LibraryDefaults.chipColors(OwnkeyTokens.Raised, OwnkeyTokens.Label),
                fundingChipColors = LibraryDefaults.chipColors(OwnkeyTokens.Raised, OwnkeyTokens.Label),
                // The license dialog is a separate window: Card fill, Label text and button.
                dialogBackgroundColor = OwnkeyTokens.Card,
                dialogContentColor = OwnkeyTokens.Label,
                dialogConfirmButtonColor = OwnkeyTokens.Label,
            ),
            shapes = LibraryDefaults.libraryShapes(chipShape = OwnkeyTokens.PillShape),
            textStyles = LibraryDefaults.libraryTextStyles(
                nameTextStyle = MaterialTheme.typography.bodyLarge,
                versionTextStyle = MaterialTheme.typography.bodySmall,
                authorTextStyle = MaterialTheme.typography.bodyMedium,
            ),
            lazyListState = lazyListState,
        )
    }
}
