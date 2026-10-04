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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private class FakeEditor(var current: EditorRange, var session: Long = 7L) : PresetRewriteEditor {
    val restored = mutableListOf<EditorRange>()
    override val sessionId: Long get() = session
    override val selection: EditorRange get() = current
    override fun setSelection(range: EditorRange): Boolean {
        restored += range
        current = range
        return true
    }
}

private fun resolved(range: EditorRange, scope: VoiceRewriteTargetScope) = VoiceRewriteTargetResolution.Resolved(
    VoiceRewriteTargetSnapshot(
        editorSessionId = 7L,
        hostPackage = "host.app",
        fieldId = 1,
        scope = scope,
        range = range,
        sourceText = "x".repeat(range.length),
        characterCount = range.length,
        integrityHash = "hash",
    ),
)

class PresetRewriteTargetingTest : FunSpec({
    val cursor = EditorRange.cursor(12)
    val wholeField = EditorRange(0, 40)

    fun selectsWholeField(editor: FakeEditor) = VoiceRewriteTargetSource {
        editor.current = wholeField
        resolved(wholeField, VoiceRewriteTargetScope.WHOLE_FIELD)
    }

    fun targeting(source: VoiceRewriteTargetSource, editor: FakeEditor, scope: CoroutineScope) =
        PresetRewriteTargeting(source, editor, scope, lateSelectAllMillis = 300L, lateCheckMillis = 10L)

    test("abandoning a whole-field rewrite puts the cursor back") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val targeting = targeting(selectsWholeField(editor), editor, this)
            targeting.capture().shouldBeInstanceOf<VoiceRewriteTargetResolution.Resolved>()
            editor.current shouldBe wholeField

            targeting.restore()
            editor.current shouldBe cursor
        }
    }

    test("an inserted result leaves the cursor where the insert put it") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val targeting = targeting(selectsWholeField(editor), editor, this)
            targeting.capture()
            targeting.committed()
            editor.current = EditorRange.cursor(55)

            targeting.restore()
            editor.restored.shouldBeEmpty()
        }
    }

    test("an existing selection is rewritten as it is and never moved") {
        coroutineScope {
            val selection = EditorRange(3, 9)
            val editor = FakeEditor(selection)
            val targeting = targeting({ resolved(selection, VoiceRewriteTargetScope.SELECTION) }, editor, this)
            targeting.capture()

            targeting.restore()
            editor.restored.shouldBeEmpty()
            editor.current shouldBe selection
        }
    }

    test("a capture rejected after select-all puts the cursor back right away") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val targeting = targeting(
                {
                    editor.current = wholeField
                    VoiceRewriteTargetResolution.Rejected(VoiceRewriteTargetFailure.TARGET_TOO_LONG)
                },
                editor,
                this,
            )
            targeting.capture().shouldBeInstanceOf<VoiceRewriteTargetResolution.Rejected>()
            editor.current shouldBe cursor
        }
    }

    test("cancelling after select-all landed puts the cursor back") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val neverConfirmed = CompletableDeferred<VoiceRewriteTargetResolution>()
            val targeting = targeting(
                {
                    editor.current = wholeField
                    neverConfirmed.await()
                },
                editor,
                this,
            )
            launch(start = CoroutineStart.UNDISPATCHED) { targeting.capture() }.cancelAndJoin()

            targeting.restore()
            editor.current shouldBe cursor
        }
    }

    test("a select-all that lands after an early cancel is undone too") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val neverConfirmed = CompletableDeferred<VoiceRewriteTargetResolution>()
            val targeting = targeting({ neverConfirmed.await() }, editor, this)
            launch(start = CoroutineStart.UNDISPATCHED) { targeting.capture() }.cancelAndJoin()

            targeting.restore()
            yield()
            editor.current = wholeField
            delay(100L)
            editor.current shouldBe cursor
        }
    }

    test("a voice rewrite started after an early cancel keeps its own select-all") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val neverConfirmed = CompletableDeferred<VoiceRewriteTargetResolution>()
            val targeting = targeting({ neverConfirmed.await() }, editor, this)
            launch(start = CoroutineStart.UNDISPATCHED) { targeting.capture() }.cancelAndJoin()

            targeting.restore()
            yield()
            targeting.forget()
            editor.current = wholeField
            delay(100L)
            editor.restored.shouldBeEmpty()
            editor.current shouldBe wholeField
        }
    }

    test("typing after an early cancel ends the watch") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val neverConfirmed = CompletableDeferred<VoiceRewriteTargetResolution>()
            val targeting = targeting({ neverConfirmed.await() }, editor, this)
            launch(start = CoroutineStart.UNDISPATCHED) { targeting.capture() }.cancelAndJoin()

            targeting.restore()
            yield()
            editor.current = EditorRange.cursor(13)
            delay(100L)
            // Typing ended the watch, so a Select All the user makes afterwards stays.
            editor.current = wholeField
            delay(100L)
            editor.restored.shouldBeEmpty()
            editor.current shouldBe wholeField
        }
    }

    test("a selection the user makes while the field is being captured is left alone") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val neverConfirmed = CompletableDeferred<VoiceRewriteTargetResolution>()
            val targeting = targeting({ neverConfirmed.await() }, editor, this)
            val capture = launch(start = CoroutineStart.UNDISPATCHED) { targeting.capture() }
            editor.current = EditorRange(5, 10)
            capture.cancelAndJoin()

            targeting.restore()
            editor.restored.shouldBeEmpty()
            editor.current shouldBe EditorRange(5, 10)
        }
    }

    test("a selection the user makes after the capture is left alone") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val targeting = targeting(selectsWholeField(editor), editor, this)
            targeting.capture()
            editor.current = EditorRange(5, 10)

            targeting.restore()
            editor.restored.shouldBeEmpty()
        }
    }

    test("another field is left alone") {
        coroutineScope {
            val editor = FakeEditor(cursor)
            val targeting = targeting(selectsWholeField(editor), editor, this)
            targeting.capture()
            editor.session = 8L

            targeting.restore()
            editor.restored.shouldBeEmpty()
        }
    }
})
