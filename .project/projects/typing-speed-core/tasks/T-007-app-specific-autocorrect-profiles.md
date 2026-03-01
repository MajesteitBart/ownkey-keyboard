---
id: T-007
name: Implement app-specific autocorrect aggressiveness profiles
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-03-01T20:50:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-003]
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Implement app-specific autocorrect aggressiveness profiles

## Description
Adjust correction aggressiveness based on app context categories such as chat vs email.

## Acceptance Criteria
- [x] App-context policy maps major target apps to profile categories.
- [x] Behavior differences are observable and configurable.

## Technical Notes
Must include safe fallback when app context is unknown.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-03-01: Added `AppSpecificAutocorrectProfilePolicy` with app-context classification for major chat and email package IDs plus fallback heuristics (`InputAttributes` variation and IME action) with safe default profile when unknown.
- 2026-03-01: Integrated app-specific profile scaling into `LatinLanguageProvider` high-certainty autocorrect policy snapshot and suggestion cache signatures so auto-commit behavior can differ per app context without stale cache reuse.
- 2026-03-01: Added new correction preferences and Typing settings controls for enabling app-specific profiles and configuring chat/email aggressiveness percentages.
- 2026-03-01: Added `AppSpecificAutocorrectProfilePolicyTest` coverage for package mapping, heuristic fallback, safe default, aggressive/conservative threshold scaling, and disabled-policy passthrough.
- 2026-03-01: Verified with `JAVA_HOME=/home/bartadmin/.local/jdks/temurin-17 ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.latin.*"` (PASS).
