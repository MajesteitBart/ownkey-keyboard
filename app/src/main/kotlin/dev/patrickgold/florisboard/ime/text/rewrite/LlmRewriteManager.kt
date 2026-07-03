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
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
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
) {
    companion object {
        private const val DoneConfirmationMillis = 900L
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

    private data class RewriteTarget(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
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

    fun rewriteWith(prompt: RewritePromptPreset) {
        if (_uiStateFlow.value.step == RewriteStep.GENERATING) {
            return
        }
        if (!secretsStore.hasApiKey()) {
            appContext.showShortToastSync("Add an LLM API key in Settings → AI")
            return
        }

        val target = resolveRewriteTarget().getOrElse { error ->
            appContext.showShortToastSync(error.message ?: "Select or type text before rewriting")
            return
        }
        activeTarget = target
        generate(prompt, target)
    }

    /** Re-runs the active prompt against the originally captured text. */
    fun tryAgain() {
        val state = _uiStateFlow.value
        val prompt = state.activePrompt ?: return
        val target = activeTarget ?: return
        generate(prompt, target)
    }

    /** Cancels an in-flight generation and returns to the options grid. */
    fun cancelGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiStateFlow.value = RewriteUiState()
    }

    /** Returns from the result sheet to the options grid, discarding the result. */
    fun backToOptions() {
        _uiStateFlow.value = RewriteUiState()
    }

    /** Commits the pending result into the editor, shows the done confirmation, then closes the panel. */
    fun insertResult() {
        val state = _uiStateFlow.value
        val resultText = state.resultText ?: return
        val target = activeTarget ?: return
        generateJob = scope.launch {
            val selected = editorInstance.setSelection(target.start, target.end)
            val committed = selected && editorInstance.commitText(resultText)
            if (!committed) {
                appContext.showShortToastSync("Could not replace text")
                _uiStateFlow.value = state.copy(step = RewriteStep.RESULT)
                return@launch
            }
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
        _uiStateFlow.value = RewriteUiState()
    }

    fun closeOptions() {
        onPanelDismissed()
        keyboardManager.isRewriteOptionsVisible = false
    }

    private fun generate(prompt: RewritePromptPreset, target: RewriteTarget) {
        generateJob?.cancel()
        _uiStateFlow.value = RewriteUiState(step = RewriteStep.GENERATING, activePrompt = prompt)
        generateJob = scope.launch {
            val rewritten = withContext(Dispatchers.IO) {
                rewriteClient.rewrite(target.text, prompt)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                _uiStateFlow.value = RewriteUiState()
                appContext.showShortToastSync(error.message ?: "Rewrite failed")
                return@launch
            }.trim()

            _uiStateFlow.value = RewriteUiState(
                step = RewriteStep.RESULT,
                activePrompt = prompt,
                resultText = rewritten,
            )
        }
    }

    private fun resolveRewriteTarget(): Result<RewriteTarget> {
        val content = editorInstance.activeContent
        val selection = content.selection
        if (selection.isNotValid) {
            return Result.failure(IllegalStateException("Select or type text before rewriting"))
        }

        val selectedText = content.selectedText
        if (selection.isSelectionMode && selectedText.isNotBlank()) {
            return Result.success(
                RewriteTarget(
                    text = selectedText,
                    start = selection.start,
                    end = selection.end,
                ),
            )
        }

        val textBefore = content.textBeforeSelection
        val trimmedEnd = textBefore.indexOfLast { !it.isWhitespace() } + 1
        if (trimmedEnd <= 0) {
            return Result.failure(IllegalStateException("Select or type text before rewriting"))
        }

        val sentenceEnd = textBefore.substring(0, trimmedEnd)
        val lastBoundary = sentenceEnd.lastIndexOfAny(charArrayOf('\n', '.', '!', '?'))
        val roughStart = (lastBoundary + 1).coerceAtLeast(0)
        val leadingWhitespace = textBefore.substring(roughStart, trimmedEnd).indexOfFirst { !it.isWhitespace() }
            .let { if (it < 0) 0 else it }
        val startInBefore = roughStart + leadingWhitespace
        val targetText = textBefore.substring(startInBefore, trimmedEnd)

        if (targetText.isBlank()) {
            return Result.failure(IllegalStateException("Select or type text before rewriting"))
        }

        val absoluteStart = selection.start - (textBefore.length - startInBefore)
        val absoluteEnd = selection.start - (textBefore.length - trimmedEnd)
        return Result.success(
            RewriteTarget(
                text = targetText,
                start = absoluteStart,
                end = absoluteEnd,
            ),
        )
    }
}
