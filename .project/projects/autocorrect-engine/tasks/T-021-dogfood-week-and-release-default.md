---
id: T-021
name: Dogfood week and release default
status: blocked
workstream: WS-A
created: 2026-09-24T23:06:09Z
updated: 2026-09-24T23:06:09Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: S
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-004]
blocked_owner: Bart
blocked_check_back: After a week of daily typing with this branch on Bart's phone
---

# Task: Dogfood week and release default

## Description

The benchmark gates are passed; the remaining gate is real use. Type with this branch for a week on Bart's phone, read the undo rate from the typing metrics, and decide whether the new engine becomes the release default.

## Acceptance Criteria

- [ ] A debug or internal build of this branch is installed on Bart's phone with the EN and NL+EN subtypes he uses.
- [ ] After a week, the autocorrect undo rate from `TypingSpeedMetrics` (applied vs. undone corrections) is at most 5% (spec Phase 1 gate; final target 3%).
- [ ] Keyboard-open and typing latency on the phone stay within the spec budgets (suggestion p95 under 50 ms; the decide-now path is logged per decision).
- [ ] Bart decides on the release default and on removing the legacy-engine devtools switch after one stable release.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001, AC-004

## Technical Notes

- Only the API 35 emulator was available for device checks during the build; everything in the evidence logs of T-001 to T-019 comes from it.
- The legacy engine stays one devtools switch away as the rollback.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created at the end of Phase 5; waits on Bart's dogfood week.
