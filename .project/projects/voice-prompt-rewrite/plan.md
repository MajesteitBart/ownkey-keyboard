---
name: Voice-Prompt Rewrite
status: active
lead: ownkey-keyboard-team
created: 2026-08-04T08:33:57Z
updated: 2026-08-06T06:25:15Z
linear_project_id: 
risk_level: high
spec_status_at_plan_time: planned
---

# Delivery Plan: Voice-Prompt Rewrite

## What Changed After Probe

T-001 completed its bounded Android probe on 2026-08-04. Nine non-shipping contract tests passed, including deterministic native/Compose/messaging/WebView/raw/secure/problematic editor profiles and phone/tablet/split layout profiles. Select All plus selection retention also passed on one API 35 emulator in Chrome's omnibox, Google Messages, and an HTML textarea. Source inspection confirmed the missing secure-field gate, lifecycle cancellation gap, autonomous mic animation, undersized recording controls, and need for pre-dispatch gesture arbitration.

The product owner explicitly authorized emulator and deterministic simulated-device evidence for the M0 gate while away from physical hardware. This approves the architecture boundary without claiming physical compatibility; representative real-device, real-microphone, rendered-layout, and TalkBack evidence remains mandatory in T-018 before release. D-011 also resolves the last product-policy blocker by deferring bespoke persistent undo from the first release.

The remaining evidence requires a physical microphone/device, TalkBack, Compose/raw/problematic editors, and rendered tablet/split coverage. This remains a provisional, probe-gated delivery plan authorized only for WS-A work; production implementation must not begin until WS-A records passing evidence and activates the spec/plan gate.

The product direction already approved before the probe is:

- tap dictates and platform-timed long-press starts voice rewrite;
- voice rewrite uses the current selection or a visibly confirmed Select All target;
- ordinary dictation and voice rewrite share a measured-amplitude recording row with pause/resume, cancel, and a trailing stop square;
- generated text is reviewed and the original target is revalidated before replacement; and
- success/error button feedback is explicit and transient, including a five-second error reset.

WS-A may tune amplitude mapping, timeout bounds, compact layout, and manager boundaries from evidence. It may not silently replace these approved product contracts.

## Technical Context

- `QuickActionButton.kt` owns the visible mic pill and the current pointer pipeline. Its active bars are clock-driven and it has no long-press branch.
- `DictationRecordingBar.kt` contains an unwired timer/waveform/pause/cancel/stop composition, but its controls are below the 48 dp target requirement and its waveform scales one current level through a fixed profile rather than keeping sample history.
- `VoxtralDictationManager.kt` owns recording, amplitude polling, transcription, and direct editor commit. Voice rewrite requires transcription without commit, explicit terminal outcomes, session-safe reset timers, and mutual exclusion.
- `AudioRecorder.kt` exposes `currentAmplitude()` and app-private temporary recording, providing the signal source for a truthful meter.
- `LlmRewriteManager.kt` owns preset target capture, generation, preview, and commit, but currently falls back to the previous sentence and does not revalidate source content immediately before commit.
- `EditorInstance.performClipboardSelectAll()` provides the supported whole-field request, while confirmation arrives asynchronously through editor content/selection updates.
- `RewriteOptionsPanel.kt` is a fixed-height two-column overlay surface that can host the pinned voice card and review states without resizing the IME.
- `TranscriptionClient` takes its language hint from a `languageHintProvider` lambda wired to `prefs.voxtral.languageHint`, which defaults to empty and sends no `language` field. D-009 changes that resolution to fall back to the active subtype's `primaryLocale`, so the seam is the provider lambda rather than the client, and `Auto` needs a representation distinct from unset.
- Current unit-test dependencies include coroutines-test and Turbine; Compose UI test infrastructure is not enabled in the app module, so the probe must decide between enabling focused Compose tests and keeping pure reducers/state holders JVM-testable.

## Architecture Decisions

