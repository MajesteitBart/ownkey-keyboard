---
id: T-002
name: Working autocorrect quick toggle
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T18:52:43Z
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

- [x] The quick action switches autocorrect off and on and shows its current state.
- [x] With autocorrect off, suggestions still show and can be tapped (AC-007).
- [x] The typing settings screen reflects the same state.
- [x] Unit test for the toggle handler.

## Traceability

- Story: US-004
- Acceptance criteria: AC-007

## Technical Notes

- Shares `KeyboardManager.kt` with T-003. Merge one before starting the other.
- T-006 later maps this switch to the Off strength level. Keep the pref key so that migration stays simple.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Toggle wired to `highCertaintyAutocorrectEnabled` through a new `KeyboardState.isAutocorrectEnabled` flag (bit 0x40000), synced from the pref flow and on each editor start. Icon switches between FontDownload and FontDownloadOff; toasts "Autocorrect is on" / "Autocorrect is off. Suggestions still show." (EN in `strings.xml`, NL in `ownkey.xml`). Settings had no autocorrect switch at all; added one at the top of Corrections, and app profiles grey out when it is off. `getAutoCommitCandidate()` returns nothing while off. `KeyboardStateAutocorrectFlagTest` (3 tests) passes; all 465 `ime` unit tests pass. Emulator (API 35, x86_64, debug build): toggled from the quick action both ways with toast and icon change, settings switch followed live, switching in settings updated the keyboard icon, and with autocorrect off typing `hel` still showed suggestions.

- 2026-09-24: Task created. The independent review confirmed the pref already exists and the policy honors it.
