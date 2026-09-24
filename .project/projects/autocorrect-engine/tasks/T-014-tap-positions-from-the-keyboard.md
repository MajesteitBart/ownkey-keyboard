---
id: T-014
name: Tap positions from the keyboard
status: done
workstream: WS-A
created: 2026-09-24T21:52:01Z
updated: 2026-09-24T22:06:29Z
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

- [x] One tap per typed character of the composing word, aligned with the text: backspace removes the last tap, and cursor moves, paste, glide input, hardware keys and suggestion commits clear the trail so misaligned taps are never used.
- [x] Positions are relative to the letter area in key widths, the same units as `KeyGeometry`, and follow layout changes (one-handed mode, floating keyboard, landscape).
- [x] Tap positions stay in memory for the current word only; they are never logged or stored.
- [x] Typing latency is unchanged (no allocation per key beyond one small object).

## Traceability

- Story: US-001
- Acceptance criteria: none (Phase 4 gate in plan.md)

## Technical Notes

- The engine already accepts taps (`LatinTap`) and the tap-noise benchmark sets already carry them, so T-015 can be built and measured before this task lands.
- `KeyboardGeometrySource` already receives the laid-out character keys from `TextKeyboardLayout`; the touch point can be converted with the same key-width unit.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [ ] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `TapTrail` keeps the tapped characters of the current word with their positions in key widths. `TextKeyboardLayout` records a character key's touch point on release (a popup alternative gets no position); `KeyboardManager` removes the last tap on backspace and clears the trail on space, enter, punctuation triggers, word deletes and suggestion commits. The provider asks `TapTrail.tapsFor(word)`, which only returns taps when the latest ones spell exactly that word, so glide, paste, hardware keys or a cursor moved into an older word get no taps instead of wrong ones. Positions are converted with the median key width of the laid-out keys, in the same view coordinates as `KeyGeometry.fromPixels`, so they follow one-handed, floating and landscape layouts. The suggestion cache key includes the taps. Nothing is logged except a debug line with the tap count and the mean distance from the typed keys.
- 2026-09-24: `TapTrailTest` (5 tests): exact-suffix matching, case, backspace, clearing, characters outside words, the 48-tap bound. Full `ime` suite: 537 tests pass.
- 2026-09-24: Emulator (debug build, scripted taps at key centers): typing `hello ` logged "5 taps, mean distance from key centers 0.01 key widths", so taps line up with their letters and the units match the key geometry. Backspacing into the previous word and adding a letter logged "none": the trail no longer spells the word, so no taps were used. Scripted taps cannot show the touch model changing a correction; `TouchModelTest` and the benchmark cover that.
- 2026-09-24: Task created with Phase 4.
