---
id: WS-A
name: WS-A Probe and Contract Gates
owner: ownkey-keyboard-team
status: done
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T21:37:43Z
---

# Workstream: WS-A Probe and Contract Gates

## Objective

Retire the material gesture, editor, lifecycle, waveform, and layout uncertainties; resolve open product policies; and activate the delivery contracts only when the documented gate passes.

## Owned Files/Areas

- `.project/projects/voice-prompt-rewrite/spec.md`
- `.project/projects/voice-prompt-rewrite/plan.md`
- `.project/projects/voice-prompt-rewrite/decisions.md`
- Prototype/probe evidence under `.project/projects/voice-prompt-rewrite/`
- Temporary or isolated prototype seams in quick-action, editor, dictation, rewrite, and smartbar code

## Dependencies

- Approved product direction already recorded in the spec and decisions log
- Representative Android devices/emulators and host editors
- Product owner input for incognito and persistent-undo scope

## Risks

- Prototype code could accidentally be mistaken for production-ready implementation.
- A single friendly editor or microphone can hide compatibility failures.
- Activating the spec without explicit evidence would bypass the Delano probe contract.

## Handoff Criteria

- Gesture, Select All, target retention, lifecycle, waveform, compact/expanded, and accessibility findings are recorded against explicit exit criteria.
- Incognito and persistent-undo decisions are accepted and folded into the spec.
- Architecture and dependencies reflect probe evidence.
- Spec and plan are activated only when the gate passes; otherwise production tasks remain blocked.

## Updates

- 2026-08-04T19:25:57Z: T-001 first pass recorded; follow-up device/editor/TalkBack probe and T-002 persistent-undo decision remain blocking. Spec and plan intentionally remain planned.
- 2026-08-04T21:38:09Z: T-001 through T-003 completed. D-011 defers persistent undo; D-012 accepts emulator plus deterministic simulation for M0 while retaining T-018 physical release evidence. Spec/plan are active, `probe_status` is completed, WS-A is done, and T-004/T-007/T-008/T-020 are ready.
