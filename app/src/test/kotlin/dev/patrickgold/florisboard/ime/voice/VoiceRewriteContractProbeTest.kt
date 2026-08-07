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

package dev.patrickgold.florisboard.ime.voice

import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

/**
 * Non-shipping executable model for WS-A/T-001. It proves the proposed contracts are internally
 * coherent before production code is authorized. It intentionally does not wire voice rewrite into
 * the IME; emulator/device compatibility evidence is recorded separately in the Delano project.
 */
class VoiceRewriteContractProbeTest : FunSpec({
    test("tap, platform-timed hold, cancellation, and accessibility dispatch exactly one mode") {
        val tap = GestureProbe(longPressTimeoutMs = 400)
        tap.down(atMs = 0)
        tap.up(atMs = 399)
        tap.outcomes shouldContainExactly listOf(GestureOutcome.DICTATION)

        val hold = GestureProbe(longPressTimeoutMs = 400)
        hold.down(atMs = 0)
        hold.advanceTo(atMs = 400)
        hold.up(atMs = 600)
        hold.outcomes shouldContainExactly listOf(GestureOutcome.VOICE_REWRITE)

        val slideCancelled = GestureProbe(longPressTimeoutMs = 400)
        slideCancelled.down(atMs = 0)
        slideCancelled.cancel(atMs = 250)
        slideCancelled.advanceTo(atMs = 500)
        slideCancelled.up(atMs = 600)
        slideCancelled.outcomes shouldContainExactly emptyList()

        val slowAccessibilityDelay = GestureProbe(longPressTimeoutMs = 1_500)
        slowAccessibilityDelay.down(atMs = 0)
        slowAccessibilityDelay.advanceTo(atMs = 1_499)
        slowAccessibilityDelay.outcomes shouldContainExactly emptyList()
        slowAccessibilityDelay.advanceTo(atMs = 1_500)
        slowAccessibilityDelay.up(atMs = 1_700)
        slowAccessibilityDelay.outcomes shouldContainExactly listOf(GestureOutcome.VOICE_REWRITE)

        val accessibilityAction = GestureProbe(longPressTimeoutMs = 400)
        accessibilityAction.accessibilityVoiceRewrite()
        accessibilityAction.outcomes shouldContainExactly listOf(GestureOutcome.VOICE_REWRITE)
    }

    test("target resolution waits for confirmed Select All and rejects unsafe editors") {
        val resolver = TargetResolverProbe(selectAllTimeoutMs = 1_000)
        val initial = EditorFrame(
            sessionId = 7,
            packageName = "probe.native",
            fieldId = 10,
            text = "alpha beta",
            selectionStart = 5,
            selectionEnd = 5,
        )
        val pending = resolver.begin(initial, nowMs = 100) as TargetResolution.AwaitingSelectAll

        resolver.confirm(pending, initial, nowMs = 400) shouldBe TargetResolution.AwaitingSelectAll(
            sessionId = 7,
            packageName = "probe.native",
            fieldId = 10,
            deadlineMs = 1_100,
        )

        val selectedAll = initial.copy(selectionStart = 0, selectionEnd = 10)
        val captured = resolver.confirm(pending, selectedAll, nowMs = 450) as TargetResolution.Captured
        captured.snapshot.scope shouldBe TargetScope.WHOLE_FIELD
        captured.snapshot.sourceText shouldBe "alpha beta"

        resolver.begin(initial.copy(secure = true), nowMs = 100) shouldBe
            TargetResolution.Rejected(TargetRejection.SECURE_FIELD)
        resolver.begin(initial.copy(text = ""), nowMs = 100) shouldBe
            TargetResolution.Rejected(TargetRejection.EMPTY)
        resolver.confirm(pending, initial, nowMs = 1_101) shouldBe
            TargetResolution.Rejected(TargetRejection.SELECT_ALL_TIMEOUT)
        resolver.confirm(pending, selectedAll.copy(packageName = "probe.other"), nowMs = 450) shouldBe
            TargetResolution.Rejected(TargetRejection.SESSION_CHANGED)
    }

    test("simulated editor matrix covers native, Compose, messaging, WebView, raw, secure, and problematic cases") {
        val resolver = TargetResolverProbe(selectAllTimeoutMs = 1_000)
        val profiles = listOf(
            SimulatedEditorProfile("native", confirmationDelayMs = 0),
            SimulatedEditorProfile("compose", confirmationDelayMs = 120),
            SimulatedEditorProfile("messaging", confirmationDelayMs = 80),
            SimulatedEditorProfile("browser-webview", confirmationDelayMs = 180),
            SimulatedEditorProfile("raw-ctrl-a", confirmationDelayMs = 250),
        )

        profiles.forEachIndexed { index, profile ->
            val frame = EditorFrame(
                sessionId = index.toLong() + 1,
                packageName = "probe.${profile.name}",
                fieldId = index,
                text = "simulated target",
                selectionStart = 7,
                selectionEnd = 7,
            )
            val pending = resolver.begin(frame, nowMs = 0) as TargetResolution.AwaitingSelectAll
            val confirmed = frame.copy(selectionStart = 0, selectionEnd = frame.text.length)
            val result = resolver.confirm(pending, confirmed, nowMs = profile.confirmationDelayMs)
            (result as TargetResolution.Captured).snapshot.scope shouldBe TargetScope.WHOLE_FIELD
            result.snapshot.matches(confirmed) shouldBe true
        }

        val problematic = EditorFrame(20, "probe.problematic", 20, "simulated target", 4, 4)
        val timedOut = resolver.begin(problematic, nowMs = 0) as TargetResolution.AwaitingSelectAll
        resolver.confirm(timedOut, problematic, nowMs = 1_001) shouldBe
            TargetResolution.Rejected(TargetRejection.SELECT_ALL_TIMEOUT)

        resolver.begin(problematic.copy(packageName = "probe.secure", secure = true), nowMs = 0) shouldBe
            TargetResolution.Rejected(TargetRejection.SECURE_FIELD)
    }

    test("target snapshots block replacement after content, range, field, package, or session drift") {
        val frame = EditorFrame(
            sessionId = 9,
            packageName = "probe.editor",
            fieldId = 2,
            text = "rewrite this",
            selectionStart = 0,
            selectionEnd = 7,
        )
        val snapshot = (TargetResolverProbe().begin(frame, nowMs = 0) as TargetResolution.Captured).snapshot

        snapshot.matches(frame) shouldBe true
        snapshot.matches(frame.copy(text = "changed this")) shouldBe false
        snapshot.matches(frame.copy(selectionEnd = 8)) shouldBe false
        snapshot.matches(frame.copy(fieldId = 3)) shouldBe false
        snapshot.matches(frame.copy(packageName = "probe.other")) shouldBe false
        snapshot.matches(frame.copy(sessionId = 10)) shouldBe false
    }

    test("every IME and panel invalidation cancels recorder and provider work once") {
        LifecycleInvalidation.entries.forEach { invalidation ->
            val session = SessionProbe()
            session.startRecording()
            session.startProviderWork()
            session.invalidate(invalidation)
            session.invalidate(invalidation)

            session.state shouldBe ProbeSessionState.CANCELLED
            session.activeRecorderCount shouldBe 0
            session.activeProviderJobCount shouldBe 0
            session.cleanupCount shouldBe 1
        }
    }

    test("rolling measured levels distinguish silence, quiet speech, normal speech, and pause") {
        val reducer = AudioLevelReducerProbe(historySize = 18)
        repeat(24) { reducer.push(raw = 0.005f) }
        val silence = reducer.history.average().toFloat()
        repeat(24) { reducer.push(raw = 0.08f) }
        val quiet = reducer.history.average().toFloat()
        repeat(24) { reducer.push(raw = 0.55f) }
        val normal = reducer.history.average().toFloat()

        quiet shouldBeGreaterThan silence
        normal shouldBeGreaterThan quiet
        reducer.history.size shouldBe 18

        reducer.pause()
        reducer.history.distinct() shouldContainExactly listOf(AudioLevelReducerProbe.BASELINE)

        val reducedMotion = AudioLevelReducerProbe(historySize = 18, reducedMotion = true)
        repeat(3) { reducedMotion.push(raw = 0.8f) }
        reducedMotion.history.distinct() shouldContainExactly listOf(AudioLevelReducerProbe.BASELINE)
        reducedMotion.push(raw = 0.8f)
        reducedMotion.history.last() shouldBeGreaterThan AudioLevelReducerProbe.BASELINE
    }

    test("48 dp controls leave a bounded truthful waveform on compact and expanded layouts") {
        listOf(320, 360, 411, 840, 1_200).forEach { availableWidthDp ->
            val layout = RecordingRowLayoutProbe.resolve(availableWidthDp)
            layout.controlSizeDp shouldBe 48
            layout.waveformWidthDp shouldBeGreaterThan 0
            (layout.barCount in 6..18) shouldBe true
            (layout.clusterWidthDp <= 840) shouldBe true
        }
    }

    test("simulated portrait, landscape, tablet, and split layouts share one centered control cluster") {
        val layouts = listOf(
            SimulatedLayoutProfile("phone-portrait", availableWidthDp = 360),
            SimulatedLayoutProfile("short-landscape", availableWidthDp = 640),
            SimulatedLayoutProfile("tablet", availableWidthDp = 1_200),
            SimulatedLayoutProfile("split-keyboard", availableWidthDp = 1_200),
        )
        layouts.forEach { profile ->
            val resolved = RecordingRowLayoutProbe.resolve(profile.availableWidthDp)
            resolved.controlSizeDp shouldBe 48
            (resolved.clusterWidthDp <= 840) shouldBe true
            resolved.waveformWidthDp shouldBeGreaterThan 0
        }
        val tablet = RecordingRowLayoutProbe.resolve(layouts.first { it.name == "tablet" }.availableWidthDp)
        val split = RecordingRowLayoutProbe.resolve(layouts.first { it.name == "split-keyboard" }.availableWidthDp)
        split shouldBe tablet
    }

    test("dictation language cue is textual for every SpaceBarMode and absent for voice rewrite") {
        SpaceBarMode.entries.forEach { mode ->
            recognitionLanguageCue(mode, VoiceMode.DICTATION, "English") shouldBe "English"
            recognitionLanguageCue(mode, VoiceMode.VOICE_REWRITE, "English") shouldBe null
        }
    }
})

