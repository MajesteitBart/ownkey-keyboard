---
id: T-002
name: Working autocorrect quick toggle
status: ready
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T11:31:43Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: [T-003]
parallel: false
priority: medium
estimate: S
story_id: US-004
acceptance_criteria_ids: [AC-007]
---

# Task: Working autocorrect quick toggle

## Description

`KeyboardManager.handleToggleAutocorrect()` shows a "not yet implemented" toast. Wire it to the existing `prefs.correction.highCertaintyAutocorrectEnabled` pref, which `HighCertaintyAutocorrectPolicy` already honors, and show the state in the quick action.

## Acceptance Criteria

- [ ] The quick action switches autocorrect off and on and shows its current state.
- [ ] With autocorrect off, suggestions still show and can be tapped (AC-007).
- [ ] The typing settings screen reflects the same state.
- [ ] Unit test for the toggle handler.

## Traceability

- Story: US-004
- Acceptance criteria: AC-007

## Technical Notes

- Shares `KeyboardManager.kt` with T-003. Merge one before starting the other.
- T-006 later maps this switch to the Off strength level. Keep the pref key so that migration stays simple.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created. The independent review confirmed the pref already exists and the policy honors it.
