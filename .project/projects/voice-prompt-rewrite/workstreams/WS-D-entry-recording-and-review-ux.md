---
id: WS-D
name: WS-D Entry Recording and Review UX
owner: ownkey-keyboard-team
status: done
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T14:26:57Z
---

# Workstream: WS-D Entry Recording and Review UX

## Objective

Deliver the exclusive tap/hold entry, shared real-amplitude recording row, discoverable rewrite entry, provider disclosure, processing/review/recovery surfaces, and adaptive accessible presentation.

## Owned Files/Areas

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionButton.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/VoiceRecordingRow.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/VoiceRecordingRowModel.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/Smartbar.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/RewriteOptionsPanel.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/TextInputLayout.kt`
- `app/src/main/res/values/strings.xml` and Ownkey UI/brand tokens
- UI/state-renderer tests and screenshot fixtures approved by the probe

## Dependencies

- WS-A approved gesture/layout findings
- WS-B session, terminal-outcome, timer, and level-history contracts
- WS-C target/preflight/session/result contracts

## Risks

- Quick-action gesture changes can break ordinary actions or double-fire tap and long-press.
- Narrow layouts can squeeze the waveform at the expense of accessible controls.
- Gesture-only discovery is insufficient without card, coach mark, and TalkBack action.
- Two independent UI state owners could disagree about recording, processing, or error.

## Handoff Criteria

- Tap, hold, accessibility action, card, cancel, pause/resume, stop, retry, rerecord, replace, and copy fallback match the spec.
- The center waveform reacts only to measured input; the trailing action uses a stop square while recording.
- Compact, landscape, tablet, and split layouts preserve hierarchy and 48 dp targets.
- State changes have resource-backed visible copy and non-repetitive accessibility semantics.
- Success/error timing and reduced motion pass deterministic UI/state tests.