private enum class GestureOutcome { DICTATION, VOICE_REWRITE }

private class GestureProbe(private val longPressTimeoutMs: Long) {
    val outcomes = mutableListOf<GestureOutcome>()
    private var downAtMs: Long? = null
    private var consumedByHold = false
    private var cancelled = false

    fun down(atMs: Long) {
        if (downAtMs != null) return
        downAtMs = atMs
    }

    fun advanceTo(atMs: Long) {
        val startedAt = downAtMs ?: return
        if (!cancelled && !consumedByHold && atMs - startedAt >= longPressTimeoutMs) {
            consumedByHold = true
            outcomes += GestureOutcome.VOICE_REWRITE
        }
    }

    fun up(atMs: Long) {
        val startedAt = downAtMs ?: return
        advanceTo(atMs)
        if (!cancelled && !consumedByHold && atMs - startedAt < longPressTimeoutMs) {
            outcomes += GestureOutcome.DICTATION
        }
        downAtMs = null
    }

    fun cancel(atMs: Long) {
        advanceTo(atMs)
        if (!consumedByHold) cancelled = true
    }

    fun accessibilityVoiceRewrite() {
        if (outcomes.isEmpty()) outcomes += GestureOutcome.VOICE_REWRITE
    }
}

private enum class TargetScope { SELECTION, WHOLE_FIELD }
private enum class TargetRejection { SECURE_FIELD, EMPTY, TOO_LONG, SELECT_ALL_TIMEOUT, SESSION_CHANGED }

