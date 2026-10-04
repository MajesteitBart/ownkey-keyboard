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

/**
 * The dictation provider choice and, below it, the Orukeet model card. The choice is the only place that switches
 * providers; an option that cannot dictate yet stays visible but disabled, with what is missing.
 */
@Composable
internal fun DictationSettings(hasCloudKey: Boolean, cloudEndpointValid: Boolean, cloudProvider: String, cloudModel: String) {
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
    val candidateInstalled = ModelCatalog.current.id in state.installed
    // An older verified model still dictates while its update downloads, so either one makes Orukeet usable.
    val usableModelId = if (candidateInstalled) ModelCatalog.current.id else state.currentId
    // Public builds don't offer a download the device can't run. A restored local selection still
    // shows Orukeet, so there is a way back to another provider. Internal builds show it to report the reason.
    val showOrukeet = BuildConfig.ORUKEET_INTERNAL || controller.compatible || selected == TranscriptionBackend.ORUKEET.preference
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
    fun useOrukeet() {
        val id = usableModelId ?: return
        perform { controller.activate(id) }
    }

    AiSectionCard(stringResource(R.string.pref__ai__dictation_group__label), stringResource(R.string.pref__ai__dictation_group__summary)) {
        // Debug builds without a key fall back to demo dictation, which has no option of its own.
        if (backend == TranscriptionBackend.MOCK || backend == TranscriptionBackend.UNAVAILABLE) {
            StatusText(stringResource(R.string.orukeet__selected, backendLabel(backend)))
        }
        if (showOrukeet) {
            ChoiceOption(
                label = stringResource(R.string.orukeet__title),
                summary = stringResource(R.string.pref__ai__dictation_option_orukeet_summary),
                note = when {
                    !controller.compatible -> stringResource(R.string.orukeet__unsupported)
                    // The first check after start-up hasn't read the stored model yet, so it can't be missing.
                    state.phase == ModelPhase.CHECKING -> stringResource(R.string.orukeet__checking)
                    state.phase == ModelPhase.ACTIVATING -> stringResource(R.string.orukeet__activating)
                    transferring -> stringResource(R.string.orukeet__downloading)
                    usableModelId == null -> stringResource(R.string.pref__ai__dictation_option_orukeet_needs_model)
                    else -> null
                },
                selected = active,
                enabled = controller.compatible && usableModelId != null && !working && !transferring,
                onClick = { if (!active) useOrukeet() },
                modifier = Modifier.testTag("dictation-option-orukeet"),
            )
        }
        ChoiceOption(
            label = stringResource(R.string.pref__ai__dictation_option_cloud, cloudProvider),
            summary = stringResource(R.string.pref__ai__dictation_option_cloud_summary, cloudProvider, cloudModel),
            note = when {
                !hasCloudKey -> stringResource(R.string.pref__ai__dictation_option_cloud_needs_key)
                !cloudEndpointValid -> stringResource(R.string.pref__ai__dictation_option_cloud_needs_endpoint)
                else -> null
            },
            selected = backend == TranscriptionBackend.CLOUD,
            enabled = hasCloudKey && cloudEndpointValid && !working,
            onClick = {
                if (backend != TranscriptionBackend.CLOUD) perform { controller.selectBackend(TranscriptionBackend.CLOUD) }
            },
            modifier = Modifier.testTag("dictation-option-cloud"),
        )
        ChoiceOption(
            label = stringResource(R.string.orukeet__external_label),
            summary = stringResource(R.string.pref__ai__dictation_option_external_summary),
            selected = backend == TranscriptionBackend.EXTERNAL_IME,
            enabled = !working,
            onClick = {
                if (backend != TranscriptionBackend.EXTERNAL_IME) {
                    perform { controller.selectBackend(TranscriptionBackend.EXTERNAL_IME) }
                }
            },
            modifier = Modifier.testTag("dictation-option-external"),
        )
        StatusText(stringResource(R.string.pref__ai__dictation_group__footnote))
    }

    if (!showOrukeet) return
    AiSectionCard(stringResource(R.string.orukeet__model_title), stringResource(R.string.orukeet__summary)) {
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
        val shownError = error ?: state.error
        if (shownError != null) {
            StatusText(stringResource(errorResource(shownError)))
            // Internal builds name the failure, so a phone problem can be reported without a debugger attached.
            if (BuildConfig.ORUKEET_INTERNAL) {
                val detail = if (error != null) errorDetail else state.errorDetail
                StatusText(
                    stringResource(R.string.orukeet__error_detail, listOfNotNull(shownError.name, detail).joinToString(" · ")),
                    modifier = Modifier.testTag("orukeet-error-detail"),
                )
            }
        }
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
            // Same action as choosing Orukeet above, offered here because this is where the download finishes.
            OwnkeyButton(stringResource(if (active) R.string.orukeet__apply_update else R.string.orukeet__activate),
                { perform { controller.activate() } }, enabled = !working && controller.compatible,
                modifier = Modifier.testTag("orukeet-activate").heightIn(min = 48.dp))
        }
        if (state.phase == ModelPhase.ACTIVATING) {
            OwnkeyButton(stringResource(R.string.action__cancel), { action?.cancel() }, secondary = true)
        }
        if (state.hasStoredData || active) {
            OwnkeyButton(stringResource(R.string.orukeet__delete), { deleteDialog = true }, enabled = !working && !transferring,
                secondary = true, modifier = Modifier.testTag("orukeet-delete").heightIn(min = 48.dp))
        }
        OwnkeyButton(stringResource(R.string.orukeet__notices), { noticesDialog = true }, secondary = true)
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
                listOf("NOTICE.md", "LICENSE-WEIGHTS", "LICENSE").joinToString("\n\n") { name ->
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
    // This card only runs model operations, so these three come from loading the model, not from a recording.
    LocalAsrFailure.PROCESS_DIED -> R.string.orukeet__load_process_died
    LocalAsrFailure.TIMEOUT -> R.string.orukeet__load_timeout
    LocalAsrFailure.RUNTIME -> R.string.orukeet__load_runtime
    else -> R.string.orukeet__transcription_failed
}
