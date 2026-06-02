---
id: T-014
name: Make accepted corrections reuse existing spacing like mainstream keyboards
status: review
created: 2026-03-28T16:45:00Z
updated: 2026-03-28T17:05:00Z
linear_issue_id:
github_issue:
github_pr: https://github.com/MajesteitBart/ownkey-keyboard/pull/7
depends_on: [T-003, T-004]
conflicts_with: []
parallel: true
priority: medium
estimate: S
workstream: WS-2
---

# Task: Make accepted corrections reuse existing spacing like mainstream keyboards

## Description
Prevent correction/suggestion acceptance from leaving the cursor in front of an already-existing space while still preserving punctuation-friendly phantom spacing when no literal separator exists yet.

## Acceptance Criteria
- [x] Accepting a suggestion at the live cursor keeps pending separator behavior for punctuation-friendly continuation.
- [x] Accepting a correction before an existing trailing space reuses that literal space instead of arming a second separator path.
- [x] Focused tests cover accepted-suggestion spacing decisions and the affected layout regression test path remains green.

## Technical Notes
Keep candidate-revert tracking alive even when phantom spacing is intentionally disabled for an accepted correction that already has a literal separator.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-03-28: Task created and completed on PR #7.
- 2026-03-28: Root cause confirmed in `EditorInstance.commitCompletion()`: accepted suggestions always armed phantom spacing, even when correcting a word that already had a literal trailing space, leaving the cursor before that space and making the next separator tap create doubles.
- 2026-03-28: Added `AcceptedSuggestionSpacingPolicy` and wired `commitCompletion()` + `finalizeComposingText()` to reuse an immediate trailing literal space by advancing the cursor past it while disabling phantom separator insertion for that case.
- 2026-03-28: Preserved backspace-revert tracking by letting `PhantomSpaceState` retain the accepted candidate even when separator insertion is disabled.
- 2026-03-28: Added `AcceptedSuggestionSpacingPolicyTest` coverage for live-cursor acceptance, trailing-space reuse, and punctuation-following acceptance.
- 2026-03-28: Verified with `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` (PASS).
