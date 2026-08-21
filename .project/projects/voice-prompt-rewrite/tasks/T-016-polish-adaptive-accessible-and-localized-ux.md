---
id: T-016
name: Polish adaptive accessible and localized UX
status: done
workstream: WS-D
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T14:26:57Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-013, T-015]
conflicts_with: []
parallel: false
priority: medium
estimate: M
story_id: US-007
acceptance_criteria_ids: [AC-024, AC-025, AC-026, AC-034, AC-035, AC-039, AC-041]
---

# Task: Polish adaptive accessible and localized UX

## Description

Apply the approved compact/expanded width policies, reach constraints, resource-backed copy, touch targets, TalkBack/live-region semantics, font scaling, contrast, and reduced-motion behavior across entry, recording, processing, result, success, and error states.

## Acceptance Criteria

- [x] Phone portrait and short landscape keep timer and all recording controls visible at 48 dp; the waveform reduces bar count before any control clips or moves into the stop button.
- [x] Tablet and split layouts center recording/processing/result content within the approved maximum widths and do not strand primary actions at opposite edges.
- [x] Only result content scrolls while back, retry/rerecord, replace/copy, and close actions remain reachable.
- [x] Every icon-only control has resource-backed click/long-click labels and descriptions; state changes announce politely once without level/timer chatter.
- [x] Reduced motion removes decorative halo/loop/travel/interpolation while preserving the low-frequency measured-level signal and visible state labels.
- [x] New copy is resource-backed, supports long localized strings and font scaling, and accurately uses `AI` as the umbrella while provider names remain configuration details.
- [x] The incognito enable and disable copy states that AI actions are disabled while incognito is active instead of promising only that Ownkey will not learn words, and the `FORCE_ON` preference description warns that it disables AI permanently.
- [x] Disabled AI controls announce their unavailability and its incognito cause through accessibility semantics rather than appearance alone.
- [x] The AI settings language field offers three reachable states — follow keyboard language as the default, explicit language, and `Auto` — and its copy no longer tells the user that an empty value means auto.
- [x] The language setting's copy states that it affects dictation recognition only — not rewrite instructions, which use auto-detection, and not the language a rewrite is returned in — so the three rules are not confused for one another.
- [x] The dictation recognition-language cue carries the language as readable text for every `SpaceBarMode` value rather than colour or highlight alone, and is absent during voice rewrite.
- [~] Dark, light/custom, high font scale, compact, expanded, and TalkBack checks have captured evidence with no clipped text or color-only state. **Partial.** Dark, 1.3x font scale, compact portrait and short-landscape/split evidence is captured in `evidence/` with no clipped text and no colour-only state. Light/custom theme rendering and TalkBack focus, announcement and custom-action execution are **not** captured here and are carried by T-018, which the delivery plan already assigns as the owner of the rendered theme, layout and TalkBack matrix. This is a deliberate, recorded carry-forward, not a waiver.

## Traceability

- Story: US-004, US-007, US-008, and US-015.
- Acceptance criteria: AC-024, AC-025, AC-026, AC-034, AC-035, AC-039, AC-041.

## Technical Notes

Follow existing Ownkey brand tokens and fixed IME height. Functional amplitude feedback remains under reduced motion but is not exposed as rapidly changing accessibility semantics.

## Definition of Done

- [x] Adaptive layouts complete.
- [x] Accessibility semantics complete.
- [x] Copy/localization resources complete.
- [~] Visual and TalkBack evidence recorded. Emulator visual evidence recorded in `evidence/`; light/custom theme and TalkBack execution remain with T-018.

## Evidence Log

- 2026-08-05T14:26:57Z: T-018 found that Compose API-key fields advertised ordinary text to the IME. Added KeyboardType.Password to both AI API-key fields; emulator now reports inputType=0x8081 and the hub/mic/presets render disabled with AI is off in secure fields. :app:compileDebugKotlin, :app:assembleDebug, 273 JVM tests, and git diff --check pass.

- 2026-08-05T14:23:27Z: Apply and verify the narrow Compose keyboard-type fix

- 2026-08-05T14:23:27Z: Reopened for T-018 secure-field remediation: API-key fields must publish password EditorInfo

- 2026-08-05T14:19:45Z: Quality remediation: selecting Specific language now preserves an existing explicit value or seeds from the active keyboard subtype instead of persisting the blank/Auto sentinel, so the explicit field becomes reachable. Added blank, Auto, and subtype-preservation regression coverage. Replaced the newly added synchronous toast with the suspend API. Evidence: :app:testDebugUnitTest passed 273 tests with 0 failures; :app:compileReleaseKotlin passed without the WS-D deprecation warning; git diff --check passed.

- 2026-08-05T14:17:31Z: Implementing reachable explicit-language selection and regression coverage

- 2026-08-05T14:17:31Z: Quality review found the Specific language state was unreachable

- 2026-08-05T13:32:52Z: Applied the adaptive, accessible and localized polish across the WS-D surfaces. Reduced motion now removes the mic halo, the travelling processing arc, the success scale, the colour interpolation, the panel slide/fade and the recording-dot pulse while keeping every icon, colour and label; the measured level already runs at 5 Hz through the T-007 reducer. The rewrite panel and shared row are fully resource-backed, the voice action rail is a fixed 52 dp so only the result body scrolls, and the 30-second countdown now applies only to the capped voice instruction rather than to open-ended dictation. Incognito enable/disable copy and the FORCE_ON description now state that AI actions are switched off. The AI settings language field became three reachable states (follow keyboard language, Auto, specific language) with copy scoping it to dictation recognition only, and the space bar carries the recognition language as readable text for every SpaceBarMode while voice rewrite shows no cue. Evidence: 266 app debug unit tests passed (0 failures) including 8 new cue and language-mode cases; :app:compileDebugKotlin, :app:compileReleaseKotlin and git diff --check passed; seven emulator screenshots recorded under evidence/ for dark, 1.3x font scale, compact portrait and short-landscape/split. Light/custom theme rendering and TalkBack execution are explicitly carried to T-018 and annotated in the task rather than waived.

- 2026-08-05T13:06:52Z: Applying adaptive, accessible and localized polish

- 2026-08-05T13:06:51Z: T-013 and T-015 done

- 2026-08-04: Task created during delivery decomposition.
