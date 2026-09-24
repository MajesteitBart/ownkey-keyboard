---
id: T-014
name: Tap positions from the keyboard
status: ready
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T21:52:01Z
linear_issue_id:
github_issue:
github_pr:
depends_on: []
conflicts_with: []
parallel: false
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: []
---

# Task: Tap positions from the keyboard

## Description

Record where each letter of the word being typed was tapped, in key units of the current layout, and pass the positions to the engine in `LatinScoringRequest.taps` for suggestions and the on-the-spot auto-commit decision.

## Acceptance Criteria

- [ ] One tap per typed character of the composing word, aligned with the text: backspace removes the last tap, and cursor moves, paste, glide input, hardware keys and suggestion commits clear the trail so misaligned taps are never used.
- [ ] Positions are relative to the letter area in key widths, the same units as `KeyGeometry`, and follow layout changes (one-handed mode, floating keyboard, landscape).
- [ ] Tap positions stay in memory for the current word only; they are never logged or stored.
- [ ] Typing latency is unchanged (no allocation per key beyond one small object).

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- The engine already accepts taps (`LatinTap`) and the tap-noise benchmark sets already carry them, so T-015 can be built and measured before this task lands.
- `KeyboardGeometrySource` already receives the laid-out character keys from `TextKeyboardLayout`; the touch point can be converted with the same key-width unit.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created with Phase 4.
