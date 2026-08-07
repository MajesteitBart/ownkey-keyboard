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

import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.editor.EditorRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

enum class VoiceRewriteTargetScope {
    SELECTION,
    WHOLE_FIELD,
}

enum class VoiceRewriteTargetFailure {
    NO_ACTIVE_EDITOR,
    RAW_EDITOR,
    SECURE_FIELD,
    INVALID_SELECTION,
    EMPTY_TARGET,
    TARGET_TOO_LONG,
    SELECT_ALL_UNSUPPORTED,
    SELECT_ALL_TIMED_OUT,
    SELECTED_TEXT_UNAVAILABLE,
    EDITOR_SESSION_CHANGED,
}

data class VoiceRewriteTargetSnapshot(
    val editorSessionId: Long,
    val hostPackage: String,
    val fieldId: Int,
    val scope: VoiceRewriteTargetScope,
    val range: EditorRange,
    val sourceText: String,
    val characterCount: Int,
    val integrityHash: String,
)

sealed interface VoiceRewriteTargetResolution {
    data class Resolved(val snapshot: VoiceRewriteTargetSnapshot) : VoiceRewriteTargetResolution
    data class Rejected(val reason: VoiceRewriteTargetFailure) : VoiceRewriteTargetResolution
}

data class VoiceRewriteEditorFrame(
    val editorSessionId: Long,
    val hostPackage: String?,
    val fieldId: Int,
    val isRawEditor: Boolean,
    val isSecureField: Boolean,
    val selection: EditorRange,
    val selectedText: String,
    val isKnownEmpty: Boolean,
)

interface VoiceRewriteEditorGateway {
    val frames: Flow<VoiceRewriteEditorFrame>
    fun currentFrame(): VoiceRewriteEditorFrame
    fun requestSelectAll(): Boolean
}

fun interface VoiceRewriteTargetSource {
    suspend fun resolve(): VoiceRewriteTargetResolution
}

/** Production adapter intentionally exposes selected text only, never surrounding-text inference. */
class EditorInstanceVoiceRewriteGateway(
    private val editorInstance: EditorInstance,
) : VoiceRewriteEditorGateway {
    override val frames: Flow<VoiceRewriteEditorFrame> = combine(
        editorInstance.activeInputSessionIdFlow,
        editorInstance.activeInfoFlow,
        editorInstance.activeContentFlow,
    ) { sessionId, editorInfo, content ->
        VoiceRewriteEditorFrame(
            editorSessionId = sessionId,
            hostPackage = editorInfo.packageName,
            fieldId = editorInfo.base.fieldId,
            isRawEditor = editorInfo.isRawInputEditor,
            isSecureField = editorInfo.isCloudAiSecureField(),
            selection = content.selection,
            selectedText = content.selectedText,
            isKnownEmpty = content.offset == 0 && content.text.isEmpty() && content.selection == EditorRange.cursor(0),
        )
    }

    override fun currentFrame(): VoiceRewriteEditorFrame {
        val editorInfo = editorInstance.activeInfo
        val content = editorInstance.activeContent
        return VoiceRewriteEditorFrame(
            editorSessionId = editorInstance.activeInputSessionId,
            hostPackage = editorInfo.packageName,
            fieldId = editorInfo.base.fieldId,
            isRawEditor = editorInfo.isRawInputEditor,
            isSecureField = editorInfo.isCloudAiSecureField(),
            selection = content.selection,
            selectedText = content.selectedText,
            isKnownEmpty = content.offset == 0 && content.text.isEmpty() && content.selection == EditorRange.cursor(0),
        )
    }

    override fun requestSelectAll(): Boolean = editorInstance.performClipboardSelectAll()
}

