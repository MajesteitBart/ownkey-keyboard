---
id: T-008
name: Release validation and screenshot QA
status: ready
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T16:45:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-003", "T-004", "T-005", "T-006"]
conflicts_with: ["release-build", "visual-qa"]
parallel: false
priority: high
estimate: M
---

# Task: Release validation and screenshot QA

## Description
Run build validation and visual QA before shipping a refreshed APK.

## Acceptance Criteria
- [ ] `:app:compileReleaseKotlin` passes.
- [ ] `:app:assembleRelease` passes.
- [ ] `git diff --check` passes.
- [ ] Screenshot review covers setup, settings, keyboard, dictation, and toast states.
- [ ] APK/package ID and SHA-256 are reported in the handoff.

## Technical Notes
If no device/emulator is available, report that explicitly and do not claim visual verification.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
