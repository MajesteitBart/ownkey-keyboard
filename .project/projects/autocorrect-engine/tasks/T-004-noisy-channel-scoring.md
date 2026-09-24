---
id: T-004
name: Noisy-channel scoring on the existing candidate indexes
status: blocked
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001, T-003
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T18:38:49Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001, T-003]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-003, AC-009]
---

# Task: Noisy-channel scoring on the existing candidate indexes

## Description

Replace `calculateConfidence`, `rankSuggestionCandidate` and the high-certainty policy math with a noisy-channel posterior: log frequency prior, weighted edit cost from key geometry, and the literal input competing as an unknown word. Keep the current delete index and prefix index. Wire it in behind a devtools switch next to the legacy scoring.

## Acceptance Criteria

- [ ] Before tuning, the Phase 1 gates in `spec.md` are re-derived from the first version's results on the tap-noise sets, with the reasons recorded in `decisions.md`.
- [ ] Error costs derive from key-center distances of the active layout.
- [ ] Completions and corrections enter the posterior as separate sources. A completion pays an omission cost per missing letter, so `becaus` autocorrects to "because" (AC-009).
- [ ] Typo lookup starts at 3-letter inputs. The hard-coded 4-letter limit is gone.
- [ ] A capitalized unknown word in mid-sentence gets a higher unknown-word prior.
- [ ] Mixed subtypes combine languages through language weights inside the prior, not through post-hoc score multipliers.
- [ ] Dictionary, user-dictionary, personal-dictionary and never-correct words block auto-commit (AC-003).
- [ ] `spell()` and suggestion ranking use the same scorer.
- [ ] The benchmark meets every provisional Phase 1 benchmark gate in `spec.md`, as updated by T-001, for EN, NL and NL+EN (AC-001).
- [ ] The devtools switch cannot enable the new scoring unless T-003's input-keyed commit path is active.

## Traceability

- Story: US-001, US-002, US-003
- Acceptance criteria: AC-001, AC-003, AC-009

## Technical Notes

- Reference math: `research/baseline-harness/engine.js`, `suggestNoisyChannel` and `channelCost`. The review showed that this scorer on the current indexes matches the full-vocabulary, distance-2 reference.
- The unknown-word prior is the main recall-versus-precision knob. A lower prior raised recall a lot but dropped vocabulary-uniform precision to 91%. Tune on the tap-noise set.
- The reference changed `idd` to "did" and `wss` to "was". Dutch chat shorthand needs a small allow-list or a higher prior for 3-letter inputs.
- Dogfood gates (undo rate, on-device latency, mismatch counter) belong to the phase gate in `plan.md`, not to this task.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created. Replaces the first-draft trie task after the independent review.
