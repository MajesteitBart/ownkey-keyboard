---
task: T-001
status: complete
date: 2026-08-04
time_box: one working day
recommendation: approve
---

# T-001 Android Interaction and Compatibility Probe

## Question

Can the approved voice-rewrite interaction be implemented without double-dispatching dictation, losing editor-target integrity, leaving recorder/provider jobs alive, faking microphone activity, or collapsing controls below 48 dp?

## Scope and Safety

- In scope: executable contract models, source inspection, one API 35 emulator, installed representative editor inventory, current IME behavior, and architecture/test-seam recommendations.
- Out of scope: production feature wiring, provider calls, real user content, production prompt text, and claiming emulator/model evidence as physical-device compatibility. Product-owner authorization accepts simulation for M0 only; T-018 retains physical release validation.
- The executable model is test-only at `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRewriteContractProbeTest.kt` and cannot ship in the APK.

## Environment

- Android 15 / API 35 x86_64 emulator.
- 1080 x 2400 px at 420 dpi (approximately 411 x 914 dp), portrait baseline.
- Android secure `long_press_timeout`: 400 ms.
- Installed editor candidates include Chrome, Google Messages, Google Contacts, Google Drive/Docs, and Android Settings.
- Ownkey debug IME is selected and serves Chrome, Google Messages, and a Chrome-hosted HTML textarea successfully.
- No physical Android device or enabled TalkBack service was available in this run.

## Source Touchpoints Exercised

| Surface | Observed baseline | Probe implication |
| --- | --- | --- |
| `QuickActionButton` | Pointer path dispatches down immediately, then up or cancel; it has no hold arbitration or long-click semantics. | Voice rewrite must arbitrate before either manager receives a key event. A recognized hold consumes release. |
| `EditorInstance.performClipboardSelectAll()` | Uses context-menu Select All for normal editors and Ctrl+A for raw editors. Selection confirmation arrives through `onUpdateSelection`. | Treat the boolean as request acceptance only. Wait for a matching non-empty selection callback with a bounded timeout. |
| `DictationMicPill` | Active bars and halo are driven by infinite clock animations; success is inferred from transcribing-to-idle after 800 ms. | Remove active waveform semantics from the button and introduce explicit session-scoped terminal outcomes. |
| Original recording-row reference (now `VoiceRecordingRow`) | Used live `audioLevelFlow`, but repeated one current value through a fixed profile; pause/cancel controls were 30 or 34 dp. | Refactor to rolling independent samples and 48 dp controls; do not wire the reference unchanged. |
| `VoxtralDictationManager` | Owns a main-scoped supervisor, recorder, 20 Hz amplitude polling, transcription, and direct editor commit. | Extract a single-recorder coordinator and transcription-only operation; the spoken instruction must never call the commit path. |
| `FlorisImeService` lifecycle | Input/view/window callbacks update editor/UI state but do not cancel dictation. `onDestroy` also does not cancel the manager scope. | A shared session invalidation hook must be called from field/input restart, finish/hide, panel disposal, and teardown paths. |
| `TextKeyboardLayout` / `SpaceBarMode` | `NOTHING` omits the label and `SPACE_BAR_KEY` substitutes a space symbol. | During dictation, temporarily force readable recognition-language text for all modes; voice rewrite shows no cue. |

## Executable Contract Evidence

The test-only model covers:

- normal tap before the configured timeout;
- hold at the configured timeout, release consumption, slide/cancel, a 1,500 ms accessibility delay, and the explicit accessibility action;
- existing selection, asynchronous Select All confirmation, empty/secure/over-limit rejection, a 1,000 ms confirmation timeout, and editor/session drift;
- stale-target rejection after source, range, field, package, or session changes;
- idempotent cleanup for keyboard hide, input restart, field switch, panel disposal, and IME teardown;
- an 18-sample measured-level history with a 0.02 normalized noise floor, square-root mapping, fast attack, slower release, pause baseline, and reduced-motion sampling at 5 Hz;
- compact through 840 dp recording clusters with two 48 dp controls and a reduced 6-18 bar count; and
- readable dictation-language text for every `SpaceBarMode`, absent during voice rewrite.

