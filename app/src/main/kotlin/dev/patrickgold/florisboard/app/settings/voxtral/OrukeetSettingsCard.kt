package dev.patrickgold.florisboard.app.settings.voxtral

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.offline.*
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.*
import org.ownkey.offline.*

@Composable
internal fun OrukeetSettingsCard(hasCloudKey: Boolean) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val selected by prefs.voxtral.dictationBackend.collectAsState()
    val previous by prefs.voxtral.previousDictationBackend.collectAsState()
    val controller = remember { context.offlineDictation() }
    val state by controller.state.collectAsState()
    val runtime by controller.runtime.state.collectAsState()
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<LocalAsrFailure?>(null) }
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
    val candidateInstalled = ModelCatalog.current.id in state.installed
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
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { error = (failure as? LocalAsrException)?.reason ?: LocalAsrFailure.RUNTIME }
            finally { action = null }
        }
    }
    AiSectionCard(stringResource(R.string.orukeet__title), stringResource(R.string.orukeet__summary)) {
        Text(stringResource(R.string.orukeet__selected, backendLabel(backend)), style = MaterialTheme.typography.titleSmall)
        StatusText(stringResource(R.string.orukeet__size))
        StatusText(stringResource(R.string.orukeet__languages_cap))
        if (!controller.compatible) StatusText(stringResource(R.string.orukeet__unsupported))
        when {
            state.phase == ModelPhase.CHECKING -> StatusText(stringResource(R.string.orukeet__checking))
            state.phase == ModelPhase.ACTIVATING -> StatusText(stringResource(R.string.orukeet__activating))
            state.phase == ModelPhase.REMOVING -> StatusText(stringResource(R.string.orukeet__removing))
            transferring -> {
                val p = state.progress
                StatusText(stringResource(when (state.transferPhase) {
                    ModelPhase.VERIFYING -> R.string.orukeet__verifying
                    ModelPhase.WAITING_FOR_NETWORK -> when (waitReason) {
                        DownloadWaitReason.NO_INTERNET -> R.string.orukeet__waiting_internet
                        DownloadWaitReason.WIFI_REQUIRED -> R.string.orukeet__waiting_wifi
                        DownloadWaitReason.STARTING -> R.string.orukeet__waiting
                    }
                    else -> R.string.orukeet__downloading
                }))
                if (p == null && state.transferPhase != ModelPhase.WAITING_FOR_NETWORK) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else if (p != null) {
                    LinearProgressIndicator(progress = { (p.completed.toFloat() / p.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    StatusText(stringResource(R.string.orukeet__progress, p.completed / 1_000_000, p.total / 1_000_000))
                }
            }
            active && state.currentId == null -> StatusText(stringResource(R.string.orukeet__not_ready))
            active -> StatusText(stringResource(when (runtime) {
                RuntimeState.LOADING -> R.string.orukeet__activating
                RuntimeState.TRANSCRIBING -> R.string.orukeet__transcribing
                else -> R.string.orukeet__active
            }))
            candidateInstalled -> StatusText(stringResource(R.string.orukeet__downloaded))
            else -> StatusText(stringResource(R.string.orukeet__not_downloaded))
        }
        (error ?: state.error)?.let { StatusText(stringResource(errorResource(it))) }
        if (transferring) {
            OwnkeyButton(stringResource(R.string.orukeet__cancel_download), { ModelDownloads.cancel(context) }, secondary = true)
            if (state.transferPhase == ModelPhase.WAITING_FOR_NETWORK) {
                OwnkeyButton(stringResource(R.string.orukeet__retry_download), {
                    perform { ModelDownloads.schedule(context, state.allowMobileData) }
                }, enabled = !working, secondary = true)
                OwnkeyButton(stringResource(R.string.orukeet__change_network), {
                    mobileData = state.allowMobileData
                    downloadDialog = true
                }, enabled = !working, secondary = true)
            }
        } else if (!candidateInstalled) {
            OwnkeyButton(stringResource(if (state.currentId != null) R.string.orukeet__download_update else R.string.orukeet__download),
                { downloadDialog = true }, enabled = !working && controller.compatible,
                modifier = Modifier.testTag("orukeet-download").heightIn(min = 48.dp))
        } else if (!active || state.currentId != ModelCatalog.current.id) {
            OwnkeyButton(stringResource(if (active) R.string.orukeet__apply_update else R.string.orukeet__activate),
                { perform { controller.activate() } }, enabled = !working && controller.compatible,
                modifier = Modifier.testTag("orukeet-activate").heightIn(min = 48.dp))
        }
        if (state.phase == ModelPhase.ACTIVATING) {
            OwnkeyButton(stringResource(R.string.action__cancel), { action?.cancel() }, secondary = true)
        }
        if (active) {
            StatusText(stringResource(R.string.orukeet__deactivate_destination, backendLabel(previousBackend)))
            OwnkeyButton(stringResource(R.string.orukeet__deactivate), { perform { controller.deactivate() } },
                enabled = !working, secondary = true, modifier = Modifier.heightIn(min = 48.dp))
        }
        if (state.hasStoredData || active) {
            OwnkeyButton(stringResource(R.string.orukeet__delete), { deleteDialog = true }, enabled = !working && !transferring,
                secondary = true, modifier = Modifier.testTag("orukeet-delete").heightIn(min = 48.dp))
        }
        OwnkeyButton(stringResource(R.string.orukeet__notices), { noticesDialog = true }, secondary = true)
        HorizontalDivider()
        OwnkeyButton(stringResource(R.string.orukeet__use_cloud), {
            perform { controller.selectBackend(TranscriptionBackend.CLOUD) }
        }, enabled = !working && hasCloudKey && backend != TranscriptionBackend.CLOUD, secondary = true)
        if (!hasCloudKey) StatusText(stringResource(R.string.orukeet__cloud_key_needed))
        OwnkeyButton(stringResource(R.string.orukeet__use_external), {
            perform { controller.selectBackend(TranscriptionBackend.EXTERNAL_IME) }
        }, enabled = !working && backend != TranscriptionBackend.EXTERNAL_IME, secondary = true)
        StatusText(stringResource(R.string.orukeet__cloud_settings_retained))
    }
    if (downloadDialog) AlertDialog(
        onDismissRequest = { downloadDialog = false },
        title = { Text(stringResource(R.string.orukeet__download)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.orukeet__size))
            Text(stringResource(R.string.orukeet__download_consent))
            listOf(false to R.string.orukeet__wifi_only, true to R.string.orukeet__mobile_data).forEach { (mobile, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(mobileData == mobile, role = Role.RadioButton,
                    onClick = { mobileData = mobile })) {
                    RadioButton(selected = mobileData == mobile, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(top = 12.dp))
                }
            }
            TextButton(onClick = { noticesDialog = true }) { Text(stringResource(R.string.orukeet__notices)) }
        } },
        confirmButton = { TextButton(onClick = {
            downloadDialog = false
            perform { ModelDownloads.schedule(context, mobileData) }
        }) { Text(stringResource(R.string.orukeet__download)) } },
        dismissButton = { TextButton(onClick = { downloadDialog = false }) { Text(stringResource(R.string.action__cancel)) } },
    )
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text(stringResource(R.string.orukeet__delete)) },
        text = { Text(if (active) stringResource(R.string.orukeet__delete_active, backendLabel(previousBackend)) else stringResource(R.string.orukeet__delete_inactive)) },
        confirmButton = { TextButton(onClick = { deleteDialog = false; perform { controller.delete() } }) { Text(stringResource(R.string.orukeet__delete)) } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.action__cancel)) } },
    )
    if (noticesDialog) {
        LaunchedEffect(Unit) {
            notices = withContext(Dispatchers.IO) {
                listOf("NOTICE.md", "LICENSE-WEIGHTS").joinToString("\n\n") { name ->
                    context.assets.open("thirdparty/orukeet/$name").bufferedReader().use { it.readText() }
                }
            }
        }
        AlertDialog(onDismissRequest = { noticesDialog = false }, title = { Text(stringResource(R.string.orukeet__notices)) },
            text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { noticesDialog = false }) { Text(stringResource(R.string.orukeet__close)) } })
    }
}

@Composable private fun backendLabel(backend: TranscriptionBackend): String = stringResource(when (backend) {
    TranscriptionBackend.ORUKEET -> R.string.orukeet__title
    TranscriptionBackend.CLOUD -> R.string.orukeet__cloud_label
    TranscriptionBackend.EXTERNAL_IME -> R.string.orukeet__external_label
    TranscriptionBackend.MOCK -> R.string.orukeet__mock_label
    TranscriptionBackend.UNAVAILABLE -> R.string.orukeet__unavailable_label
})

private fun errorResource(error: LocalAsrFailure): Int = when (error) {
    LocalAsrFailure.UNSUPPORTED -> R.string.orukeet__unsupported
    LocalAsrFailure.INSUFFICIENT_STORAGE -> R.string.orukeet__space_error
    LocalAsrFailure.INTEGRITY, LocalAsrFailure.MODEL_DAMAGED -> R.string.orukeet__integrity_error
    LocalAsrFailure.MODEL_MISSING -> R.string.orukeet__not_ready
    LocalAsrFailure.BUSY -> R.string.orukeet__busy_error
    LocalAsrFailure.DOWNLOAD -> R.string.orukeet__download_error
    else -> R.string.orukeet__transcription_failed
}
