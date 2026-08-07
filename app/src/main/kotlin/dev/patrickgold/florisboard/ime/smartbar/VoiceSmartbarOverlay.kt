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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.OwnkeyBrand
import dev.patrickgold.florisboard.app.ownkeyAccentColor
import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteSurface
import dev.patrickgold.florisboard.voiceRewriteUiController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

private const val CoachMarkVisibleMillis = 7_000L

/**
 * Smartbar overlay for the dictation-key accelerator.
 *
 * While voice rewrite is resolving its target it shows the `Speak an edit` mode status as visible
 * text plus a polite live region, so hold recognition is confirmed in words and not only by haptic
 * and colour. When no session is active it may instead show the one-time coach mark. The overlay
 * lives in the smartbar and never covers the key area, so ordinary typing is never blocked.
 */
@Composable
fun VoiceSmartbarOverlay(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiController = remember(context) { context.voiceRewriteUiController() }
    val model by uiController.value.uiState.collectAsState()

    when (model.surface) {
        VoiceRewriteSurface.TARGETING,
        VoiceRewriteSurface.DISCLOSURE,
        -> VoiceModeStatusPill(
            modifier = modifier,
            label = stringRes(R.string.voice_rewrite__state_speak_an_edit),
        )

        VoiceRewriteSurface.HUB -> VoiceRewriteCoachMark(modifier = modifier)

        else -> Unit
    }
}

@Composable
private fun VoiceModeStatusPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = OwnkeyBrand.Glass.Sheet,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                contentDescription = null,
                tint = ownkeyAccentColor(),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One-time `Tap to dictate · hold to rewrite` hint. It appears only once both AI providers are
 * configured, and tapping it or letting it time out records the dismissal so it cannot recur.
 */
@Composable
private fun VoiceRewriteCoachMark(
    modifier: Modifier = Modifier,
) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alreadyDismissed by prefs.voxtral.voiceRewriteCoachMarkShown.collectAsState()
    val uiController = remember(context) { context.voiceRewriteUiController() }
    var providersConfigured by remember { mutableStateOf(false) }

    LaunchedEffect(alreadyDismissed) {
        providersConfigured = if (alreadyDismissed) {
            false
        } else {
            // Reading configured provider state touches the Keystore-backed secret stores, so it
            // must never run on the typing-critical thread.
            withContext(Dispatchers.IO) { uiController.value.providersConfigured() }
        }
    }

    if (alreadyDismissed || !providersConfigured) return

    val dismiss: () -> Unit = {
        scope.launch { prefs.voxtral.voiceRewriteCoachMarkShown.set(true) }
    }

    LaunchedEffect(Unit) {
        delay(CoachMarkVisibleMillis)
        dismiss()
    }

    val dismissLabel = stringRes(R.string.voice_rewrite__coach_mark_dismiss)
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClickLabel = dismissLabel, onClick = dismiss),
        color = OwnkeyBrand.Glass.Sheet,
        contentColor = OwnkeyBrand.Glass.Ink,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_tabler_microphone),
                contentDescription = null,
                tint = ownkeyAccentColor(),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringRes(R.string.voice_rewrite__coach_mark),
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dismissLabel,
                color = ownkeyAccentColor(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
