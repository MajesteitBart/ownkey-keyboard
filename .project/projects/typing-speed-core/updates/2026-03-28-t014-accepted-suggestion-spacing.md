---
title: T-014 accepted suggestion spacing contract
timestamp: 2026-03-28T17:05:00Z
status: review
task: T-014
stream: ws-2-autocorrect-control-and-recovery-ux
---

# Progress Update

## Completed
- Diagnosed the real double-space path around accepted corrections instead of reverting the earlier punctuation-friendly phantom-space change.
- Added `AcceptedSuggestionSpacingPolicy` to distinguish between two contracts:
  - live acceptance at the cursor with no literal separator yet -> keep phantom spacing active
  - correction acceptance with an immediate trailing literal space -> reuse that separator and advance the cursor past it
- Extended `AbstractEditorInstance.finalizeComposingText()` with an optional cursor-advance parameter so correction acceptance can preserve existing separator position in the same commit flow.
- Updated `EditorInstance.commitCompletion()` to apply the new spacing decision and to keep revert metadata while disabling separator insertion for reuse cases.
- Made `CharactersModifierLayoutTest` path-robust so the full local unit-test target can run cleanly from the Gradle module working directory.

## Behavioral Contract After This Change
- Tapping or auto-accepting a suggestion at the active cursor still supports punctuation-friendly follow-up without an inserted hard space.
- Accepting a correction for a word that already has a literal trailing space now reuses that space instead of leaving the cursor before it.
- The fix is scoped to accepted-suggestion completion flow. It does not globally normalize whitespace or rewrite unrelated spacing rules.

## Verification
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (pass)

## Files Changed
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AcceptedSuggestionSpacingPolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/AbstractEditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/editor/AcceptedSuggestionSpacingPolicyTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/CharactersModifierLayoutTest.kt`
- `.project/projects/typing-speed-core/tasks/T-014-mainstream-spacing-for-accepted-corrections.md`

## Next Actions
- Push the branch head so PR #7 picks up the spacing fix.
- If device QA still finds awkward mid-sentence correction cases around punctuation or multi-space text, capture those as a follow-up rather than broadening this contract silently.
