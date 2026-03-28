---
id: T-012
name: Add smart backspace recovery for corrected words
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-03-28T17:40:00Z
linear_issue_id:
github_issue:
github_pr: 7
depends_on: [T-004]
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Add smart backspace recovery for corrected words

## Description
Make backspace context-aware so recently autocorrected words can be restored quickly.

## Acceptance Criteria
- [x] Backspace restores corrected token form in expected contexts.
- [x] Standard backspace behavior remains unchanged elsewhere.

## Technical Notes
Share correction history model with one-tap undo behavior, but keep the backspace path narrower than the explicit undo action: only restore the latest autocorrect when the cursor is still at the end of that corrected word or directly after its trailing separator.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-03-28: Chose T-012 as the next execution slice after T-014 because Delano already had the trust-policy and one-tap undo groundwork in place, and smart backspace is the next user-meaningful recovery behavior in WS-2.
- 2026-03-28: Reused `AutocorrectUndoTracker` state for backspace recovery instead of adding a second history model, keeping the behavior aligned with T-004 while avoiding a broader editor rewrite.
- 2026-03-28: Updated `KeyboardManager.handleBackwardDelete()` to restore the latest tracked autocorrect on normal character backspace before falling through to standard deletion.
- 2026-03-28: Narrowed the restore window so backspace recovery only fires when the cursor is still at the end of the corrected current word or directly after that token with a trailing separator, leaving mid-word edits and active selections on normal delete behavior.
- 2026-03-28: Added `AutocorrectUndoTrackerTest` coverage for trailing-space restore, current-word-end restore, mid-word no-restore, and active-selection no-restore.
- 2026-03-28: Verified with `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (PASS) and `bash .claude/scripts/pm/validate.sh` (PASS).
