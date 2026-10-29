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

import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageHints
import dev.patrickgold.florisboard.ime.text.dictation.TranscriptionLanguageMode
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyField
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyHints
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.CloudVocabularyMode
import dev.patrickgold.florisboard.ime.text.dictation.offline.DownloadWaitReason
import dev.patrickgold.florisboard.ime.text.dictation.offline.LocalModelState
import dev.patrickgold.florisboard.ime.text.dictation.offline.ModelPhase
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviderPreset
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPreset
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPresets
import dev.patrickgold.florisboard.ime.text.rewrite.ResolvedRewriteProvider
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import org.ownkey.offline.LocalAsrFailure
import org.ownkey.offline.ModelCatalog
import org.ownkey.offline.RuntimeState

/** What the rewrite Model row shows. */
sealed interface RewriteModelValue {
    data object Default : RewriteModelValue

    /** A custom server has no default, so an empty model can't work. */
    data object NotSet : RewriteModelValue

    data class Named(val model: String) : RewriteModelValue
}

/** What the dictation Language row shows. */
sealed interface DictationLanguageValue {
    data object KeyboardLanguage : DictationLanguageValue
    data object Automatic : DictationLanguageValue
    data class Specific(val code: String) : DictationLanguageValue
}

/** Where cloud dictation sends data, for the group footer. [host] is null when the address has no readable host. */
data class CloudDictationFooter(val host: String?, val sendsDictionaryWords: Boolean)

/** The outcome of saving or removing an API key: what the key row shows, and whether the dialog may close. */
data class ApiKeyChange(val hasKey: Boolean, val succeeded: Boolean)

/** The Orukeet model status row value. */
enum class OrukeetModelValue {
    UNSUPPORTED,
    CHECKING,
    LOADING,
    DELETING,
    VERIFYING,
    WAITING_FOR_WIFI,
    WAITING_FOR_INTERNET,
    WAITING_TO_START,
    DOWNLOADING,
    UPDATE_AVAILABLE,
    IN_USE,
    READY,
    DOWNLOADED,
    NOT_DOWNLOADED,
}

/** The one Primary action of the on-device model group. */
enum class OrukeetNextAction {
    DOWNLOAD,
    DOWNLOAD_UPDATE,
    RETRY_DOWNLOAD,
    USE,
    APPLY_UPDATE,
}

/** Pure rules behind the AI screen's values, footers and dialogs. */
object AiSettings {
    /** The address a cloud request uses: a blank preference means the default Mistral endpoint. */
    fun cloudEndpoint(storedEndpointUrl: String): String = effectiveCloudEndpoint(storedEndpointUrl)

    /**
     * A dialog field starts empty when the stored value is the default, so the default shows as the hint and
     * editing never starts from a long URL the user didn't type.
     */
    fun draftOf(storedValue: String, default: String): String =
        storedValue.trim().takeUnless { it == default }.orEmpty()

    /** What to store from a dialog field: empty means the default. */
    fun storedValueOf(input: String, default: String): String = input.trim().ifEmpty { default }

    /** A server address field accepts http(s) URLs, and an empty field where a default exists. */
    fun isAcceptableServerAddress(input: String, emptyAllowed: Boolean): Boolean {
        val trimmed = input.trim()
        return if (trimmed.isEmpty()) emptyAllowed else NetworkUtils.hasHttpScheme(trimmed)
    }

    /**
     * Whether a server address field shows its error. A value that is still a prefix of "https://" or "http://"
     * is being typed, so it only blocks Save.
     */
    fun showsServerAddressError(input: String, emptyAllowed: Boolean): Boolean {
        val trimmed = input.trim().lowercase()
        if (isAcceptableServerAddress(trimmed, emptyAllowed) || trimmed.isEmpty()) return false
        return !("https://".startsWith(trimmed) || "http://".startsWith(trimmed))
    }

