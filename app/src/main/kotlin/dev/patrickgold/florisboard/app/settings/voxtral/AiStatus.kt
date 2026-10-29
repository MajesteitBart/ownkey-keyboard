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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionBackend
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionProviderNaming
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralRelayTranscriptionClient
import dev.patrickgold.florisboard.ime.text.dictation.VoxtralSecretsStore
import dev.patrickgold.florisboard.ime.text.dictation.offline.LocalModelState
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.dictation.offline.offlineDictation
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteSecretsStore
import dev.patrickgold.florisboard.ime.text.rewrite.ResolvedRewriteProvider
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import java.net.URI

enum class AiStatusTone { Normal, Warning }

/** One value of the AI summary: what a feature uses right now, or what it still needs. */
sealed interface AiStatusValue {
    val tone: AiStatusTone
}

sealed interface DictationStatus : AiStatusValue {
    data object OnDevice : DictationStatus {
        override val tone = AiStatusTone.Normal
    }

    data object OnDeviceNeedsDownload : DictationStatus {
        override val tone = AiStatusTone.Warning
    }

    /** [provider] is null for a server that is neither a known provider nor has a readable host. */
    data class Cloud(val provider: String?) : DictationStatus {
        override val tone = AiStatusTone.Normal
    }

    data object CloudNeedsKey : DictationStatus {
        override val tone = AiStatusTone.Warning
    }

    data object CloudCheckServerAddress : DictationStatus {
        override val tone = AiStatusTone.Warning
    }

    data object SystemVoiceInput : DictationStatus {
        override val tone = AiStatusTone.Normal
    }

    /** Debug builds without a key or a choice dictate canned text. */
    data object Demo : DictationStatus {
        override val tone = AiStatusTone.Normal
    }

    data object NotSetUp : DictationStatus {
        override val tone = AiStatusTone.Warning
    }
}

sealed interface RewriteStatus : AiStatusValue {
    /** [provider] is null for a custom server without a readable host. */
    data class Ready(val provider: String?) : RewriteStatus {
        override val tone = AiStatusTone.Normal
    }

    data object NeedsKey : RewriteStatus {
        override val tone = AiStatusTone.Warning
    }

    data object NeedsSetup : RewriteStatus {
        override val tone = AiStatusTone.Warning
    }
}

/** Whether Orukeet can dictate, as far as settings can tell. */
enum class OrukeetAvailability {
    /** The start-up check hasn't read the stored model yet, so a missing model isn't known yet. */
    CHECKING,
    READY,
    /** A model is downloaded but not in use. */
    NOT_ACTIVE,
    MISSING,
    UNSUPPORTED;

    companion object {
        fun of(compatible: Boolean, state: LocalModelState): OrukeetAvailability = when {
            !compatible -> UNSUPPORTED
            state.phase == ModelPhase.CHECKING -> CHECKING
            state.currentId != null -> READY
            state.installed.isNotEmpty() -> NOT_ACTIVE
            else -> MISSING
        }
    }
}

/**
 * What dictation and rewrite use right now. A null value is still being worked out, usually because the key check
 * hasn't finished; show no value and no warning for it.
 */
@Immutable
data class AiStatus(val dictation: DictationStatus?, val rewrite: RewriteStatus?) {
    companion object {
        /** Key presence is null until it has been read. */
        fun of(
            dictationPreference: String,
            hasCloudKey: Boolean?,
            cloudEndpointUrl: String,
            orukeet: OrukeetAvailability,
            rewriteProvider: ResolvedRewriteProvider,
            hasRewriteKey: Boolean?,
            debug: Boolean,
        ) = AiStatus(
            dictation = dictation(dictationPreference, hasCloudKey, cloudEndpointUrl, orukeet, debug),
            rewrite = rewrite(rewriteProvider, hasRewriteKey),
        )

        fun dictation(
            preference: String,
            hasCloudKey: Boolean?,
            cloudEndpointUrl: String,
            orukeet: OrukeetAvailability,
            debug: Boolean,
        ): DictationStatus? {
            // An install that never chose resolves by key presence.
            if (preference.isEmpty() && hasCloudKey == null) return null
            return when (TranscriptionBackend.resolve(preference, hasCloudKey == true, debug)) {
                TranscriptionBackend.ORUKEET -> when (orukeet) {
                    OrukeetAvailability.CHECKING -> null
                    OrukeetAvailability.READY -> DictationStatus.OnDevice
                    OrukeetAvailability.MISSING -> DictationStatus.OnDeviceNeedsDownload
                    // A downloaded model only has to be put to use, and a phone that can't run Orukeet
                    // gains nothing from a download.
                    OrukeetAvailability.NOT_ACTIVE, OrukeetAvailability.UNSUPPORTED -> DictationStatus.NotSetUp
                }
                TranscriptionBackend.CLOUD -> when {
                    hasCloudKey == null -> null
                    !hasCloudKey -> DictationStatus.CloudNeedsKey
                    !isUsableCloudEndpoint(cloudEndpointUrl) -> DictationStatus.CloudCheckServerAddress
                    else -> DictationStatus.Cloud(cloudProviderName(cloudEndpointUrl))
                }
                TranscriptionBackend.EXTERNAL_IME -> DictationStatus.SystemVoiceInput
                TranscriptionBackend.MOCK -> DictationStatus.Demo
                TranscriptionBackend.UNAVAILABLE -> DictationStatus.NotSetUp
            }
        }

        fun rewrite(rewrite: ResolvedRewriteProvider, hasKey: Boolean?): RewriteStatus? = when {
            hasKey == null -> null
            !hasKey -> RewriteStatus.NeedsKey
            !rewrite.isComplete || !rewrite.hasHttpEndpoint -> RewriteStatus.NeedsSetup
            rewrite.preset.isCustom -> RewriteStatus.Ready(endpointHost(rewrite.endpointUrl))
            else -> RewriteStatus.Ready(rewrite.preset.providerName)
        }
    }
}

