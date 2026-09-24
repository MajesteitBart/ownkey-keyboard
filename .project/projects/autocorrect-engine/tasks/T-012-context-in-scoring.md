---
id: T-012
name: Context in scoring
status: blocked
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T21:04:22Z
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
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-011
---

# Task: Context in scoring

## Description

Use the previous word in the scorer: interpolate the bigram probability with the unigram prior per language, keep the personal n-gram boost, and add confusion pairs (then/than, your/you're, word/wordt) as suggestion-only candidates for real words.

## Acceptance Criteria

- [ ] On the context typo sets, right corrections and top-1 improve over the no-context baseline, with precision at or above 98% on every set with at least 10 corrections.
- [ ] Context typo sets reach the spec's final recall targets where the signal allows: EN >= 75%, NL >= 72%, NL on the NL+EN subtype >= 65%. Misses are reported, and a target moves to Phase 4 when context cannot supply the signal.
- [ ] Real-word sets: the intended word is the first suggestion in at least 60% of sentences, and no real-word error is auto-committed (AC-011).
- [ ] Clean text stays at or below 0.3 false corrections per 1,000 words, and a confusion alternative is the first suggestion for at most 2% of correctly typed words.
- [ ] Phase 1 floors and strength calibration still pass on the isolated-word sets.

## Traceability

- Story: US-006
- Acceptance criteria: AC-010, AC-011

## Technical Notes

- Witten-Bell style interpolation per language: lambda(prev) = N(prev) / (N(prev) + k * T(prev)), with k tuned on the context sets.
- The previous word only counts inside the current sentence part; after . ! ? or a line break the sentence start `<s>` is the context.
- Real-word candidates never auto-commit; they only change the order of suggestions.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 3.
