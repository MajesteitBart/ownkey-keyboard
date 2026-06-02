---
id: T-007
name: Map planning scope to milestones and Linear execution
status: blocked
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:35:00Z
linear_issue_id: 2ecd539a-bffb-426a-8a83-72296f2df395
github_issue:
github_pr:
depends_on: [T-001, T-003, T-004, T-005]
conflicts_with: []
parallel: false
priority: high
estimate: S
workstream: WS-4
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001, T-003, T-004, T-005
---

# Task: Map planning scope to milestones and Linear execution

## Description
Convert the planning package into milestone-aligned tracker objects, issue sequencing, registry updates, and architecture-decision visibility in Linear.

## Acceptance Criteria
- [ ] Linear project, milestones, and initial issues map back to local Delano artifacts.
- [ ] Method-family decisions and architecture assumptions are visible in the planning copy.
- [ ] Any sync gaps are documented with exact manual follow-up steps.

## Technical Notes
Local Delano contracts remain authoritative if tracker fields or schemas drift. In this run, direct GraphQL is an acceptable fallback when the generated CLI wrapper is incomplete.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-74` (https://linear.app/bartvandermeeren/issue/BAR-74/t-007-map-planning-scope-to-milestones-and-linear-execution).
- 2026-03-28: Task created during predictive typing planning pass.
