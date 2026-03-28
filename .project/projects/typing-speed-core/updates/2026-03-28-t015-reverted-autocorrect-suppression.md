---
title: T-015 remember reverted autocorrect spellings
timestamp: 2026-03-28T17:16:03Z
status: review
task: T-015
stream: ws-2-autocorrect-control-and-recovery-ux
---

# Progress Update

## Why this slice was next
- The existing `typing-speed-core` backlog already had a later full `never-correct list` task, but the repo state showed a smaller and more immediate trust gap: OwnKey already tracked autocorrect undo/backspace restores, yet that explicit user feedback did not change future auto-commit behavior.
- This slice is dependency-safe because T-004 and T-012 already capture the exact original token and recovery event needed to act on a revert without new editor architecture.
- It materially improves trust in real typing by stopping the keyboard from repeating the same false autocorrect right after the user just corrected it.

## Completed
- Added persistent `NeverCorrectWords` storage under preferences with per-language scoping, deduping, and targeted removal support for later UI work.
- Extended `SuggestionProvider.notifySuggestionReverted()` so undo/backspace recovery can pass the restored original token to language providers.
- Updated `KeyboardManager` and `AutocorrectUndoTracker` so revert notifications carry the original typed word whenever the reverted candidate matches the tracked autocorrect.
- Wired `LatinLanguageProvider` to:
  - add reverted autocorrect spellings to the protected-word store
  - clear suggestion cache after a new protection entry is learned
  - suppress future auto-commit for protected inputs while still allowing the suggestion row to surface candidates normally
- Added focused tests for the new protected-word store, the policy-level auto-commit block, and original-token lookup from tracked autocorrect state.

## Behavioral Contract After This Change
- If OwnKey autocorrects a word and the user explicitly restores the original spelling via undo or smart backspace, that original spelling becomes protected for the active language.
- The next time the user types that same protected spelling, OwnKey can still suggest alternatives, but it will not auto-commit over the user’s chosen spelling.
- Protection is scoped by language so a reverted spelling in one language does not automatically suppress another language’s behavior.

## Verification
- `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (pass)
- `bash .claude/scripts/pm/validate.sh` (pass)

## Files Changed
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/AutocorrectUndoTracker.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpProviders.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/han/HanShapeBasedLanguageProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/HighCertaintyAutocorrectPolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/NeverCorrectWords.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiSuggestionProvider.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/AutocorrectUndoTrackerTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/HighCertaintyAutocorrectPolicyTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/NeverCorrectWordsTest.kt`
- `.project/projects/typing-speed-core/tasks/T-015-remember-reverted-autocorrect-spellings.md`

## Next Actions
- Push the branch head so PR #7 and CI pick up the new trust behavior.
- In a later UX slice, expose the protected-word list through a user-manageable screen so entries can be reviewed, added manually, or removed without waiting for another revert event.
