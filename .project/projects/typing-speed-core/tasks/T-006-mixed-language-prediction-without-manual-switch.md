---
id: T-006
name: Support mixed-language prediction without manual switch
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T21:28:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: L
---

# Task: Support mixed-language prediction without manual switch

## Description
Enable scoring/ranking that handles mixed-language sentence input without requiring manual language toggle.

## Acceptance Criteria
- [x] Mixed-language test corpus shows relevance improvement.
- [x] No major latency regression in prediction path.

## Technical Notes
Coordinate dictionary priority and language confidence balancing.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Implemented mixed-language ranking in `LatinLanguageProvider` by loading language models for primary + secondary locales, computing context-based language confidence, and applying weighted candidate scoring across prefix/typo/fallback prediction paths without requiring manual language switching.
- 2026-02-25: Added language-aware rapid-learning integration by resolving accepted/reverted suggestion locale from active subtype models and promoting learned terms into matching locale dictionary entries.
- 2026-02-25: Added `MixedLanguageScoringPolicy` with `MixedLanguageScoringPolicyTest` coverage for language-prior balancing, context evidence shifts, exact-input boosts, and weighted rank/confidence blending.
- 2026-02-25: Verified with `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.latin.*"` and `./gradlew :app:testDebugUnitTest` (PASS).
