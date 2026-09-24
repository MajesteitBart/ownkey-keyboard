---
id: T-010
name: Context benchmark sets
status: done
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T21:04:22Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-006
acceptance_criteria_ids: [AC-010, AC-011]
---

# Task: Context benchmark sets

## Description

Add benchmark sets that carry the words before each typo, so the effect of context can be measured: tap-noise typos injected into held-out sentences, real-word errors in sentences, and next-word prediction on held-out sentences. The current typo sets are single words without context.

## Acceptance Criteria

- [x] `context_en.txt` and `context_nl.txt` hold 2,000 held-out Tatoeba sentences each, never counted by the bigram build and not in the clean-text sets.
- [x] A context typo set injects one tap-noise typo per sentence (same generator and filters as the usage sets) and scores it with the words before it, for EN, NL and NL on the NL+EN subtype.
- [x] `realword_en.tsv` and `realword_nl.tsv` hold hand-written sentences with one real-word error each (then/than, your/you're, word/wordt); the metric is how often the intended word is the first suggestion, and auto-commit must never fire on them.
- [x] A next-word metric reports how often the actual next word of a held-out sentence is among the first 3 predictions.
- [x] Baselines for all three, with and without context, are in the benchmark report.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010, AC-011

## Technical Notes

- The real-word sets are written by hand and partly mirror the confusion pairs the engine will know, so they measure whether context picks the right member of a pair, not how many pairs exist.
- Held-out rule: Tatoeba sentence id divisible by 10 (`HOLDOUT_MODULUS` in both scripts).

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `TapNoiseTypoGenerator.buildContextTypos` puts one tap-noise typo into a lowercase word (3+ letters, not the first word) of each held-out sentence and keeps the text before it; typos that are real words are dropped like in the other sets (18.8% EN, 18.5% NL). 1,557 EN and 1,564 NL cases. `evaluateRealWords` checks the first suggestion and counts auto-commits; `evaluateNextWord` asks a predictor at every word boundary of the held-out sentences. The baseline predictor is what the keyboard shows without field or personal matches: the most frequent words of the primary language.
- 2026-09-24: Baseline, noisy-channel scorer (`research/benchmark-context-baseline-t010.md`): context typos fixed EN 66.3% (precision 99.2%, top-1 85.8%), NL 62.1% (98.5%, 82.4%), NL on NL+EN 56.2% (98.5%), EN on NL+EN 58.2% (98.9%). Removing the words before the typo changes nothing, so today's scorer takes no signal from context. Real-word sets: intended word first in 10.2% EN and 0% NL, in the top 3 in 40.7% EN and 75.7% NL, never auto-committed. Next word: actual word first 2.2% EN and 1.5% NL, in the first three 9.9% EN and 6.0% NL. All 515 tests pass; every context set stays above the 97% precision floor.
- 2026-09-24: Task created with Phase 3. Held-out sentence files generated with `tools/autocorrect-datasets/prepare.py --only context`; real-word sets written: 59 EN and 37 NL sentences.
