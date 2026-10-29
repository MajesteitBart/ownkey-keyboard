---
id: T-002
name: Restyle and restructure core settings and setup
status: done
workstream: WS-B
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T09:15:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ['T-001']
conflicts_with: []
parallel: true
priority: high
estimate: L
story_id:
acceptance_criteria_ids: ['AC-001', 'AC-002', 'AC-004']
---

# Task: Restyle and restructure core settings and setup

## Description
See `../spec.md` and `../plan.md` for scope and the workstream file for owned areas.

## Acceptance Criteria
- [x] Matches the build spec for this workstream.
- [x] `:app:compileDebugKotlin` and the full unit suite pass.
- [x] Emulator screenshots reviewed.

## Traceability
- Story: none
- Acceptance criteria: AC-001, AC-002, AC-004

## Technical Notes
Built against the redesign build spec; independent verification per stream.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-10-29: Built in a separate worktree, verified independently (compile, full unit suite, assembleDebug, emulator screenshots), integrated without conflicts, polished.
