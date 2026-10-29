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

package dev.patrickgold.florisboard.lib.compose

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisAppBar
import org.florisboard.lib.compose.FlorisAppBarCollapsedHeight
import org.florisboard.lib.compose.LocalOwnkeyScreenInset
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes

/**
 * Content padding for screens with `scrollable = false` that scroll their own `LazyColumn`: pass it as
 * `contentPadding` so the 16dp inset and the end space scroll with the list.
 */
val LocalOwnkeyContentPadding = staticCompositionLocalOf {
    PaddingValues(
        start = OwnkeyTokens.ScreenInset,
        top = 8.dp,
        end = OwnkeyTokens.ScreenInset,
        bottom = 32.dp,
    )
}

private val ScrollContentPadding = PaddingValues(
    start = OwnkeyTokens.ScreenInset,
    top = 8.dp,
    end = OwnkeyTokens.ScreenInset,
    bottom = 32.dp,
)

@Composable
fun FlorisScreen(builder: @Composable FlorisScreenScope.() -> Unit) {
    val scope = remember { FlorisScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias FlorisScreenActions = @Composable RowScope.() -> Unit
typealias FlorisScreenBottomBar = @Composable () -> Unit
typealias FlorisScreenContent = PreferenceUiContent<FlorisPreferenceModel>
typealias FlorisScreenFab = @Composable () -> Unit
typealias FlorisScreenNavigationIcon = @Composable () -> Unit

interface FlorisScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: FlorisScreenActions)

    fun bottomBar(bottomBar: FlorisScreenBottomBar)

    fun content(content: FlorisScreenContent)

    fun floatingActionButton(fab: FlorisScreenFab)

    fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon)
}

private class FlorisScreenScopeImpl : FlorisScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(false)

    private var actions: FlorisScreenActions = @Composable { }
    private var bottomBar: FlorisScreenBottomBar = @Composable { }
    private var content: FlorisScreenContent = @Composable { }
    private var fab: FlorisScreenFab = @Composable { }
    private var navigationIcon: FlorisScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        // Bare 24dp arrow in a 48dp target. The AutoMirrored icon flips itself in RTL.
        IconButton(onClick = { navController.popBackStack() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringRes(R.string.ownkey_ui__back),
                tint = OwnkeyTokens.Label,
            )
        }
    }

    override fun actions(actions: FlorisScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: FlorisScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: FlorisScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: FlorisScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: FlorisScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val density = LocalDensity.current
        val previewFieldController = LocalPreviewFieldController.current

        SideEffect {
            val window = (context as Activity).window
            previewFieldController?.isVisible = previewFieldVisible
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = false
            } else {
                window.navigationBarColor = OwnkeyTokens.Black.toArgb()
            }
        }

        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val scrollState = rememberScrollState()

        // With the preview field focused the keyboard leaves little room, so the large title gives way.
        val isImeVisible = WindowInsets.isImeVisible
        var collapsedForIme by remember { mutableStateOf(false) }
        LaunchedEffect(previewFieldVisible, isImeVisible) {
            val barState = scrollBehavior.state
            if (previewFieldVisible && isImeVisible) {
                barState.heightOffset = barState.heightOffsetLimit
                collapsedForIme = true
            } else if (collapsedForIme) {
                collapsedForIme = false
                if (!scrollable || scrollState.value == 0) {
                    barState.heightOffset = 0f
                }
            }
        }

        // Typing in the preview field with the keyboard up can leave less room above the field than the collapsed
        // bar needs (landscape). Then the screen draws nothing rather than a clipped sliver of bar and rows. It stays
        // composed, so scroll positions and open dialogs survive.
        val barInsets = TopAppBarDefaults.windowInsets
        val typingInPreview = previewFieldVisible && previewFieldController?.isFocused == true
        var tooShortForBar by remember { mutableStateOf(false) }
        fun Density.minBarHeightPx(): Float = barInsets.getTop(this) + FlorisAppBarCollapsedHeight.toPx()

        Scaffold(
            modifier = Modifier
                .onSizeChanged { size -> tooShortForBar = size.height < density.minBarHeightPx() }
                .drawWithContent {
                    if (!typingInPreview || size.height >= minBarHeightPx()) drawContent()
                }
                .then(if (typingInPreview && tooShortForBar) Modifier.clearAndSetSemantics { } else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { FlorisAppBar(title, navigationIcon.takeIf { navigationIconVisible }, actions, scrollBehavior) },
            bottomBar = bottomBar,
            floatingActionButton = fab,
            containerColor = OwnkeyTokens.Black,
            contentColor = OwnkeyTokens.Label,
        ) { innerPadding ->
            if (scrollable) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .florisVerticalScroll(scrollState),
                ) {
                    CompositionLocalProvider(LocalOwnkeyScreenInset provides true) {
                        PreferenceLayout(
                            FlorisPreferenceStore,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = OwnkeyTokens.ContentMaxWidth)
                                .fillMaxWidth()
                                .padding(ScrollContentPadding),
                            iconSpaceReserved = iconSpaceReserved,
                            content = content,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                ) {
                    CompositionLocalProvider(
                        LocalOwnkeyScreenInset provides false,
                        LocalOwnkeyContentPadding provides ScrollContentPadding,
                    ) {
                        PreferenceLayout(
                            FlorisPreferenceStore,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = OwnkeyTokens.ContentMaxWidth)
                                .fillMaxWidth(),
                            iconSpaceReserved = iconSpaceReserved,
                            content = content,
                        )
                    }
                }
            }
        }
    }
}
