---
id: T-008
name: Optimize keyboard open and first-input responsiveness
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
estimate: M
---

# Task: Optimize keyboard open and first-input responsiveness

## Description
Reduce startup and initial interaction overhead to make keyboard open + first input feel instant (<200 ms target).

## Acceptance Criteria
- [ ] Startup benchmark meets target envelope on baseline devices.
- [ ] No regressions in key rendering stability.

## Technical Notes
Coordinate with prediction warm start in T-002.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
