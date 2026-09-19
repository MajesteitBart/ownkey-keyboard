---
id: T-003
name: Dictation integration and recognition hints
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
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-007, AC-008, AC-009, AC-011]
---

# Task: Dictation integration and recognition hints

## Description

Snapshot the dictionary at recording start, clean ordinary dictation off the main thread before commit with a lease recheck, treat filler-only speech as a neutral result, send vocabulary to Mistral as `context_bias` and to explicitly compatible endpoints as `prompt`, and pass encoded hotwords through the Orukeet IPC with a bounded budget and a beam-search decoder profile.

## Acceptance Criteria
- [x] Words attached to the session travel with cloud requests through a documented field only; unknown endpoints get none.
- [x] Local requests carry a sanitised, budgeted hotword string; the inference process keeps one engine keyed by model and decoder profile.
- [x] Rewrite instructions receive hints but bypass cleanup; filler-only speech shows a neutral toast and no error state.
- [x] Multipart fields are written as UTF-8, so accented vocabulary survives.

## Traceability
- Story: US-001, US-003
- Acceptance criteria: AC-001, AC-007, AC-008, AC-009, AC-011

## Technical Notes

`TranscriptionClient.kt`, `TranscriptionBackend.kt`, `VoxtralDictationManager.kt`, `offline/OfflineDictationController.kt`, `lib/offline-asr` (`HotwordTransport.kt`, `OrukeetEngine.kt`, `InferenceService.kt`, `InferenceConnection.kt`). Physical-device quality and cost of the beam profile remain in T-005.

## Definition of Done
- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log
- 2026-09-17: `CloudVocabularyHintsTest`, `OrdinaryDictationCleanupTest`, `HotwordTransportTest` green; existing dictation and rewrite suites green.
