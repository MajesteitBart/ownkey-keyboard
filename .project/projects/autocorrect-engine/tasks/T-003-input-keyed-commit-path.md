---
id: T-003
name: Input-keyed commit path and trigger characters
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T19:16:27Z
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

- [x] Candidates, or a wrapper held by `NlpManager`, record the input they were computed for.
- [x] When the latest list does not match the current word, the commit path decides synchronously for that word, using only in-memory data: no Room query, ContentResolver call or file load on the main thread.
- [x] Autocorrect triggers on space and on token-ending `. , ! ? ; :` only. Apostrophe, hyphen, digits, `@` and `/` never trigger it, and tokens containing `@` or starting with `www.` or `http` are never corrected (AC-008).
- [x] Enter keeps today's behavior and does not autocorrect.
- [x] A candidate from a list computed for another input can no longer be auto-committed (`AutoCommitSelector`, unit-tested). The debug-log export counts decisions made on the spot instead, with their p95 latency. Checking the count during real fast typing moved to the Phase 1 dogfood gate, because adb taps (about 100 ms each) never outrun the suggestion run on the emulator.
- [x] Synchronous decision time is recorded on device (debug-log export) and logged per decision. The on-device p95 is part of the Phase 1 dogfood gate for the same reason. JVM scorer time per word: p50 0.01 to 0.04 ms, p95 0.03 to 0.12 ms.
- [x] Undo and backspace restore still work: `AutocorrectUndoTrackerTest` passes. AC-004 on device moved to T-004, the first task where a correction actually fires.

## Traceability

- Story: US-001
- Acceptance criteria: AC-004, AC-005, AC-008

## Technical Notes

- Call sites: `KeyboardManager.handleSpace`, `handleHardwareKeyboardSpace`, the non-alphabetic character branch and the media-mode branch.
- `isUserDictionaryWord` goes through `DictionaryManager` today, which does Room and system user-dictionary queries. Keep a snapshot in memory and refresh it off the main thread.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `NlpManager` keeps a `WordSuggestionBatch` (input plus candidates); `autoCommitCandidateFor(content)` replaces `getAutoCommitCandidate()` at all four call sites and uses `AutoCommitSelector`. On mismatch the provider's new `decideAutoCommit` runs with in-memory data only: loaded models via `tryWithLock`, a user-dictionary snapshot refreshed off the main thread, `PersonalNgramStore.continuationScoreIfLoaded`. `AutocorrectTriggerPolicy` limits triggers to space and `. , ! ? ; :`, skips tokens with `@`, `/`, digits, inner dots or a www/http prefix, and turns autocorrect off in password, e-mail, URL and person-name fields and with `flagTextNoSuggestions`. Right after an accepted suggestion (phantom space) nothing is corrected. Tests: `AutocorrectTriggerPolicyTest`, `AutoCommitSelectorTest`, `GuardedByLockTest`; 477 `ime` unit tests pass.
- 2026-09-24: Device check found an existing freeze. Typing right after the keyboard process started produced an ANR (emulator trace: main thread in `AbstractEditorInstance.commitTextInternal` → `runBlocking`, waiting for the `NlpManager` provider lock, which `preload()` held for the whole dictionary load). Fixed: `preload()` holds the lock only to look providers up, `LatinLanguageProvider` loads each dictionary single-flight, and `providerForcesSuggestionOn` no longer blocks. After the fix the same scenario (fresh install, focus, immediate key burst) ran 3 times without an ANR and without lost text. Soft-key typing logged `Autocorrect decision source=BATCH applies=false` (the legacy scorer never fires). Known limitation: hardware-keyboard letters bypass the IME, so on hardware space the editor content can lag and autocorrect is skipped.

- 2026-09-24: Task created from code review of `NlpManager.suggest()` and `KeyboardManager`. The trigger-character issue came from the independent review.
