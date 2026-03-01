---
timestamp: 2026-02-25T21:00:13Z
status: review
task: T-004
stream: ws-2-autocorrect-control-and-recovery-ux
---

# Progress Update

## Completed
- Added `AutocorrectUndoTracker` to keep only the latest autocorrect undo state in memory and locate the corrected token near cursor for one-tap restoration.
- Integrated tracker usage in `KeyboardManager.commitCandidate` for high-certainty auto-commits and wired `KeyCode.UNDO` to restore the original token with a single interaction before defaulting to editor undo behavior.
- Preserved correction-history interoperability by clearing pending undo state when backspace reverts the same candidate and continuing provider revert notifications.
- Added unit tests in `AutocorrectUndoTrackerTest` for cursor-near-token restoration, current-word restoration, mismatch handling, and invalid-history guards.

## Verification
- `./gradlew :app:compileDebugKotlin` (pass)
- `./gradlew :app:testDebugUnitTest` (pass)

## Privacy Notes
- One-tap undo state is ephemeral and in-memory only.
- No typed content is logged, persisted, or exported as part of this task.

## Next Actions
- Move T-004 to done after review sign-off.
