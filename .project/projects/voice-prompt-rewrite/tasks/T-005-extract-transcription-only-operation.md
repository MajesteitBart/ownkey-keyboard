---
id: T-005
name: Extract transcription-only operation
status: done
workstream: WS-B
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:07:55Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-004]
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-014, AC-015, AC-016, AC-039, AC-040]
---

# Task: Extract transcription-only operation

## Description

Refactor internal dictation so a completed recording can be transcribed and returned to a caller without committing text, while ordinary dictation explicitly performs its existing commit step after a successful transcript.

## Acceptance Criteria

- [x] A transcription-only caller receives trimmed text or a typed empty/failure/cancel result and no editor mutation occurs on any path.
- [x] Ordinary dictation still commits one successful transcript exactly once and preserves its configured provider, endpoint, model, mock, and external-fallback behavior.
- [x] Ordinary dictation resolves its hint as explicit user language, else the active keyboard subtype's primary language, else no hint when `Auto` is chosen.
- [x] The transcription-only path used by voice rewrite sends no language field under any dictation language setting or subtype, and this is asserted on the built request rather than inferred.
- [x] `Auto` is representable distinctly from an unset value so provider auto-detection stays reachable after the default changes.
- [x] Subtype resolution happens where the transcription request is built, leaving the stored preference value unchanged so the `WearVoxtralSync` payload keeps its current meaning.
- [x] Language resolution is unit-tested for dictation across explicit, unset, and `Auto` values and across subtype switches, and for the instruction path across the same inputs proving the field is always absent, without network access.
- [x] Empty/no-speech transcription is distinct from provider/configuration/transport failure and cannot start a rewrite request.
- [x] Cancellation during stop/read or transcription suppresses every later callback and removes temporary audio.
- [x] Tests with fake recorder/transcription/editor dependencies prove no-commit and ordinary-commit behavior without network access.

## Traceability

- Story: US-001, US-003, and US-004.
- Acceptance criteria: AC-014, AC-015, AC-016, AC-039, AC-040.

## Technical Notes

Do not expose `stopAndInsertTranscript()` as the reusable voice-rewrite seam. Separate capture/transcription from the ordinary dictation commit responsibility while preserving the current provider routing contract. The language seam is the `languageHintProvider` lambda passed to `TranscriptionClient`, not the client itself; resolve the subtype fallback there so both consumers share one path. The settings surface for `Auto` belongs to T-016.

## Definition of Done

- [x] Transcription-only API implemented.
- [x] Language-hint resolution implemented with `Auto` reachable.
- [x] Ordinary dictation commit preserved.
- [x] Cancellation and cleanup tests pass.
- [x] No content-bearing logs added.

## Evidence Log

- 2026-08-04T22:07:55Z: Extracted editor-independent typed TranscriptionOnlyOperation and explicit ordinary commit step; added request-field language resolution for explicit, subtype fallback, Auto, and voice-instruction omission; full :app:testDebugUnitTest passed with fake recorder/client/editor and no-network request-field tests.

- 2026-08-04T22:03:57Z: Beginning dependency-safe transcription-only extraction

- 2026-08-04T22:03:57Z: T-004 is done and the sole local dependency is satisfied

- 2026-08-04: Task created during delivery decomposition.