private data class SimulatedEditorProfile(val name: String, val confirmationDelayMs: Long)

private data class EditorFrame(
    val sessionId: Long,
    val packageName: String,
    val fieldId: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val secure: Boolean = false,
)

private data class TargetSnapshot(
    val sessionId: Long,
    val packageName: String,
    val fieldId: Int,
    val scope: TargetScope,
    val selectionStart: Int,
    val selectionEnd: Int,
    val sourceText: String,
) {
    fun matches(frame: EditorFrame): Boolean {
        if (sessionId != frame.sessionId || packageName != frame.packageName || fieldId != frame.fieldId) return false
        if (selectionStart != frame.selectionStart || selectionEnd != frame.selectionEnd) return false
        return frame.text.substringOrNull(selectionStart, selectionEnd) == sourceText
    }
}

private sealed interface TargetResolution {
    data class Captured(val snapshot: TargetSnapshot) : TargetResolution
    data class AwaitingSelectAll(
        val sessionId: Long,
        val packageName: String,
        val fieldId: Int,
        val deadlineMs: Long,
    ) : TargetResolution
    data class Rejected(val reason: TargetRejection) : TargetResolution
}

private class TargetResolverProbe(
    private val selectAllTimeoutMs: Long = 1_000,
    private val maxCharacters: Int = 12_000,
) {
    fun begin(frame: EditorFrame, nowMs: Long): TargetResolution {
        if (frame.secure) return TargetResolution.Rejected(TargetRejection.SECURE_FIELD)
        if (frame.text.isEmpty()) return TargetResolution.Rejected(TargetRejection.EMPTY)
        if (frame.selectionStart != frame.selectionEnd) {
            return capture(frame, TargetScope.SELECTION)
        }
        return TargetResolution.AwaitingSelectAll(
            sessionId = frame.sessionId,
            packageName = frame.packageName,
            fieldId = frame.fieldId,
            deadlineMs = nowMs + selectAllTimeoutMs,
        )
    }

    fun confirm(pending: TargetResolution.AwaitingSelectAll, frame: EditorFrame, nowMs: Long): TargetResolution {
        if (pending.sessionId != frame.sessionId || pending.packageName != frame.packageName || pending.fieldId != frame.fieldId) {
            return TargetResolution.Rejected(TargetRejection.SESSION_CHANGED)
        }
        if (nowMs > pending.deadlineMs) return TargetResolution.Rejected(TargetRejection.SELECT_ALL_TIMEOUT)
        if (frame.selectionStart == frame.selectionEnd) return pending
        return capture(frame, TargetScope.WHOLE_FIELD)
    }

    private fun capture(frame: EditorFrame, scope: TargetScope): TargetResolution {
        val source = frame.text.substringOrNull(frame.selectionStart, frame.selectionEnd)
            ?: return TargetResolution.Rejected(TargetRejection.SESSION_CHANGED)
        if (source.isEmpty()) return TargetResolution.Rejected(TargetRejection.EMPTY)
        if (source.length > maxCharacters) return TargetResolution.Rejected(TargetRejection.TOO_LONG)
        return TargetResolution.Captured(
            TargetSnapshot(
                sessionId = frame.sessionId,
                packageName = frame.packageName,
                fieldId = frame.fieldId,
                scope = scope,
                selectionStart = frame.selectionStart,
                selectionEnd = frame.selectionEnd,
                sourceText = source,
            ),
        )
    }
}

