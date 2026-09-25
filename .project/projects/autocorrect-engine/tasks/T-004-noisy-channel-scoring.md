---
id: T-004
name: Noisy-channel scoring on the existing candidate indexes
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-25T14:23:05Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001, T-003]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-003, AC-004, AC-009]
---

# Task: Noisy-channel scoring on the existing candidate indexes

## Description

Replace `calculateConfidence`, `rankSuggestionCandidate` and the high-certainty policy math with a noisy-channel posterior: log frequency prior, weighted edit cost from key geometry, and the literal input competing as an unknown word. Keep the current delete index and prefix index. Wire it in behind a devtools switch next to the legacy scoring.

## Acceptance Criteria

- [x] Before tuning, the Phase 1 gates in `spec.md` are re-derived from the first version's results on the tap-noise sets, with the reasons recorded in `decisions.md`.
- [x] Error costs derive from key-center distances of the active layout.
- [x] Completions and corrections enter the posterior as separate sources. A completion pays an omission cost per missing letter, so `becaus` autocorrects to "because" (AC-009).
- [x] Typo lookup starts at 3-letter inputs. The hard-coded 4-letter limit is gone.
- [x] A capitalized unknown word in mid-sentence gets a higher unknown-word prior.
- [x] Mixed subtypes combine languages through language weights inside the prior, not through post-hoc score multipliers.
- [x] Dictionary, user-dictionary, personal-dictionary and never-correct words block auto-commit (AC-003).
- [x] `spell()` and suggestion ranking use the same scorer.
- [x] The benchmark meets every provisional Phase 1 benchmark gate in `spec.md`, as updated by T-001, for EN, NL and NL+EN (AC-001).
- [x] The devtools switch cannot enable the new scoring unless T-003's input-keyed commit path is active. (T-003 is merged and always active; the switch now selects the legacy scorer instead.)
- [x] On device, a correction fires on space and one backspace restores the typed word (AC-004, moved here from T-003).

## Traceability

- Story: US-001, US-002, US-003
- Acceptance criteria: AC-001, AC-003, AC-004, AC-009

## Technical Notes

- Reference math: `research/baseline-harness/engine.js`, `suggestNoisyChannel` and `channelCost`. The review showed that this scorer on the current indexes matches the full-vocabulary, distance-2 reference.
- The unknown-word prior is the main recall-versus-precision knob. A lower prior raised recall a lot but dropped vocabulary-uniform precision to 91%. Tune on the tap-noise set.
- The reference changed `idd` to "did" and `wss` to "was". Dutch chat shorthand needs a small allow-list or a higher prior for 3-letter inputs.
- Dogfood gates (undo rate, on-device latency, mismatch counter) belong to the phase gate in `plan.md`, not to this task.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `NoisyChannelLatinScorer` (engine package) with `KeyGeometry` (vertical distance in rows), `ChatShorthand`, `AutocorrectSettings` and `LatinWordModel.totalFrequency`. `KeyboardGeometrySource` reads letter-key bounds from the laid-out character keyboard and falls back to standard phone QWERTY. `LatinLanguageProvider` uses the new scorer for suggestions, on-the-spot decisions and `spell()`; speech-dictionary words, user-dictionary words and chat shorthand block autocorrect; app profiles shift the threshold. Devtools switch "Use legacy autocorrect engine". Benchmark (`research/benchmark-noisy-channel-t004.md`): tap-noise 70.6% EN / 68.5% NL / 60.3% NL+EN right at 98.8 to 99.4% precision, top-1 87.9 / 86.9%, real EN 66%, real NL 81.6 to 85.8% with 0 wrong, clean text at most 0.13 per 1,000, 1 to 2 of 313 unknown words changed. Floors enforced in `AutocorrectBenchmarkReportTest`; `NoisyChannelAcceptanceTest` covers AC-001, AC-003, AC-009 and the spec examples. 488 `ime` unit tests pass.
- 2026-09-24: Emulator (API 35, debug build, Chrome textarea, soft-key taps): `teh becuase thsi wrld` became "The because this world"; `becaus` became "Because" (AC-009) and one backspace restored "Becaus" (AC-004), after which revert memory kept it; `webhook` and `voxtral` stayed (AC-003). On-the-spot decisions took 2 to 7 ms. A first device run exposed a race: `tryWithLock` on the model and provider locks skipped autocorrect whenever a background suggestion run held them; fixed with a volatile model snapshot and the constant provider map.

- 2026-09-24: Task created. Replaces the first-draft trie task after the independent review.
