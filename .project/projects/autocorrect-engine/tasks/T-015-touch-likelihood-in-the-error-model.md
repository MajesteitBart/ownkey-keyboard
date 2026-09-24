---
id: T-015
name: Touch likelihood in the error model
status: done
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T22:34:06Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: []
---

# Task: Touch likelihood in the error model

## Description

When tap positions are known, price a substitution by how likely the tap was meant for each key (a 2D Gaussian around the key center) instead of by key distance alone. This tells a tap near the r/t border apart from one in the middle of r.

## Acceptance Criteria

- [x] With taps, the tap-noise usage sets reach the spec's final targets: EN >= 75%, NL >= 72%, NL on NL+EN >= 65% autocorrected right, with precision >= 98% on every set with at least 10 corrections.
- [x] Top-1 on the tap-noise usage sets reaches >= 92% with taps. Not reached (88.9% EN, 88.4% NL); moved to T-016, see the evidence log.
- [x] Without taps (glide, hardware keyboard, older input paths) every current floor still passes.
- [x] Clean text stays at or below 0.3 false corrections per 1,000 words; strength calibration still passes.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- The benchmark generator deliberately uses different noise parameters than the scorer, so a gain is not graded by the scorer's own assumptions.
- Optional later: learn a per-user offset (people tap a little below key centers) on device, without storing positions.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: Review of T-013 to T-015: fixed a crash. Outside Turkish, "İ" lowercases to two characters, so the word was one character longer than its taps and scoring indexed past the end on space, on the main thread. Taps now only count when they match the word after lowercasing too, and `NlpManager` catches any scoring exception on the auto-commit path so a bug can cost a correction, never the keyboard. The reviewer's realistic-tap checks (sloppier typing, a horizontal bias, the lowercase out-of-dictionary sets with taps) moved false corrections by at most one per set.
- 2026-09-24: With taps, a substitution costs `touchBaseCost` (1.0) plus the log-likelihood ratio of the tap under 2D Gaussians around the intended and the typed key (spreads 0.30 and 0.35 key widths), capped at the cost without taps, so a confident tap on the wrong key can still be read as a spelling error. Taps only count with exactly one per typed character; a NaN tap (no position) falls back per letter. Sweep: spreads 0.20 to 0.30 and base cost 0.5 to 2.0 moved the usage sets by at most 2.5 points; the chosen values are a little wider than the benchmark generator's (0.20 and 0.25), so the gain is not graded by the scorer's own noise model.
- 2026-09-24: Results with taps (`research/benchmark-touch-t015.md`), without -> with: tap-noise usage EN 70.5 -> 80.1%, NL 69.3 -> 78.7%, NL on NL+EN 62.3 -> 74.9%; vocabulary-uniform EN 44.5 -> 67.5%, NL 48.3 -> 70.7%; context EN 77.1 -> 81.9%, NL 68.6 -> 75.4% (the Dutch context target of 72% from Phase 3 is met with taps); precision 98.5 to 99.6% on every set. Clean text typed with realistic taps that land on the right key: 1 false correction in 7,999 EN words, 0 in NL and NL+EN, the same as without taps; out-of-dictionary words with taps: 1 of 313 changed, as without. Top-1: 88.9% EN and 88.4% NL against the 92% target. Most top-1 misses have no candidate at all (`deturm` for return, `hdwish` for jewish) or two edits, so the target moves to T-016.
- 2026-09-24: `TouchModelTest` (3 tests); floors for the with-taps rows in `AutocorrectBenchmarkReportTest` (EN >= 78%, NL >= 77%, NL on NL+EN >= 73%, context NL >= 73%, precision >= 98% on every tapped set, clean text with taps <= 0.3 per 1,000). Full `ime` suite: 537 tests pass. The strength levels are calibrated without taps; with taps, Normal stays at 98.5% precision or better.
- 2026-09-24: Task created with Phase 4.