private fun String.substringOrNull(start: Int, end: Int): String? {
    if (start < 0 || end < start || end > length) return null
    return substring(start, end)
}

private enum class LifecycleInvalidation { KEYBOARD_HIDE, INPUT_RESTART, FIELD_SWITCH, PANEL_DISPOSAL, IME_TEARDOWN }
private enum class ProbeSessionState { IDLE, RECORDING, PROCESSING, CANCELLED }

private class SessionProbe {
    var state = ProbeSessionState.IDLE
        private set
    var activeRecorderCount = 0
        private set
    var activeProviderJobCount = 0
        private set
    var cleanupCount = 0
        private set

    fun startRecording() {
        check(activeRecorderCount == 0)
        activeRecorderCount = 1
        state = ProbeSessionState.RECORDING
    }

    fun startProviderWork() {
        check(state == ProbeSessionState.RECORDING)
        activeProviderJobCount = 1
        state = ProbeSessionState.PROCESSING
    }

    fun invalidate(reason: LifecycleInvalidation) {
        @Suppress("UNUSED_VARIABLE") val recordedReason = reason
        if (state == ProbeSessionState.CANCELLED) return
        activeRecorderCount = 0
        activeProviderJobCount = 0
        cleanupCount += 1
        state = ProbeSessionState.CANCELLED
    }
}

private class AudioLevelReducerProbe(
    private val historySize: Int,
    private val reducedMotion: Boolean = false,
) {
    companion object {
        const val BASELINE = 0.08f
        private const val NOISE_FLOOR = 0.02f
    }

    private var smoothed = BASELINE
    private var sampleIndex = 0
    val history = ArrayDeque<Float>().apply { repeat(historySize) { add(BASELINE) } }

    fun push(raw: Float) {
        sampleIndex += 1
        if (reducedMotion && sampleIndex % 4 != 0) return
        val normalized = ((raw.coerceIn(0f, 1f) - NOISE_FLOOR) / (1f - NOISE_FLOOR)).coerceIn(0f, 1f)
        val perceptual = BASELINE + sqrt(normalized) * (1f - BASELINE)
        val alpha = if (perceptual > smoothed) 0.72f else 0.24f
        smoothed += (perceptual - smoothed) * alpha
        history.removeFirst()
        history.addLast(smoothed.coerceIn(BASELINE, 1f))
    }

    fun pause() {
        smoothed = BASELINE
        history.clear()
        repeat(historySize) { history.add(BASELINE) }
    }
}

private data class RecordingRowLayout(
    val clusterWidthDp: Int,
    val controlSizeDp: Int,
    val waveformWidthDp: Int,
    val barCount: Int,
)

private data class SimulatedLayoutProfile(val name: String, val availableWidthDp: Int)

private object RecordingRowLayoutProbe {
    fun resolve(availableWidthDp: Int): RecordingRowLayout {
        val cluster = availableWidthDp.coerceAtMost(840)
        val trailingAction = 53
        val timer = 62
        val controls = 48 * 2
        val gapsAndDividers = 26
        val waveform = (cluster - trailingAction - timer - controls - gapsAndDividers).coerceAtLeast(1)
        val bars = (waveform / 8).coerceIn(6, 18)
        return RecordingRowLayout(cluster, 48, waveform, bars)
    }
}

private enum class VoiceMode { DICTATION, VOICE_REWRITE }

private fun recognitionLanguageCue(mode: SpaceBarMode, voiceMode: VoiceMode, language: String): String? {
    @Suppress("UNUSED_VARIABLE") val configuredSpaceBarMode = mode
    return if (voiceMode == VoiceMode.DICTATION) language else null
}