### AD-001: Use a dedicated voice-rewrite session orchestrator

Create a `VoiceRewriteSessionManager` (final name may follow local conventions) that coordinates target resolution, preflight, audio capture, transcription-only output, rewrite generation, result review, cancellation, and lifecycle invalidation. Keep preset rewriting in `LlmRewriteManager` and ordinary dictation routing in `VoxtralDictationManager`.

Rationale: extending either current manager to own the other domain would couple editor mutation, recorder lifecycle, and provider state too tightly. A dedicated orchestrator can depend on narrow target, audio, transcription, and rewrite contracts while preserving existing entry paths.

### AD-002: Share one audio-session contract, not two recorders

Factor a shared audio-session coordinator and a transcription-only operation from the current dictation path. It owns the single-recorder lease and exposes start, pause, resume, stop, cancel, elapsed time, measured levels, and explicit terminal outcomes. Ordinary dictation adds editor commit after transcription; voice rewrite consumes the transcript without committing it.

Rationale: mutual exclusion and lifecycle cleanup belong at the resource boundary. Reusing `stopAndInsertTranscript()` would leak the spoken instruction into the host editor.

### AD-003: Resolve and snapshot targets before microphone access

Introduce a testable target resolver that accepts a valid existing selection or invokes `performClipboardSelectAll()`, waits for confirmed editor selection, applies secure/empty/12,000-character guards, and creates an immutable snapshot containing session identity, package, source scope, range, source text, and integrity data.

Rationale: surrounding-text APIs can be truncated and editor selection updates are asynchronous. Confirmed selection is both the user-visible scope contract and the safe data-access mechanism.

### AD-004: Verify the target again at replacement

The voice result path may replace only when editor identity, host package, scope/range, and source content still match the snapshot. Otherwise it exposes copy/close without automatic insertion.

Rationale: recording plus two provider calls materially increases stale-target risk compared with preset rewriting.

### AD-005: Drive UI from immutable session state and explicit outcomes

Compose consumes hoisted immutable state for ready, targeting, disclosure, recording, paused, transcribing, rewriting, result, success, warning, and error. Success/error are explicit events or presentation states with session-scoped timers; they are not inferred from generic processing-to-idle transitions.

Rationale: explicit state makes cancellation, five-second error reset, accessibility announcements, and deterministic tests reliable.

### AD-006: Use a pure rolling audio-level reducer

Map recorder amplitude into a bounded 16-20-sample history through a pure reducer with noise floor, perceptual scaling, attack/release, silence baseline, pause, and reduced-motion behavior. The recording row renders that history; the stop button never renders a waveform.

Rationale: separating signal processing from Compose allows deterministic tests and device tuning without embedding fake animation in the action control.

### AD-007: Arbitrate tap and long-press at the quick-action boundary

The voice quick action uses Android's configured long-press timeout and accessibility touch-and-hold delay. A recognized hold consumes release, fires one haptic, exposes explicit long-click semantics and a `Voice rewrite` accessibility action, and cannot dispatch the ordinary tap action.

Rationale: the current pointer handler dispatches down/up directly, so gesture ownership must be resolved before either manager starts work.

### AD-008: Preserve provider and privacy boundaries

Audio uses the configured dictation provider; captured target plus transcript uses the configured rewrite provider. Provider readiness and first-use disclosure run before microphone access. Content, raw internal prompts, provider bodies, and content-derived fingerprints do not enter production logs or telemetry.

Rationale: Ownkey is BYOK and does not provide a hosted relay; users need accurate routing disclosure and content-safe diagnostics.

### AD-010: Express the output-language rule in the fixed policy, not in app code

D-008's source-language default lives in the app's fixed rewrite policy sent alongside the captured text and instruction. No local language detection is added, and no language decision is derived from the transcript, keyboard subtype, or device locale.

