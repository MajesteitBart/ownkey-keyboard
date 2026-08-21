---
id: T-020
name: Gate cloud AI in incognito mode
status: done
workstream: WS-C
created: 2026-08-04T17:01:03Z
updated: 2026-08-04T22:39:22Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-003]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-015
acceptance_criteria_ids: [AC-035, AC-036]
---

# Task: Gate cloud AI in incognito mode

## Description

Implement the single cloud-AI availability policy required by D-007 and enforce it at all three entry points, so an incognito editor session disables dictation, preset rewrite, and voice rewrite before any content is read, the microphone opens, or a provider request is sent.

## Acceptance Criteria

- [x] One shared availability policy derives cloud-AI availability from the active editor session's incognito flag and secure-field state and returns a typed reason; no caller re-derives the rule.
- [x] Dictation entry, preset rewrite entry, and the voice-rewrite preflight all consult the policy and refuse before reading selected content, acquiring the audio lease, or constructing a provider request.
- [x] Forced incognito, host-app dynamic incognito via `flagNoPersonalizedLearning`, and the user's smartbar toggle all produce the same block with a distinct incognito reason that cannot be confused with a permission, configuration, busy-recorder, or target failure.
- [x] Leaving incognito restores availability for the same or a new editor session without app restart, and no action blocked during incognito is queued, retried, or replayed.
- [x] The policy exposes availability as observable state so blocked controls can render a disabled treatment rather than failing on invocation.
- [x] No content is read, hashed, logged, or captured for AI use while the policy reports unavailable.
- [x] Unit tests cover each incognito source, each entry point, secure-field interaction, session transitions in and out of incognito, and the absence of microphone or provider side effects.

## Traceability

- Story: US-015, with US-009 and US-010.
- Acceptance criteria: AC-035 and AC-036; closes NC-001 through D-007.

## Technical Notes

`KeyboardState.isIncognitoMode` and `EditorInstance`'s per-session resolution already exist and should be consumed, not duplicated. Neither the dictation nor the rewrite package references incognito or secure fields today, so both gates are new call-site work. Keep this task domain-only: the disabled-state presentation belongs to T-014, and the incognito toast and `FORCE_ON` preference copy belong to T-016. Incognito is an availability policy, not a secure-field equivalent; keep the two reasons distinct.

## Definition of Done

- [x] Shared availability policy implemented.
- [x] All three entry points enforced.
- [x] Session transition behavior verified.
- [x] Unit tests pass with no microphone or network side effects.

## Evidence Log

- 2026-08-04T22:39:22Z: Implemented one observable typed cloud-AI availability policy, wired dictation and preset rewrite gates with active-session cancellation/provider rechecks, and added 5 passing incognito/secure/no-side-effect tests; full :app:testDebugUnitTest and git diff --check passed.

- 2026-08-04T22:22:49Z: Beginning dependency-safe WS-C cloud-AI availability implementation

- 2026-08-04T21:37:44Z: T-003 activation gate passed

- 2026-08-04: Task created after D-007 resolved NC-001 to disable all cloud AI in incognito mode.
