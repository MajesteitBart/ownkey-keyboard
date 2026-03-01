---
id: T-005
name: Implement rapid personal vocabulary learning (1-3 exposures)
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T21:05:59Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Implement rapid personal vocabulary learning (1-3 exposures)

## Description
Update personalization logic to learn frequent user words/names after 1-3 repeat usages with quality safeguards.

## Acceptance Criteria
- [x] New frequent user terms surface in predictions within 1-3 confirmations.
- [x] Temporary/noisy terms decay over time.

## Technical Notes
Must work with per-language dictionary partitioning.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Implemented rapid personal vocabulary learning via `RapidPersonalVocabularyLearner` with 1-3 confirmation thresholds, per-language exposure partitioning, decay windows for temporary/noisy terms, and suppression after user reverts.
- 2026-02-25: Wired `LatinLanguageProvider.notifySuggestionAccepted` / `notifySuggestionReverted` to promote learned terms into the Floris user dictionary with locale-scoped entries and cache invalidation for immediate candidate refresh.
- 2026-02-25: Added `RapidPersonalVocabularyLearnerTest` coverage for 1-step and 2-step promotion, decay behavior, reversion suppression, language partition isolation, and noisy token filtering.
- 2026-02-25: Verified with `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest` (PASS).
