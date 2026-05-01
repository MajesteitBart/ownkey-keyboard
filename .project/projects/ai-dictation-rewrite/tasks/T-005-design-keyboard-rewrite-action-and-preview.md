---
id: T-005
name: Design keyboard rewrite action and preview UX
status: ready
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001, T-004]
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Design keyboard rewrite action and preview UX

## Description
Define where the rewrite button lives and how preview/apply/cancel states work in the keyboard.

## Acceptance Criteria
- [ ] Entry point is chosen: smartbar action, actions overflow, long-press voice key, or another explicit route.
- [ ] Loading, preview, apply, cancel, and error states are specified.
- [ ] Copy explains cloud/local processing clearly.
- [ ] UX does not block normal typing after failure or cancel.

## Technical Notes
Preview-first is the current decision. A one-tap auto-apply shortcut can be considered only after dogfood trust is high.

## Definition of Done
- [ ] UX decision recorded
- [ ] Implementation task split is clear
- [ ] Edge cases documented

## Evidence Log
- 2026-05-01: Task created.
