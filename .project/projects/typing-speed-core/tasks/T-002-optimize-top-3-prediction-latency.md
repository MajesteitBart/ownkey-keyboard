---
id: T-002
name: Optimize top-3 prediction latency
status: ready
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
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
- [ ] Benchmark shows <50 ms p95 suggestion latency in target test scenario.
- [ ] No regression in prediction relevance quality.

## Technical Notes
Coordinate with startup path optimization to avoid cold-start penalties.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