    fun cloudFooter(storedEndpointUrl: String, mode: CloudVocabularyMode, dictionaryWords: Int): CloudDictationFooter {
        val endpoint = cloudEndpoint(storedEndpointUrl)
        // Only words travel as hints, and only when the request actually carries a hint field.
        val sendsWords = dictionaryWords > 0 && CloudVocabularyHints.field(endpoint, mode) != CloudVocabularyField.NONE
        return CloudDictationFooter(host = endpointHost(endpoint), sendsDictionaryWords = sendsWords)
    }

    /** Where rewrite text goes: the request's host, else the provider's name. Null for a custom server without one. */
    fun rewriteHost(resolved: ResolvedRewriteProvider): String? =
        endpointHost(resolved.endpointUrl) ?: resolved.preset.providerName.takeUnless { resolved.preset.isCustom }

    fun rewriteModelValue(preset: LlmRewriteProviderPreset, storedModel: String): RewriteModelValue {
        val model = storedModel.trim()
        return when {
            preset.isCustom -> if (model.isEmpty()) RewriteModelValue.NotSet else RewriteModelValue.Named(model)
            model.isEmpty() || model == preset.defaultModel -> RewriteModelValue.Default
            else -> RewriteModelValue.Named(model)
        }
    }

    /** The model field starts empty when the provider default is used, so the default shows as the hint. */
    fun rewriteModelDraft(preset: LlmRewriteProviderPreset, storedModel: String): String =
        when (rewriteModelValue(preset, storedModel)) {
            is RewriteModelValue.Named -> storedModel.trim()
            else -> ""
        }

    /** An empty field stores the provider default, which is also what a request falls back to. */
    fun rewriteModelToStore(preset: LlmRewriteProviderPreset, input: String): String =
        input.trim().ifEmpty { if (preset.isCustom) "" else preset.defaultModel }

    /**
     * A save worked only when the store call itself succeeded and a key is stored afterwards. Finding a key
     * isn't enough: when replacing a key fails, the old one is still there and later requests would use it.
     */
    fun apiKeySaved(storeSucceeded: Boolean, hasKeyAfter: Boolean): ApiKeyChange =
        ApiKeyChange(hasKey = hasKeyAfter, succeeded = storeSucceeded && hasKeyAfter)

    /**
     * A removal worked when no key can be read afterwards. A store that can't be opened counts as empty, like
     * everywhere else on this screen, because requests can't read its key either.
     */
    fun apiKeyRemoved(hasKeyAfter: Boolean): ApiKeyChange =
        ApiKeyChange(hasKey = hasKeyAfter, succeeded = !hasKeyAfter)

    fun languageValue(storedLanguageHint: String): DictationLanguageValue =
        when (TranscriptionLanguageHints.modeOf(storedLanguageHint)) {
            TranscriptionLanguageMode.FOLLOW_KEYBOARD -> DictationLanguageValue.KeyboardLanguage
            TranscriptionLanguageMode.AUTO -> DictationLanguageValue.Automatic
            TranscriptionLanguageMode.EXPLICIT -> DictationLanguageValue.Specific(storedLanguageHint.trim())
        }

    /** Whole percent of a download, or null before its size is known. */
    fun downloadPercent(completed: Long, total: Long): Int? =
        if (total <= 0L) null else ((completed * 100) / total).toInt().coerceIn(0, 100)

