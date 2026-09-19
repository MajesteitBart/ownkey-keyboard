---
id: T-002
name: Personal dictionary settings, links and backup
status: done
workstream: WS-A
created: 2026-09-17T21:40:00Z
updated: 2026-09-17T21:40:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-004
acceptance_criteria_ids: [AC-005, AC-006, AC-010, AC-011]
---

# Task: Personal dictionary settings, links and backup

## Description

Add the Personal dictionary page under AI settings with the Windows-parity add form (one field, or heard/should-be when correcting a misspelling), a searchable entry list with edit and undoable delete, filler-word controls with per-language inventories and protections, cloud and on-device hint controls with disclosure, and a link from the typing dictionaries page. Add a versioned personal dictionary section to backup and restore.

## Acceptance Criteria
- [x] Add, edit, delete with undo, and validation messages for blank, identical and duplicate entries.
- [x] Filler switch, five language checkboxes with removed and kept words, custom words, live preview of removed words.
- [x] Cloud hint mode (automatic, prompt, off) and on-device hint toggle with hotword budget status.
- [x] Backup writes `speech/personal_dictionary.json`; restore validates, merges or replaces, and leaves data alone when the section is missing.
- [x] English and Dutch copy.

## Traceability
- Story: US-004
- Acceptance criteria: AC-005, AC-006, AC-010, AC-011

## Technical Notes

`app/settings/voxtral/SpeechDictionaryScreen.kt`, route `settings/voxtral/dictionary` with optional `heard` query, `DictionaryScreen.kt` link, `BackupScreen.kt`/`RestoreScreen.kt` section.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-09-17: debug build compiles; emulator smoke run recorded in the progress update.
