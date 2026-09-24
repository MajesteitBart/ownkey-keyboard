---
id: T-018
name: Mark autocorrections in the text
status: ready
workstream: WS-A
created: 2026-09-24T22:22:47Z
updated: 2026-09-24T22:22:47Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: medium
estimate: S
story_id: US-003
acceptance_criteria_ids: []
---

# Task: Mark autocorrections in the text

## Description

After an autocorrection, the text field should show that a word was changed and let the user get the original back, the way Android keyboards do it: the corrected word is committed with a suggestion span flagged as an autocorrection that carries the typed word.

## Acceptance Criteria

- [ ] Auto-committed corrections are committed with `SuggestionSpan.FLAG_AUTO_CORRECTION` and the original word as its suggestion, so standard text fields underline the word and offer the original on tap.
- [ ] Manual suggestion taps, casing-only changes ("I") and apostrophe forms keep committing plain text or follow the same rule consistently; nothing else about committing changes.
- [ ] Backspace right after an autocorrection still restores the typed word (AC-004).
- [ ] Checked in a standard Android text field and in Chrome on the emulator; editors that ignore the span behave as before.

## Traceability

- Story: US-003
- Acceptance criteria: none (Phase 5 in plan.md)

## Technical Notes

- The span is a platform mechanism: the editor draws the underline and handles the tap. Editors that do not support it simply ignore it.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 5.
