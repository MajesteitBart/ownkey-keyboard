---
id: T-015
name: Touch likelihood in the error model
status: ready
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T21:52:01Z
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

- [ ] With taps, the tap-noise usage sets reach the spec's final targets: EN >= 75%, NL >= 72%, NL on NL+EN >= 65% autocorrected right, with precision >= 98% on every set with at least 10 corrections.
- [ ] Top-1 on the tap-noise usage sets reaches >= 92% with taps.
- [ ] Without taps (glide, hardware keyboard, older input paths) every current floor still passes.
- [ ] Clean text stays at or below 0.3 false corrections per 1,000 words; strength calibration still passes.

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- The benchmark generator deliberately uses different noise parameters than the scorer, so a gain is not graded by the scorer's own assumptions.
- Optional later: learn a per-user offset (people tap a little below key centers) on device, without storing positions.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 4.
