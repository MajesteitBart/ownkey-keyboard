---
timestamp: 2026-08-04T17:01:03Z
status: complete
task: T-005
stream: scoped-change
---

# Progress Update

## Completed

- Refined D-009 to split language behavior by path. Ordinary dictation keeps the explicit-language, else subtype, else `Auto` resolution. Voice-rewrite instruction transcription now sends no language hint at all and relies on provider auto-detection.
- Accepted D-010: the spacebar indicates the recognition language while a dictation session is active, with the language readable as text. Voice rewrite shows no cue, which is accurate because nothing is pinned there.
- Split AC-039 and added AC-040 and AC-041; replaced FR-059 with FR-059, FR-059a, and FR-059b.
- Reassigned coverage: T-005 asserts the absent language field on the instruction request, T-016 owns the cue and the corrected settings copy, T-001 recommends the spacebar treatment, T-018 measures unhinted short-utterance recognition against the hinted dictation baseline.

## In Progress

- Spec and plan remain `planned` with `probe_status: pending`.

## Blockers

- Unchanged: NC-002 persistent undo still blocks T-002, T-003, and all production work.

## Next Actions

- Resolve NC-002 to unblock T-002.
- Execute T-001.

## Notes

- The split resolves the cross-language tension recorded against the earlier form of D-009: an English keyboard with a Dutch instruction no longer sends an English hint.
- The cost moves to the monolingual case, where a correct hint would have helped and auto-detection is weakest on short utterances. This is the weakest assumption in the language design and is why T-018 records unhinted instruction recognition against a hinted baseline; if it proves worse, the reversal is to send the subtype hint for instructions too.
- Because the cue is scoped to dictation, the earlier problem of the spacebar not being composed while the Rewrite hub is open no longer applies.
