---
id: T-009
name: Create voice-rewrite session preflight
status: done
workstream: WS-C
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:44:43Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-004, T-005, T-008, T-020]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-005
acceptance_criteria_ids: [AC-001, AC-003, AC-008, AC-009, AC-010, AC-011, AC-022, AC-023, AC-035]
---

# Task: Create voice-rewrite session preflight

## Description

Introduce the headless voice-rewrite session orchestrator and deterministic preflight order covering target resolution, secure/incognito/limit checks, recorder ownership, permission, both provider configurations, and versioned first-use disclosure before recording starts.

## Acceptance Criteria

- [x] Session state has mutually exclusive ready, targeting, disclosure, recording, paused, transcribing, rewriting, result, warning, error, success, and cancelled phases with a unique generation identifier.
- [x] Preflight runs in the spec order and returns the narrowest typed recovery without opening the microphone or sending a request when any prerequisite fails.
- [x] The incognito check consumes the T-020 availability policy and runs before target resolution, returning the distinct incognito reason rather than a permission, configuration, or target failure.
- [x] The disclosure state exposes configured audio and rewrite provider display names without keys, raw endpoint URLs, or content.
- [x] Acknowledged versioned disclosure proceeds without repeated interruption; changed disclosure version requires acknowledgement again.
- [x] The session acquires the shared audio lease once and reports `Finish dictation first`/equivalent busy state without starting a second recorder.
- [x] Cancellation or lifecycle invalidation from every pre-recording phase resets state and discards the target snapshot when it is no longer valid.
- [x] State-transition tests cover every preflight failure and prove that target, microphone, and provider side effects occur only in the approved order.

## Traceability

- Story: US-005, US-009, US-010, and US-015.
- Acceptance criteria: AC-001, AC-003, AC-008, AC-009, AC-010, AC-011, AC-022, AC-023, AC-035.

## Technical Notes

Keep UI copy keys outside domain state where practical; expose typed status/recovery values. Provider configuration stays in existing Settings -> AI paths. Do not add hosted routing or a third provider configuration.

## Definition of Done

- [x] Orchestrator/preflight state implemented.
- [x] Provider/disclosure data contract implemented.
- [x] Side-effect ordering tests pass.
- [x] No microphone/network work occurs on failed preflight.

## Evidence Log

- 2026-08-04T22:44:43Z: Implemented generation-scoped headless session preflight with 12 explicit phases, ordered availability/target/busy/permission/provider/disclosure gates, versioned provider-only disclosure, shared recorder lease acquisition, cancellation/invalidation cleanup, and 8 passing preflight tests; full 181-test app unit suite passed.

- 2026-08-04T22:39:32Z: Beginning dependency-safe WS-C preflight implementation

- 2026-08-04T22:39:32Z: All local dependencies T-004, T-005, T-008, and T-020 are done

- 2026-08-04: Task created during delivery decomposition.
