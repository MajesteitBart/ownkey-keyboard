/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.rewrite

import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.editor.EditorRange
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class VoiceRewriteTargetVerificationFailure {
    NO_REVIEWED_RESULT,
    EDITOR_SESSION_CHANGED,
    HOST_PACKAGE_CHANGED,
    FIELD_CHANGED,
    RAW_EDITOR,
    SECURE_FIELD,
    RANGE_CHANGED,
    SOURCE_CHANGED,
    INTEGRITY_CHANGED,
    SELECTION_FAILED,
    COMMIT_FAILED,
}

sealed interface VoiceRewriteEditorReplaceResult {
    data object Replaced : VoiceRewriteEditorReplaceResult
    data object SelectionFailed : VoiceRewriteEditorReplaceResult
    data object CommitFailed : VoiceRewriteEditorReplaceResult
}

sealed interface VoiceRewriteReplacementOutcome {
    data object Replaced : VoiceRewriteReplacementOutcome
    data class CopyFallback(val reason: VoiceRewriteTargetVerificationFailure) : VoiceRewriteReplacementOutcome
    data object Unavailable : VoiceRewriteReplacementOutcome
}

interface VoiceRewriteReplacementGateway {
    fun currentFrame(): VoiceRewriteEditorFrame
    fun replace(range: EditorRange, text: String): VoiceRewriteEditorReplaceResult
    fun copy(text: String): Boolean
}

class EditorInstanceVoiceRewriteReplacementGateway(
    private val editorInstance: EditorInstance,
    private val clipboardManager: ClipboardManager,
) : VoiceRewriteReplacementGateway {
    private val targetGateway = EditorInstanceVoiceRewriteGateway(editorInstance)

    override fun currentFrame(): VoiceRewriteEditorFrame = targetGateway.currentFrame()

    override fun replace(range: EditorRange, text: String): VoiceRewriteEditorReplaceResult {
        if (!editorInstance.setSelection(range.start, range.end)) {
            return VoiceRewriteEditorReplaceResult.SelectionFailed
        }
        return if (editorInstance.commitText(text)) {
            VoiceRewriteEditorReplaceResult.Replaced
        } else {
            VoiceRewriteEditorReplaceResult.CommitFailed
        }
    }

    override fun copy(text: String): Boolean {
        clipboardManager.addNewPlaintext(text)
        return true
    }
}

object VoiceRewriteTargetIntegrity {
    fun calculate(
        editorSessionId: Long,
        hostPackage: String,
        fieldId: Int,
        scope: VoiceRewriteTargetScope,
        range: EditorRange,
        sourceText: String,
    ): String {
        val digestInput = buildString {
            append(editorSessionId)
            append('\u0000')
            append(hostPackage)
            append('\u0000')
            append(fieldId)
            append('\u0000')
            append(scope.name)
            append('\u0000')
            append(range.start)
            append(':')
            append(range.end)
            append('\u0000')
            append(sourceText)
        }.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(digestInput)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

fun VoiceRewriteTargetSnapshot.verify(frame: VoiceRewriteEditorFrame): VoiceRewriteTargetVerificationFailure? {
    if (editorSessionId != frame.editorSessionId) {
        return VoiceRewriteTargetVerificationFailure.EDITOR_SESSION_CHANGED
    }
    if (hostPackage != frame.hostPackage) return VoiceRewriteTargetVerificationFailure.HOST_PACKAGE_CHANGED
    if (fieldId != frame.fieldId) return VoiceRewriteTargetVerificationFailure.FIELD_CHANGED
    if (frame.isRawEditor) return VoiceRewriteTargetVerificationFailure.RAW_EDITOR
    if (frame.isSecureField) return VoiceRewriteTargetVerificationFailure.SECURE_FIELD
    if (range != EditorRange.normalized(frame.selection.start, frame.selection.end)) {
        return VoiceRewriteTargetVerificationFailure.RANGE_CHANGED
    }
    if (sourceText != frame.selectedText) return VoiceRewriteTargetVerificationFailure.SOURCE_CHANGED
    val expectedIntegrity = VoiceRewriteTargetIntegrity.calculate(
        editorSessionId = editorSessionId,
        hostPackage = hostPackage,
        fieldId = fieldId,
        scope = scope,
        range = range,
        sourceText = sourceText,
    )
    if (integrityHash != expectedIntegrity) return VoiceRewriteTargetVerificationFailure.INTEGRITY_CHANGED
    return null
}
