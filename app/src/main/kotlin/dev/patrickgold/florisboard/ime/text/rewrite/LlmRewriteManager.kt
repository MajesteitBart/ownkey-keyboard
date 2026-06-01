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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToastSync

class LlmRewriteManager(
    context: Context,
) {
    enum class RewriteState {
        IDLE,
        REWRITING,
    }

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

    private val _stateFlow = MutableStateFlow(RewriteState.IDLE)
    val stateFlow: StateFlow<RewriteState> = _stateFlow

    fun rewriteWith(prompt: RewritePromptPreset) {
        if (_stateFlow.value == RewriteState.REWRITING) {
            appContext.showShortToastSync("Rewrite already running")
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

        scope.launch {
            _stateFlow.value = RewriteState.REWRITING
            val rewritten = withContext(Dispatchers.IO) {
                rewriteClient.rewrite(target.text, prompt)
            }.getOrElse { error ->
                _stateFlow.value = RewriteState.IDLE
                appContext.showShortToastSync(error.message ?: "Rewrite failed")
                return@launch
            }.trim()

            val selected = editorInstance.setSelection(target.start, target.end)
            val committed = selected && editorInstance.commitText(rewritten)
            _stateFlow.value = RewriteState.IDLE
            if (committed) {
                keyboardManager.isRewriteOptionsVisible = false
            } else {
                appContext.showShortToastSync("Could not replace text")
            }
        }
    }

    fun closeOptions() {
        keyboardManager.isRewriteOptionsVisible = false
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
