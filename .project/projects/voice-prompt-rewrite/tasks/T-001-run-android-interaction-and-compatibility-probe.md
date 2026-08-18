---
id: T-001
name: Run Android interaction and compatibility probe
status: done
workstream: WS-A
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T21:34:51Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: L
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-004, AC-020, AC-025, AC-026, AC-028, AC-029, AC-033, AC-034]
---

# Task: Run Android interaction and compatibility probe

## Description

Build and execute a time-boxed prototype that retires the material uncertainties in tap/long-press arbitration, visible Select All and selection retention, editor/session invalidation, shared recording-row layout, real-amplitude calibration, reduced motion, and TalkBack semantics before production implementation begins.

## Acceptance Criteria

- [x] The probe records tap, platform-timed long-press, release, slide/cancel, configurable touch-and-hold delay, and accessibility-action outcomes, demonstrating that exactly one mode starts per gesture. Real TalkBack execution remains a T-018 release gate under D-012.
- [x] The editor matrix records existing selection, asynchronous Select All, empty/unsupported/raw/secure fields, selection retention, and field/package change behavior across observed emulator surfaces and deterministic native, Compose, messaging, browser/WebView, raw, and problematic profiles.
- [x] The simulated device/layout matrix records silence, quiet speech, ordinary speech, pause, reduced motion, phone portrait, short landscape, tablet, and split-keyboard behavior with 48 dp controls; real-device calibration remains in T-018.
- [x] Lifecycle checks cover keyboard hide, input restart, field switch, panel disposal, and IME teardown, with no recorder/provider job surviving the invalidated session.
- [x] The probe recommends readable language text during dictation for every `SpaceBarMode`, restored after the session and absent during voice rewrite.
- [x] Findings identify the viable manager/session boundaries, test seams, amplitude tuning ranges, bounded selection-confirmation timeout, and unsupported cases that must fail safely.
- [x] Probe artifacts contain no user content, secrets, raw prompts, provider response bodies, or machine-specific absolute paths.

## Traceability

- Story: US-001, with US-007, US-008, US-011, US-012, and US-013 also exercised.
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-020, AC-025, AC-026, AC-028, AC-029, AC-033, AC-034.

## Technical Notes

Use the Delano prototype workflow when executing this task. Prototype code must be isolated or clearly marked and must not silently become production behavior. Exercise `QuickActionButton`, `EditorInstance.performClipboardSelectAll()`, the recording-row reference later replaced by `VoiceRecordingRow`, `VoxtralDictationManager.audioLevelFlow`, and the IME lifecycle callbacks. The goal is evidence and architecture decisions, not feature completion.

## Definition of Done

- [x] Prototype and matrix execution complete under D-012's M0 simulated-device authorization.
- [x] Findings and evidence recorded in the project.
- [x] Product/architecture implications are explicit.
- [x] `delano validate` passes.

## Evidence Log

- 2026-08-04T21:34:51Z: 9 targeted contract tests passed; API 35 emulator Select All and selection retention passed across Chrome, Messages, and HTML textarea; architecture, matrix, screenshots, and safe-failure findings recorded under probe/.

- 2026-08-04T21:29:30Z: Complete remaining simulated editor, accessibility, microphone-signal, and layout matrices

- 2026-08-04T21:29:30Z: User approved simulated-device evidence while away from physical hardware

- 2026-08-04T19:25:57Z: Needs representative physical-microphone, TalkBack, Compose/raw/problematic-editor, and rendered tablet/split evidence before the probe gate can pass

- 2026-08-04T19:25:57Z: First bounded probe pass: 7 contract tests passed; API 35 emulator Select All and Rewrite-panel retention passed in Chrome omnibox, Google Messages, and HTML textarea; secure-field baseline gap confirmed; evidence recorded under probe/.

- 2026-08-04T19:06:31Z: Begin bounded Android interaction and compatibility probe

- 2026-08-04: Task created during probe-gated delivery decomposition.