Rationale: the provider model already resolves source language and handles mixed-language text; reimplementing that locally adds a failure mode and requires reading content for a decision one policy sentence covers. Keeping it in the fixed policy also preserves the FR-022 separation between app policy and user data. The cost is that output-language behavior is provider-dependent and must be verified against real models rather than asserted in unit tests.

### AD-009: Gate cloud AI availability in one shared policy

Introduce a single availability policy that answers whether configured-provider AI may run for the active editor session, evaluated from the existing `KeyboardState.isIncognitoMode` flag and the secure-field check. Dictation entry, preset rewrite entry, and the voice-rewrite preflight all consult it and receive a typed reason; no caller re-derives the rule.

Rationale: D-007 makes incognito disable all three AI paths, and those paths currently live in three different managers with no shared gate. One predicate keeps the rule testable, keeps the disabled-state copy consistent, and makes rollback a single seam. Duplicating the check per manager is how one entry point silently keeps working.

## Policy and Contract Checks

- [x] `.project` remains the execution source of truth.
- [x] Probe requirement is explicit: `probe_required: true`; T-003 records `probe_status: completed` at activation.
- [x] Production tasks are dependency-gated behind WS-A approval task T-003.
- [x] Evidence gates are defined per task, milestone, and rollout stage.
- [x] External sync writes require a dry run or explicit operator approval; none are part of this decomposition.
- [x] Spec and plan activation follows successful completion of T-001, T-002, and the T-003 go decision.

## Generated Artifact Map

- `spec.md`: active product direction and measurable acceptance contract; probe completed under D-012.
- `plan.md`: provisional architecture, sequencing, rollout, tests, and rollback defined in this planning pass.
- `workstreams/WS-A-probe-and-contract-gates.md`: prototype evidence and activation gate.
- `workstreams/WS-B-audio-session-and-feedback-foundation.md`: recorder/session, transcription-only, terminal outcomes, and level reducer.
- `workstreams/WS-C-targeting-and-rewrite-orchestration.md`: selection targeting, voice pipeline, retry, and safe replacement.
- `workstreams/WS-D-entry-recording-and-review-ux.md`: gesture, recording row, rewrite panel, adaptive layout, and accessibility.
- `workstreams/WS-E-verification-and-release-readiness.md`: cross-flow automation, device/editor validation, privacy/performance/power evidence, and rollout gate.
- `tasks/T-001` through `tasks/T-021`: atomic probe, implementation, verification, observability, and release-readiness tasks with an acyclic dependency graph.

## Complexity Exceptions

- Five workstreams and twenty tasks are justified by the feature crossing Android gesture arbitration, editor selection integrity, microphone ownership, two provider calls, Compose state surfaces, accessibility, and privacy. Collapsing these into a single UI or manager task would make review and rollback unsafe.
- T-020 is a separate task rather than an acceptance criterion inside T-009 because the incognito gate changes ordinary dictation and preset rewrite availability, not only the new voice flow. It must be reviewable and revertable without touching the voice session.
- WS-A includes delivery-governance work because the spec is intentionally probe-gated. All production tasks remain blocked through T-003 even though decomposition is complete.
- T-018 is an `L` task because representative editor, device-width, real-microphone, reduced-motion, and TalkBack validation cannot be safely split without duplicating setup and losing one compatibility matrix.

## Probe-Driven Architecture Changes

T-001 supports AD-001 through AD-007 and recommends a 1,000 ms Select All confirmation bound, an 18-sample measured-level history, initial 0.02 noise floor / 70 ms attack / 200 ms release tuning, a 5 Hz reduced-motion level update, and an 840 dp maximum recording cluster. Emulator interaction evidence plus nine deterministic contract tests cover the M0 architecture gate. Product-owner authorization accepts simulated editor, signal, accessibility-timing, and layout profiles here; T-018 retains representative physical-device/editor/TalkBack verification before release.

T-018 must still record release evidence for:

