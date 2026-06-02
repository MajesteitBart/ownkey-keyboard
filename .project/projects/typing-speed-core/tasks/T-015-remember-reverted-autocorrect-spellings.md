---
id: T-015
name: Remember reverted autocorrect spellings
status: review
created: 2026-03-28T17:16:03Z
updated: 2026-03-28T17:16:03Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-004, T-012]
conflicts_with: []
parallel: true
priority: high
estimate: S
workstream: WS-2
---

# Task: Remember reverted autocorrect spellings

## Description
When the user explicitly reverts an autocorrect, remember the original spelling per language and suppress future auto-commit for that spelling.

## Acceptance Criteria
- [x] Reverted autocorrect spellings are remembered per language.
- [x] Protected spellings still appear in suggestions when relevant, but they no longer auto-commit.

## Technical Notes
This is a trust-first bridge toward the broader never-correct list. Treat explicit user revert actions as strong negative feedback for future autocorrect, without adding broad new settings UI in the same slice.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-03-28: Task created after reviewing the current predictive-typing branch state and Delano backlog.
- 2026-03-28: Added persistent `NeverCorrectWords` storage in preferences and wired `LatinLanguageProvider` to suppress auto-commit when the current normalized input is protected for the active language.
- 2026-03-28: Extended autocorrect revert notifications to include the restored original token so explicit undo/backspace recovery can seed the protection list immediately.
- 2026-03-28: Added focused tests for protected-word storage/scoping, policy-level suppression, and undo-tracker original-token lookup.
- 2026-03-28: Verified with `JAVA_HOME=$HOME/.local/jdks/temurin-17 ./gradlew --no-daemon --no-watch-fs :app:testDebugUnitTest` and `bash .claude/scripts/pm/validate.sh` (PASS).
