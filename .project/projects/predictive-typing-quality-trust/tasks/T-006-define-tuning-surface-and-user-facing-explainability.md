---
id: T-006
name: Define tuning surface and user-facing explainability
status: ready
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:35:00Z
linear_issue_id: 0268e1e8-49ef-44b7-87d1-c478a53826eb
github_issue:
github_pr:
depends_on: [T-003, T-005]
conflicts_with: []
parallel: false
priority: medium
estimate: S
---

# Task: Define tuning surface and user-facing explainability

## Description
Specify the minimum settings, copy, and explainability needed so users can tune predictive typing, personalization, and autocorrect behavior without confusion.

## Acceptance Criteria
- [ ] The proposal defines the minimum viable tuning controls and when they should appear.
- [ ] User-facing wording is clear about speed-versus-control trade-offs.
- [ ] Privacy and personalization controls are understandable without exposing model jargon.

## Technical Notes
Avoid feature sprawl. Preference surfaces should clarify, not overwhelm, especially when personalization and trust controls intersect.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-73` (https://linear.app/bartvandermeeren/issue/BAR-73/t-006-define-tuning-surface-and-user-facing-explainability).
- 2026-03-28: Task created during predictive typing planning pass.
