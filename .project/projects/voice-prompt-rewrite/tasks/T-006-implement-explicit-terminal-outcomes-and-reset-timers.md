---
id: T-006
name: Implement explicit terminal outcomes and reset timers
status: done
workstream: WS-B
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:12:26Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-005]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-014
acceptance_criteria_ids: [AC-031, AC-032, AC-033]
---

# Task: Implement explicit terminal outcomes and reset timers

## Description

Replace inferred success and durable action-button error with explicit, session-scoped terminal outcomes and controllable timers shared by dictation and voice-rewrite presentation.

## Acceptance Criteria

- [x] Success is emitted only after the intended operation succeeds and displays for approximately 900 ms before idle.
- [x] Error includes a content-free typed reason, is immediately retryable, displays for five seconds within the specified test tolerance, and returns to idle without user input.
- [x] Cancellation, empty/no-speech, and generic processing-to-idle transitions cannot produce a false success event.
- [x] A new session or newer outcome cancels the older reset timer, and keyboard/component disposal prevents a stale timer from resetting new state.
- [x] Error is announced once while persistent rewrite recovery state can remain outside the action button.
- [x] Deterministic virtual-clock tests cover success, error, immediate retry, newer outcomes, cancellation, and disposal without wall-clock sleeps.

## Traceability

- Story: US-014 and US-003.
- Acceptance criteria: AC-031, AC-032, AC-033.

## Technical Notes

Keep domain failure/recovery information separate from the transient mic-button acknowledgement. Avoid storing raw provider messages when a content-free error category and localized copy key are sufficient.

## Definition of Done

- [x] Explicit outcome model implemented.
- [x] Session-safe timers implemented.
- [x] Accessibility event behavior verified.
- [x] Deterministic tests pass.

## Evidence Log

- 2026-08-04T22:12:26Z: Implemented shared typed VoiceActionFeedbackController, wired explicit dictation success/error outcomes into the mic pill, and passed deterministic virtual-clock coverage for 900 ms success, 5 s error, retry, replacement, cancellation, stale sessions, announcement IDs, and disposal; full :app:testDebugUnitTest passed.

- 2026-08-04T22:07:55Z: Beginning dependency-safe explicit terminal-outcome and timer implementation

- 2026-08-04T22:07:55Z: T-005 is done and the sole local dependency is satisfied

- 2026-08-04: Task created during delivery decomposition.
