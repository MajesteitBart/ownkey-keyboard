---
id: T-010
name: Context benchmark sets
status: ready
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T20:57:41Z
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

- [ ] `context_en.txt` and `context_nl.txt` hold 2,000 held-out Tatoeba sentences each, never counted by the bigram build and not in the clean-text sets.
- [ ] A context typo set injects one tap-noise typo per sentence (same generator and filters as the usage sets) and scores it with the words before it, for EN, NL and NL on the NL+EN subtype.
- [ ] `realword_en.tsv` and `realword_nl.tsv` hold hand-written sentences with one real-word error each (then/than, your/you're, word/wordt); the metric is how often the intended word is the first suggestion, and auto-commit must never fire on them.
- [ ] A next-word metric reports how often the actual next word of a held-out sentence is among the first 3 predictions.
- [ ] Baselines for all three, with and without context, are in the benchmark report.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010, AC-011

## Technical Notes

- The real-word sets are written by hand and partly mirror the confusion pairs the engine will know, so they measure whether context picks the right member of a pair, not how many pairs exist.
- Held-out rule: Tatoeba sentence id divisible by 10 (`HOLDOUT_MODULUS` in both scripts).

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 3. Held-out sentence files generated with `tools/autocorrect-datasets/prepare.py --only context`; real-word sets written: 59 EN and 37 NL sentences.
