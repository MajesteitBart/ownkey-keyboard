---
id: T-010
name: Implement two-provider voice-rewrite pipeline
status: done
workstream: WS-C
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:50:00Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-009]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-001
acceptance_criteria_ids: [AC-012, AC-013, AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-037, AC-038]
---

# Task: Implement two-provider voice-rewrite pipeline

## Description

Connect recording controls to transcription-only output, then combine the captured target and recognized instruction for the configured rewrite provider, with explicit state, cancellation, retry, rerecord, and in-memory retention rules.

## Acceptance Criteria

- [x] Pause/resume, explicit stop, cancel, elapsed-time accounting, 30-second auto-stop, and temporary-audio cleanup drive the shared session contract exactly once.
- [x] Successful transcription is trimmed, stored only for the valid in-memory session, shown through state, and never committed into the host editor.
- [x] Empty/no-speech and transcription failure stop before the rewrite request and expose the specified record-again recovery.
- [x] Successful transcription starts one rewrite request using the immutable target plus instruction and keeps fixed policy separate from user data.
- [x] The fixed policy states the D-008 language rule: return the result in the source text's language, honour an explicit language request in the instruction as an override, and preserve mixed-language source text.
- [x] No code path derives an output language from the transcript, keyboard subtype, or device locale, and no local language detection is added.
- [x] Voice instruction transcription passes the configured dictation language hint through unchanged and does not override it for the voice path.
- [x] `Try again` reuses target/instruction without audio; `Record instruction again` retains the valid target but replaces audio/transcript/result.
- [x] Cancellation, timeout, keyboard hide, input restart, field change, secure transition, and stale session prevent all later provider results from updating active state.
- [x] Fake recorder/transcription/rewrite tests cover success, empty, failure, timeout, retry, rerecord, cancellation at each boundary, and no content-bearing logs.

## Traceability

- Story: US-001, US-003, US-004, US-006, and US-009.
- Acceptance criteria: AC-012, AC-013, AC-014, AC-015, AC-016, AC-017, AC-018, AC-019, AC-037, AC-038.

## Technical Notes

Reuse configured dictation and rewrite clients through injected interfaces. Internal prompt policy must remain separate from selected text and instruction and must not be copied into project artifacts or logs. Output-language behavior itself is provider-dependent and is verified against real models in T-018; what this task must prove deterministically is that the rule is present in the policy and that nothing local decides the language.

## Definition of Done

- [x] End-to-end headless pipeline implemented.
- [x] Language rule present in the fixed policy with no local language decision.
- [x] Retry/rerecord and cancellation implemented.
- [x] Temporary-data cleanup verified.
- [x] Fake-provider tests pass.

## Evidence Log

- 2026-08-04T22:50:00Z: Implemented the generation-scoped two-provider pipeline over WS-B audio/transcription contracts: pause/resume elapsed accounting, 30-second active-time cap, exact-once stop, trim/no-speech mapping, fixed source-language policy separated from user data, retry/rerecord, cancellation/stale-result guards, and 7 passing pipeline tests; full app unit suite passed.

- 2026-08-04T22:44:44Z: Beginning dependency-safe WS-C two-provider pipeline implementation

- 2026-08-04T22:44:43Z: Dependency T-009 is done

- 2026-08-04: Task created during delivery decomposition.
