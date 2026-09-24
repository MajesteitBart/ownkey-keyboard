---
id: T-016
name: Candidate search for two-edit typos
status: done
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T22:19:55Z
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

- [x] Candidates within two edits (including a dropped first letter) of the typed word are found for words in the top 20,000, without a full distance-2 delete index.
- [x] Dutch context typos reach >= 72% autocorrected right (moved from Phase 3), precision >= 98%. Met with taps: 77.4% at 98.6%. Without taps (glide, hardware keyboards) it is 69.2%.
- [x] Top-1 on the tap-noise usage sets reaches >= 92% with taps (moved from T-015, where it reached 88.9% EN and 88.4% NL).
- [x] Scoring p95 on the JVM stays under 1 ms per word, and the heap of the candidate index grows by at most 5 MB for EN and NL together.
- [x] Clean text and out-of-dictionary gates still pass.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- Options: expand only the most likely second edits (tap neighbors of each typed letter, when taps are known), or run the distance-1 lookup on distance-1 variants of the input. Measure both.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Miss analysis first (tap-noise usage sets, with taps): of 166 EN and 174 NL typos without the intended word first, 119 and 136 had it missing from the candidates, and 102 and 123 of those were two edits away. `LatinWordModel.lookupTwoEditCandidates` adds them without a new index: two deletions from the input matched against the existing delete index, and two substituted letters where each position tries the three keys nearest the tap (with taps only; without taps the neighbor pairs cost 0.3 ms more and added nothing). `KeyGeometry` gained `neighbors` and `nearestKeys`.
- 2026-09-24: Three rules keep it safe and fast. (1) The lookup only runs when no one-edit reading is confident enough to auto-commit; that keeps p50 at about 38 us and p95 at about 0.3 ms on NL+EN with taps after warm-up (0.1 ms without the lookup). (2) Two-edit readings only count toward the auto-commit decision when one of them is the best reading; the first version let them dilute confident one-edit fixes and lost 1.2 to 4.6 points of recall. (3) A two-edit auto-commit needs 0.995 at Normal (`twoEditDoubtFactor` 0.1): at 0.95 it added four wrong changes to lowercase names (gijs -> his, trello -> tell, joost -> just, jelle -> hele); at 0.1 the name results are back to where they were.
- 2026-09-24: Results (`research/benchmark-two-edit-t016.md`), with taps, before -> after: top-1 EN 88.9 -> 92.6%, NL 88.4 -> 92.2%, NL on NL+EN 87.4 -> 91.1%; typos fixed EN 80.1 -> 81.7%, NL 78.7 -> 79.9%, NL on NL+EN 74.9 -> 75.9%; context NL 75.4 -> 77.4%; precision 98.5 to 99.5%. Without taps: top-1 EN 86.9 -> 89.0%, NL 86.6 -> 89.1%, typos fixed within 0.4 points of before. Clean text, out-of-dictionary sets (including lowercase names after "talk to") unchanged. No extra heap. `TwoEditCandidateTest` (4 tests), top-1 floors of 92% with taps; full `ime` suite 541 tests pass.
- 2026-09-24: Task created with Phase 4.
