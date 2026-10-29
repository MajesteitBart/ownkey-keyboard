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

package dev.patrickgold.florisboard.app.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.compose.FlorisScreenScope
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.ownkeyFocusRing
import org.florisboard.lib.compose.stringRes

private val StatusCircleSize = 28.dp
private val StepFocusShape = RoundedCornerShape(20.dp)

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
    val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
    val notificationPermissionState by prefs.internal.notificationPermissionState.collectAsState()
    val microphonePermissionState by prefs.internal.microphonePermissionState.collectAsState()
    var hasMicrophonePermission by remember {
        mutableStateOf(context.hasMicrophonePermission())
    }

    val requestMicrophone =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            hasMicrophonePermission = isGranted
            scope.launch {
                prefs.internal.microphonePermissionState.set(
                    if (isGranted) MicrophonePermissionState.GRANTED else MicrophonePermissionState.DENIED
                )
            }
        }

    val requestNotification =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            scope.launch {
                prefs.internal.notificationPermissionState.set(
                    if (isGranted) NotificationPermissionState.GRANTED else NotificationPermissionState.DENIED
                )
            }
        }

    content(
        isFlorisBoardEnabled,
        isFlorisBoardSelected,
        hasMicrophonePermission,
        context,
        navController,
        requestMicrophone,
        requestNotification,
        microphonePermissionState,
        notificationPermissionState,
        scope,
    )
}

@Composable
private fun FlorisScreenScope.content(
    isFlorisBoardEnabled: Boolean,
    isFlorisBoardSelected: Boolean,
    hasMicrophonePermission: Boolean,
    context: Context,
    navController: NavController,
    requestMicrophone: ManagedActivityResultLauncher<String, Boolean>,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    microphonePermissionState: MicrophonePermissionState,
    notificationPermissionState: NotificationPermissionState,
    scope: CoroutineScope,
) {
    fun autoStep(): Int = when {
        !isFlorisBoardEnabled -> Steps.EnableIme.id
        !isFlorisBoardSelected -> Steps.SelectIme.id
        !hasMicrophonePermission && microphonePermissionState == MicrophonePermissionState.NOT_SET -> Steps.SelectMicrophone.id
        notificationPermissionState == NotificationPermissionState.NOT_SET && AndroidVersion.ATLEAST_API33_T -> Steps.SelectNotification.id
        else -> Steps.FinishUp.id
    }

    val stepState = rememberSaveable(saver = FlorisStepState.Saver) {
        FlorisStepState.new(init = autoStep())
    }

    content {
        LaunchedEffect(
            isFlorisBoardEnabled,
            isFlorisBoardSelected,
            hasMicrophonePermission,
            microphonePermissionState,
            notificationPermissionState,
        ) {
            stepState.setCurrentAuto(autoStep())
        }

        // Return from the system IME enabler activity as soon as Ownkey becomes enabled.
        LaunchedEffect(Unit) {
            while (true) {
                delay(200L)
                val isEnabled = InputMethodUtils.isFlorisboardEnabled(context)
                if (stepState.getCurrentAuto().value == Steps.EnableIme.id &&
                    stepState.getCurrentManual().value == -1 &&
                    !isFlorisBoardEnabled &&
                    !isFlorisBoardSelected &&
                    isEnabled
                ) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = (Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                }
            }
        }

        SetupContent(
            context = context,
            stepState = stepState,
            steps = steps(context, navController, requestMicrophone, requestNotification, scope),
        )
    }
}

