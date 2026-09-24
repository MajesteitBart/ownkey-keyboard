---
id: T-003
name: Input-keyed commit path and trigger characters
status: ready
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T11:31:43Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: [T-002]
parallel: false
priority: high
estimate: S
story_id: US-001
acceptance_criteria_ids: [AC-004, AC-005, AC-008]
---

# Task: Input-keyed commit path and trigger characters

## Description

Make the auto-commit paths only apply a correction computed for the exact current word, and only on the right characters. Today `getAutoCommitCandidate()` returns the first eligible candidate from whichever async suggestion run finished last, and every non-alphabetic character triggers it. Neither causes damage yet, because nothing is eligible, but both will once T-004 lands. This task goes first.

## Acceptance Criteria

- [ ] Candidates, or a wrapper held by `NlpManager`, record the input they were computed for.
- [ ] When the latest list does not match the current word, the commit path decides synchronously for that word, using only in-memory data: no Room query, ContentResolver call or file load on the main thread.
- [ ] Autocorrect triggers on space and on token-ending `. , ! ? ; :` only. Apostrophe, hyphen, digits, `@` and `/` never trigger it, and tokens containing `@` or starting with `www.` or `http` are never corrected (AC-008).
- [ ] Enter keeps today's behavior and does not autocorrect.
- [ ] A devtools counter records auto-commits applied from a candidate list computed for another input. It reads 0 in a device session of fast typing.
- [ ] Synchronous decision time is measured on device and reported, with a target of under 5 ms p95.
- [ ] Undo and backspace restore still work: `AutocorrectUndoTrackerTest` passes and AC-004 holds on device.

## Traceability

- Story: US-001
- Acceptance criteria: AC-004, AC-005, AC-008

## Technical Notes

- Call sites: `KeyboardManager.handleSpace`, `handleHardwareKeyboardSpace`, the non-alphabetic character branch and the media-mode branch.
- `isUserDictionaryWord` goes through `DictionaryManager` today, which does Room and system user-dictionary queries. Keep a snapshot in memory and refresh it off the main thread.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created from code review of `NlpManager.suggest()` and `KeyboardManager`. The trigger-character issue came from the independent review.
