---
id: T-001
name: Instrument latency and suggestion baseline
status: review
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T22:05:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Instrument latency and suggestion baseline

## Description
Add privacy-safe metrics for suggestion latency p95, top-3 acceptance rate, keystrokes per word, false autocorrect ratio, and undo-autocorrect frequency.

## Acceptance Criteria
- [ ] Baseline metrics are captured in debug/internal builds.
- [ ] No private typed content is logged.

## Technical Notes
This task enables KPI-driven sequencing and validation for all subsequent tasks.

## Definition of Done
- [x] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
- 2026-02-25: Added aggregate-only typing speed metrics instrumentation for suggestion latency p95, top-3 acceptance, keystrokes/word, autocorrect false ratio, and undo frequency.
- 2026-02-25: Added unit tests for `TypingSpeedMetrics` aggregation math and word-boundary behavior.
- 2026-02-25: Verification attempt blocked in this environment (`JAVA_HOME`/`java` missing), so Gradle tests/compile could not run.
