---
id: T-011
name: Verify target and replace safely
status: done
workstream: WS-C
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:53:56Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-008, T-010]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-003
acceptance_criteria_ids: [AC-020, AC-021, AC-042]
---

# Task: Verify target and replace safely

## Description

Implement reviewed replacement using the immutable target snapshot, revalidating editor/session/package/scope/range/source content immediately before one replacement operation and providing copy/close fallback on mismatch.

## Acceptance Criteria

- [x] Replacement is unavailable before a non-empty reviewed result exists and is invoked only by explicit `Replace` intent.
- [x] The commit path verifies editor/input-session identity, host package, scope, normalized range, and original source content immediately before mutation.
- [x] A valid target is selected and replaced as one editor operation where supported, with one explicit success outcome.
- [x] Any mismatch, selection failure, field/package change, invalid editor, or commit failure leaves source text unchanged and exposes `Copy result` plus `Close` instead of automatic insertion.
- [x] Copy fallback uses the existing clipboard abstraction and does not retain the result after session disposal.
- [x] Fake-editor tests prove exact selected/whole-field replacement, every mismatch branch, no wrong-field insertion, and no success event on failed commit.

## Traceability

- Story: US-003 and US-006.
- Acceptance criteria: AC-020, AC-021, and AC-042, with the review contract from AC-015 through AC-019.

## Technical Notes

Do not rely only on reselecting the stored range. Confirm the source content still matches. Keep current preset behavior outside this task unless a shared verification primitive can be adopted without changing its approved semantics.

## Definition of Done

- [x] Verification and replacement implemented.
- [x] Copy/close fallback implemented.
- [x] Exact-mutation tests pass.
- [x] Failure paths emit no false success.

## Evidence Log

- 2026-08-04T22:53:56Z: Implemented explicit reviewed replacement with immediate session/package/field/range/source/integrity verification, one commit operation, typed selection/commit failures, content-free success, and clipboard copy fallback that is cleared on disposal; 6 replacement tests and the full app unit suite passed.

- 2026-08-04T22:50:00Z: Beginning dependency-safe WS-C target verification and replacement implementation

- 2026-08-04T22:50:00Z: Dependencies T-008 and T-010 are done

- 2026-08-04: Task created during delivery decomposition.