/** A status value as text. While the value is still unknown, [text] is empty and [tone] is null. */
@Immutable
data class AiStatusLine(val text: String, val tone: AiStatusTone?) {
    val needsSetup: Boolean get() = tone == AiStatusTone.Warning
}

@Composable
fun AiStatusValue?.toLine(): AiStatusLine {
    if (this == null) return AiStatusLine(text = "", tone = null)
    val customServer = stringRes(R.string.settings__ai__status__custom_server)
    val text = when (this) {
        DictationStatus.OnDevice -> stringRes(R.string.settings__ai__status__on_device)
        DictationStatus.OnDeviceNeedsDownload -> stringRes(R.string.settings__ai__status__on_device_needs_download)
        is DictationStatus.Cloud -> stringRes(
            R.string.settings__ai__status__cloud,
            "provider" to (provider ?: customServer),
        )
        DictationStatus.CloudNeedsKey -> stringRes(R.string.settings__ai__status__cloud_needs_key)
        DictationStatus.CloudCheckServerAddress -> stringRes(R.string.settings__ai__status__cloud_check_address)
        DictationStatus.SystemVoiceInput -> stringRes(R.string.settings__ai__status__system_voice_input)
        DictationStatus.Demo -> stringRes(R.string.settings__ai__status__demo)
        DictationStatus.NotSetUp -> stringRes(R.string.settings__ai__status__not_set_up)
        is RewriteStatus.Ready -> provider ?: customServer
        RewriteStatus.NeedsKey -> stringRes(R.string.settings__ai__status__needs_key)
        RewriteStatus.NeedsSetup -> stringRes(R.string.settings__ai__status__needs_setup)
    }
    return AiStatusLine(text = text, tone = tone)
}

/**
 * The current [AiStatus], for summaries outside the AI screen. Key checks build the Keystore-backed stores and
 * decrypt them, so they run on the IO dispatcher, and the values that depend on them stay null until they finish.
 * The keys are read once per composition; a screen that edits keys should call [AiStatus.of] with its own state.
 */
@Composable
fun rememberAiStatus(): AiStatus {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val dictationPreference by prefs.voxtral.dictationBackend.collectAsState()
    val cloudEndpointUrl by prefs.voxtral.endpointUrl.collectAsState()
    val rewriteProviderId by prefs.voxtral.postProcessingProvider.collectAsState()
    val rewriteEndpointUrl by prefs.voxtral.postProcessingEndpointUrl.collectAsState()
    val rewriteModel by prefs.voxtral.postProcessingModel.collectAsState()
    val hasCloudKey by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { hasStoredKey { VoxtralSecretsStore(context).hasApiKey() } }
    }
    val hasRewriteKey by produceState<Boolean?>(null) {
        value = withContext(Dispatchers.IO) { hasStoredKey { LlmRewriteSecretsStore(context).hasApiKey() } }
    }
    // Only an Orukeet choice needs the model state, so other installs never start the model check from here.
    val orukeetSelected = dictationPreference == TranscriptionBackend.ORUKEET.preference
    val orukeet by produceState(OrukeetAvailability.CHECKING, orukeetSelected) {
        if (!orukeetSelected) return@produceState
        val controller = withContext(Dispatchers.IO) { context.offlineDictation() }
        controller.state.collect { value = OrukeetAvailability.of(controller.compatible, it) }
    }
    return AiStatus.of(
        dictationPreference = dictationPreference,
        hasCloudKey = hasCloudKey,
        cloudEndpointUrl = cloudEndpointUrl,
        orukeet = orukeet,
        rewriteProvider = LlmRewriteProviders.resolve(rewriteProviderId, rewriteEndpointUrl, rewriteModel),
        hasRewriteKey = hasRewriteKey,
        debug = BuildConfig.DEBUG,
    )
}

/** A key store that can't be opened counts as having no key; the AI screen is where that gets fixed. */
private inline fun hasStoredKey(read: () -> Boolean): Boolean = runCatching { read() }.getOrDefault(false)

/** A blank cloud address means the default the client falls back to. */
internal fun effectiveCloudEndpoint(endpointUrl: String): String =
    endpointUrl.trim().ifEmpty { VoxtralRelayTranscriptionClient.DefaultEndpointUrl }

/** Cloud requests only go to http(s) addresses. */
internal fun isUsableCloudEndpoint(endpointUrl: String): Boolean =
    NetworkUtils.hasHttpScheme(effectiveCloudEndpoint(endpointUrl))

/**
 * Name of the cloud dictation provider: a known provider's name, else the server's host. Null for an address
 * without a readable host.
 */
internal fun cloudProviderName(endpointUrl: String): String? {
    val endpoint = effectiveCloudEndpoint(endpointUrl)
    return TranscriptionProviderNaming.knownLabel(endpoint) ?: endpointHost(endpoint)
}

/** Host of an endpoint URL, so settings can say where text goes without showing paths or keys. */
internal fun endpointHost(endpointUrl: String): String? =
    runCatching { URI(endpointUrl.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }
