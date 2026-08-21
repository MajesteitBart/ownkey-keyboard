---
id: T-007
name: Build real-amplitude history reducer
status: done
workstream: WS-B
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:03:45Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-003]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-012
acceptance_criteria_ids: [AC-029, AC-030, AC-034]
---

# Task: Build real-amplitude history reducer

## Description

Create a pure, configurable reducer that converts measured recorder amplitude into the bounded rolling bar history used by the shared recording row, including silence, perceptual scaling, attack/release, pause, mock input, and reduced-motion behavior.

## Acceptance Criteria

- [x] Active bar values originate only from supplied measured amplitude; time, random, sine, and prerecorded sources cannot create activity by themselves.
- [x] The reducer maintains the probe-approved 16-20-sample bounded history and applies documented noise-floor, perceptual, attack, release, and clamping rules.
- [x] Silence and mock/no-input settle to a uniform minimum baseline; quiet and ordinary speech remain visibly distinguishable without full-row clipping.
- [x] Pause produces the dim low baseline and stops history advancement; resume starts from a safe baseline without replaying stale peaks.
- [x] Reduced-motion output preserves measured level at the approved lower update frequency without scrolling or interpolated travel.
- [x] Deterministic silence, step, pulse, saturation, pause/resume, and reduced-motion unit tests pass.

## Traceability

- Story: US-012 and US-013.
- Acceptance criteria: AC-029, AC-030, AC-034.

## Technical Notes

Keep the reducer independent of Compose and `MediaRecorder` so it can be fed fixed sequences. Calibrated constants must be named/documented and traceable to T-001 evidence rather than tuned through an autonomous visual loop.

## Definition of Done

- [x] Pure reducer implemented.
- [x] Calibration constants documented.
- [x] Deterministic tests pass.
- [x] No clock-only waveform path remains in the new component.

## Evidence Log

- 2026-08-04T22:03:45Z: Implemented the pure 18-sample measured-amplitude reducer with documented calibration and deterministic silence, speech, saturation, pause/resume, reduced-motion, and mock-input coverage; full :app:testDebugUnitTest passed.

- 2026-08-04T21:55:19Z: Dependency T-003 is complete; beginning pure measured-amplitude reducer implementation

- 2026-08-04T21:37:43Z: T-003 activation gate passed

- 2026-08-04: Task created during delivery decomposition.
