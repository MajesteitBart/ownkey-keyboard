---
id: T-004
name: Probe editor targeting and safe replacement
status: ready
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: M
---

# Task: Probe editor targeting and safe replacement

## Description
Prove that OwnKey can safely identify and replace selected text, last dictated text, or bounded recent text before cursor.

## Acceptance Criteria
- [ ] Selected-text replacement path is tested.
- [ ] Last-dictation range tracking design is documented.
- [ ] Bounded recent-text fallback policy is documented.
- [ ] Failure cases leave original text untouched.
- [ ] Manual app matrix includes at least Mattermost, WhatsApp, Gmail, browser fields, and notes/Obsidian-like editor.

## Technical Notes
The editor replacement path is likely the highest implementation risk. Use fake provider output first so network/LLM behavior does not hide editor bugs.

## Definition of Done
- [ ] Probe result documented
- [ ] Implementation approach approved or risk escalated
- [ ] Follow-up implementation tasks split if feasible

## Evidence Log
- 2026-05-01: Existing editor code exposes text-before/after cursor and commit/delete primitives, but safe range replacement still needs proof.
