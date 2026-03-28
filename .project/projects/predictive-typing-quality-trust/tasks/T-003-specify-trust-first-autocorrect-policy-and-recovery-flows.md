---
id: T-003
name: Specify trust-first autocorrect policy and recovery flows
status: ready
created: 2026-03-28T11:19:17Z
updated: 2026-03-28T11:35:00Z
linear_issue_id: fbc0bfe5-f4e7-486b-9db6-46bfeb3f1372
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Specify trust-first autocorrect policy and recovery flows

## Description
Define confidence thresholds, suppression rules, undo behavior, and backspace recovery for predictive typing corrections across literal typing, completion, and autocorrect flows.

## Acceptance Criteria
- [ ] The policy defines when OwnKey should autocorrect, suggest only, or do nothing.
- [ ] Recovery paths cover undo, backspace restore, and never-correct suppression.
- [ ] The policy distinguishes decoder certainty from language-model certainty when deciding to auto-apply a correction.

## Technical Notes
Trust failures should be treated as first-class product defects, not only accuracy misses, especially when decoder behavior and language modeling disagree.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-03-28: Synced to Linear as `BAR-70` (https://linear.app/bartvandermeeren/issue/BAR-70/t-003-specify-trust-first-autocorrect-policy-and-recovery-flows).
- 2026-03-28: Task created during predictive typing planning pass.
