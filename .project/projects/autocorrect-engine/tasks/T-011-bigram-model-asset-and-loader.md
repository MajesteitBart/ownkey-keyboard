---
id: T-011
name: Bigram model asset and loader
status: done
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T21:23:08Z
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
---

# Task: Bigram model asset and loader

## Description

Ship per-language bigram assets built by `tools/dictionary-build/bigrams.py` and load them into a compact in-memory model next to the word model, off the main thread.

## Acceptance Criteria

- [x] `ime/dict/latin/en.bigrams.txt` and `nl.bigrams.txt` are built from Tatoeba with the held-out split, with attribution updated.
- [x] `LatinBigramModel` answers P(next | previous) and the successor list of a word without allocating per lookup.
- [x] Heap for both bigram models stays under 8 MB on the JVM measurement, and load time is recorded on the emulator.
- [x] Languages without a bigram asset keep working with no context model.

## Traceability

- Story: US-006
- Acceptance criteria: none (Phase 3 gate in plan.md)

## Technical Notes

- Storage: one packed, sorted word string with binary-search lookup; per word an offset into successor id and count arrays sorted by id; counts as log100 like the word list.
- Load together with the word model in the same single-flight load, and publish through the same volatile snapshot so the main thread never waits.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `tools/dictionary-build/bigrams.py` writes `ime/dict/latin/en.bigrams.txt` (313,398 pairs seen 3+ times, 2.58 MB, 0.87 MB gzip) and `nl.bigrams.txt` (84,040 pairs seen 2+ times, 0.73 MB, 0.24 MB gzip). Format v2: a sorted word list, then per previous word the successor ids as differences with their log counts, so the app parses numbers only. Attribution: `latin/ATTRIBUTION.md`, and a Tatoeba entry with a CC BY 2.0 FR notice in the third-party licenses screen (present in the generated `aboutlibraries.json`).
- 2026-09-24: `LatinBigramModel` keeps all words in one packed string and finds them by binary search; successors are id-sorted ranges of an int and a short array. Lookups allocate nothing. The first version used a string per word and two hash maps: 10.3 MB for EN and NL together, and 0.8 to 2.4 s to load on the emulator. Now 4 MB together on the JVM (the word models are 16 and 18 MB), parsed in 100 ms (EN) and 25 ms (NL) on the JVM and 169 to 207 ms (EN) and 103 to 110 ms (NL) on the emulator. The model hangs off `LatinWordModel` and loads in the same off-main-thread load, so it reaches the main thread through the existing snapshot. Languages without a bigram file get an empty model.
- 2026-09-24: Side finding, not caused by this task: with NL+EN active, the Dutch word list takes about 3.0 s to load on the emulator with or without bigrams (EN about 1.05 s), against 0.8 s measured alone in T-007. Both languages load in parallel; worth a look if keyboard-start latency comes up in dogfooding.
- 2026-09-24: `LatinBigramModelTest` (4 tests) and the full `ime` suite pass: 519 tests. The benchmark numbers are unchanged, since the scorer does not use the model yet (T-012).
- 2026-09-24: Task created with Phase 3.
