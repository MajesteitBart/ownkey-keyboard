---
id: T-009
name: Improve symbol/number flow with fewer mode switches
status: in_progress
created: 2026-02-25T19:38:38Z
updated: 2026-03-28T13:20:31Z
linear_issue_id:
github_issue:
github_pr: 7
depends_on: []
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Improve symbol/number flow with fewer mode switches

## Description
Design and implement quicker symbol/number input interactions that preserve typing and prediction rhythm.

## Acceptance Criteria
- [ ] Fewer mode-switch interactions are required for common symbol/number patterns.
- [ ] Prediction context resumes correctly after symbol entry.

## Technical Notes
May involve layout shortcuts or temporary symbol rows.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-03-28: Started the first execution slice by adding a direct `VIEW_NUMERIC_ADVANCED` popup shortcut to the base `?123` key in `charactersMod/default.json`, reducing one mode-switch interaction for common number and symbol bursts.
- 2026-03-28: Added `CharactersModifierLayoutTest` plus a JSON structural assertion to lock the new shortcut wiring to `VIEW_NUMERIC_ADVANCED`.
- 2026-03-28: Delano contract validation passed via `bash .claude/scripts/pm/validate.sh`.
- 2026-03-28: Local Gradle compile/test/build attempts were blocked in the Codex sandbox because Gradle's file-lock listener could not enumerate network interfaces (`java.net.SocketException: Operation not permitted (Socket creation failed)` from `NetworkInterface.getNetworkInterfaces()`), so APK production continued via CI.
- 2026-03-28: Opened GitHub PR #7 and verified Android CI run `23686005603` succeeded with phone artifact `ownkey-phone-debug-v0.6.0-alpha02-1d8be84.apk`.