class VoiceRewriteTargetResolver(
    private val editor: VoiceRewriteEditorGateway,
    private val selectAllTimeoutMs: Long = 1_000L,
    private val maxCharacters: Int = 12_000,
) : VoiceRewriteTargetSource {
    override suspend fun resolve(): VoiceRewriteTargetResolution {
        val initial = editor.currentFrame()
        rejectFrame(initial)?.let { return VoiceRewriteTargetResolution.Rejected(it) }
        if (initial.selection.isSelectionMode) {
            return capture(initial, VoiceRewriteTargetScope.SELECTION)
        }
        if (initial.selection.isNotValid) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.INVALID_SELECTION)
        }
        if (initial.isKnownEmpty) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EMPTY_TARGET)
        }

        val pendingEditor = PendingEditorIdentity.from(initial)
        if (!editor.requestSelectAll()) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECT_ALL_UNSUPPORTED)
        }

        return withTimeoutOrNull(selectAllTimeoutMs) {
            editor.frames
                .mapNotNull { frame -> confirmationResult(pendingEditor, frame) }
                .first()
        } ?: VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECT_ALL_TIMED_OUT)
    }

    private fun confirmationResult(
        pendingEditor: PendingEditorIdentity,
        frame: VoiceRewriteEditorFrame,
    ): VoiceRewriteTargetResolution? {
        if (!pendingEditor.matches(frame)) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EDITOR_SESSION_CHANGED)
        }
        rejectFrame(frame)?.let { return VoiceRewriteTargetResolution.Rejected(it) }
        if (!frame.selection.isSelectionMode) return null
        return capture(frame, VoiceRewriteTargetScope.WHOLE_FIELD)
    }

    private fun rejectFrame(frame: VoiceRewriteEditorFrame): VoiceRewriteTargetFailure? = when {
        frame.editorSessionId <= 0L || frame.hostPackage.isNullOrBlank() -> VoiceRewriteTargetFailure.NO_ACTIVE_EDITOR
        frame.isRawEditor -> VoiceRewriteTargetFailure.RAW_EDITOR
        frame.isSecureField -> VoiceRewriteTargetFailure.SECURE_FIELD
        frame.selection.isNotValid -> VoiceRewriteTargetFailure.INVALID_SELECTION
        else -> null
    }

    private fun capture(
        frame: VoiceRewriteEditorFrame,
        scope: VoiceRewriteTargetScope,
    ): VoiceRewriteTargetResolution {
        val range = EditorRange.normalized(frame.selection.start, frame.selection.end)
        if (!range.isSelectionMode) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.EMPTY_TARGET)
        }
        val sourceText = frame.selectedText
        if (sourceText.isEmpty() || sourceText.length != range.length) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.SELECTED_TEXT_UNAVAILABLE)
        }
        val characterCount = sourceText.codePointCount(0, sourceText.length)
        if (characterCount > maxCharacters) {
            return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.TARGET_TOO_LONG)
        }
        val hostPackage = frame.hostPackage
            ?: return VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.NO_ACTIVE_EDITOR)
        val snapshot = VoiceRewriteTargetSnapshot(
            editorSessionId = frame.editorSessionId,
            hostPackage = hostPackage,
            fieldId = frame.fieldId,
            scope = scope,
            range = range,
            sourceText = sourceText,
            characterCount = characterCount,
            integrityHash = VoiceRewriteTargetIntegrity.calculate(
                editorSessionId = frame.editorSessionId,
                hostPackage = hostPackage,
                fieldId = frame.fieldId,
                scope = scope,
                range = range,
                sourceText = sourceText,
            ),
        )
        return VoiceRewriteTargetResolution.Resolved(snapshot)
    }

    private data class PendingEditorIdentity(
        val editorSessionId: Long,
        val hostPackage: String?,
        val fieldId: Int,
    ) {
        fun matches(frame: VoiceRewriteEditorFrame): Boolean =
            editorSessionId == frame.editorSessionId &&
                hostPackage == frame.hostPackage &&
                fieldId == frame.fieldId

        companion object {
            fun from(frame: VoiceRewriteEditorFrame) = PendingEditorIdentity(
                editorSessionId = frame.editorSessionId,
                hostPackage = frame.hostPackage,
                fieldId = frame.fieldId,
            )
        }
    }
}
