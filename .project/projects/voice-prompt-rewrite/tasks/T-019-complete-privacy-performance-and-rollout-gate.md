---
id: T-019
name: Complete privacy performance and rollout gate
status: blocked
workstream: WS-E
created: 2026-08-04T13:27:38Z
updated: 2026-08-06T07:03:58Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-018, T-021]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-009
acceptance_criteria_ids: [AC-007, AC-013, AC-020, AC-022, AC-023, AC-027, AC-035, AC-036]
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-018
---

# Task: Complete privacy performance and rollout gate

## Description

Perform final privacy/logging/temporary-data, lifecycle, typing/dictation/preset regression, performance, release, and rollback review and record an evidence-backed go/no-go recommendation.

## Acceptance Criteria

- [ ] Secure-field tests pass, and the D-007 incognito gate is verified to block dictation, preset rewrite, and voice rewrite under forced, host-app dynamic, and user-toggled incognito with availability restored on leaving incognito.
- [ ] Audio, target, instruction, result, provider bodies, and content-derived fingerprints are absent from production logs, telemetry, fixtures, and evidence.
- [ ] Shipped incognito and `FORCE_ON` copy matches the enforced behavior; the app does not promise a gate it does not enforce or enforce one it does not disclose.
- [ ] Temporary audio is app-private and deleted after success, error, empty speech, timeout, cancellation, field switch, keyboard hide, input restart, and process/lifecycle recovery.
- [ ] Keyboard open, ordinary input, idle smartbar, suggestions/autocorrect, and normal dictation benchmarks remain within the spec's p95 regression threshold; supported-device power traces contain the expected content-free operation spans and no unexplained idle wake-up regression.
- [ ] Tap-to-dictate, external-IME fallback, preset rewrite, provider settings, retry, cancellation, stale-target blocking, and single-recorder regression suites pass.
- [ ] Internal/debug and closed/beta gates, content-free monitoring, unsupported-case handling, and production enablement criteria are documented.
- [ ] Rollback can disable long-press/card entry without breaking normal dictation/presets and requires no content/schema/server migration.
- [ ] Release recommendation, remaining risks, validation output, and handoff evidence are recorded in Delano; no rollout occurs on a stale-target, secure-field, concurrent-recorder, or content-leak defect.

## Traceability

- Story: US-002, US-009, US-010, and US-015.
- Acceptance criteria: AC-007, AC-013, AC-020, AC-022, AC-023, AC-027, AC-035, AC-036, plus the spec's privacy and performance success metrics.

## Technical Notes

This task authorizes neither external sync nor store rollout by itself. Follow the release workflow and obtain the appropriate approval before any production mutation. Content-safe crash/performance evidence is acceptable; production content telemetry is not.

## Definition of Done

- [ ] Privacy and cleanup review passes.
- [ ] Performance/regression gates pass.
- [ ] Rollout/rollback recommendation recorded.
- [ ] Delano validation and handoff checks pass.

## Evidence Log

- 2026-08-04: Task created during delivery decomposition.
- 2026-08-06T07:03:58Z: T-021 completed the local battery-observability implementation and gates. This task remains blocked on T-018 plus supported physical-device power/trace evidence; no device was attached during T-021 closeout.
