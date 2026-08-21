---
id: T-013
name: Wire shared recording row and stop action
status: done
workstream: WS-D
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T12:49:44Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-006, T-007, T-009, T-012]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-013
acceptance_criteria_ids: [AC-012, AC-028, AC-029, AC-030, AC-031, AC-032, AC-033, AC-034]
---

# Task: Wire shared recording row and stop action

## Description

Refactor and wire the first smartbar action row as the shared ordinary-dictation/voice-rewrite recording surface, rendering measured history in the center, pause/resume and cancel to its right, and a trailing orange stop-square action with explicit processing/success/error transitions.

## Acceptance Criteria

- [x] Entering either recording mode replaces the normal first-row center content with elapsed time, measured waveform, pause/resume, and cancel while preserving the sticky action position.
- [x] While recording or paused, the sticky voice action is an orange `Stop recording` button with a solid square and contains no waveform bars.
- [x] The waveform renders only T-007 history, visibly distinguishes silence/speech, settles on pause/mock input, and follows reduced-motion output without autonomous animation.
- [x] Pause/resume updates the icon, semantics, timer, capture, and level state; cancel discards audio; stop begins processing exactly once.
- [x] Processing replaces the meter with labelled progress, keeps a separate cancel action, and uses the approved mic-button processing treatment.
- [x] Explicit success and error outcomes render their existing treatments for the approved durations, allow immediate retry, and cannot be reset by an older session timer.
- [x] Timer/status updates do not trigger excessive recomposition of the keyboard body or repetitive accessibility announcements.
- [x] Focused renderer/state tests cover recording, pause, processing, success, error, retry, cancellation, and disposal for both modes.

## Traceability

- Story: US-012, US-013, and US-014.
- Acceptance criteria: AC-012, AC-028, AC-029, AC-030, AC-031, AC-032, AC-033, AC-034.

## Technical Notes

Reuse/refactor `DictationRecordingBar` rather than wiring it unchanged: its existing hit boxes are under 48 dp and its current Canvas uses one level with a fixed profile. Remove the visible sine-bar path from `DictationMicPill` when active. Preserve idle mic styling and normal dictation semantics.

## Definition of Done

- [x] Shared row integrated for both modes.
- [x] Stop/action and measured-meter separation verified.
- [x] State/accessibility tests pass.
- [x] Ordinary dictation regression passes.

## Evidence Log

- 2026-08-05T12:49:44Z: Replaced the unwired DictationRecordingBar with a shared state-hoisted recording row driven by one pure model (voiceRecordingRowState) and the probe-approved RecordingRowLayoutPolicy: elapsed timer, centred measured-amplitude waveform, 48 dp pause/resume and cancel, and an orange solid-square Stop action in the sticky dictation-key slot for both modes. Added AudioLevelHistorySampler as the single 20 Hz poller feeding the T-007 reducer, removed the per-manager polling, and deleted the clock-driven sine bars from the mic pill. Processing swaps the meter for labelled progress, keeps a separate cancel, and shows the progress treatment in the trailing slot. The waveform is excluded from accessibility; a polite live region announces status and target scope once per change while the timer stays silent. Evidence: 225 app debug unit tests passed (0 failures), including 14 new row-state, width-policy and sampler cases; :app:compileDebugKotlin passed with no new warnings in the touched packages.

- 2026-08-05T12:21:58Z: Wiring the shared measured-amplitude recording row and stop action

- 2026-08-05T12:18:46Z: T-006, T-007, T-009 done and T-012 implemented

- 2026-08-04: Task created during delivery decomposition.
