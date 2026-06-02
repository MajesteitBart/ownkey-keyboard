---
id: T-002
name: Set latency budget and warm-start acceptance bar
status: blocked
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T13:11:21Z
linear_issue_id: 7153d4f5-e877-4d45-8b22-78421c1381b4
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: false
priority: high
estimate: S
workstream: WS-1
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001
---

# Task: Set latency budget and warm-start acceptance bar

## Description
Define the performance envelope for suggestion refresh, keyboard open, first-useful-suggestion readiness, and the fast-path layers that must stay lightweight in the predictive stack.

## Acceptance Criteria
- [ ] p95 suggestion and warm-start targets are explicit and measurable.
- [ ] The plan defines how to detect latency regressions separately from ranking-quality regressions.
- [ ] The latency plan states which responsibilities belong in decoder or compact-model layers versus richer neural layers.

## Technical Notes
Use representative device classes instead of one desktop-like benchmark only, and assume some stack layers may need n-gram or finite-state simplicity for speed. The public Gboard decoder and search-space work are the main anchors for keeping low-latency candidate-generation layers lightweight even when downstream ranking grows more neural. Sources: [1704.03987](https://arxiv.org/abs/1704.03987), [2410.15575](https://arxiv.org/abs/2410.15575).

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-69` (https://linear.app/bartvandermeeren/issue/BAR-69/t-002-set-latency-budget-and-warm-start-acceptance-bar).
- 2026-03-28: Task created during predictive typing planning pass.
