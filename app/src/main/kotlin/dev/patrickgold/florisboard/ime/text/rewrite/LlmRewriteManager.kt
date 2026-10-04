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

package dev.patrickgold.florisboard.ime.text.rewrite

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToastSync

class LlmRewriteManager(
    context: Context,
    private val aiAvailabilityPolicy: AiAvailabilityPolicy,
) {
    companion object {
        /** Same dwell as the voice flow's `Text replaced` confirmation, so both feel like one panel. */
        private const val DoneConfirmationMillis = 1_200L
    }

    /**
     * Step of the AI rewrite panel state machine: options -> generating -> result -> done.
     * Exactly one step is interactive at a time; visuals are derived entirely from [RewriteUiState].
     */
    enum class RewriteStep {
        OPTIONS,
        GENERATING,
        RESULT,
        DONE,
    }

    data class RewriteUiState(
        val step: RewriteStep = RewriteStep.OPTIONS,
        val activePrompt: RewritePromptPreset? = null,
        val resultText: String? = null,
    )

    /** The captured text and where it was, plus the identity checked again right before replacing it. */
    private data class RewriteTarget(
        val text: String,
        val start: Int,
        val end: Int,
        val snapshot: VoiceRewriteTargetSnapshot,
    )

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val clipboardManager by context.clipboardManager()
    private val prefs by FlorisPreferenceStore
    private val secretsStore = LlmRewriteSecretsStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rewriteClient = LlmRewriteClient(
        apiKeyProvider = { secretsStore.getApiKey() },
        endpointUrlProvider = { prefs.voxtral.postProcessingEndpointUrl.get() },
        modelProvider = { prefs.voxtral.postProcessingModel.get() },
        providerIdProvider = { prefs.voxtral.postProcessingProvider.get() },
    )

    private val _uiStateFlow = MutableStateFlow(RewriteUiState())
    val uiStateFlow: StateFlow<RewriteUiState> = _uiStateFlow

    private var generateJob: Job? = null
    private var activeTarget: RewriteTarget? = null
    private var activeTargeting = false

    // Created on first use, so the editor is only touched once a preset is tapped.
    private val targetGateway by lazy { EditorInstanceVoiceRewriteGateway(editorInstance) }
    private val targeting by lazy {
        PresetRewriteTargeting(
            source = VoiceRewriteTargetResolver(targetGateway),
            editor = object : PresetRewriteEditor {
                override val sessionId: Long get() = editorInstance.activeInputSessionId
                override val selection: EditorRange get() = editorInstance.activeContent.selection
                override val selectionReachesFieldEnd: Boolean
                    get() {
                        if (!editorInstance.activeContent.selection.isSelectionMode) return false
                        // Asks the app: the cached content can't tell the end of the field from text it couldn't
                        // read. An app that can't answer leaves the end unknown, and the selection is left alone.
                        val after = FlorisImeService.currentInputConnection()?.getTextAfterCursor(1, 0) ?: return false
                        return after.isEmpty()
                    }
                override fun setSelection(range: EditorRange): Boolean =
                    editorInstance.setSelection(range.start, range.end)
            },
            scope = scope,
        )
    }

    init {
        scope.launch {
            aiAvailabilityPolicy.state.collect { availability ->
                if (availability is AiAvailability.Unavailable) {
                    generateJob?.cancel()
                    generateJob = null
                    activeTarget = null
                    abandonTarget()
                    _uiStateFlow.value = RewriteUiState()
                }
            }
        }
    }

    fun rewriteWith(prompt: RewritePromptPreset) {
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            return
        }
        if (_uiStateFlow.value.step == RewriteStep.GENERATING) {
            return
        }
        if (!secretsStore.hasApiKey()) {
            appContext.showShortToastSync("Add an LLM API key in Settings → AI")
            return
        }

        // Like a spoken instruction, a preset rewrites the selection, or the whole field when nothing is
        // selected. Capturing the field selects it all, so the panel shows progress from the tap onwards.
        generateJob?.cancel()
        _uiStateFlow.value = RewriteUiState(step = RewriteStep.GENERATING, activePrompt = prompt)
        activeTargeting = true
        generateJob = scope.launch {
            when (val resolution = targeting.capture()) {
                is VoiceRewriteTargetResolution.Resolved -> {
                    val snapshot = resolution.snapshot
                    val target = RewriteTarget(snapshot.sourceText, snapshot.range.start, snapshot.range.end, snapshot)
                    activeTarget = target
                    runGeneration(prompt, target)
                }
                is VoiceRewriteTargetResolution.Rejected -> {
                    _uiStateFlow.value = RewriteUiState()
                    appContext.showShortToastSync(resolution.reason.message().stringResId())
                }
            }
        }
    }

    /** Re-runs the active prompt against the originally captured text. */
    fun tryAgain() {
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            return
        }
        val state = _uiStateFlow.value
        val prompt = state.activePrompt ?: return
        val target = activeTarget ?: return
        generate(prompt, target)
    }

    /** Cancels an in-flight generation and returns to the options grid. */
    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        abandonTarget()
        _uiStateFlow.value = RewriteUiState()
    }

    /** Returns from the result sheet to the options grid, discarding the result. */
    fun backToOptions() {
        abandonTarget()
        _uiStateFlow.value = RewriteUiState()
    }

    /** Commits the pending result into the editor, shows the done confirmation, then closes the panel. */
    fun insertResult() {
        val state = _uiStateFlow.value
        val resultText = state.resultText ?: return
        val target = activeTarget ?: return
        generateJob = scope.launch {
            // The field may have changed while the result was generated, for example by autofill or the app itself.
            // Replacing the old range then would overwrite newer text, so the result goes to the clipboard instead.
            if (target.snapshot.verify(targetGateway.currentFrame()) != null) {
                clipboardManager.addNewPlaintext(resultText)
                appContext.showShortToastSync(R.string.rewrite_panel__text_changed_copied)
                activeTarget = null
                abandonTarget()
                _uiStateFlow.value = RewriteUiState()
                return@launch
            }
            val selected = editorInstance.setSelection(target.start, target.end)
            val committed = selected && editorInstance.commitText(resultText)
            if (!committed) {
                appContext.showShortToastSync("Could not replace text")
                _uiStateFlow.value = state.copy(step = RewriteStep.RESULT)
                return@launch
            }
            targeting.committed()
            _uiStateFlow.value = state.copy(step = RewriteStep.DONE)
            delay(DoneConfirmationMillis)
            closeOptions()
        }
    }

    /**
     * Resets the rewrite flow state without touching panel visibility. Called when the panel leaves
     * the composition through any dismissal path, so reopening always starts at the options grid.
     */
    fun onPanelDismissed() {
        generateJob?.cancel()
        generateJob = null
        activeTarget = null
        abandonTarget()
        _uiStateFlow.value = RewriteUiState()
    }

    /**
     * Voice rewrite is about to select the field for itself: a preset that was abandoned a moment ago must not
     * undo that selection when its own Select All arrives late.
     */
    fun releasePresetTarget() {
        if (activeTargeting) targeting.forget()
    }

    /** Puts the cursor back if a preset selected the whole field and its result was not inserted. */
    private fun abandonTarget() {
        // Nothing to undo before the first preset; avoids creating the editor adapter on every dismissal.
        if (activeTargeting) targeting.restore()
    }

    fun closeOptions() {
        onPanelDismissed()
        keyboardManager.isRewriteOptionsVisible = false
    }

    /**
     * Narrow generation primitive for the voice-rewrite orchestrator. Preset targeting, preview and
     * commit stay here; the voice session owns its own target, review, and replacement.
     */
    fun voiceRewriteOperation(): VoiceRewriteOperation = LlmVoiceRewriteOperation(rewriteClient)

    fun isRewriteConfigured(): Boolean = secretsStore.hasApiKey()

    /** Configured rewrite provider name without the API variant, never the endpoint URL. */
    fun rewriteProviderLabel(): String = LlmRewriteProviders.resolve(
        providerId = prefs.voxtral.postProcessingProvider.get(),
        endpointUrl = prefs.voxtral.postProcessingEndpointUrl.get(),
        model = prefs.voxtral.postProcessingModel.get(),
    ).preset.providerName

    private fun generate(prompt: RewritePromptPreset, target: RewriteTarget) {
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            return
        }
        generateJob?.cancel()
        _uiStateFlow.value = RewriteUiState(step = RewriteStep.GENERATING, activePrompt = prompt)
        generateJob = scope.launch { runGeneration(prompt, target) }
    }

    private suspend fun runGeneration(prompt: RewritePromptPreset, target: RewriteTarget) {
        if (aiAvailabilityPolicy.current() !is AiAvailability.Available) {
            activeTarget = null
            abandonTarget()
            _uiStateFlow.value = RewriteUiState()
            return
        }
        val rewritten = withContext(Dispatchers.IO) {
            rewriteClient.rewrite(target.text, prompt)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            abandonTarget()
            _uiStateFlow.value = RewriteUiState()
            appContext.showShortToastSync(error.message ?: "Rewrite failed")
            return
        }.trim()

        _uiStateFlow.value = RewriteUiState(
            step = RewriteStep.RESULT,
            activePrompt = prompt,
            resultText = rewritten,
        )
    }
}
