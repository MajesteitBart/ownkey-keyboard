---
id: T-012
name: Context in scoring
status: done
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-25T14:23:05Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-010, T-011]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-006
acceptance_criteria_ids: [AC-010, AC-011]
---

# Task: Context in scoring

## Description

Use the previous word in the scorer: interpolate the bigram probability with the unigram prior per language, keep the personal n-gram boost, and add confusion pairs (then/than, your/you're, word/wordt) as suggestion-only candidates for real words.

## Acceptance Criteria

- [x] On the context typo sets, right corrections and top-1 improve over the no-context baseline, with precision at or above 98% on every set with at least 10 corrections.
- [x] Context typo sets reach the spec's final recall targets where the signal allows: EN >= 75%, NL >= 72%, NL on the NL+EN subtype >= 65%. Misses are reported, and a target moves to Phase 4 when context cannot supply the signal.
- [x] Real-word sets: no real-word error is auto-committed (AC-011), and the intended word is the first suggestion in at least 60% of English sentences (71%). Rescoped in the PR #17 review: the Dutch 60% target was never met here (51.4%); it moved to Phase 4 and then to T-020 (deferred), and a regression floor holds Dutch at 45%.
- [x] Clean text stays at or below 0.3 false corrections per 1,000 words, and a confusion alternative is the first suggestion for at most 2% of correctly typed words.
- [x] Phase 1 floors and strength calibration still pass on the isolated-word sets.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010, AC-011

## Technical Notes

- Witten-Bell style interpolation per language: lambda(prev) = N(prev) / (N(prev) + k * T(prev)), with k tuned on the context sets.
- The previous word only counts inside the current sentence (. ! ? ; : and line breaks end one). The first word of a sentence gets no word context: Tatoeba's sentence starts are dominated by stock openings, and "Zn" at a sentence start stopped becoming "Z'n" with `<s>` as context. `<s>` stays in the asset for next-word predictions.
- Real-word candidates never auto-commit; they only change the order of suggestions.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Independent review of T-011 and T-012 (read-only, with probe tests and a fresh Tatoeba download): math, thread safety, sort order (157 accented Dutch words), line endings, the held-out split and the auto-commit guard for confusion alternatives all check out. Fixed: a bigram file that fails to parse threw past the `IOException` handler and made the language fall back to the legacy `data.json` list; it now logs and continues without word context. Fixed: the previous word was looked up four times per candidate; it is now looked up once per language (`LatinBigramModel.Context`), which cut p95 scoring time on the context sets from 168 to 68 us (EN) and from about 365 to 142 us (NL+EN). Added to the report: lowercase out-of-dictionary words without context and after predictive words. English changes 7 of 313 with no words before, 8 after "talk to" and 9 after "I went to the"; Dutch changes 3 and 5 after "ik ga naar de". So word context adds 1 or 2 wrong changes to an existing weakness with lowercase names, capped at 10 in the floors. Noted, not changed: the real-word sets only contain listed confusion pairs (see T-010); Witten-Bell uses the successor counts after pruning, about half the full numbers, and the weight sweep showed no sensitivity to that; Python drops a trailing apostrophe or hyphen that Kotlin keeps, which only costs the next word its context.
- 2026-09-24: Word context in `NoisyChannelLatinScorer`: per language P(word | previous) = lambda * pair probability + (1 - lambda) * word probability, lambda = N / (N + T) (Witten-Bell, `bigramBackoffWeight` 1.0; 0.5 to 4 changed the context sets by at most 1.2 points). Confusion alternatives (`ConfusionSets`: 41 English groups such as then/than and your/you're, 4 Dutch groups, and Dutch d/dt/t verb endings) join the candidates of a correctly spelled word at cost 3.0; cost 2 raised the real-word sets further but moved more correct words out of the first slot on clean text. On mixed keyboards the language weights now come from how well the recent words fit each language's pair model (at most 3 nats per word, each language keeps at least 5%); cap and floor between 2 and 5 and 2% and 10% made no measurable difference.
- 2026-09-24: Results (`research/benchmark-context-t012.md`), before -> after: context typos fixed EN 66.3 -> 77.1% (precision 99.2%), NL 62.1 -> 68.6% (98.7%), NL on NL+EN 56.2 -> 66.7% (98.8%), EN on NL+EN 58.2 -> 75.3% (99.0%); top-1 EN 85.8 -> 88.5%, NL 82.4 -> 85.5%. Real-word errors with the intended word first: EN 10.2 -> 71.2%, NL 0 -> 51.4%, never auto-committed. Out-of-dictionary words changed: 2 -> 1 on both sets (async no longer becomes sync). Clean text: 1 false correction per 7,999 EN words (trans -> trains, 0.13 per 1,000); a correctly typed word is not the first suggestion for 0.50 to 0.85% of words (end -> and, form -> from, names such as Sami -> same). The isolated-word sets, strength calibration and all Phase 1 floors are unchanged. p95 scoring time on the JVM rose from about 200 to 365 us on the NL+EN context sets.
- 2026-09-24: Two targets are not met and move to Phase 4 per the spec rule: NL context typos 68.6% (target 72%) and Dutch real-word errors 51.4% (target 60%). The Dutch context misses are mostly typos two edits away or with a dropped first letter (`blssenn` for bossen, `ijn` for zijn), which the touch model and candidate generation address; the Dutch real-word misses mostly need the word after the error (`Wordt je`, `jou boek`), which is not typed yet when the error is scored. English meets both targets.
- 2026-09-24: Floors added to `AutocorrectBenchmarkReportTest`: context EN >= 75%, NL >= 66%, NL on NL+EN >= 65%, EN on NL+EN >= 73%; real-word EN >= 60% and NL >= 45% first; no real-word error auto-committed; correct word not first <= 2% on clean text. `ContextScoringTest` (5 tests); full `ime` suite 524 tests pass.
- 2026-09-24: Emulator (debug build): `Hij word` shows "wordt" first and space keeps "word"; `I would rather then` shows "than" first and space keeps "then" (AC-010); `I would rather go home then` keeps "then" first, since "home then" is common; `we went to the stoer` becomes "store".
- 2026-09-24: Task created with Phase 3.
