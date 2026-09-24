---
id: T-013
name: Next-word predictions from bigrams
status: blocked
workstream: WS-A
created: 2026-09-24T20:57:41Z
updated: 2026-09-24T20:57:41Z
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
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-011
---

# Task: Next-word predictions from bigrams

## Description

After a space, suggest the most likely next words from the bigram model of the active languages, merged with the personal n-gram predictions, instead of the same list of frequent words after every word.

## Acceptance Criteria

- [ ] After `ik` the Dutch predictions start with verbs such as "ben", "heb", "ga"; after `I` the English ones with "am", "have", "think" (AC-012).
- [ ] Next-word top-3 hit rate on the held-out sentences beats the current frequency-only predictions, per language.
- [ ] Personal n-gram predictions keep their priority for words the user typed before.
- [ ] Prediction latency p95 stays within the suggestion budget on the emulator.

## Traceability

- Story: US-006
- Acceptance criteria: AC-012

## Technical Notes

- Predictions for mixed subtypes weight each language's successors with the same sentence-based language check that T-008 uses for "I".

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 3.