Targeted result: all 9 probe tests passed with
`./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.voice.VoiceRewriteContractProbeTest"`.
The build completed successfully. Existing localized-resource substitution warnings were emitted and are unrelated to this probe.

## Gesture Findings

- Use the platform-provided long-press timeout at gesture start; do not cache a fixed product duration.
- Do not call the current `QuickAction.InsertKey.onPointerDown()` until the gesture resolves. Ordinary dictation begins on a released tap; voice rewrite begins when the hold threshold is reached.
- Hold recognition emits one haptic and one voice-rewrite action, marks the gesture consumed, and makes release a no-op.
- Cancellation before recognition emits neither mode. Cancellation after recognition cancels the newly started voice session rather than falling through to dictation.
- Add an explicit semantic `Voice rewrite` custom action/on-long-click label; TalkBack must not depend on synthesized touch holding.

## Target and Editor Findings

- A 1,000 ms selection-confirmation timeout is the initial safe bound: it permits asynchronous editor callbacks without allowing microphone startup against an unconfirmed scope. Device/editor evidence must tune rather than remove this bound.
- Snapshot identity needs a monotonic Ownkey input-session generation plus package name and a stable field identity derived from the active `EditorInfo`; field id alone is not sufficient across restarts.
- Snapshot target data needs scope, normalized range, source text, and a non-reversible integrity digest. Replacement compares live editor/session identity, range, and source text; the digest is for content-free diagnostics, not a substitute for the live comparison.
- Raw editors may receive Ctrl+A, but still require the same confirmed callback. Unsupported, empty, secure, incomplete, or timed-out editors fail before microphone/provider work and ask for manual selection.

## Audio and Feedback Findings

- `MediaRecorder.maxAmplitude / 32767` is a truthful normalized source, sampled at the existing 20 Hz rate.
- Initial reducer tuning for physical-device validation: 0.02 normalized noise floor, square-root perceptual mapping, approximately 70 ms attack, approximately 200 ms release, and 18 samples (900 ms) of history.
- Silence and no-input/mock modes stay at an 8% uniform baseline. Pause stops sampling and resets history to the same dim baseline.
- Reduced motion may sample/render every fourth 20 Hz sample (5 Hz), with no traveling, interpolated, halo, or looping motion.
- The mic button needs explicit `success` and `error` outcomes. Reset timers are keyed to a session/outcome generation so an older 900 ms or 5 s timer cannot reset a newer action.

## Layout Findings

- A centered recording cluster capped at 840 dp is viable from a 320 dp compact keyboard through expanded layouts when pause and cancel remain 48 dp and the trailing stop slot is approximately 53 dp.
- The waveform flexes after timer and controls, reducing from 18 bars to no fewer than 6 bars on compact widths.
- The current 30/34 dp `RecordingIconButton` sizes fail the contract and must not be promoted unchanged.
- Split layouts should center one shared recording cluster over the full IME body rather than duplicate controls in each half.

## Lifecycle and Manager Boundary Recommendation

1. `AudioSessionCoordinator`: owns the single recorder lease, app-private temporary audio, pause/resume/stop/cancel, 20 Hz measured levels, elapsed time, and idempotent cleanup.
2. `VoxtralDictationManager`: requests the shared lease, performs transcription, and commits only for ordinary dictation.
3. `VoiceRewriteSessionManager`: owns target resolution/snapshot, preflight, transcription-only output, rewrite request, retry/rerecord, review state, and invalidation generation.
4. `LlmRewriteManager`: retains preset behavior and exposes a narrow generation primitive; it does not own the microphone or voice target lifecycle.
5. `FlorisImeService` and panel composition forward all lifecycle invalidations to the active shared/voice session before UI state is discarded.

Pure test seams should cover the gesture arbiter, target resolver/verifier, session reducer, terminal-outcome timer reducer, audio-level reducer, and recording-row width policy. Emulator/instrumented seams should cover real `InputConnection` callbacks, Compose semantics, and layout rendering.

