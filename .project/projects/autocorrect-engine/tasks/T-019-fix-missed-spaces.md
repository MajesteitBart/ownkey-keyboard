---
id: T-019
name: Fix missed spaces
status: ready
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T22:22:47Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-001
acceptance_criteria_ids: []
---

# Task: Fix missed spaces

## Description

A word that is really two words run together (`thisis`, `ikben`) should become the two words when that is clearly the best reading.

## Acceptance Criteria

- [ ] An out-of-dictionary input can be read as two dictionary words, priced by a missed-space cost plus the word-pair probability; the split competes with single-word corrections and with keeping the word.
- [ ] A benchmark set of run-together pairs from the held-out sentences shows the split as the first suggestion in at least 60% of cases and auto-commits only above the normal threshold, with precision >= 97%.
- [ ] Clean text, out-of-dictionary words and every existing floor stay where they are; compound-heavy Dutch words that are in the dictionary are never split.
- [ ] Undo with backspace restores the typed word.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- Extra spaces (`th e`) need to edit the previous word and are left out of this task.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 5.
