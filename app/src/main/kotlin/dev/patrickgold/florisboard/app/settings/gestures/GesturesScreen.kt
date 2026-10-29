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

package dev.patrickgold.florisboard.app.settings.gestures

import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.DialogSliderPreference
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.isChanged
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun GesturesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__gestures__title)
    previewFieldVisible = true

    content {
        val swipeLeft = stringRes(R.string.pref__gestures__swipe_left__label)
        val longPress = stringRes(R.string.settings__keyboard__gestures_long_press)

        // Letter swipes don't apply while glide typing (a devtools switch) is on.
        PreferenceGroup(title = stringRes(R.string.settings__keyboard__gestures_letters)) {
            ListPreference(
                prefs.gestures.swipeUp,
                title = stringRes(R.string.pref__gestures__swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeDown,
                title = stringRes(R.string.pref__gestures__swipe_down__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeLeft,
                title = swipeLeft,
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
            ListPreference(
                prefs.gestures.swipeRight,
                title = stringRes(R.string.pref__gestures__swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
                enabledIf = { prefs.glide.enabled isEqualTo false },
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__gestures_space_bar)) {
            ListPreference(
                prefs.gestures.spaceBarSwipeUp,
                title = stringRes(R.string.pref__gestures__swipe_up__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeLeft,
                title = swipeLeft,
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarSwipeRight,
                title = stringRes(R.string.pref__gestures__swipe_right__label),
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
            ListPreference(
                prefs.gestures.spaceBarLongPress,
                title = longPress,
                entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__gestures_delete_key)) {
            ListPreference(
                prefs.gestures.deleteKeySwipeLeft,
                title = swipeLeft,
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
            )
            ListPreference(
                prefs.gestures.deleteKeyLongPress,
                title = longPress,
                entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
            )
        }

        PreferenceGroup(title = stringRes(R.string.settings__keyboard__gestures_sensitivity)) {
            DialogSliderPreference(
                prefs.gestures.swipeVelocityThreshold,
                title = stringRes(R.string.pref__gestures__swipe_velocity_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
                min = 400,
                max = 4000,
                stepIncrement = 100,
                visibleIf = { isChanged(prefs.gestures.swipeVelocityThreshold) },
            )
            DialogSliderPreference(
                prefs.gestures.swipeDistanceThreshold,
                title = stringRes(R.string.pref__gestures__swipe_distance_threshold__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 12,
                max = 72,
                stepIncrement = 1,
                visibleIf = { isChanged(prefs.gestures.swipeDistanceThreshold) },
            )
        }
    }
}
