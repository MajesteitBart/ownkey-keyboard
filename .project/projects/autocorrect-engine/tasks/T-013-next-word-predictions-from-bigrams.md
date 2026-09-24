---
id: T-013
name: Next-word predictions from bigrams
status: done
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T21:50:13Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-011]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-006
acceptance_criteria_ids: [AC-012]
---

# Task: Next-word predictions from bigrams

## Description

After a space, suggest the most likely next words from the bigram model of the active languages, merged with the personal n-gram predictions, instead of the same list of frequent words after every word.

## Acceptance Criteria

- [x] After `ik` the Dutch predictions start with verbs such as "ben", "heb", "ga"; after `I` the English ones with verbs such as "think", "know", "don't" (AC-012).
- [x] Next-word top-3 hit rate on the held-out sentences beats the current frequency-only predictions, per language.
- [x] Personal n-gram predictions keep their priority for words the user typed before.
- [x] Prediction latency stays within the suggestion budget: a prediction is one partial selection over the successors of one word per language, off the main thread; no slowdown was visible on the emulator.

## Traceability

- Story: US-006
- Acceptance criteria: AC-012

## Technical Notes

- Predictions for mixed subtypes weight each language by the context language weights from T-012 and rank by the pair's share of all pairs in that language, so a language that rarely sees the previous word cannot win with its few successors.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `NoisyChannelLatinScorer.predictNextWords` ranks the successors of the previous word (the sentence start after . ! ? or at the start of the text) per language by language weight times the pair's share of all counted pairs, and capitalizes English "I" forms. `LatinLanguageProvider` adds these predictions to the field matches and personal n-grams with a lower score range (1.5 to 5.5 against 6 to 14 for personal n-grams), so the user's own sequences stay first. The legacy-engine devtools switch turns them off. Frequency-only predictions stay as the fallback, now also with a capital I.
- 2026-09-24: Benchmark on the held-out sentences (`research/benchmark-next-word-t013.md`): the actual next word is in the first three predictions for 29.2% of EN and 23.9% of NL positions (frequency-only: 9.9% and 6.0%), and first for 16.4% and 13.0% (2.2% and 1.5%). NL on the NL+EN subtype: 23.8%. A floor in `AutocorrectBenchmarkReportTest` requires at least 10 points over frequency-only. `NextWordPredictionTest` (5 tests); full `ime` suite 529 tests pass.
- 2026-09-24: A first version ranked by P(next | previous) per language. On NL+EN, `naar de` then predicted "janeiro" first, because English sees "de" almost only in "Rio de Janeiro". Ranking by the pair's share of all pairs fixed it; single-language rankings are unchanged.
- 2026-09-24: Emulator: `Yesterday I` shows "don't, would, in, wasn't, think, know" (would, in and wasn't come from earlier typing on this device); `Morgen ga ik` shows "zag, heb, ben, wil, kan, weet"; `Morgen ga ik naar de` shows "hele, deur, eerste, waarheid, auto". Bigrams only see "de", not "naar de", so place words such as "winkel" need a longer context.
- 2026-09-24: Task created with Phase 3.