    fun orukeetModelValue(
        compatible: Boolean,
        state: LocalModelState,
        waitReason: DownloadWaitReason,
        active: Boolean,
        runtime: RuntimeState,
        catalogId: String = ModelCatalog.current.id,
    ): OrukeetModelValue {
        val candidateInstalled = catalogId in state.installed
        return when {
            !compatible -> OrukeetModelValue.UNSUPPORTED
            state.phase == ModelPhase.CHECKING -> OrukeetModelValue.CHECKING
            state.phase == ModelPhase.ACTIVATING -> OrukeetModelValue.LOADING
            state.phase == ModelPhase.REMOVING -> OrukeetModelValue.DELETING
            state.transferPhase == ModelPhase.VERIFYING -> OrukeetModelValue.VERIFYING
            state.transferPhase == ModelPhase.WAITING_FOR_NETWORK -> when (waitReason) {
                DownloadWaitReason.WIFI_REQUIRED -> OrukeetModelValue.WAITING_FOR_WIFI
                DownloadWaitReason.NO_INTERNET -> OrukeetModelValue.WAITING_FOR_INTERNET
                DownloadWaitReason.STARTING -> OrukeetModelValue.WAITING_TO_START
            }
            state.transferPhase != null -> OrukeetModelValue.DOWNLOADING
            // An older model still dictates while a newer one is offered.
            state.currentId != null && state.currentId != catalogId -> OrukeetModelValue.UPDATE_AVAILABLE
            active && state.currentId != null -> when (runtime) {
                RuntimeState.LOADING -> OrukeetModelValue.LOADING
                RuntimeState.TRANSCRIBING -> OrukeetModelValue.IN_USE
                else -> OrukeetModelValue.READY
            }
            candidateInstalled -> OrukeetModelValue.DOWNLOADED
            else -> OrukeetModelValue.NOT_DOWNLOADED
        }
    }

    /**
     * The Primary action, or null when none fits: while the model is checked, loaded, deleted or transferred, when
     * the phone can't run Orukeet, and when the current model is in use.
     */
    fun orukeetNextAction(
        compatible: Boolean,
        state: LocalModelState,
        active: Boolean,
        error: LocalAsrFailure?,
        catalogId: String = ModelCatalog.current.id,
    ): OrukeetNextAction? {
        if (!compatible || state.transferPhase != null) return null
        // Until the start-up check has read the store, a missing model only looks missing.
        if (state.phase == ModelPhase.CHECKING || state.phase == ModelPhase.ACTIVATING || state.phase == ModelPhase.REMOVING) return null
        val candidateInstalled = catalogId in state.installed
        return when {
            !candidateInstalled -> when {
                error == LocalAsrFailure.DOWNLOAD -> OrukeetNextAction.RETRY_DOWNLOAD
                state.currentId != null -> OrukeetNextAction.DOWNLOAD_UPDATE
                else -> OrukeetNextAction.DOWNLOAD
            }
            !active || state.currentId == null -> OrukeetNextAction.USE
            state.currentId != catalogId -> OrukeetNextAction.APPLY_UPDATE
            else -> null
        }
    }
}

/** Rules of the rewrite styles list. Every change is saved right away, so each returns the list to store. */
object RewriteStyles {
    private val StyleName = Regex("""Style (\d+)""")

    /** A style's row summary: the first non-empty line of its instruction. The row caps it at two lines. */
    fun summary(instruction: String): String {
        return instruction.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    }

    /** The rewrite panel needs at least one style, so the last one can't be deleted. */
    fun canDelete(styles: List<RewritePromptPreset>): Boolean = styles.size > 1

    fun delete(styles: List<RewritePromptPreset>, id: String): List<RewritePromptPreset> =
        if (canDelete(styles)) styles.filterNot { it.id == id } else styles

    /** Replaces the style with the same id, or adds it at the end. Names and instructions are trimmed. */
    fun save(styles: List<RewritePromptPreset>, style: RewritePromptPreset): List<RewritePromptPreset> {
        val cleaned = style.copy(name = style.name.trim(), instruction = style.instruction.trim())
        val index = styles.indexOfFirst { it.id == cleaned.id }
        return if (index >= 0) styles.toMutableList().also { it[index] = cleaned } else styles + cleaned
    }

    fun canSave(name: String, instruction: String): Boolean = name.isNotBlank() && instruction.isNotBlank()

    /** A new, unsaved style named "Style N" after the highest existing number, with an empty instruction. */
    fun newStyle(styles: List<RewritePromptPreset>): RewritePromptPreset {
        val next = (styles.mapNotNull { StyleName.matchEntire(it.name.trim())?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0) + 1
        val base = RewritePromptPresets.newCustom(next - 1)
        var id = base.id
        while (styles.any { it.id == id }) id += "_"
        return base.copy(id = id, instruction = "")
    }
}
