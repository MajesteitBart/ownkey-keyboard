---
id: T-005
name: Define personalization learning policy and privacy guardrails
status: blocked
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:35:00Z
linear_issue_id: a2397a74-b122-46cc-bb62-a33a11676f4d
github_issue:
github_pr:
depends_on: [T-001, T-004]
conflicts_with: []
parallel: false
priority: high
estimate: M
workstream: WS-3
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-001, T-004
---

# Task: Define personalization learning policy and privacy guardrails

## Description
Define when user terms should be learned, when they should decay or be suppressed, and which privacy boundaries must never be crossed in a future on-device or federated-learning setup.

## Acceptance Criteria
- [ ] Promotion, decay, and suppression rules are explicit.
- [ ] The policy forbids raw typed-content logging and clarifies on-device learning boundaries.
- [ ] The plan states how future federated-learning or differential-privacy approaches could fit without weakening user trust.

## Technical Notes
Learning speed should optimize for trust and usefulness, not vocabulary volume. The note basis suggests personalization quality is a major product differentiator, but only when privacy guarantees stay credible.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-72` (https://linear.app/bartvandermeeren/issue/BAR-72/t-005-define-personalization-learning-policy-and-privacy-guardrails).
- 2026-03-28: Task created during predictive typing planning pass.
