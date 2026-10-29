---
id: T-006
name: Integrate streams, add e2e suite and run bot-user test
status: done
workstream: WS-F
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T09:15:44Z
linear_issue_id:
github_issue: https://github.com/MajesteitBart/ownkey-keyboard/issues/25
github_pr:
depends_on: ['T-002', 'T-003', 'T-004', 'T-005']
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id:
acceptance_criteria_ids: ['AC-001', 'AC-002', 'AC-003', 'AC-004', 'AC-005']
---

# Task: Integrate streams, add e2e suite and run bot-user test

## Description
See `../spec.md` and `../plan.md` for scope and the workstream file for owned areas.

## Acceptance Criteria
- [x] Matches the build spec for this workstream.
- [x] `:app:compileDebugKotlin` and the full unit suite pass.
- [x] Emulator screenshots reviewed.

## Traceability
- Story: none
- Acceptance criteria: AC-001, AC-002, AC-003, AC-004, AC-005

## Technical Notes
Built against the redesign build spec; independent verification per stream.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-10-29: Integrated four streams (109 files, no conflicts). Maestro suite: 17 flows plus 18 deep links, all passing on the API 35 emulator. Bot-user session (gpt-6-astra) completed 14 tasks; its findings were fixed.
