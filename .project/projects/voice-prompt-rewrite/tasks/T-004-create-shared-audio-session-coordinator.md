---
id: T-004
name: Create shared audio-session coordinator
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
story_id: US-009
acceptance_criteria_ids: [AC-013, AC-022, AC-023]
---

# Task: Create shared audio-session coordinator

## Description

Introduce an injectable single-recorder session contract shared by ordinary dictation and voice rewrite, including owner/mode, start, pause/resume, stop, cancel, elapsed time, audio levels, and lifecycle invalidation.

## Acceptance Criteria

- [x] Exactly one owner can hold the recorder; a second dictation or voice-rewrite request receives the specified non-destructive busy result.
- [x] Session state exposes mode, phase, elapsed-time inputs, pause state, measured level, and a generation/session identifier without exposing captured content.
- [x] Stop and cancel are idempotent and stale callbacks cannot change a newer session.
- [x] Field switch, keyboard hide, input restart, secure transition, and IME teardown can invalidate the lease and delete temporary audio.
- [x] Existing ordinary dictation routing, including external-IME fallback, remains behaviorally unchanged when the shared internal session is idle.
- [x] Coroutines/Turbine tests cover ownership, mutual exclusion, pause/resume, idempotence, invalidation, and stale completion.

## Traceability

- Story: US-009.
- Acceptance criteria: AC-013, AC-022, AC-023.

## Technical Notes

Keep Android recorder work off typing-critical paths. Prefer narrow interfaces and injected clocks/dependencies so subsequent managers and tests do not require a live application singleton. Do not log audio bytes, transcripts, target text, or content-derived identifiers.

## Definition of Done

- [x] Coordinator and contracts implemented.
- [x] Ordinary dictation compatibility verified.
- [x] Unit tests pass.
- [x] Lifecycle cleanup documented.

## Evidence Log

- 2026-08-04T22:03:45Z: Implemented generation-scoped AudioSessionCoordinator, wired ordinary dictation and IME lifecycle invalidation, and passed :app:compileDebugKotlin plus the full :app:testDebugUnitTest suite including AudioSessionCoordinatorTest.

- 2026-08-04T21:55:19Z: Dependency T-003 is complete; beginning shared audio-session coordinator implementation

- 2026-08-04T21:37:43Z: T-003 activation gate passed

- 2026-08-04: Task created during delivery decomposition.
