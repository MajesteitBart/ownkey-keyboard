---
id: T-001
name: Speech dictionary storage and transcript cleanup
status: done
workstream: WS-A
created: 2026-09-17T21:40:00Z
updated: 2026-09-17T21:40:00Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-002, AC-003, AC-004, AC-005, AC-006, AC-010]
---

# Task: Speech dictionary storage and transcript cleanup

## Description

Port the Windows cleanup rules to pure Kotlin and add a dedicated, file-backed speech dictionary repository with stable ids, saved order, case-insensitive uniqueness, typed filler settings, atomic writes and versioned export/restore.

## Acceptance Criteria
- [x] All 15 Windows cleanup assertions pass on the JVM, plus Unicode, apostrophe/hyphen, literal replacement, cascade, paragraph and custom-override cases.
- [x] Entries persist through process recreation; an explicitly empty language list stays empty; unreadable files are kept and reported.
- [x] Restore validates before writing; newer or invalid documents are rejected without clearing entries; merge and erase behave as specified.

## Traceability
- Story: US-001, US-002, US-003, US-004
- Acceptance criteria: AC-002, AC-003, AC-004, AC-005, AC-006, AC-010

## Technical Notes

`ime/text/dictation/dictionary/`: `FillerLanguages.kt`, `TranscriptCleanup.kt`, `SpeechDictionaryDocument.kt`, `SpeechDictionaryRepository.kt`. The repository replaces the Room proposal from the plan; see decisions.md.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-09-17: `./gradlew :app:testDebugUnitTest` 415 tests, 0 failures (includes `TranscriptCleanupTest`, `SpeechDictionaryRepositoryTest`).
