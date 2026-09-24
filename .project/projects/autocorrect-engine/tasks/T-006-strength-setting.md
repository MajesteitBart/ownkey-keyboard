---
id: T-006
name: Autocorrect strength setting
status: blocked
blocked_owner: ownkey-keyboard-team
blocked_check_back: After dependencies are done: T-004
workstream: WS-A
created: 2026-09-24T11:31:43Z
updated: 2026-09-24T11:31:43Z
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

Replace the 4 percentage sliders and the minimum-length slider in the typing settings with one strength setting: Off, Gentle, Normal, Strong. Each level maps to a posterior threshold calibrated on the benchmark.

## Acceptance Criteria

- [ ] Each level meets a precision floor and a recall floor on the tap-noise set: Gentle at least 99% precision, Normal at least 97%, Strong at least 95%, and each level at least 10 percentage points more recall than the level below it. A level that never fires fails.
- [ ] Off uses the same pref as the T-002 quick toggle.
- [ ] Existing slider values migrate to the nearest level. Old pref keys stay readable for one release.
- [ ] Chat and e-mail app profiles shift the threshold relative to the chosen level.
- [ ] The old sliders stay reachable in devtools for calibration.

## Traceability

- Story: US-004
- Acceptance criteria: none, verified by the benchmark floors above

## Technical Notes

- Settings UI: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`.
- Copy follows `OwnkeyBrand.kt` and `strings.xml` conventions.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-09-24: Task created. The recall floor was added after the review pointed out that precision-only thresholds can be met by never firing.
