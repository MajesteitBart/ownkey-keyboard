---
id: T-007
name: Local code review and pull request
status: review
workstream: WS-F
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T09:15:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: ['T-006']
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id:
acceptance_criteria_ids: []
---

# Task: Local code review and pull request

## Description
See `../spec.md` and `../plan.md` for scope and the workstream file for owned areas.

## Acceptance Criteria
- [ ] Matches the build spec for this workstream.
- [ ] `:app:compileDebugKotlin` and the full unit suite pass.
- [ ] Emulator screenshots reviewed.

## Traceability
- Story: none
- Acceptance criteria: none

## Technical Notes
Built against the redesign build spec; independent verification per stream.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-10-29: Local review by gpt-6.1-sol found three P2 issues (key replacement failure path, legacy app-icon alias, clipboard copy); all fixed and sent for a targeted follow-up review.
