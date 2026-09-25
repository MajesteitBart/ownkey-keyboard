---
id: T-001
name: In-repo autocorrect benchmark
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-25T14:23:05Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id:
acceptance_criteria_ids: []
---

# Task: In-repo autocorrect benchmark

## Description

Turn `research/baseline-harness/` into a JVM benchmark test that runs against the real Kotlin scoring code. Extract the current scoring from `LatinLanguageProvider` into a class with no Android dependencies, without changing behavior, so the benchmark measures what ships. Add the datasets that later gates depend on, and re-derive the provisional Phase 1 gates on data that does not share the scorer's error model.

This task enables every acceptance scenario but delivers none on its own.

## Acceptance Criteria

- [x] Current scoring lives in a pure-Kotlin class that `LatinLanguageProvider` delegates to, with identical output.
- [x] The harness datasets are ported verbatim, including the seed-42 typo generator, and the legacy scoring reproduces the harness baseline within 1 percentage point on each of them.
- [x] A tap-noise typo set per language is added: typos come from simulated tap positions around key centers, not from the key-adjacency model the reference scorer uses.
- [x] A clean-text set of at least 5,000 correctly spelled words per language from a license-compatible source.
- [x] An out-of-dictionary set of at least 300 words: names, product names, jargon, Dutch compounds and Dutch chat shorthand.
- [x] The benchmark prints, per set and per subtype (EN, NL, NL+EN): autocorrect right, wrong, precision, top-1, top-3, clean-text false corrections per 1,000 words, and p50/p95 time per word.
- [x] Moved to T-004: re-deriving the Phase 1 gates needs a first version of the new scorer on the tap-noise set.

## Traceability

- Story: none, enabling task
- Acceptance criteria: none directly

## Technical Notes

- Datasets live under `app/src/test/resources/autocorrect/`. No private typed text: hand-written or public sources only.
- JVM timings are only a relative signal. On-device latency gates are measured with `benchmark/`.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-25: Correction to the extraction entry: the copied `research/benchmark-legacy.md` ran on the first `oov.txt` of 294 words (0 changed). The set grew to 313 words later; the legacy scorer changes 0 of those 313 too (current `AutocorrectBenchmarkReportTest` output).
- 2026-09-24: Scoring extracted to `ime/nlp/latin/engine/` (`LatinText`, `LatinWordModel`, `LegacyLatinScorer`); `LatinLanguageProvider` delegates. `LegacyBaselineReproductionTest` (8 tests) reproduces every harness number within 0.06 points, including the seed-42 and seed-7 generators and completion reach. `AutocorrectBenchmarkReportTest` writes `app/build/reports/autocorrect-benchmark/legacy.md`, copied to `research/benchmark-legacy.md`: 0% autocorrection on every typo set, 0 clean-text false corrections on 7,999 EN and 7,993 NL words, 0 of 313 out-of-dictionary words changed. `:app:testDebugUnitTest --tests 'dev.patrickgold.florisboard.ime.nlp.*'`: all green.

- 2026-09-24: Task created from the baseline measurement and the independent review.