@Composable
private fun SetupContent(
    context: Context,
    stepState: FlorisStepState,
    steps: List<SetupStep>,
) {
    val currentStepId by stepState.getCurrent()
    val autoStepId by stepState.getCurrentAuto()

    Text(
        modifier = Modifier.padding(bottom = OwnkeyTokens.GroupGap),
        text = stringRes(R.string.setup__intro_message),
        style = MaterialTheme.typography.bodyLarge,
        color = OwnkeyTokens.Secondary,
    )

    OwnkeyGroup {
        steps.forEachIndexed { index, step ->
            val status = when {
                step.id == currentStepId -> StepStatus.CURRENT
                step.id < autoStepId -> StepStatus.DONE
                else -> StepStatus.LOCKED
            }
            SetupStepRow(
                number = index + 1,
                step = step,
                status = status,
                // Finished steps can be reopened, for example to switch keyboards again.
                onClick = if (step.id <= autoStepId && status != StepStatus.CURRENT) {
                    { stepState.setCurrentManual(step.id) }
                } else {
                    null
                },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        OwnkeyButton(
            label = stringRes(R.string.setup__footer__privacy_policy),
            onClick = { context.launchUrl(privacyPolicyUrl) },
            style = OwnkeyButtonStyle.Plain,
        )
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        OwnkeyButton(
            label = stringRes(R.string.setup__footer__repository),
            onClick = { context.launchUrl(repositoryUrl) },
            style = OwnkeyButtonStyle.Plain,
        )
    }
}

private enum class StepStatus { DONE, CURRENT, LOCKED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupStepRow(
    number: Int,
    step: SetupStep,
    status: StepStatus,
    onClick: (() -> Unit)?,
) {
    val stateText = stringRes(
        when (status) {
            StepStatus.DONE -> R.string.setup__step_status_done
            StepStatus.CURRENT -> R.string.setup__step_status_current
            StepStatus.LOCKED -> R.string.setup__step_status_locked
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OwnkeyTokens.Card)
            .then(
                if (onClick != null) {
                    Modifier
                        .ownkeyFocusRing(StepFocusShape, inset = true)
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = status != StepStatus.CURRENT) { stateDescription = stateText }
            .testTag("setup-step-${step.id}")
            .heightIn(min = OwnkeyTokens.RowMinHeight)
            .padding(horizontal = OwnkeyTokens.RowPaddingHorizontal, vertical = OwnkeyTokens.RowPaddingVertical),
        verticalAlignment = Alignment.Top,
    ) {
        StatusCircle(number = number, status = status)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.heightIn(min = StatusCircleSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (status == StepStatus.LOCKED) OwnkeyTokens.Secondary else OwnkeyTokens.Label,
                )
            }
            if (status == StepStatus.CURRENT) {
                Text(
                    text = step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OwnkeyTokens.Secondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OwnkeyButton(
                        modifier = Modifier.testTag("setup-step-action"),
                        label = step.actionLabel,
                        onClick = step.onAction,
                    )
                    if (step.skipLabel != null && step.onSkip != null) {
                        OwnkeyButton(
                            modifier = Modifier.testTag("setup-step-skip"),
                            label = step.skipLabel,
                            onClick = step.onSkip,
                            style = OwnkeyButtonStyle.Plain,
                        )
                    }
                }
            }
        }
    }
}

/** Done: accent with a check. Current: white with a black number. Locked: Raised with a grey number. */
@Composable
private fun StatusCircle(number: Int, status: StepStatus) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(StatusCircleSize)
            .clip(CircleShape)
            .background(
                when (status) {
                    StepStatus.DONE -> accent
                    StepStatus.CURRENT -> OwnkeyTokens.Label
                    StepStatus.LOCKED -> OwnkeyTokens.Raised
                }
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (status == StepStatus.DONE) {
            Icon(
                modifier = Modifier.size(18.dp),
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = onAccent,
            )
        } else {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (status == StepStatus.CURRENT) OwnkeyTokens.Black else OwnkeyTokens.Secondary,
            )
        }
    }
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.steps(
    context: Context,
    navController: NavController,
    requestMicrophone: ManagedActivityResultLauncher<String, Boolean>,
    requestNotification: ManagedActivityResultLauncher<String, Boolean>,
    scope: CoroutineScope,
): List<SetupStep> {
    val notNow = stringRes(R.string.setup__not_now)
    return listOfNotNull(
        SetupStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
            body = stringRes(R.string.setup__enable_ime__description),
            actionLabel = stringRes(R.string.setup__enable_ime__open_settings_btn),
            onAction = { InputMethodUtils.showImeEnablerActivity(context) },
        ),
        SetupStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
            body = stringRes(R.string.setup__select_ime__description),
            actionLabel = stringRes(R.string.setup__select_ime__switch_keyboard_btn),
            onAction = { InputMethodUtils.showImePicker(context) },
        ),
        SetupStep(
            id = Steps.SelectMicrophone.id,
            title = stringRes(R.string.setup__grant_microphone_permission__title),
            body = stringRes(R.string.setup__grant_microphone_permission__description),
            actionLabel = stringRes(R.string.setup__grant_microphone_permission__btn),
            onAction = {
                scope.launch { prefs.internal.microphonePermissionRequested.set(true) }
                requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            },
            // Skipping counts as a denial, so setup stops asking; AI asks again when dictation needs it.
            skipLabel = notNow,
            onSkip = {
                scope.launch { prefs.internal.microphonePermissionState.set(MicrophonePermissionState.DENIED) }
            },
        ),
        if (AndroidVersion.ATLEAST_API33_T) {
            SetupStep(
                id = Steps.SelectNotification.id,
                title = stringRes(R.string.setup__grant_notification_permission__title),
                body = stringRes(R.string.setup__grant_notification_permission__description),
                actionLabel = stringRes(R.string.setup__grant_notification_permission__btn),
                onAction = { requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS) },
                skipLabel = notNow,
                onSkip = {
                    scope.launch {
                        prefs.internal.notificationPermissionState.set(NotificationPermissionState.DENIED)
                    }
                },
            )
        } else null,
        SetupStep(
            id = Steps.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
            body = stringRes(R.string.setup__finish_up__description_p1),
            actionLabel = stringRes(R.string.setup__finish_up__finish_btn),
            onAction = {
                scope.launch { this@steps.prefs.internal.isImeSetUp.set(true) }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            },
        ),
    )
}

private data class SetupStep(
    val id: Int,
    val title: String,
    val body: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    val skipLabel: String? = null,
    val onSkip: (() -> Unit)? = null,
)

private fun Context.hasMicrophonePermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}

private sealed class Steps(val id: Int) {
    data object EnableIme : Steps(id = 1)
    data object SelectIme : Steps(id = 2)
    data object SelectMicrophone : Steps(id = 3)
    data object SelectNotification : Steps(id = 4)
    data object FinishUp : Steps(id = 5)
}
