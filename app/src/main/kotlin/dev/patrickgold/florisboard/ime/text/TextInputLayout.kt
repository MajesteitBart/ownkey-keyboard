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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.ime.window.isFloatingSplit
import dev.patrickgold.florisboard.ime.window.FloatingSplitControls
import dev.patrickgold.florisboard.ime.keyboard.SplitLayout
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.rewrite.RewriteOptionsPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.dictationFixController
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationFixPanel
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationFixRow
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.DictationFixState
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.audioSessionCoordinator
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()
    val windowSpec by LocalWindowController.current.activeWindowSpec.collectAsState()
    val splitMode by prefs.keyboard.splitLayoutMode.collectAsState()
    val compactSplit = windowSpec.isFloatingSplit &&
        SplitLayout.isActive(splitMode, LocalConfiguration.current, state.keyboardMode)
    val audioCoordinator by context.audioSessionCoordinator()
    // The session publishes every level sample; the layout only needs to know whether one runs.
    val audioSessionActive by androidx.compose.runtime.remember(audioCoordinator) {
        audioCoordinator.state.map { it != null }.distinctUntilChanged()
    }.collectAsState(initial = audioCoordinator.state.value != null)

    InlineSuggestionsStyleCache()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        val dictationFixController = androidx.compose.runtime.remember(context) { context.dictationFixController().value }
        // Only the chooser predicate is observed here, so the per-keystroke preview updates while a
        // word is retyped never recompose the keyboard layout.
        val dictationFixChoosing by androidx.compose.runtime.remember(dictationFixController) {
            dictationFixController.state.map { it is DictationFixState.Choosing }.distinctUntilChanged()
        }.collectAsState(initial = dictationFixController.state.value is DictationFixState.Choosing)
        // The fix offer after dictation lives in the Smartbar, so the split toolbar gives way to it too.
        val dictationFixOffered by androidx.compose.runtime.remember(dictationFixController) {
            dictationFixController.state.map { it is DictationFixState.Offered }.distinctUntilChanged()
        }.collectAsState(initial = dictationFixController.state.value is DictationFixState.Offered)
        val showSmartbar = !compactSplit || state.isActionsOverflowVisible ||
            keyboardManager.isRewriteOptionsVisible || dictationFixChoosing || dictationFixOffered || audioSessionActive
        if (showSmartbar) {
            Smartbar()
        } else {
            FloatingSplitControls(actions = true)
        }
        // While a word is being retyped the keyboard stays; the row above it shows the replacement.
        DictationFixRow()
        if (keyboardManager.isRewriteOptionsVisible) {
            RewriteOptionsPanel()
        } else if (dictationFixChoosing) {
            DictationFixPanel()
        } else if (state.isActionsOverflowVisible) {
            QuickActionsOverflowPanel()
        } else {
            Box(Modifier.padding(top = if (compactSplit) 8.dp else 0.dp)) {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.collectAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        FlorisImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator, allowFloatingSplitPanels = !showSmartbar)
            }
        }
    }
}
