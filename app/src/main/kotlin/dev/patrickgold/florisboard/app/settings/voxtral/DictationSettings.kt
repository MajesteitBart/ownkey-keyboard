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

package dev.patrickgold.florisboard.app.settings.voxtral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyChoiceRow
import dev.patrickgold.florisboard.app.ui.OwnkeyDialog
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.offline.DownloadWaitReason
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloadNetwork
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelDownloads
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.OwnkeyTokens
import org.florisboard.lib.compose.stringRes
import org.ownkey.offline.LocalAsrException
import org.ownkey.offline.LocalAsrFailure
import org.ownkey.offline.ModelCatalog

/**
 * The dictation route choice and, below it, the on-device model group. The choice is the only place that
 * switches routes. A route that can't dictate yet stays visible but dimmed, says what is missing, and runs
 * its fix when tapped: the download dialog for On device, [onAddCloudKey] or [onFixCloudAddress] for Cloud.
 */
@Composable
internal fun DictationSettings(
    hasCloudKey: Boolean,
    cloudEndpointUrl: String,
    onAddCloudKey: () -> Unit,
    onFixCloudAddress: () -> Unit,
    dictionaryRow: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val selected by prefs.voxtral.dictationBackend.collectAsState()
    val controller = remember { context.offlineDictation() }
    val previous by prefs.voxtral.previousDictationBackend.collectAsState()
    val state by controller.state.collectAsState()
    val runtime by controller.runtime.state.collectAsState()
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<LocalAsrFailure?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var downloadDialog by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var noticesDialog by rememberSaveable { mutableStateOf(false) }
    var mobileData by rememberSaveable { mutableStateOf(false) }
    var notices by remember { mutableStateOf("") }
    var waitReason by remember { mutableStateOf(DownloadWaitReason.STARTING) }
    val backend = TranscriptionBackend.resolve(selected, hasCloudKey, BuildConfig.DEBUG)
    val previousBackend = TranscriptionBackend.resolve(previous, hasCloudKey, BuildConfig.DEBUG)
    val active = backend == TranscriptionBackend.ORUKEET
    val working = action?.isActive == true || state.phase in setOf(ModelPhase.CHECKING, ModelPhase.ACTIVATING, ModelPhase.REMOVING)
    val transferring = state.transferPhase != null
    val waiting = state.transferPhase == ModelPhase.WAITING_FOR_NETWORK
    val candidateInstalled = ModelCatalog.current.id in state.installed
    // An older verified model still dictates while its update downloads, so either one makes Orukeet usable.
    val usableModelId = if (candidateInstalled) ModelCatalog.current.id else state.currentId
    // Public builds don't offer a download the device can't run. A restored local selection still
    // shows Orukeet, so there is a way back to another provider. Internal builds show it to report the reason.
    val showOrukeet = BuildConfig.ORUKEET_INTERNAL || controller.compatible || selected == TranscriptionBackend.ORUKEET.preference
    val cloudEndpointValid = isUsableCloudEndpoint(cloudEndpointUrl)
    val cloudProvider = cloudProviderName(cloudEndpointUrl) ?: stringRes(R.string.settings__ai__status__custom_server)
    LaunchedEffect(state.transferPhase, state.allowMobileData) {
        while (state.transferPhase == ModelPhase.WAITING_FOR_NETWORK) {
            waitReason = ModelDownloadNetwork.waitReason(context, state.allowMobileData)
            delay(2000)
        }
    }
    fun perform(block: suspend () -> Unit) {
        if (action?.isActive == true) return
        action = scope.launch {
            error = null
            errorDetail = null
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                val local = failure as? LocalAsrException
                error = local?.reason ?: LocalAsrFailure.RUNTIME
                errorDetail = if (local != null) local.detail else failure.javaClass.simpleName
            }
            finally { action = null }
        }
    }
    fun openDownloadDialog() {
        mobileData = state.allowMobileData
        downloadDialog = true
    }

    OwnkeyGroup(
        title = stringRes(R.string.settings__ai__dictation__title),
        footer = stringRes(R.string.settings__ai__dictation__footer),
    ) {
        // Debug builds without a key fall back to demo dictation, which has no choice of its own.
        if (backend == TranscriptionBackend.MOCK || backend == TranscriptionBackend.UNAVAILABLE) {
            OwnkeyNotice(text = stringResource(R.string.orukeet__selected, backendLabel(backend)))
        }
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(OwnkeyTokens.RowGap),
        ) {
            if (showOrukeet) {
                val needsDownload = controller.compatible && usableModelId == null && !transferring && !working
                OwnkeyChoiceRow(
                    label = stringRes(R.string.settings__ai__dictation__on_device),
                    summary = stringRes(R.string.settings__ai__dictation__on_device_summary),
                    note = when {
                        !controller.compatible -> stringRes(R.string.settings__ai__dictation__note_unsupported)
                        // The first check after start-up hasn't read the stored model yet, so it can't be missing.
                        state.phase == ModelPhase.CHECKING -> stringRes(R.string.settings__ai__dictation__note_checking)
                        state.phase == ModelPhase.ACTIVATING -> stringRes(R.string.settings__ai__dictation__note_loading)
                        transferring -> stringRes(R.string.settings__ai__dictation__note_downloading)
                        usableModelId == null -> stringRes(R.string.settings__ai__dictation__note_needs_download)
                        else -> null
                    },
                    selected = active,
                    enabled = controller.compatible && usableModelId != null && !working && !transferring,
                    onClick = {
                        val id = usableModelId
                        if (!active && id != null) perform { controller.activate(id) }
                    },
                    onDisabledClick = if (needsDownload) ::openDownloadDialog else null,
                    modifier = Modifier.testTag("dictation-option-orukeet"),
                )
            }
            OwnkeyChoiceRow(
                label = stringRes(R.string.settings__ai__dictation__cloud),
                summary = stringRes(R.string.settings__ai__dictation__cloud_summary, "provider" to cloudProvider),
                note = when {
                    !hasCloudKey -> stringRes(R.string.settings__ai__dictation__note_needs_key)
                    !cloudEndpointValid -> stringRes(R.string.settings__ai__dictation__note_fix_address)
                    else -> null
                },
                selected = backend == TranscriptionBackend.CLOUD,
                enabled = hasCloudKey && cloudEndpointValid && !working,
                onClick = {
                    if (backend != TranscriptionBackend.CLOUD) perform { controller.selectBackend(TranscriptionBackend.CLOUD) }
                },
                onDisabledClick = when {
                    !hasCloudKey -> onAddCloudKey
                    !cloudEndpointValid -> onFixCloudAddress
                    else -> null
                },
                modifier = Modifier.testTag("dictation-option-cloud"),
            )
            OwnkeyChoiceRow(
                label = stringRes(R.string.settings__ai__dictation__system),
                summary = stringRes(R.string.settings__ai__dictation__system_summary),
                selected = backend == TranscriptionBackend.EXTERNAL_IME,
                enabled = !working,
                onClick = {
                    if (backend != TranscriptionBackend.EXTERNAL_IME) {
                        perform { controller.selectBackend(TranscriptionBackend.EXTERNAL_IME) }
                    }
                },
                modifier = Modifier.testTag("dictation-option-external"),
            )
        }
        dictionaryRow()
    }

    if (showOrukeet) {
        val modelValue = AiSettings.orukeetModelValue(controller.compatible, state, waitReason, active, runtime)
        val shownError = error ?: state.error
        val nextAction = AiSettings.orukeetNextAction(controller.compatible, state, active, shownError)
        val progress = state.progress
        OwnkeyGroup(title = stringRes(R.string.settings__ai__model__title)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__row),
                    value = modelValue.label(progress?.let { AiSettings.downloadPercent(it.completed, it.total) }),
                    valueColor = if (active && modelValue == OrukeetModelValue.NOT_DOWNLOADED) OwnkeyTokens.Warning else Color.Unspecified,
                    summary = when (modelValue) {
                        OrukeetModelValue.WAITING_FOR_WIFI -> stringRes(R.string.settings__ai__model__waiting_wifi_summary)
                        OrukeetModelValue.WAITING_FOR_INTERNET -> stringRes(R.string.settings__ai__model__waiting_internet_summary)
                        OrukeetModelValue.WAITING_TO_START -> stringRes(R.string.settings__ai__model__waiting_start_summary)
                        OrukeetModelValue.DOWNLOADING, OrukeetModelValue.VERIFYING -> progress?.let {
                            stringResource(R.string.orukeet__progress, it.completed / 1_000_000, it.total / 1_000_000)
                        }
                        else -> null
                    },
                    modifier = Modifier.testTag("orukeet-status"),
                )
                if (transferring && !waiting) {
                    val fraction = progress?.takeIf { it.total > 0 }?.let { (it.completed.toFloat() / it.total).coerceIn(0f, 1f) }
                    val indicatorModifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    if (fraction == null) {
                        LinearProgressIndicator(
                            modifier = indicatorModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = OwnkeyTokens.Raised,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = indicatorModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = OwnkeyTokens.Raised,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = { },
                        )
                    }
                }
            }
            if (shownError != null) {
                OwnkeyNotice(text = stringResource(errorResource(shownError)), tone = OwnkeyTone.Error)
                // Internal builds name the failure, so a phone problem can be reported without a debugger attached.
                if (BuildConfig.ORUKEET_INTERNAL) {
                    val detail = if (error != null) errorDetail else state.errorDetail
                    CardBlock {
                        Text(
                            text = stringResource(
                                R.string.orukeet__error_detail,
                                listOfNotNull(shownError.name, detail).joinToString(" · "),
                            ),
                            modifier = Modifier.testTag("orukeet-error-detail"),
                            style = MaterialTheme.typography.bodySmall,
                            color = OwnkeyTokens.Secondary,
                        )
                    }
                }
            }
            if (nextAction != null) {
                CardBlock {
                    OwnkeyButton(
                        label = stringRes(nextAction.labelRes()),
                        onClick = {
                            when (nextAction) {
                                OrukeetNextAction.DOWNLOAD,
                                OrukeetNextAction.DOWNLOAD_UPDATE,
                                OrukeetNextAction.RETRY_DOWNLOAD,
                                -> openDownloadDialog()
                                // Same action as choosing On device above, offered here because this is where the download finishes.
                                OrukeetNextAction.USE,
                                OrukeetNextAction.APPLY_UPDATE,
                                -> perform { controller.activate() }
                            }
                        },
                        enabled = !working,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                when (nextAction) {
                                    OrukeetNextAction.USE, OrukeetNextAction.APPLY_UPDATE -> "orukeet-activate"
                                    else -> "orukeet-download"
                                },
                            ),
                    )
                }
            }
            if (transferring) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__cancel_download),
                    onClick = { ModelDownloads.cancel(context) },
                    modifier = Modifier.testTag("orukeet-cancel-download"),
                )
            }
            if (waiting) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__retry_now),
                    enabled = !working,
                    onClick = { perform { ModelDownloads.schedule(context, state.allowMobileData) } },
                    modifier = Modifier.testTag("orukeet-retry"),
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__change_connection),
                    enabled = !working,
                    onClick = ::openDownloadDialog,
                    modifier = Modifier.testTag("orukeet-change-connection"),
                )
            }
            if (state.phase == ModelPhase.ACTIVATING) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__cancel_loading),
                    onClick = { action?.cancel() },
                    modifier = Modifier.testTag("orukeet-cancel-loading"),
                )
            }
            if (state.hasStoredData || active) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__ai__model__delete),
                    titleColor = OwnkeyTokens.Danger,
                    enabled = !working && !transferring,
                    onClick = { deleteDialog = true },
                    modifier = Modifier.testTag("orukeet-delete"),
                )
            }
            OwnkeyRow(
                title = stringRes(R.string.settings__ai__model__notices),
                onClick = { noticesDialog = true },
                modifier = Modifier.testTag("orukeet-notices"),
            )
        }
    }

    if (downloadDialog) {
        OwnkeyDialog(
            title = stringRes(R.string.settings__ai__model__download_title),
            onDismiss = { downloadDialog = false },
            text = stringRes(R.string.settings__ai__model__download_text),
            confirmLabel = stringRes(R.string.settings__ai__model__download),
            onConfirm = {
                downloadDialog = false
                perform { ModelDownloads.schedule(context, mobileData) }
            },
            dismissLabel = stringRes(R.string.action__cancel),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            DialogChoices {
                listOf(
                    false to R.string.settings__ai__model__wifi_only,
                    true to R.string.settings__ai__model__any_connection,
                ).forEach { (mobile, label) ->
                    OwnkeyChoiceRow(
                        label = stringRes(label),
                        selected = mobileData == mobile,
                        onClick = { mobileData = mobile },
                        modifier = Modifier.testTag(if (mobile) "orukeet-any-connection" else "orukeet-wifi-only"),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OwnkeyButton(
                label = stringRes(R.string.settings__ai__model__notices),
                onClick = { noticesDialog = true },
                style = OwnkeyButtonStyle.Plain,
            )
        }
    }
    if (deleteDialog) {
        OwnkeyDialog(
            title = stringRes(R.string.settings__ai__model__delete_title),
            onDismiss = { deleteDialog = false },
            text = if (active) {
                stringRes(R.string.settings__ai__model__delete_active, "route" to backendLabel(previousBackend))
            } else {
                stringRes(R.string.settings__ai__model__delete_inactive)
            },
            confirmLabel = stringRes(R.string.settings__ai__model__delete_confirm),
            onConfirm = {
                deleteDialog = false
                perform { controller.delete() }
            },
            destructive = true,
            dismissLabel = stringRes(R.string.action__cancel),
        )
    }
    if (noticesDialog) {
        LaunchedEffect(Unit) {
            notices = withContext(Dispatchers.IO) {
                listOf("NOTICE.md", "LICENSE-WEIGHTS", "LICENSE").joinToString("\n\n") { name ->
                    context.assets.open("thirdparty/orukeet/$name").bufferedReader().use { it.readText() }
                }
            }
        }
        OwnkeyDialog(
            title = stringRes(R.string.settings__ai__model__notices),
            onDismiss = { noticesDialog = false },
            confirmLabel = stringRes(R.string.orukeet__close),
            onConfirm = { noticesDialog = false },
        ) {
            Text(
                text = notices,
                style = MaterialTheme.typography.bodySmall,
                color = OwnkeyTokens.Label,
            )
        }
    }
}

@Composable
private fun OrukeetModelValue.label(percent: Int?): String = when (this) {
    OrukeetModelValue.UNSUPPORTED -> stringRes(R.string.settings__ai__model__unsupported)
    OrukeetModelValue.CHECKING -> stringRes(R.string.settings__ai__model__checking)
    OrukeetModelValue.LOADING -> stringRes(R.string.settings__ai__model__loading)
    OrukeetModelValue.DELETING -> stringRes(R.string.settings__ai__model__deleting)
    OrukeetModelValue.VERIFYING -> stringRes(R.string.settings__ai__model__verifying)
    OrukeetModelValue.WAITING_FOR_WIFI -> stringRes(R.string.settings__ai__model__waiting_wifi)
    OrukeetModelValue.WAITING_FOR_INTERNET -> stringRes(R.string.settings__ai__model__waiting_internet)
    OrukeetModelValue.WAITING_TO_START -> stringRes(R.string.settings__ai__model__waiting_start)
    OrukeetModelValue.DOWNLOADING -> if (percent != null) {
        stringRes(R.string.settings__ai__model__downloading, "percent" to percent)
    } else {
        stringRes(R.string.settings__ai__model__downloading_unknown)
    }
    OrukeetModelValue.UPDATE_AVAILABLE -> stringRes(R.string.settings__ai__model__update)
    OrukeetModelValue.IN_USE -> stringRes(R.string.settings__ai__model__in_use)
    OrukeetModelValue.READY -> stringRes(R.string.settings__ai__model__ready)
    OrukeetModelValue.DOWNLOADED -> stringRes(R.string.settings__ai__model__downloaded)
    OrukeetModelValue.NOT_DOWNLOADED -> stringRes(R.string.settings__ai__model__not_downloaded)
}

private fun OrukeetNextAction.labelRes(): Int = when (this) {
    OrukeetNextAction.DOWNLOAD -> R.string.settings__ai__model__download
    OrukeetNextAction.DOWNLOAD_UPDATE -> R.string.settings__ai__model__download_update
    OrukeetNextAction.RETRY_DOWNLOAD -> R.string.settings__ai__model__retry
    OrukeetNextAction.USE -> R.string.settings__ai__model__use
    OrukeetNextAction.APPLY_UPDATE -> R.string.settings__ai__model__apply_update
}

@Composable
internal fun backendLabel(backend: TranscriptionBackend): String = stringRes(
    when (backend) {
        TranscriptionBackend.ORUKEET -> R.string.settings__ai__dictation__on_device
        TranscriptionBackend.CLOUD -> R.string.settings__ai__dictation__cloud
        TranscriptionBackend.EXTERNAL_IME -> R.string.settings__ai__dictation__system
        TranscriptionBackend.MOCK -> R.string.orukeet__mock_label
        TranscriptionBackend.UNAVAILABLE -> R.string.orukeet__unavailable_label
    },
)

private fun errorResource(error: LocalAsrFailure): Int = when (error) {
    LocalAsrFailure.UNSUPPORTED -> R.string.orukeet__unsupported
    LocalAsrFailure.INSUFFICIENT_STORAGE -> R.string.orukeet__space_error
    LocalAsrFailure.INTEGRITY, LocalAsrFailure.MODEL_DAMAGED -> R.string.orukeet__integrity_error
    LocalAsrFailure.MODEL_MISSING -> R.string.orukeet__not_ready
    LocalAsrFailure.BUSY -> R.string.orukeet__busy_error
    LocalAsrFailure.DOWNLOAD -> R.string.orukeet__download_error
    // This group only runs model operations, so these three come from loading the model, not from a recording.
    LocalAsrFailure.PROCESS_DIED -> R.string.orukeet__load_process_died
    LocalAsrFailure.TIMEOUT -> R.string.orukeet__load_timeout
    LocalAsrFailure.RUNTIME -> R.string.orukeet__load_runtime
    else -> R.string.orukeet__transcription_failed
}
