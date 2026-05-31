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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
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
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.FlorisStepState
import org.florisboard.lib.compose.stringRes

private val OwnkeySetupBackground = Color(0xFF0B0D10)
private val OwnkeySetupPanel = Color(0xFF11151A)
private val OwnkeySetupPanelRaised = Color(0xFF171C22)
private val OwnkeySetupAction = Color(0xFF25272D)
private val OwnkeySetupActionPressed = Color(0xFF30333A)
private val OwnkeySetupPrimary = Color(0xFF0B57FF)
private val OwnkeySetupText = Color(0xFFF7F8FA)
private val OwnkeySetupMutedText = Color(0xFFB6BAC3)
private val OwnkeySetupBorder = Color(0xFF2B3037)

@Composable
fun SetupScreen() = FlorisScreen {
    title = stringRes(R.string.setup__title)
    navigationIconVisible = false
    scrollable = false

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

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = OwnkeySetupPrimary,
                onPrimary = OwnkeySetupText,
                background = OwnkeySetupBackground,
                onBackground = OwnkeySetupText,
                surface = OwnkeySetupPanel,
                onSurface = OwnkeySetupText,
                surfaceVariant = OwnkeySetupAction,
                onSurfaceVariant = OwnkeySetupMutedText,
                outline = OwnkeySetupBorder,
            ),
        ) {
            SetupContent(
                stepState = stepState,
                steps = steps(context, navController, requestMicrophone, requestNotification, scope),
                footer = { footer(context) },
            )
        }
    }
}

@Composable
private fun SetupContent(
    stepState: FlorisStepState,
    steps: List<SetupStep>,
    footer: @Composable () -> Unit,
) {
    val currentStepId by stepState.getCurrent()
    val autoStepId by stepState.getCurrentAuto()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OwnkeySetupBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OwnkeySetupPanel,
                contentColor = OwnkeySetupText,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringRes(R.string.floris_app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringRes(R.string.setup__intro_message),
                        color = OwnkeySetupMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            steps.forEachIndexed { index, step ->
                SetupStepCard(
                    number = index + 1,
                    step = step,
                    isCurrent = step.id == currentStepId,
                    isAvailable = step.id <= autoStepId,
                    isDone = step.id < autoStepId,
                    onClick = { stepState.setCurrentManual(step.id) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            footer()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    number: Int,
    step: SetupStep,
    isCurrent: Boolean,
    isAvailable: Boolean,
    isDone: Boolean,
    onClick: () -> Unit,
) {
    val modifier = if (isAvailable && !isCurrent) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    val cardColor = if (isCurrent) OwnkeySetupPanelRaised else OwnkeySetupPanel
    val numberColor = when {
        isCurrent -> OwnkeySetupPrimary
        isDone -> OwnkeySetupActionPressed
        else -> OwnkeySetupAction
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, OwnkeySetupBorder, RoundedCornerShape(22.dp)),
        color = cardColor,
        contentColor = OwnkeySetupText,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        shadowElevation = if (isCurrent) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(numberColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = number.toString(),
                        color = OwnkeySetupText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!isCurrent) {
                        Text(
                            text = if (isDone) stringRes(R.string.setup__step_status_done) else stringRes(R.string.setup__step_status_locked),
                            color = OwnkeySetupMutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isCurrent) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    step.content(this)
                }
            }
        }
    }
}

@Composable
private fun footer(context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy), color = OwnkeySetupMutedText)
        }
        FlorisBulletSpacer()
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository), color = OwnkeySetupMutedText)
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
    return listOfNotNull(
        SetupStep(
            id = Steps.EnableIme.id,
            title = stringRes(R.string.setup__enable_ime__title),
        ) {
            StepText(stringRes(R.string.setup__enable_ime__description))
            StepButton(label = stringRes(R.string.setup__enable_ime__open_settings_btn)) {
                InputMethodUtils.showImeEnablerActivity(context)
            }
        },
        SetupStep(
            id = Steps.SelectIme.id,
            title = stringRes(R.string.setup__select_ime__title),
        ) {
            StepText(stringRes(R.string.setup__select_ime__description))
            StepButton(label = stringRes(R.string.setup__select_ime__switch_keyboard_btn)) {
                InputMethodUtils.showImePicker(context)
            }
        },
        SetupStep(
            id = Steps.SelectMicrophone.id,
            title = stringRes(R.string.setup__grant_microphone_permission__title),
        ) {
            StepText(stringRes(R.string.setup__grant_microphone_permission__description))
            StepButton(label = stringRes(R.string.setup__grant_microphone_permission__btn)) {
                requestMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        if (AndroidVersion.ATLEAST_API33_T) {
            SetupStep(
                id = Steps.SelectNotification.id,
                title = stringRes(R.string.setup__grant_notification_permission__title),
            ) {
                StepText(stringRes(R.string.setup__grant_notification_permission__description))
                StepButton(stringRes(R.string.setup__grant_notification_permission__btn)) {
                    requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else null,
        SetupStep(
            id = Steps.FinishUp.id,
            title = stringRes(R.string.setup__finish_up__title),
        ) {
            StepText(stringRes(R.string.setup__finish_up__description_p1))
            StepText(stringRes(R.string.setup__finish_up__description_p2))
            StepButton(label = stringRes(R.string.setup__finish_up__finish_btn)) {
                scope.launch { this@steps.prefs.internal.isImeSetUp.set(true) }
                navController.navigate(Routes.Settings.Home) {
                    popUpTo(Routes.Setup.Screen) {
                        inclusive = true
                    }
                }
            }
        }
    )
}

@Composable
private fun StepText(text: String) {
    Text(
        modifier = Modifier.padding(bottom = 10.dp),
        text = text,
        color = OwnkeySetupMutedText,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ColumnScope.StepButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .align(Alignment.End)
            .padding(top = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OwnkeySetupPrimary,
            contentColor = OwnkeySetupText,
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

private data class SetupStep(
    val id: Int,
    val title: String,
    val content: @Composable ColumnScope.() -> Unit,
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