## Compatibility Matrix

| Area | Evidence this run | Result |
| --- | --- | --- |
| API 35 native IME serving | Ownkey serves Chrome omnibox and Google Messages at 411 dp portrait; input connections are active. | Pass for two installed editors. |
| Tap/hold/cancel contract | Executable deterministic model across 400 ms and 1,500 ms timeouts. | M0 contract pass; real pointer/TalkBack integration remains in T-018. |
| Existing selection / Select All | Ownkey's Select All quick action selected the full dummy target in Chrome omnibox, Google Messages, and an HTML textarea; deterministic profiles cover native, Compose, messaging, browser/WebView, raw, secure, and timeout/problematic behavior. | M0 pass; representative physical editor matrix remains in T-018. |
| Selection retention | The selection highlight remained after opening the existing Rewrite panel in Chrome omnibox, Google Messages, and an HTML textarea. | Pass on three API 35 emulator surfaces. |
| Secure editor | A dummy HTML password field masked its value, but the current Rewrite and mic controls remained visibly available. | Baseline gap confirmed; the new preflight must gate before content/mic access. |
| Field/package/content drift | Deterministic snapshot verifier model. | Contract pass; real editor restart callbacks pending. |
| Lifecycle invalidation | Deterministic model for five invalidation sources plus source-gap inspection. | Contract pass; integration pending. |
| Waveform | Deterministic measured-level reducer model for silence, quiet speech, ordinary speech, pause, and reduced motion. | M0 signal contract pass; real microphone calibration remains in T-018. |
| Compact/expanded/split | Deterministic 320-1,200 dp width policy and explicit portrait, landscape, tablet, and split profiles, plus a live short-landscape baseline. | M0 geometry pass; rendered tablet/split screenshots remain in T-018. |
| TalkBack/reduced motion | Explicit accessibility action, configurable hold delay, announcement-safe state, and 5 Hz reduced-motion contracts modeled. | M0 contract pass; TalkBack execution remains in T-018. |

## Screenshot Evidence

- `t001-chrome-select-all.png` and `t001-chrome-rewrite-selection.png`: Chrome omnibox selection and retention.
- `t001-messages-select-all.png` and `t001-messages-rewrite-selection.png`: Google Messages selection and retention.
- `t001-webview-select-all.png` and `t001-webview-rewrite-selection.png`: HTML textarea selection and retention.
- `t001-webview-secure.png`: masked secure-field baseline with AI controls still visibly available.
- `t001-short-landscape-keyboard.png`: current short-landscape full-width keyboard/control baseline.

## Footguns

- Calling `onPointerDown()` before hold resolution starts ordinary dictation too early to make hold exclusive.
- Treating `performClipboardSelectAll()` returning true as proof of selection can record against an empty or stale target.
- Using the current application-scoped manager without explicit invalidation allows recorder/provider work to outlive the editor or IME window.
- Reusing `stopAndInsertTranscript()` inserts the instruction into the host editor.
- Rendering one amplitude through a decorative fixed profile looks canned even though one scalar is real.
- A target digest alone cannot prove safe replacement; live text and identity must still match.
- Full endpoint URLs, source text, transcript, raw prompt, and provider response bodies must remain absent from probe and production logs.

## Approval Recommendation

The architecture and pure contracts are sufficiently bounded to guide production tasks. On 2026-08-04 the product owner explicitly authorized emulator and deterministic simulated-device evidence for M0 because physical hardware was unavailable. That substitution retires the activation-gate uncertainty without asserting physical-device compatibility.

Recommendation: **approve** T-001 and the M0 architecture gate. T-018 remains responsible for representative physical-editor Select All/selection retention, real-microphone amplitude calibration, rendered compact/tablet/split/font-scale/reduced-motion layouts, and TalkBack focus/actions/announcements before release. Any failure there blocks rollout and reopens the affected contract; it is not silently waived.
