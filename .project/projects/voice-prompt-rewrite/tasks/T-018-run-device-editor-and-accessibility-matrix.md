---
id: T-018
name: Run device editor and accessibility matrix
status: blocked
workstream: WS-E
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T14:31:19Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-016, T-017]
conflicts_with: []
parallel: false
priority: high
estimate: L
story_id: US-007
acceptance_criteria_ids: [AC-003, AC-004, AC-020, AC-024, AC-025, AC-026, AC-028, AC-029, AC-034, AC-037, AC-038, AC-039, AC-040, AC-041]
blocked_owner: ownkey-keyboard-team
blocked_check_back: When representative physical devices, working TalkBack/TTS, and consented test-provider credentials are available
---

# Task: Run device editor and accessibility matrix

## Description

Execute the release candidate across representative devices, widths, editors, real microphones, Android touch-and-hold settings, reduced motion, font scaling, themes, and TalkBack; record safe support boundaries and visual evidence.

## Acceptance Criteria

- [ ] The editor matrix covers native/AOSP or Google fields, Compose, messaging, browser/WebView/contenteditable, raw editor behavior, secure fields, selection retention, Select All confirmation, and stale-target blocking.
- [ ] The device/layout matrix covers phone portrait, short landscape, tablet landscape, split keyboard, supported dark/light/custom themes, font scaling, and narrow/expanded recording rows.
- [ ] Real-microphone checks distinguish silence, quiet speech, and ordinary speech without dead/saturated or fake movement, including pause and reduced motion.
- [ ] Gesture checks cover configured touch-and-hold delays, release, sliding cancellation, immediate retry, and exactly one tap/hold action.
- [ ] TalkBack completes entry, target confirmation, disclosure, recording controls, processing, review, replacement/copy, and error recovery without relying on waveform or color.
- [ ] Each configured rewrite provider is checked against the D-008 language rule using synthetic text: an instruction spoken in another language leaves the source language unchanged, and an explicit translation request is honoured from either language.
- [ ] Dictation is checked across all three language-hint states — subtype default, explicit language, and `Auto` — and the spacebar recognition-language cue is verified for every `SpaceBarMode` value.
- [ ] Instruction transcription is checked with no hint sent, including short commands in the subtype language and in another language, and the recognition quality of unhinted short utterances is recorded against the hinted dictation baseline so the D-009 split can be reversed on evidence if it proves worse.
- [ ] Every unsupported editor/device case fails before provider work or replacement and is documented with user-facing recovery.
- [ ] Screenshots/recordings and notes contain no real typed/spoken/selected content, secrets, raw prompts, or absolute paths.

## Traceability

- Story: US-004, US-007, US-008, US-010, US-011, and US-012.
- Acceptance criteria: AC-003, AC-004, AC-020, AC-024, AC-025, AC-026, AC-028, AC-029, AC-034, AC-037, AC-038, AC-039, AC-040, AC-041.

## Technical Notes

Use synthetic, non-sensitive test text and test provider accounts/fixtures. Record exact Android/device/editor versions in content-safe evidence. A single passing editor cannot waive a failing safety case elsewhere.

## Definition of Done

- [ ] Matrix executed.
- [x] Visual/accessibility evidence recorded.
- [x] Unsupported cases documented safely.
- [x] Release-blocking defects triaged.

## Evidence Log

- 2026-08-05T14:31:19Z: Physical microphone/device calibration, complete TalkBack traversal, provider-language matrix, and light/custom theme rendering remain required before release

- 2026-08-05: Partial API 35 emulator matrix recorded in `evidence/t018-device-editor-accessibility-matrix.md`. Chrome Select All, expanded split layout, font scaling, the remediated Compose secure-field gate, and crash-free operation pass. A release-blocking secure-field defect was found, repaired under reopened T-016, and reverified. T-018 remains blocked on representative physical microphone/device coverage, complete TalkBack/TTS traversal, configured provider-language checks, and light/additional custom theme rendering; T-019 is therefore not dependency-safe.

- 2026-08-05T14:26:57Z: Resume matrix after closing the release-blocking secure-field defect

- 2026-08-05T14:26:57Z: Secure-field remediation verified; T-016 is done and the T-018 matrix can resume

- 2026-08-05T14:23:27Z: Release-blocking secure-field defect found on the Compose API-key field: EditorInfo inputType is ordinary text, so cloud AI controls are not gated

- 2026-08-05T14:14:43Z: Execute the dependency-safe WS-E device/editor/accessibility matrix

- 2026-08-05T14:14:43Z: T-016 and T-017 are done; begin representative device/editor/accessibility verification

- 2026-08-04: Task created during delivery decomposition.
