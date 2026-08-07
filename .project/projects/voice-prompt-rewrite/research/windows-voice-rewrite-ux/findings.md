---
type: research_findings
project: voice-prompt-rewrite
slug: windows-voice-rewrite-ux
created: 2026-08-04T08:34:03Z
updated: 2026-08-04T11:49:14Z
---

# Findings: Windows Voice-Instruction Rewrite UX

## Question

How should Ownkey Android combine selected text with a spoken rewrite instruction, using the Windows experience as evidence while fitting Android IME constraints across phone, tablet, success, cancellation, and failure states?

## Source References

- User-supplied Android screenshots: current portrait rewrite hub, portrait keyboard, expanded split keyboard, and expanded rewrite hub.
- Current Android implementation:
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteManager.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/RewriteOptionsPanel.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteClient.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/DictationRecordingBar.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/TextInputLayout.kt`
- Follow-up Android recording-state implementation:
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionButton.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/DictationRecordingBar.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt`
  - `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioRecorder.kt`
- [Ownkey Windows repository and README](https://github.com/MajesteitBart/ownkey-windows), inspected at commit `69923eacf2654db978b390955bcfa12e634e5de5`.
- [Windows selected-text voice rewrite implementation](https://github.com/MajesteitBart/ownkey-windows/blob/main/ownkey.py).
- [Windows overlay design spec](https://github.com/MajesteitBart/ownkey-windows/blob/main/docs/OVERLAY_DESIGN_SPEC.md).
- [Windows overlay state and labels](https://github.com/MajesteitBart/ownkey-windows/blob/main/overlay-ui/src/lib/overlay.ts).
- Windows README UI assets under `assets/readme/`, especially `ownkey-windows-features.png` and `ownkey-windows-dictation.png`.
- [Ownkey website repository](https://github.com/MajesteitBart/ownkey-site), inspected at commit `db013e061f9509484ba94c0367d95fcf33629298`.
- Website Windows mockup in `index.html` and `.overlaypill` styling in `style.css`.

## Observations

1. **Windows is a direct mode, not a preset.** The user selects text, holds a dedicated rewrite hotkey, speaks an instruction, releases, and receives a replacement. The overlay changes its label from a voice-editing listening state to rewriting and done.
2. **Windows captures selection early.** For safe hotkeys, selected text is copied when the hotkey is pressed, not after transcription. The source explicitly documents this as protection against a capture race.
3. **Windows has meaningful guards.** It requires non-empty selection, caps the target at 12,000 characters, distinguishes missing audio and rewrite configuration, and leaves the selection unchanged on an empty result or failure.
4. **Windows uses two configurable provider paths.** Instruction audio is transcribed through the audio configuration; captured text plus the resulting instruction goes through the rewrite configuration. The README makes direct provider transfer explicit.
5. **The Windows UI is intentionally small and state-led.** Its brand pill uses a waveform plus terse labels such as listening, speak an edit, rewriting, done, warning, and error. Orange indicates active AI/voice, green success, amber warning, and red failure; wording, not color, distinguishes rewrite from dictation.
6. **Windows auto-replaces; Android already has a safer review surface.** The Android manager generates a result, lets the user retry, and only mutates the editor after an explicit action. Voice transcription uncertainty makes that review more valuable, not less.
7. **Android has the pieces but not the orchestration boundary.** Rewrite owns selected target/range, provider call, preview, and replacement. Dictation owns recording/transcription but its terminal operation inserts the transcript directly. Voice rewrite needs transcription without commit.
8. **Android's current preset target is broader than the requested flow.** With no selection it may rewrite the previous sentence. Voice rewrite must instead use the existing selection or visibly invoke Select All and wait for a confirmed whole-field selection; it must never infer the target from partial surrounding text.
9. **The mic needs two explicit, mutually exclusive gesture contracts.** A normal tap cannot safely become contextual and must remain dictation. A platform-timed long-press can be the rewrite accelerator when it consumes the gesture, gives immediate haptic and semantic feedback, and has a visible and accessible alternate route.
10. **Async target integrity is the largest Android risk.** The current manager stores a range and later reselects it, but does not prove the original source still occupies that range immediately before commit. Voice adds recording and transcription latency, increasing the chance of field/content drift.
11. **Expanded layouts need composition, not stretching.** The supplied tablet screenshots show extremely wide panel cards and distant edge controls. Recording, disclosure, processing, and result states should use a centered maximum width while retaining the current two-column hub.
12. **The live site could be loaded but not snapshotted in the shared preview.** UI conclusions about the site therefore come from its public source and repository assets, not a claimed live interaction recording.
13. **The visible Android mic animation is not evidence of input.** `DictationWaveformBars` uses an infinite sine transition and does not consume `audioLevelFlow`, so it moves even when the person is silent.
14. **The preferable recording composition already exists but is unwired.** `DictationRecordingBar` lays out elapsed time, a center level meter, pause/resume, cancel, and a trailing stop action, and its waveform consumes the manager's amplitude flow. No production call site currently renders it.
15. **The amplitude source is real but needs presentation work.** The recorder exposes `MediaRecorder.maxAmplitude` normalized to 0-1 and the manager polls it every 50 ms. Cross-device sensitivity, noise floor, smoothing, rolling history, pause, and reduced-motion behavior still need calibration and tests.
16. **Terminal feedback is partly inferred and partly durable.** The manager exposes a persistent `ERROR` state with no timeout and no explicit success state; the mic UI infers success from transcribing-to-idle and hides it after 800 ms. Explicit terminal outcome events are safer and make a five-second error reset session-aware.

## Options Considered

| Option | Pros | Cons | Decision |
| --- | --- | --- | --- |
| Make a normal mic tap switch to rewrite instruction while the panel is open | No extra tile; short path | Hidden mode change; easy to dictate when intending to command; weak accessibility | Rejected; tap always remains dictation |
| Add a voice tile as one equal item in the preset grid | Small implementation surface | Can move below the fold; visually implies it is just another preset | Rejected |
| Pin a full-width voice card above a scrollable preset grid | Discoverable; clear hierarchy; presets preserved; adaptive | Requires grid scrolling when many presets exist | Retained as the discoverable and TalkBack-friendly route |
| Platform-timed long-press on the dictation key | Fast for repeated use; available from the normal keyboard; close to the Windows hold gesture | Requires careful tap/hold arbitration and teaching; gesture-only use is inaccessible | Approved as the primary accelerator, with haptic/state feedback and the pinned card retained |
| Require an existing selection | Simple and maximally explicit | Makes whole-field rewriting unnecessarily manual | Rejected as the only target rule |
| With no selection, visibly Select All and confirm the result | Whole-field rewriting is convenient while the exact transmitted scope remains visible | Some host editors may not support or promptly report Select All | Approved, with failure instead of partial-text inference |
| Keep the animated waveform inside the mic button | Compact; preserves the current state visual | Hides the stop action and moves without measured input | Rejected for active recording |
| Restore a shared first-row recording strip with a measured center waveform and trailing stop button | Clear action/status separation; reuses existing structure and amplitude flow; works for dictation and rewrite | Requires responsive-width and device-calibration work | Approved |
| Leave the mic button red until the next user action | Error remains noticeable | Makes a past failure look like a permanently broken/current state | Rejected; use a five-second transient treatment |
| Auto-replace when the model returns | Fastest and matches Windows | Mobile transcription error can overwrite text before review; diverges from current Android trust model | Rejected for first delivery |
| Preview, then explicit `Replace` | Safer; reuses current Android result UI; retry/rerecord possible | One additional tap | Recommended |
| Build a separate third provider configuration for voice instructions | Fully independent control | Settings complexity and key duplication | Rejected; reuse dictation configuration |

## Post-Research Product Decision

Product review approved the dictation key as the fast-path entry and expanded the safe target rule. A normal tap remains dictation. A long-press, recognized using the Android platform timeout rather than a fixed three-second delay, starts voice rewrite after haptic and visible `Speak an edit` feedback; the user may release once recording starts. If text is selected, that selection is the target. Otherwise Ownkey visibly invokes Select All and waits until a non-empty whole-field selection is confirmed before opening the microphone. The pinned rewrite-hub card remains for discovery and accessibility.

Recording-UX review also approved restoring the first action row as the shared recording surface. The row uses a real amplitude-driven waveform in its center, pause/resume and cancel immediately to its right, and a stop-square button in the normal mic position. The button keeps its current state language but no longer acts as an artificial waveform; success is brief and error returns to idle automatically after approximately five seconds.

This decision supersedes the initial recommendation that an existing explicit selection be mandatory and that the pinned card be the primary entry. It does not relax the early-capture, review-before-replace, provider-disclosure, or stale-target safeguards.

## Recommendation

Implement the platform-timed long-press on the dictation key as the primary accelerator, while keeping tap as normal dictation and retaining the pinned `Tell Ownkey what to change` card plus an explicit TalkBack action. Resolve the target before microphone access: use the current non-empty selection or visibly invoke Select All and confirm a non-empty whole-field selection. Never derive a whole field from partial before/after-cursor text, and fail safely when Select All is unsupported or the field is empty.

Use one state-hoisted recording row for ordinary dictation and voice rewrite. Drive its centered rolling waveform only from measured amplitude; keep pause/resume and cancel beside it; use the trailing mic position for a stop square. Preserve the recognizable button states, but emit explicit terminal outcomes and reset error presentation after approximately five seconds without requiring input.

Then capture the confirmed target, transcribe the spoken instruction without committing it, send target plus instruction through the configured rewrite provider, show the result for review, and verify target integrity again before replacement. Use Windows' terse state vocabulary and early-capture discipline, but retain Android's explicit preview and a mutually exclusive audio-session boundary.

## Fold-Forward Candidates

| Finding | Target Artifact | Proposed Change |
| --- | --- | --- |
| Voice must be explicit and coexist with presets | `spec.md` | Platform-timed dictation-key long-press plus pinned card and TalkBack action |
| No-selection rewriting needs a visible, exact scope | `spec.md` | Invoke Select All, confirm the resulting whole-field selection, and never infer from partial text |
| Recording action and input feedback should not compete | `spec.md` | Shared first-row meter plus pause/cancel; trailing mic becomes stop |
| Current button waveform is artificial | `spec.md` | Drive a short history only from measured amplitude; silence/mock remains at baseline |
| Error presentation is currently durable | `spec.md` | Explicit terminal outcomes and session-safe five-second error-to-idle reset |
| Two-provider pipeline | `spec.md` | First-use disclosure, provider labels, distinct preflight errors |
| Early capture plus stale-target risk | `spec.md` | Immutable target snapshot and pre-commit integrity verification |
| Dictation currently commits transcript | `spec.md` | Require a transcription-only operation and shared audio-session coordination |
| Android review is safer than Windows auto-replace | `spec.md` | Mandatory result preview and `Replace` action |
| Expanded UI is over-stretched | `spec.md` | Centered maximum-width recording/result surfaces |
| Provider/content privacy constraints | `spec.md` | No persistence or content-bearing logs; temporary audio cleanup |

## Open Questions

- Does incognito mode permit explicit provider-backed AI actions or disable them by default?
- Should a persistent custom undo chip be part of the first delivery?
- Which host editors reliably support Select All and promptly expose the resulting selection to an IME?
- Can the current quick-action gesture pipeline distinguish tap and platform-timed long-press without double firing across touch and accessibility configurations?
- What amplitude noise floor and perceptual curve distinguish silence, quiet speech, and normal speech across the supported device matrix?
- How many history bars can the narrowest smartbar retain while timer, pause/resume, cancel, and stop maintain 48 dp targets?
- Which host editors reliably preserve and re-report selected content throughout the longer voice flow?
- Should orchestration live in an expanded rewrite manager or a dedicated voice-rewrite session manager?

## Confidence

High on the Windows behavior and product-selected UX direction because implementation evidence and product review now agree. High on the current Android recording-state diagnosis because the visible and unwired components plus manager/recorder source agree. Medium on gesture arbitration, Select All compatibility, amplitude calibration, responsive composition, and selection/lifecycle behavior until a focused device/emulator prototype exercises representative editors and microphones.
