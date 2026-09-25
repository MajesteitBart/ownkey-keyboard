---
id: T-020
name: Real-word errors with the next word
status: deferred
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T22:22:47Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: low
estimate: M
story_id: US-006
acceptance_criteria_ids: []
---

# Task: Real-word errors with the next word

## Description

Dutch real-word errors such as `Wordt je` or `jou boek` can only be decided once the next word is known. That needs a correction of the previous word after the fact, which is a new interaction, not an engine change.

## Acceptance Criteria

- [ ] Decide with Bart whether a correction of the previous word after the fact fits Ownkey's trust rules before building it.
- [ ] If yes: Dutch real-word errors reach the 60% target from Phase 3.

## Traceability

- Story: US-006
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- Deferred on purpose: every other Ownkey correction happens at the word being typed, and changing an earlier word is visible and surprising.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created and deferred with Phase 5; the Dutch real-word target (51.4% after T-012) stays open.
