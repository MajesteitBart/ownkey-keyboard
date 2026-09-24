---
id: T-005
name: Curated dictionary removals
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T19:46:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: S
story_id: US-002
acceptance_criteria_ids: [AC-002]
---

# Task: Curated dictionary removals

## Description

Misspellings in the current word lists count as correct words and can never be corrected: `mischien` (1,043 hits) and `eigelijk` (286) in Dutch, `untill`, `seperate`, `occured` and `tommorow` in English. 13 of 100 English and 5 of 60 Dutch real-world misspellings in the benchmark are dictionary words. Remove them from the current assets with a reviewed list, before the Phase 2 pipeline exists.

## Acceptance Criteria

- [x] A reviewed removal list per language, checked into the repo, covering known misspellings, backtick forms (`` don`t ``) and OCR single letters (`l`, `s`, `o`, `t` in English).
- [x] Split contraction fragments (`don`, `didn`, `isn`) stay for now. They go in T-007, once contraction forms exist to replace them.
- [x] Apostrophe-less forms (`dont`, `im`) stay until T-008 adds their replacements.
- [x] The benchmark shows no word from the removal list as an exact match, and AC-002 passes with the T-004 scoring.
- [x] The clean-text false-correction rate does not rise.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002

## Technical Notes

- Apply the list at load time or as a small asset patch, whichever keeps T-007 simpler. T-007 later reuses the same list as an input.
- Removing words changes the calibration. Rerun the benchmark and adjust T-004 thresholds afterward.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Reviewed removal lists in `app/src/main/assets/ime/dict/removals/{en,nl}.txt` (32 EN, 13 NL words), applied at load time by `LatinDictionaryCleanup`, which also drops backtick and U+FFFD tokens and English single letters other than a, i, k, u and x. EN candidates came from the Wikipedia misspelling list intersected with `en_50k.txt` (40 hits, 8 kept as real or deliberate words: thru, toke, momento, quitted, vermillion, dum, plus the apostrophe-less forms left for T-008). `LatinDictionaryCleanupTest` checks that every listed word exists in the raw list and none survives in the shipped model. The legacy reproduction test now runs on the raw lists.
- 2026-09-24: Benchmark after removals (`research/benchmark-noisy-channel-t005.md`): real EN curated 66% → 81%, real NL curated 81.7% → 88.3%, real NL on NL+EN 81.6% → 85.6%, all with 0 wrong; clean-text false corrections did not rise (at most 0.13 per 1,000). AC-002 (`mischien` → misschien on NL+EN) and `untill`, `seperate` pass in `NoisyChannelAcceptanceTest`. `seperate` also exposed a gap in the T-004 error model: spelling errors (vowel for vowel, doubled letters) were priced as unlikely taps. Added vowel-substitution (5.5) and doubled-letter (4.0) costs; Wikipedia misspellings 24% → 29% at 97.7% precision, tap sets unchanged or better. New out-of-dictionary change: `mergen` → morgen (2 of 313, within the gate). 494 `ime` unit tests pass.

- 2026-09-24: Task created. Moved into Phase 1 after the independent review found that AC-002 cannot pass without it.