- tap/long-press exclusivity across cancellation, sliding, TalkBack, and configured hold delays;
- Select All confirmation and selection retention across representative native, Compose, browser/WebView, and raw editors;
- safe editor/session identity and stale-target verification fields;
- amplitude calibration for silence, quiet speech, ordinary speech, pause, and reduced motion on representative devices;
- viable compact and expanded recording-row composition with 48 dp controls;
- cancellation behavior on field switch, keyboard hide, input restart, and IME teardown; and
- the final boundary between the audio coordinator, dictation manager, voice-rewrite orchestrator, and rewrite manager.

If T-018 evidence invalidates an architectural decision, release is blocked and the affected implementation task is reopened or repaired before rollout.

## Workstream Design

- **WS-A Probe and Contract Gates:** retires material uncertainties, resolves the two open product policies, and is the only entry to production work.
- **WS-B Audio Session and Feedback Foundation:** owns microphone/session primitives, transcription-only output, explicit terminal outcomes, timers, and the pure waveform reducer; it does not own rewrite targeting or Compose screens.
- **WS-C Targeting and Rewrite Orchestration:** owns selected/whole-field target snapshots, preflight, sequential provider orchestration, retry/rerecord state, and safe replacement; it does not render UI.
- **WS-D Entry, Recording, and Review UX:** owns quick-action gestures, shared recording Compose, pinned card/disclosure, result/recovery surfaces, resource copy, adaptation, and accessibility; it consumes WS-B/WS-C contracts.
- **WS-E Verification and Release Readiness:** owns cross-flow automated tests, the device/editor/accessibility matrix, privacy/performance/power evidence, and rollout recommendation after implementation handoff.

## Milestone Strategy

1. **M0 Probe and Approval Gate (T-001-T-003):** evidence and policy decisions are complete; spec/plan become active.
2. **M1 Safe Foundations (T-004-T-008, T-020):** one audio-session contract, transcription-only output, explicit terminal state, truthful level reducer, verified target snapshots, and the shared cloud-AI availability gate exist with unit tests.
3. **M2 End-to-End Voice Rewrite Core (T-009-T-011):** preflight, two-provider pipeline, retry/rerecord, stale-target blocking, and safe replacement work headlessly.
4. **M3 Product UX (T-012-T-016):** tap/hold entry, shared recording row, pinned action, disclosure, review/recovery, adaptive layout, localization, and accessibility are integrated.
5. **M4 Release Evidence (T-017-T-019, T-021):** cross-flow automation, editor/device/TalkBack evidence, battery observability, privacy/performance checks, and rollout/rollback recommendation pass.

## Rollout Strategy

1. M0 is complete; keep new entry points unavailable until their dependency-safe production implementation is integrated. Prototype code remains test-only and must not ship as production behavior.
2. During M1-M2, merge foundations only when ordinary tap-to-dictate and preset rewrite regression tests remain green. Keep long-press/card entry disabled until their complete path exists.
3. Enable the feature in internal/debug dogfood for configured BYOK providers and run the consented task-success study plus real-device/editor matrix.
4. Advance to a closed/beta cohort only after privacy, accessibility, cancellation, stale-target, and ordinary-typing performance gates pass.
5. Advance to production without content telemetry; rely on content-free crash/performance evidence, support feedback, and consented usability sessions.

No server-side flag or Ownkey relay is introduced. If staged exposure needs a gate, use an app-local build/release capability gate that defaults off in unsupported builds and does not add a new user-facing settings form.

## Test Strategy

