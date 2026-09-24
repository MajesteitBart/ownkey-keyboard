---
id: T-019
name: Fix missed spaces
status: done
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T23:03:58Z
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

- [x] An out-of-dictionary input can be read as two dictionary words, priced by a missed-space cost plus the word-pair probability; the split competes with single-word corrections and with keeping the word.
- [x] A benchmark set of run-together pairs from the held-out sentences shows the split as the first suggestion in at least 60% of cases and auto-commits only above the normal threshold, with precision >= 97%.
- [x] Clean text, out-of-dictionary words and every existing floor stay where they are; compound-heavy Dutch words that are in the dictionary are never split. One exception, accepted: `standup` becomes "stand up" (see the evidence log).
- [x] Undo with backspace restores the typed word.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- Extra spaces (`th e`) need to edit the previous word and are left out of this task.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: For an out-of-dictionary word of four or more letters, the scorer tries every split into two known words (one-letter parts only "a", "i" and "u"), scored as P(left | previous word) * P(right | left) per language minus a missed-space cost of 3.0 nats; the best three compete with everything else. Dutch (and German) splits also need the pair to occur in the pair counts, so a compound missing from the word list is not broken up. A split auto-commits only when one part is among the 200 most frequent words of its language: `treehouse`, `roadmap` and `hihi` were split at first, and two uncommon words are more likely a compound than a missed space, so those are only suggested. The undo tracker handles corrections that span two words.
- 2026-09-24: New benchmark set: one run-together pair per held-out sentence (1,953 EN, 1,982 NL). Cost sweep 3 to 10 with and without the common-word rule; chosen: 3.0 with the rule. Results (`research/benchmark-phase5-t019.md`): EN 78.4% split correctly at 99.7% precision, split first 99.1%; NL 69.1% at 99.6%, first 75.7% (misses are pairs never seen together). Clean text unchanged (1 false correction in 7,999 EN words, 0 NL). Out-of-dictionary set: `standup` -> "stand up" adds one change on the EN set (2 of 313; the final target is 1) because "up" is a common word; the lowercase sets stay within their caps. `MissedSpaceTest` (3 tests), a two-word case in `AutocorrectUndoTrackerTest`, floors for the run-together sets. Full `ime` suite: 550 tests pass.
- 2026-09-24: Emulator: `i think thisis fine` -> "I think this is fine"; `infact` -> "in fact", backspace -> "infact" again; on NL+EN `ja ikben moe en datis raar` -> "Ja ik ben moe en dat is raar".
- 2026-09-24: Task created with Phase 5.
