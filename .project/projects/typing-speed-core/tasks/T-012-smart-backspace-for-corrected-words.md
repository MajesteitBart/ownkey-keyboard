---
id: T-012
name: Add smart backspace recovery for corrected words
status: ready
created: 2026-02-25T19:38:38Z
updated: 2026-02-25T19:38:38Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-004]
conflicts_with: []
parallel: true
priority: medium
estimate: M
---

# Task: Add smart backspace recovery for corrected words

## Description
Make backspace context-aware so recently autocorrected words can be restored quickly.

## Acceptance Criteria
- [ ] Backspace restores corrected token form in expected contexts.
- [ ] Standard backspace behavior remains unchanged elsewhere.

## Technical Notes
Share correction history model with one-tap undo behavior.

## Definition of Done
- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log
- 2026-02-25: Task created.
