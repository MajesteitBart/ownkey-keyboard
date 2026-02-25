---
id: T-006
name: Support mixed-language prediction without manual switch
status: ready
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
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
- [ ] Mixed-language test corpus shows relevance improvement.
- [ ] No major latency regression in prediction path.

## Technical Notes
Coordinate dictionary priority and language confidence balancing.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
