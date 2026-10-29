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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ui.ChoiceOption
import dev.patrickgold.florisboard.app.ui.DialogSliderPreference
import dev.patrickgold.florisboard.app.ui.ListPreference
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceListRow
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import dev.patrickgold.florisboard.app.ui.SwitchPreference
import dev.patrickgold.florisboard.app.ui.isChanged
import dev.patrickgold.florisboard.ime.input.HapticVibrationMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import org.florisboard.lib.compose.stringRes
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode as ActivationMode

@Composable
private fun feedbackOptions(followPhoneSummary: String) = listOf(
    ChoiceOption(FeedbackChoice.OFF, stringRes(R.string.settings__keyboard__feedback_off)),
    ChoiceOption(
        FeedbackChoice.FOLLOW_PHONE,
        stringRes(R.string.settings__keyboard__feedback_follow_phone),
        followPhoneSummary,
    ),
    ChoiceOption(FeedbackChoice.ALWAYS, stringRes(R.string.settings__keyboard__feedback_always)),
)

private fun CoroutineScope.applyFeedback(
    choice: FeedbackChoice,
    enabled: PreferenceData<Boolean>,
    activationMode: PreferenceData<ActivationMode>,
    featKeyPress: PreferenceData<Boolean>,
) {
    val writes = choice.writes()
    launch {
        writes.activationMode?.let { activationMode.set(it) }
        writes.featKeyPress?.let { featKeyPress.set(it) }
        enabled.set(writes.enabled)
    }
}

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun InputFeedbackScreen() = FlorisScreen {
    title = stringRes(R.string.settings__input_feedback__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val vibrator = context.systemVibratorOrNull()

    content {
        val scope = rememberCoroutineScope()
        val feedback = prefs.inputFeedback

        PreferenceGroup(title = "") {
            val audioEnabled by feedback.audioEnabled.collectAsState()
            val audioMode by feedback.audioActivationMode.collectAsState()
            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("feedback-key-sounds"),
                title = stringRes(R.string.settings__keyboard__key_sounds),
                options = feedbackOptions(stringRes(R.string.settings__keyboard__key_sounds_follow_summary)),
                selected = FeedbackChoice.of(audioEnabled, audioMode),
                onSelect = { choice ->
                    scope.applyFeedback(choice, feedback.audioEnabled, feedback.audioActivationMode, feedback.audioFeatKeyPress)
                },
            )
            DialogSliderPreference(
                feedback.audioVolume,
                title = stringRes(R.string.settings__keyboard__volume),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 1,
                max = 100,
                stepIncrement = 1,
                visibleIf = { feedback.audioEnabled isEqualTo true },
            )
            SwitchPreference(
                feedback.audioFeatKeyPress,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                visibleIf = { isChanged(feedback.audioFeatKeyPress) },
            )
            SwitchPreference(
                feedback.audioFeatKeyLongPress,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                visibleIf = { isChanged(feedback.audioFeatKeyLongPress) },
            )
            SwitchPreference(
                feedback.audioFeatKeyRepeatedAction,
                title = stringRes(R.string.pref__input_feedback__audio_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                visibleIf = { isChanged(feedback.audioFeatKeyRepeatedAction) },
            )
            SwitchPreference(
                feedback.audioFeatGestureSwipe,
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_swipe__label),
                visibleIf = { isChanged(feedback.audioFeatGestureSwipe) },
            )
            SwitchPreference(
                feedback.audioFeatGestureMovingSwipe,
                title = stringRes(R.string.pref__input_feedback__audio_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_moving_swipe__summary),
                visibleIf = { isChanged(feedback.audioFeatGestureMovingSwipe) },
            )
        }

        // A phone that can't vibrate gets no Vibration settings: none of them would do anything.
        PreferenceGroup(title = "", visibleIf = { vibrator != null }) {
            val hapticEnabled by feedback.hapticEnabled.collectAsState()
            val hapticMode by feedback.hapticActivationMode.collectAsState()
            OwnkeyChoiceListRow(
                modifier = Modifier.testTag("feedback-vibration"),
                title = stringRes(R.string.settings__keyboard__vibration),
                options = feedbackOptions(stringRes(R.string.settings__keyboard__vibration_follow_summary)),
                selected = FeedbackChoice.of(hapticEnabled, hapticMode),
                onSelect = { choice ->
                    scope.applyFeedback(choice, feedback.hapticEnabled, feedback.hapticActivationMode, feedback.hapticFeatKeyPress)
                },
            )

            // One "Strength" slider: amplitude where the phone supports it, otherwise how long each buzz lasts.
            // Moving it switches to direct vibration, the only mode where either value applies.
            val hasAmplitudeControl = vibrator?.hasAmplitudeControl() == true
            val useDirectVibration = {
                scope.launch { feedback.hapticVibrationMode.set(HapticVibrationMode.USE_VIBRATOR_DIRECTLY) }
            }
            if (hasAmplitudeControl) {
                DialogSliderPreference(
                    feedback.hapticVibrationStrength,
                    title = stringRes(R.string.settings__keyboard__strength),
                    valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    onPreviewSelectedValue = { strength ->
                        useDirectVibration()
                        vibrator.vibrate(feedback.hapticVibrationDuration.get(), strength)
                    },
                    visibleIf = { feedback.hapticEnabled isEqualTo true },
                )
                DialogSliderPreference(
                    feedback.hapticVibrationDuration,
                    title = stringRes(R.string.pref__input_feedback__haptic_vibration_duration__label),
                    valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    onPreviewSelectedValue = { duration ->
                        vibrator.vibrate(duration, feedback.hapticVibrationStrength.get())
                    },
                    visibleIf = { isChanged(feedback.hapticVibrationDuration) },
                )
            } else {
                DialogSliderPreference(
                    feedback.hapticVibrationDuration,
                    title = stringRes(R.string.settings__keyboard__strength),
                    valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                    min = 1,
                    max = 100,
                    stepIncrement = 1,
                    onPreviewSelectedValue = { duration ->
                        useDirectVibration()
                        vibrator?.vibrate(duration, feedback.hapticVibrationStrength.get())
                    },
                    visibleIf = { feedback.hapticEnabled isEqualTo true && vibrator != null },
                )
            }
            ListPreference(
                feedback.hapticVibrationMode,
                title = stringRes(R.string.pref__input_feedback__haptic_vibration_mode__label),
                entries = enumDisplayEntriesOf(HapticVibrationMode::class),
                visibleIf = { isChanged(feedback.hapticVibrationMode) },
            )
            SwitchPreference(
                feedback.hapticFeatKeyPress,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_press__summary),
                visibleIf = { isChanged(feedback.hapticFeatKeyPress) },
            )
            SwitchPreference(
                feedback.hapticFeatKeyLongPress,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_long_press__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_long_press__summary),
                visibleIf = { isChanged(feedback.hapticFeatKeyLongPress) },
            )
            SwitchPreference(
                feedback.hapticFeatKeyRepeatedAction,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_key_repeated_action__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_key_repeated_action__summary),
                visibleIf = { isChanged(feedback.hapticFeatKeyRepeatedAction) },
            )
            SwitchPreference(
                feedback.hapticFeatGestureSwipe,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_swipe__label),
                visibleIf = { isChanged(feedback.hapticFeatGestureSwipe) },
            )
            SwitchPreference(
                feedback.hapticFeatGestureMovingSwipe,
                title = stringRes(R.string.pref__input_feedback__haptic_feat_gesture_moving_swipe__label),
                summary = stringRes(R.string.pref__input_feedback__any_feat_gesture_moving_swipe__summary),
                visibleIf = { isChanged(feedback.hapticFeatGestureMovingSwipe) },
            )
        }
    }
}
