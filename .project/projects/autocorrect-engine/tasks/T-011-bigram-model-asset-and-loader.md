---
id: T-011
name: Bigram model asset and loader
status: blocked
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T20:57:41Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-009]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-006
acceptance_criteria_ids: []
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-009
---

# Task: Bigram model asset and loader

## Description

Ship per-language bigram assets built by `tools/dictionary-build/bigrams.py` and load them into a compact in-memory model next to the word model, off the main thread.

## Acceptance Criteria

- [ ] `ime/dict/latin/en.bigrams.txt` and `nl.bigrams.txt` are built from Tatoeba with the held-out split, with attribution updated.
- [ ] `LatinBigramModel` answers P(next | previous) and the successor list of a word without allocating per lookup.
- [ ] Heap for both bigram models stays under 8 MB on the JVM measurement, and load time is recorded on the emulator.
- [ ] Languages without a bigram asset keep working with no context model.

## Traceability

- Story: US-006
- Acceptance criteria: none (Phase 3 gate in plan.md)

## Technical Notes

- Storage: previous-word ids with offsets into sorted successor id and count arrays (CSR layout), counts as log100 like the word list.
- Load together with the word model in the same single-flight load, and publish through the same volatile snapshot so the main thread never waits.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 3.
