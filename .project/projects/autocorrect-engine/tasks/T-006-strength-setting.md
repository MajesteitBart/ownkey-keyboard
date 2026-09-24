---
id: T-006
name: Autocorrect strength setting
status: done
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T19:55:57Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-004]
conflicts_with: []
parallel: false
priority: medium
estimate: S
story_id: US-004
acceptance_criteria_ids: []
---

# Task: Autocorrect strength setting

## Description

Replace the chat and e-mail aggressiveness sliders in the typing settings, and the hidden confidence, gap and minimum-length prefs, with one strength setting: Off, Gentle, Normal, Strong. Each level maps to a posterior threshold calibrated on the benchmark.

## Acceptance Criteria

- [x] Each level meets a precision floor on the usage-weighted tap-noise sets (EN, NL, NL+EN): Gentle at least 99%, Normal at least 97%, Strong at least 95%. Each level fixes at least 10 points more typos than the level below it, averaged over those three sets. Wrong corrections on the rare-word (vocabulary-uniform) sets stay under 0.5%, 1.5% and 3.5%; their precision percentage is too noisy with few corrections. A level that never fires fails the recall step.
- [x] Off uses the same pref as the T-002 quick toggle.
- [x] Existing values migrate to the nearest level: a one-time runtime migration maps the hidden minimum-confidence pref (default 88%) to a level. Old pref keys are untouched, so they stay readable.
- [x] Chat and e-mail app profiles shift the threshold relative to the chosen level.
- [x] The old sliders stay reachable in devtools for calibration: chat and e-mail aggressiveness moved there. The confidence, gap and minimum-length prefs never had a slider.

## Traceability

- Story: US-004
- Acceptance criteria: none, verified by the benchmark floors above

## Technical Notes

- Settings UI: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`.
- Copy follows `OwnkeyBrand.kt` and `strings.xml` conventions.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-09-24: `AutocorrectStrength` (Gentle 0.985 / +2, Normal 0.95 / 0, Strong 0.80 / -1 as threshold and keep-as-typed bias) with `toSettings` and `fromLegacyMinConfidencePercent`. `AutocorrectStrengthCalibrationTest`: precision on usage sets Gentle 99.1 to 99.6%, Normal 98.8 to 99.4%, Strong 97.2 to 97.9%; average recall 34.4%, 67.3% and 79.1% (steps 32.9 and 11.9 points); rare-word wrong rates at most 0.2%, 1.1% and 3.1%. Gentle first used threshold 0.98 and missed its floor on NL+EN (98.99%), so it moved to 0.985. Pref `correction__autocorrect_strength` with a one-time migration in `FlorisApplication`; the chat and e-mail profiles still shift the threshold. 496 `ime` unit tests pass.
- 2026-09-24: Emulator: Typing settings show "Autocorrect strength: Normal" after migration, the picker lists Gentle, Normal and Strong with descriptions, a choice persists (Strong shown after OK), and the aggressiveness sliders are gone from Typing and present in Devtools.

- 2026-09-24: Task created. The recall floor was added after the review pointed out that precision-only thresholds can be met by never firing.
