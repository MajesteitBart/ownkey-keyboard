---
id: T-008
name: Release validation and screenshot QA
status: review
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:18:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-003", "T-004", "T-005", "T-006"]
conflicts_with: ["release-build", "visual-qa"]
parallel: false
priority: high
estimate: M
workstream: WS-D
---

# Task: Release validation and screenshot QA

## Description
Run build validation and visual QA before shipping a refreshed APK.

## Acceptance Criteria
- [x] `:app:compileReleaseKotlin` passes.
- [x] `:app:assembleRelease` passes.
- [x] `git diff --check` passes.
- [ ] Screenshot review covers setup, settings, keyboard, dictation, and toast states.
- [x] APK/package ID and SHA-256 are reported in the handoff.

## Technical Notes
If no device/emulator is available, report that explicitly and do not claim visual verification.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
- 2026-05-31: `./gradlew :app:compileReleaseKotlin` passed after adding Android icon resources and brand-token wiring.
- 2026-05-31: `./gradlew :app:compileReleaseKotlin :app:assembleRelease`, `git diff --check`, and `delano validate` passed.
- 2026-05-31: Package badging verified `nl.bartvandermeeren.ownkey` with app label `Ownkey Keyboard`; release APK SHA-256 is `60cececaeba5440382aadbdf933a568ab45b3c68d6cb35b333158955fa9ece09`.
- 2026-05-31: Captured emulator evidence for setup branding and the dark Ownkey keyboard surface in `artifacts/screenshots/`.
- 2026-05-31: Dictation/toast visual confirmation remains a manual follow-up because the emulator mic tap opened Google voice input instead of the Ownkey dictation path; do not claim that state as visually verified.
