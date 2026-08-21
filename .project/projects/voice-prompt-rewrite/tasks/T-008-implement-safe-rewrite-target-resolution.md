---
id: T-008
name: Implement safe rewrite target resolution
status: done
workstream: WS-C
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:39:22Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-003]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-011
acceptance_criteria_ids: [AC-003, AC-004, AC-007, AC-020, AC-021]
---

# Task: Implement safe rewrite target resolution

## Description

Create a testable voice-rewrite target resolver that uses a current non-empty selection or visibly requests Select All, waits for confirmed selection state, applies guards, and captures an immutable integrity snapshot without using preset previous-sentence fallback or partial surrounding-text inference.

## Acceptance Criteria

- [x] A valid existing selection returns an exact `selection` snapshot without issuing Select All.
- [x] No-selection flow invokes `performClipboardSelectAll()`, waits only for the bounded probe-approved confirmation, and returns an exact `whole-field` snapshot when a non-empty selection is reported.
- [x] Unsupported, timed-out, empty, raw/invalid, secure/password, and over-12,000-character targets return distinct typed failures before microphone or provider work.
- [x] The snapshot contains editor/input-session identity, host package, scope, normalized range, source text, and non-reversible integrity data without logging content.
- [x] Text-before/after-cursor and previous-sentence fallback cannot satisfy whole-field resolution.
- [x] Fake-editor tests cover synchronous/asynchronous confirmation, reversed/changed ranges, unsupported and truncated editors, secure fields, limits, and focus changes.

## Traceability

- Story: US-011 and US-010.
- Acceptance criteria: AC-003, AC-004, AC-007, AC-020, AC-021.

## Technical Notes

Keep preset target behavior unchanged. The voice resolver may wrap or add narrow APIs to `EditorInstance`, but it must not expose selected content to logs, telemetry, or project evidence.

## Definition of Done

- [x] Resolver and snapshot model implemented.
- [x] Typed failure mapping implemented.
- [x] Fake-editor test matrix passes.
- [x] Preset rewrite regression verified.

## Evidence Log

- 2026-08-04T22:39:22Z: Implemented exact selected/confirmed-Select-All snapshots with session/package/field identity, normalized range, Unicode limit, SHA-256 integrity, typed failures, and 8 passing resolver tests; full :app:testDebugUnitTest and git diff --check passed.

- 2026-08-04T22:22:49Z: Beginning dependency-safe WS-C target-resolution implementation

- 2026-08-04T21:37:43Z: T-003 activation gate passed

- 2026-08-04: Task created during delivery decomposition.
