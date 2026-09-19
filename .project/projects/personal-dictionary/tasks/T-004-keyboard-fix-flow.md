---
id: T-004
name: Keyboard fix-a-word flow after dictation
status: done
workstream: WS-A
created: 2026-09-17T21:40:00Z
updated: 2026-09-17T21:40:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001, T-003]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-002
acceptance_criteria_ids: [AC-003, AC-009]
---

# Task: Keyboard fix-a-word flow after dictation

## Description

After dictation inserts text, offer a short-lived "Fix a word" chip in the smartbar. It opens a chooser where the user taps the misheard word (or a phrase), the word is selected in the host editor, the user retypes or dictates it with the ordinary keyboard, and Save stores the correction plus, optionally, the replacement as a vocabulary word. If the editor changed underneath, the flow hands the heard text to the settings page.

## Acceptance Criteria
- [x] Chip appears only after a successful ordinary insertion and disappears on typing, a new recording, another panel, or after 15 seconds.
- [x] Chooser supports single words and adjacent phrases; the action button names the selection.
- [x] Replacement preview follows the host editor; Save is disabled for blank or unchanged text.
- [x] Editor session, package, field and text are verified before selecting; failures fall back to the manual path.
- [x] Nothing from the transcript is persisted or logged outside the saved entry.

## Traceability
- Story: US-002
- Acceptance criteria: AC-003, AC-009

## Technical Notes

`DictationFixController.kt` (state machine, testable through `DictationFixEditorGateway`), `DictationFixPanel.kt` (chip, chooser, row), hooks in `Smartbar.kt`, `TextInputLayout.kt`, `KeyboardManager.kt`, `VoxtralDictationManager.insertionListener`.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-09-17: `DictationFixModelTest`, `DictationFixControllerTest` green (offer, chooser, retype, dictate-over, manual fallback, interrupts).
