---
title: T-012 smart backspace recovery for corrected words
timestamp: 2026-03-28T17:40:00Z
status: review
task: T-012
stream: ws-2-autocorrect-control-and-recovery-ux
---

# Progress Update

## Why this slice was next
- The predictive-typing planning project still has planning/admin work open, but the highest-value dependency-safe user behavior in the implementation stream was `typing-speed-core` T-012.
- T-004 already shipped the shared autocorrect history model and explicit undo path, so smart backspace could now be added without inventing new persistence or broad editor state.
- This directly improves trust in real typing by making the most common "keyboard changed my word" recovery gesture behave like mainstream mobile keyboards.

## Completed
- Reused `AutocorrectUndoTracker` for a new backspace-specific lookup path instead of introducing a second recovery history.
- Added a narrower restore contract for backspace than for explicit undo:
  - restore when the cursor is still at the end of the corrected current word
  - restore when the cursor is directly after that corrected token with a trailing separator
  - do not restore for active selections or when the cursor has moved into the middle of the word
- Updated `KeyboardManager.handleBackwardDelete()` to restore the tracked autocorrect before normal deletion when the backspace context matches.
- Refactored the actual replacement/metrics/provider-notification flow into a shared helper so backspace restore and one-tap undo stay behaviorally aligned.

## Behavioral Contract After This Change
- First backspace after a recent autocorrect can restore the original typed token instead of deleting a character from the corrected output.
- Once the cursor context no longer matches that immediate recovery window, backspace falls through to normal delete behavior.
- Manual selections and mid-word cursor edits keep standard deletion semantics.

## Verification
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.keyboard.AutocorrectUndoTrackerTest` (pass)
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (pass)
- `bash .claude/scripts/pm/validate.sh` (pass)

## Files Changed
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/AutocorrectUndoTracker.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/AutocorrectUndoTrackerTest.kt`
- `.project/projects/typing-speed-core/tasks/T-012-smart-backspace-for-corrected-words.md`

## Next Actions
- Push the branch head so PR #7 picks up the backspace recovery slice.
- Use device QA or CI artifact testing to confirm the restored-token cursor position feels right in real text fields, especially after trailing spaces and quick follow-up backspaces.
