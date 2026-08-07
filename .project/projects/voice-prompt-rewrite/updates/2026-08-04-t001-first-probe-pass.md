---
timestamp: 2026-08-04T19:25:57Z
status: blocked
task: T-001
stream: WS-A
---

# T-001 First Probe Pass

## Completed

- Added and passed seven non-shipping contract tests covering gesture exclusivity, Select All confirmation, target integrity, lifecycle cleanup, measured-level reduction, 48 dp geometry, and the dictation-only language cue.
- Exercised Ownkey Select All and Rewrite-panel selection retention on Android 15/API 35 in Chrome's omnibox, Google Messages, and an HTML textarea.
- Captured the masked password-field baseline, which confirms the current AI controls are not yet gated in secure editors.
- Recorded source findings, initial timeout/signal/layout tuning, manager boundaries, screenshots, footguns, and the follow-up recommendation under `probe/`.
- Folded the partial findings into the draft spec and provisional plan without activating either contract.

## In Progress

- None. T-001 is paused at the explicit follow-up evidence gate.

## Blockers

- Owner: `ownkey-keyboard-team`.
- Check back: 2026-08-05.
- Required access: representative physical microphone/device, TalkBack, Compose/raw/problematic editors, and rendered tablet/split layouts.
- T-002 separately remains blocked on the product owner's persistent-undo decision.

## Next Actions

- Run the remaining narrow physical-device/editor/TalkBack probe and append results to the existing T-001 artifact.
- Resolve persistent undo as an accepted product decision in T-002.
- Only after both tasks are done, execute T-003 to reconcile the final graph and activate the delivery gate.
