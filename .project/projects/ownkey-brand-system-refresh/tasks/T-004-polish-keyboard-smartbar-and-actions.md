---
id: T-004
name: Polish keyboard smartbar and actions
status: done
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ["T-001"]
conflicts_with: ["ime-smartbar", "quick-actions"]
parallel: false
priority: high
estimate: M
workstream: WS-C
---

# Task: Polish keyboard smartbar and actions

## Description
Apply Ownkey styling to the smartbar and quick actions while preserving recent spacing and alignment fixes.

## Acceptance Criteria
- [x] Action buttons use consistent Ownkey color/shape states.
- [x] Extended action row and lower controls remain aligned in expanded default mode.
- [x] Button hit areas and visual density remain suitable for daily typing.
- [x] No layout shift when actions collapse/expand.

## Technical Notes
Treat the current smartbar alignment work as a constraint, not a starting point to refactor.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log
- 2026-05-31: Task created from Delano project plan.
- 2026-05-31: Smartbar/dictation controls now use Ownkey action, bone, and orange token colors while preserving existing sizing/alignment constraints.
