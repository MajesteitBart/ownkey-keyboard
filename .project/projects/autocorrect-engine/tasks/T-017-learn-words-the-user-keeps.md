---
id: T-017
name: Learn words the user keeps
status: done
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T22:42:11Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: S
story_id: US-003
acceptance_criteria_ids: []
---

# Task: Learn words the user keeps

## Description

Words the user types and keeps (names, jargon, slang) should stop being corrected without a trip to the settings. Every time a word is typed and left as it is, keeping it gets more likely.

## Acceptance Criteria

- [x] A word the user typed and kept at least three times is no longer autocorrected in the usual contexts, while typos of very common words (`teh`) still are. Less clear-cut misspellings kept three times (`becuase`, `mischien`) are kept as well: with autocorrect on, a word only reaches three kept uses when the user left it that way.
- [x] Only words that personal learning already records count: nothing is learned in incognito mode, password fields or with personal learning switched off, and clearing personal learning forgets them.
- [x] The check never blocks typing: it reads the in-memory counts without waiting for the store.
- [x] Benchmark: with each out-of-dictionary word typed three times before, at most one of 313 is changed; tap-noise and clean-text results are unchanged without personal data.

## Traceability

- Story: US-003
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- Source: the personal n-gram store already learns the text as committed, so a kept word is stored as typed and an autocorrected word in its corrected form. Word counts are kept next to the pairs and rebuilt from them on load.
- Keeping the typed word gets a bonus of 4 * ln(1 + times typed) nats, instead of turning the word into a dictionary word: a user who leaves `teh` uncorrected three times with autocorrect off must still get "the" once autocorrect is on.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `PersonalNgramModel` now counts how often each word was typed and kept, next to its pairs; the counts are rebuilt from the stored pairs on load, evicted with the same rule, and cleared with personal learning. They only grow while autocorrect is on, so text typed with autocorrect off cannot teach typos. `PersonalNgramStore.timesTypedIfLoaded` reads them without waiting (safe on the main thread) and both scoring hooks pass them to the scorer. From three kept uses on, keeping the typed word gets 4 * ln(1 + times) nats (5.5 at three). A factor of 5 also stopped `teh` from becoming "the"; 4 keeps that correction.
- 2026-09-24: `LearnedWordsTest`: with every word of the out-of-dictionary set learned three times, lowercase names change 1 of 313 after "talk to" (hidde -> hide) and 0 after "I went to the", after "ik ga naar de" and with no words before (without learning: 8, 9, 5 and 3). Twice is not enough (`thijs` still becomes "this"); `teh` still becomes "the" after three kept uses. `PersonalNgramModelTest` covers counting, restore, clearing and the autocorrect-off case. The benchmark sets use no personal data, so their numbers are unchanged. Full `ime` suite: 546 tests pass.
- 2026-09-24: Task created with Phase 5.
