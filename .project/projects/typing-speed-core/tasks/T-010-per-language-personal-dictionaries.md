---
id: T-010
name: Split personal dictionaries per language
status: ready
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-005]
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Split personal dictionaries per language

## Description
Store and query personal vocabulary by language to prevent cross-language contamination.

## Acceptance Criteria
- [ ] Personal terms remain scoped to their language dictionary.
- [ ] Migration path preserves existing user dictionary entries.

## Technical Notes
Must interoperate with mixed-language scoring in T-006.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
