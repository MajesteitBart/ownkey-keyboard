---
id: T-017
name: Add cross-flow automated test suite
status: done
workstream: WS-E
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T14:14:26Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-006, T-007, T-011, T-013, T-015]
conflicts_with: []
parallel: true
priority: high
estimate: L
story_id: US-003
acceptance_criteria_ids: [AC-001, AC-002, AC-003, AC-013, AC-020, AC-022, AC-023, AC-027, AC-029, AC-033, AC-042]
---

# Task: Add cross-flow automated test suite

## Description

Build reusable fake editor/input-connection, recorder, transcription, rewrite-provider, lifecycle, and clock fixtures and use them to verify the complete voice-rewrite safety/state contract plus normal dictation and preset rewrite regressions.

## Acceptance Criteria

- [x] Test fixtures model synchronous/asynchronous Select All, selection mutation, field/package/session change, secure/raw/empty editors, recorder levels, provider delay/failure, and virtual time without real content or secrets.
- [x] End-to-end tests cover long-press target resolution through recording, transcription, rewriting, review, verified replacement, success, and idle.
- [x] Failure/cancellation tests cover every preflight and async boundary and prove host text unchanged, temporary data removed, and stale completions ignored.
- [x] Timing tests verify 900 ms success, five-second error reset, immediate retry, newer outcome replacement, and component/session disposal deterministically.
- [x] Waveform tests prove no clock-only movement, silence baseline, speech distinction, bounded history, pause, saturation, and reduced motion.
- [x] Regression tests prove tap-to-dictate, one transcript commit, preset order/target/invocation, and ordinary typing-critical behavior are unchanged.
- [x] Language tests assert the fixed policy carries the source-language rule and that no transcript, subtype, or locale value reaches an output-language decision; provider-dependent output is left to the T-018 matrix rather than asserted against a fake model.
- [x] The chosen Compose/state test seam is documented and the app test command passes consistently without network access.

## Traceability

- Story: US-003, US-009, US-012, and US-014.
- Acceptance criteria: AC-001, AC-002, AC-003, AC-013, AC-020, AC-022, AC-023, AC-027, AC-029, AC-033, AC-042; all other scenarios receive component-level coverage.

## Technical Notes

Prefer pure reducers and injected interfaces over application-singleton tests. If Compose test dependencies are enabled, keep them scoped to the app module and document runtime cost; otherwise validate composables through probe-approved state/render contracts plus targeted Android interaction tests.

## Definition of Done

- [x] Reusable fixtures implemented.
- [x] Cross-flow and regression suites implemented.
- [x] Test command passes repeatedly.
- [x] Coverage gaps are explicitly documented.

## Evidence Log

- 2026-08-18: Review remediation removed the duplicated test-only contract probe and added production-class regressions for late terminal callbacks, cancellation during transcription, re-record permission/provider revocation, sampler cadence, paused countdown visibility, recognition-cue lifecycle, accessibility/pointer exclusivity, backward-clock cleanup rescheduling, and thrown recorder failures. A forced full rerun passed 278 tests, followed by an exact final-source run of 280 tests across 40 suites with 0 failures and 0 skips.

- 2026-08-05T14:14:26Z: Five deterministic cross-flow scenarios added; 271 app JVM tests across 39 suites pass with zero failures/skips; coverage seam and T-018/T-019 carry-forward documented in evidence/t017-cross-flow-automated-verification.md

- 2026-08-05T14:04:18Z: Begin dependency-safe WS-E cross-flow verification

- 2026-08-05T14:04:18Z: WS-B, WS-C, and WS-D dependencies are complete; verification work can begin

- 2026-08-04: Task created during delivery decomposition.
- 2026-08-05: Added a five-scenario production-seam cross-flow suite and reusable deterministic editor/recorder/provider/clock fixtures. `:app:testDebugUnitTest` passed with 271 tests, 0 failures, and 0 skipped; evidence and the T-018/T-019 coverage boundary are recorded in `evidence/t017-cross-flow-automated-verification.md`.