- Pure JVM tests cover gesture classification helpers where feasible, target-resolution state, preflight order, session transitions, five-second error reset, 900 ms success display, cancellation generations, target integrity, and waveform reduction with deterministic clocks/inputs.
- Turbine and coroutines-test verify no double dispatch, single-recorder ownership, transcription-only behavior, retry/rerecord reuse, terminal events, and stale jobs that cannot mutate a newer session.
- Fake input connections cover selected text, asynchronous Select All, unsupported/empty/raw/secure editors, over-limit targets, field/package changes, and replacement only after review.
- Fake recorder/transcription/rewrite providers cover silence, timeout, provider failure, empty results, cancellation, and the guarantee that instruction transcripts never commit directly.
- UI tests or probe-approved state-renderer tests cover 48 dp targets, click/long-click labels, live regions, stop-square semantics, transient terminal visuals, pinned-card hierarchy, fixed action rails, and reduced motion.
- Screenshot/manual checks cover phone portrait, short landscape, expanded tablet, split keyboard, dark/light/custom themes, long localized strings, and font scaling.
- Real-device checks cover amplitude calibration and system touch-and-hold settings; TalkBack checks cover the explicit `Voice rewrite` action and non-repetitive announcements.
- Performance checks compare keyboard open, ordinary key input, idle smartbar recomposition, and normal dictation against the existing baseline with no more than the spec's 5% p95 regression.
- Power checks use the release-like benchmark variant, Macrobenchmark `PowerMetric`, and fixed content-free Perfetto spans to correlate keyboard idle, recording, processing, transcription, and rewrite with system-wide energy activity. Supported physical hardware remains required for actual power numbers.
- Incognito tests cover all three AI entry points under forced, host-app-dynamic, and user-toggled incognito, prove no microphone or provider work starts, and prove availability is restored when the session leaves incognito without replaying blocked work.
- Output-language coverage splits by what is deterministic: unit tests assert that the fixed policy carries the source-language rule and that no code path derives an output language from the transcript, subtype, or locale; the provider matrix verifies actual source-language retention and explicit translation against each configured rewrite provider.
- Security/privacy review verifies secure-field gates, app-private temporary audio deletion, configured endpoint routing, and absence of selected/spoken/generated content in logs and telemetry.

## Rollback Strategy

- Disable or remove the long-press and pinned voice-card entry while leaving normal tap-to-dictate, preset rewriting, provider settings, and editor behavior intact.
- The incognito gate reverts independently of the voice flow because it is a single shared predicate; reverting it restores prior dictation and preset availability and requires reverting the incognito copy in the same change so the app never promises a gate it no longer enforces.
- Detach `VoiceRewriteSessionManager` from application/UI entry points; cancel and dispose any active session before rollback.
- Retain the shared recording row for ordinary dictation only if its independent regression suite passes; otherwise restore the previous mic treatment and remove the row wiring together.
- Keep transcription-only and target-resolver primitives only when unused code does not alter ordinary behavior; otherwise revert their call-site integration as a unit.
- No schema, account, server, or content migration is planned. Disclosure acknowledgement and coach-mark preferences, if added, must tolerate being ignored or removed.
- D-011 introduces no persistent undo payload, result history, or target history, so rollback has no undo-state cleanup or migration.
- On any stale-target, secure-field, wrong-field insertion, concurrent-recorder, or content-logging defect, halt rollout and disable voice-rewrite entry rather than degrading to automatic or partial-field behavior.

## Remaining Delivery Risks

- Some editors may not confirm Select All or may collapse the selection when the IME panel changes.
- The current application-scoped managers need explicit input-session invalidation to prevent jobs surviving field or IME lifecycle changes.
- `MediaRecorder.maxAmplitude` varies across hardware; poor calibration can make a truthful waveform appear dead or saturated.
- The current quick-action pointer pipeline may need careful refactoring to preserve down/up key semantics for every non-voice action.
- Compose UI test infrastructure is not currently enabled, so test seams must be agreed during the probe instead of relying only on manual screenshots.
- Sequential transcription and rewrite can feel slow; cancellation and progress states must remain responsive without streaming output.
- The incognito gate removes dictation and preset rewrite in sessions the host app marks with `flagNoPersonalizedLearning`, which the user never chose. If that fires more often than expected, the feature will read as broken rather than private, and the disabled-state copy is the only thing preventing that.
- External voice-IME fallback cannot support the in-process voice-rewrite transcript and must fail/recover explicitly rather than silently switching behavior.
