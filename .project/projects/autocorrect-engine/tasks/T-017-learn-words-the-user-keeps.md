---
id: T-017
name: Learn words the user keeps
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
priority: high
estimate: S
story_id: US-003
acceptance_criteria_ids: []
---

# Task: Learn words the user keeps

## Description

Words the user types and keeps (names, jargon, slang) should stop being corrected without a trip to the settings. Every time a word is typed and left as it is, keeping it gets more likely.

## Acceptance Criteria

- [ ] A word the user typed and kept at least three times is no longer autocorrected in the usual contexts, while frequent typos of common words (`teh`) still are.
- [ ] Only words that personal learning already records count: nothing is learned in incognito mode, password fields or with personal learning switched off, and clearing personal learning forgets them.
- [ ] The check never blocks typing: it reads the in-memory counts without waiting for the store.
- [ ] Benchmark: with each out-of-dictionary word typed three times before, at most one of 313 is changed; tap-noise and clean-text results are unchanged without personal data.

## Traceability

- Story: US-003
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- Source: the personal n-gram store already learns the text as committed, so a kept word is stored as typed and an autocorrected word in its corrected form. Word counts are kept next to the pairs and rebuilt from them on load.
- Keeping the typed word gets a bonus of 5 * ln(1 + times typed) nats, instead of turning the word into a dictionary word: a user who leaves `teh` uncorrected three times with autocorrect off must still get "the" once autocorrect is on.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 5.
