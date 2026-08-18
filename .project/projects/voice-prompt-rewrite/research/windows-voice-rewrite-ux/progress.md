---
type: research_progress
project: voice-prompt-rewrite
slug: windows-voice-rewrite-ux
created: 2026-08-04T08:34:03Z
updated: 2026-08-04T11:55:20Z
---

# Progress: Windows Voice-Instruction Rewrite UX

## 2026-08-04T08:34:03Z

- Opened the Delano research intake for `voice-prompt-rewrite`.
- Primary question: How should Ownkey Android combine selected text with a spoken rewrite instruction, using the Windows experience as evidence while fitting Android IME constraints across phone, tablet, success, cancellation, and failure states?
- Initial Delano validation passed.

## 2026-08-04T08:43:36Z

- Inspected repository operating context, current project contracts, and the user-supplied Android compact/expanded design baseline.
- Inspected Android rewrite target capture, panel states, result replacement, dictation recording/transcription, editor lifecycle, and secure-field signals.
- Inspected the public Ownkey Windows README, selected-text rewrite pipeline, settings model, overlay state contract, overlay design spec, and README UI assets.
- Inspected the public Ownkey website source and Windows listening-pill mockup.
- Attempted shared live-browser inspection of `https://ownkey.bvdm.ai/`; navigation succeeded but repeated snapshot calls failed. Recorded website conclusions from public source/assets instead of claiming live-flow evidence.
- Compared entry, target capture, recording, processing, result, cancellation, provider disclosure, accessibility, and compact/expanded layout options.
- Folded durable findings into `.project/projects/voice-prompt-rewrite/spec.md`.

## 2026-08-04T08:51:09Z

- Ran final Delano validation after the research fold-forward; it completed with 0 errors and 0 warnings.
- Confirmed the project remains discovery-only: spec `planned`, plan `planned`, 0 workstreams, and 0 tasks.
- Confirmed the new project artifacts contain no trailing whitespace or unresolved scaffold placeholders in the drafted spec/research files.

## 2026-08-04T11:33:39Z

- Recorded the product decision to use a platform-timed long-press on the dictation key as the primary voice-rewrite accelerator while preserving normal tap-to-dictate behavior.
- Replaced the existing-selection-only rule with a precise target contract: use selected text when present; otherwise visibly invoke Select All and wait for a confirmed non-empty whole-field selection before microphone or provider work begins.
- Retained the pinned rewrite-hub card and added an explicit TalkBack action as discoverable and accessible alternatives to the gesture.
- Kept recording active after the long-press is recognized and the finger is released; rejected both a fixed three-second delay and continuous hold-to-talk.
- Reconciled the spec, research findings, research task plan, and decision log. The project remains planned and prototype-gated; no implementation tasks were created.

## 2026-08-04T11:40:23Z

- Ran Delano validation after the product-decision update; it completed with 0 errors and 0 warnings.
- Confirmed `voice-prompt-rewrite` remains at spec `planned`, plan `planned`, 0 workstreams, and 0 tasks.

## 2026-08-04T11:49:14Z

- Recorded the follow-up product decision to restore the first action row as the shared ordinary-dictation/voice-rewrite recording surface.
- Specified the approved hierarchy: elapsed time, real amplitude-driven center waveform, pause/resume and cancel immediately to its right, and an orange stop-square action in the normal mic position.
- Preserved the useful idle, triggered/recording, processing, success, and error button treatments while removing the artificial in-button waveform from active recording.
- Specified explicit terminal outcomes, approximately 900 ms success feedback, and a session-safe five-second error-to-idle reset with immediate retry.
- Inspected the Android source and recorded that the original visible pill used a sine animation, the recording-row reference contained the preferred composition, live amplitude was available, and the durable error/inferred-success model required correction. The shipped implementation now lives in `VoiceRecordingRow` and `VoiceRecordingRowModel`.

## 2026-08-04T11:55:20Z

- Ran Delano validation after the recording-UX refinement; it completed with 0 errors and 0 warnings.
- Confirmed the project remains at spec `planned`, plan `planned`, 0 workstreams, and 0 tasks.

## Validation Evidence

- Initial project creation: Delano project command completed successfully.
- Research intake creation: Delano validation passed.
- Final artifact validation: `delano validate` passed with 0 errors and 0 warnings.
- Product-decision update validation: `delano validate` passed with 0 errors and 0 warnings.
- Recording-UX refinement validation: `delano validate` passed with 0 errors and 0 warnings.
- Contract state check: `delano project show voice-prompt-rewrite --json` reported planned spec/plan with 0 workstreams and 0 tasks.

## Handoff Summary

- Research question answered with a high-confidence UX direction and explicit Android prototype gaps.
- Approved direction: tap dictates; platform-timed hold starts voice rewrite; selected text is used when present, otherwise Ownkey visibly selects the whole field; both voice modes use the first-row measured waveform with pause/cancel and a trailing stop square; terminal feedback is explicit and transient; recording uses a transcription-only audio path; the result is reviewed before replacement; target integrity and provider disclosure remain mandatory.
- Canonical fold-forward target: `spec.md`.
- Remaining blockers are prototype evidence for gesture arbitration, Select All compatibility, editor lifecycle, waveform calibration/responsive layout, terminal-state timing, and the two unresolved policy decisions documented in the spec; no implementation plan or tasks were authorized.
