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

import dev.patrickgold.florisboard.ime.editor.EditorRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The editor access a preset rewrite needs to capture its target and to put the cursor back. */
internal interface PresetRewriteEditor {
    val sessionId: Long
    val selection: EditorRange
    /** If [selection] is known to run to the end of the field, as a Select All does; false when that is unknown. */
    val selectionReachesFieldEnd: Boolean
    fun setSelection(range: EditorRange): Boolean
}

/**
 * Captures the text a preset rewrites: the selection, or the whole field when nothing is selected, with the same
 * resolver as voice rewrite. Capturing the whole field selects it, so until the result is inserted this puts the
 * cursor back when the rewrite is abandoned; otherwise the next keystroke would replace all of the user's text.
 *
 * Only the selection this capture made is undone. Select All is asynchronous, so before the resolver confirms it
 * the capture's selection is recognised by its shape: it spans the whole field, from the start to the end. Any
 * other selection the user makes is left alone.
 */
internal class PresetRewriteTargeting(
    private val source: VoiceRewriteTargetSource,
    private val editor: PresetRewriteEditor,
    private val scope: CoroutineScope,
    /** How long a Select All that lands after an early cancel is still undone; matches the resolver's timeout. */
    private val lateSelectAllMillis: Long = 1_000L,
    private val lateCheckMillis: Long = 50L,
) {
    private data class PendingRestore(
        val sessionId: Long,
        val cursor: EditorRange,
        /** The whole-field selection this capture made, once the resolver has confirmed it. */
        val captured: EditorRange? = null,
    )

    private var pending: PendingRestore? = null
    private var lateRollback: Job? = null

    suspend fun capture(): VoiceRewriteTargetResolution {
        lateRollback?.cancel()
        pending = null
        val before = editor.selection
        // Only a cursor is widened to the whole field; an existing selection is rewritten as it is.
        if (before.isValid && !before.isSelectionMode) {
            pending = PendingRestore(sessionId = editor.sessionId, cursor = before)
        }
        val resolution = source.resolve()
        when (resolution) {
            is VoiceRewriteTargetResolution.Resolved -> {
                pending = pending?.copy(captured = resolution.snapshot.range)
            }
            is VoiceRewriteTargetResolution.Rejected -> restore()
        }
        return resolution
    }

    /**
     * The rewrite was abandoned: puts the cursor back where it was. Leaves the editor alone when the user has since
     * made another selection or moved to another field. If the Select All has not arrived yet, it is undone when it
     * does, within [lateSelectAllMillis].
     */
    fun restore() {
        val restore = pending ?: return
        pending = null
        if (editor.sessionId != restore.sessionId) return
        val current = editor.selection
        if (isOurs(current, restore)) {
            editor.setSelection(restore.cursor)
            return
        }
        if (restore.captured != null || current != restore.cursor) return
        // Checks the selection the keyboard works with, not the host's change notifications, which can stay
        // silent when a coalesced report equals an earlier one. Only after an early cancel, for at most a second.
        lateRollback = scope.launch {
            var waited = 0L
            while (waited < lateSelectAllMillis) {
                delay(lateCheckMillis)
                waited += lateCheckMillis
                if (editor.sessionId != restore.sessionId) return@launch
                val next = editor.selection
                if (next == restore.cursor) continue
                // The first change decides: our Select All is undone, anything else is the user's.
                if (isOurs(next, restore)) editor.setSelection(restore.cursor)
                return@launch
            }
        }
    }

    /** The result replaced the target, which leaves the cursor after it, so there is nothing to put back. */
    fun committed() = forget()

    /** Another flow, such as voice rewrite, now owns the selection; nothing may be undone after this. */
    fun forget() {
        lateRollback?.cancel()
        pending = null
    }

    private fun isOurs(selection: EditorRange, restore: PendingRestore): Boolean {
        restore.captured?.let { return selection == it }
        return selection.isSelectionMode && selection.start == 0 && selection.end >= restore.cursor.end &&
            editor.selectionReachesFieldEnd
    }
}
