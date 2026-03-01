---
id: T-004
name: Add one-tap undo for latest autocorrect
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T21:00:13Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-003]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Add one-tap undo for latest autocorrect

## Description
Track latest autocorrect operation and expose immediate one-tap undo action.

## Acceptance Criteria
- [x] User can undo last autocorrect with one interaction.
- [x] Undo behavior restores original token reliably.

## Technical Notes
Should interoperate with smart backspace and correction history state.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Implemented one-tap undo tracking for latest high-certainty autocorrect using an in-memory `AutocorrectUndoTracker` and wired `KeyCode.UNDO` to restore the original token before falling back to editor undo.
- 2026-02-25: Kept correction-revert interoperability by clearing tracked autocorrect state when backspace reverts a candidate and by sending provider revert callbacks for one-tap undo restores.
- 2026-02-25: Added `AutocorrectUndoTrackerTest` coverage for token restoration lookup and invalid history guards.
- 2026-02-25: Verified with `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest` (PASS).
