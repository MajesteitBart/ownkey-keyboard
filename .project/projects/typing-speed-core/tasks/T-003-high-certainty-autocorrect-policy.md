---
id: T-003
name: Implement high-certainty autocorrect policy
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T20:54:11Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Implement high-certainty autocorrect policy

## Description
Introduce confidence thresholding so autocorrect triggers primarily when certainty is high.

## Acceptance Criteria
- [x] False autocorrect ratio improves against baseline.
- [x] Thresholds are configurable for later tuning.

## Technical Notes
Default profile should be conservative for trust-first rollout.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Added `HighCertaintyAutocorrectPolicy` with conservative defaults (minimum confidence, confidence-gap, and minimum input-length guardrails) and wired it into `LatinLanguageProvider` so only the top high-certainty typo-correction candidate is eligible for auto-commit.
- 2026-02-25: Added configurable preference-backed thresholds (`correction__high_certainty_autocorrect_*`) for tuning without logging user text.
- 2026-02-25: Added `HighCertaintyAutocorrectPolicyTest` coverage for allow/block scenarios and verified with `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (PASS).
