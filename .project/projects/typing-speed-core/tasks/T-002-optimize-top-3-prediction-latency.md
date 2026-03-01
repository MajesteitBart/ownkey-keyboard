---
id: T-002
name: Optimize top-3 prediction latency
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T23:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: false
priority: high
estimate: L
---

# Task: Optimize top-3 prediction latency

## Description
Optimize suggestion path using caching, warm starts, and ranking shortcuts to keep top-3 prediction updates under 50 ms p95.

## Acceptance Criteria
- [x] Benchmark shows <50 ms p95 suggestion latency in target test scenario.
- [x] No regression in prediction relevance quality.

## Technical Notes
Coordinate with startup path optimization to avoid cold-start penalties.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Implemented warm-started prefix/fallback shortcut indexes in `LatinLanguageProvider` to reduce top-3 suggestion latency on the hot path while keeping all processing local/in-memory.
- 2026-02-25: Optimized composing-mode suggestion cache keying to reduce redundant recomputation without using user-content logging.
- 2026-02-25: Added `LatinPredictionShortcutsTest` with functional ranking assertions and a benchmark-style p95 latency test that verifies top-3 lookup stays under 50 ms in a representative scenario.
- 2026-02-25: Verified with `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` (PASS).
