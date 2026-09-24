---
id: T-016
name: Candidate search for two-edit typos
status: ready
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T22:06:29Z
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

# Task: Candidate search for two-edit typos

## Description

Find intended words that are two edits away or start with a dropped letter (`blssenn` for bossen, `ijn` for zijn), which the distance-1 delete index misses. These are most of the remaining Dutch context misses from T-012.

## Acceptance Criteria

- [ ] Candidates within two edits (including a dropped first letter) of the typed word are found for words in the top 20,000, without a full distance-2 delete index.
- [ ] Dutch context typos reach >= 72% autocorrected right (moved from Phase 3), precision >= 98%. With taps this is already met (75.4%, T-015); without taps it is 68.6%.
- [ ] Top-1 on the tap-noise usage sets reaches >= 92% with taps (moved from T-015, where it reached 88.9% EN and 88.4% NL).
- [ ] Scoring p95 on the JVM stays under 1 ms per word, and the heap of the candidate index grows by at most 5 MB for EN and NL together.
- [ ] Clean text and out-of-dictionary gates still pass.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- Options: expand only the most likely second edits (tap neighbors of each typed letter, when taps are known), or run the distance-1 lookup on distance-1 variants of the input. Measure both.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 4.
