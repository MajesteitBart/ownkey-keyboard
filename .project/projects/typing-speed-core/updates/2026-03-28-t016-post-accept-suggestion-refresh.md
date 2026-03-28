---
title: T-016 immediate follow-up suggestions after accepted suggestions
timestamp: 2026-03-28T18:35:00Z
status: review
task: T-016
stream: ws-2-autocorrect-control-and-recovery-ux
---

# Progress Update

## Completed
- Diagnosed the post-accept suggestion gap without broadening the earlier spacing/recovery fixes.
- Confirmed the real contract break: accepted suggestions often leave `phantomSpace` active, but `KeyboardManager.resetSuggestions()` was still refreshing from the literal editor snapshot, which has no real boundary yet.
- Added `PendingSeparatorSuggestionContent` to synthesize a virtual boundary only for suggestion refresh when a pending separator exists.
- Updated `KeyboardManager.resetSuggestions()` to send the derived context to `nlpManager.suggest()` instead of forcing a generic extra refresh.
- Added focused unit coverage for pending-separator refresh behavior and re-ran full local validation.

## Behavioral Contract After This Change
- After tapping a suggestion that leaves a pending separator, OwnKey now refreshes the row as if the accepted word is already followed by a boundary, so next-word suggestions can appear immediately.
- The change is scoped to the pending-separator state. Literal editor content remains unchanged, and unrelated suggestion modes still refresh from the real editor snapshot.
- Existing trust work remains intact because this slice only changes the suggestion-refresh context, not spacing insertion, undo/backspace recovery, or reverted-autocorrect suppression rules.

## Verification
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.PendingSeparatorSuggestionContentTest` (pass)
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (pass)
- `bash .claude/scripts/pm/validate.sh` (pass)

## Files Changed
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/PendingSeparatorSuggestionContent.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor/PendingSeparatorSuggestionContentTest.kt`
- `.project/projects/typing-speed-core/tasks/T-016-refresh-next-word-suggestions-after-accepted-suggestions.md`

## Next Actions
- Push the branch head so PR #7 and CI pick up the post-accept refresh fix.
- Device QA should confirm the row now repopulates immediately after tapped suggestions in common chat/text fields, especially around punctuation continuation after the pending separator state.
