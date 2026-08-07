---
timestamp: 2026-08-04T17:01:03Z
status: complete
task: T-005
stream: scoped-change
---

# Progress Update

## Completed

- Accepted D-009: the transcription language hint defaults to the active keyboard subtype's primary language. Explicit user language still wins, and an explicit `Auto` choice sends no hint. Ordinary dictation and voice-rewrite instruction transcription resolve it through one path.
- Added FR-059 through FR-061 and AC-039, and recorded that the subtype language governs only what is heard — never the rewrite output language, which stays on D-008.
- Carved transcription language-hint resolution out of the ordinary-dictation out-of-scope line and into scope, because this changes request construction for existing users.
- Assigned resolution logic to T-005 via the `languageHintProvider` seam, the settings surface to T-016, and three-state verification to T-018.

## In Progress

- Spec and plan remain `planned` with `probe_status: pending`.

## Blockers

- Unchanged: NC-002 persistent undo still blocks T-002, T-003, and all production work.

## Next Actions

- Resolve NC-002 to unblock T-002.
- Execute T-001.

## Notes

- `Auto` must become an explicit choice. Today an empty field means auto-detect; once empty means subtype language, auto-detect becomes unreachable unless the setting offers it deliberately. This is captured as a footgun and as an acceptance criterion rather than left to implementation.
- This decision trades against D-008's cross-language case: an English keyboard with a Dutch instruction now sends an English hint. The remedies are switching subtype or choosing `Auto`, both explicit. T-018 measures the cost across all three states before release.
