---
id: WS-B
name: WS-B Audio Session and Feedback Foundation
owner: ownkey-keyboard-team
status: done
created: 2026-08-04T13:27:38Z
updated: 2026-08-04T22:12:26Z
---

# Workstream: WS-B Audio Session and Feedback Foundation

## Objective

Create the single-recorder, transcription-only, explicit-outcome, and truthful audio-level primitives shared by ordinary dictation and voice rewrite without changing normal dictation provider/commit semantics.

## Owned Files/Areas

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioRecorder.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionClient.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt`
- New audio-session and audio-level reducer/state files under the dictation or shared AI input package
- Focused JVM tests for audio session, terminal outcomes, timers, and level reduction

## Dependencies

- WS-A approval gate (T-003)
- Existing configured dictation providers and app-private recording storage
- Lifecycle/cancellation requirements in the spec

## Risks

- Manager refactoring could regress normal tap-to-dictate or external-IME fallback.
- Stale timer/job completion could reset or commit into a newer session.
- Device amplitude variance can make a mathematically valid meter visually misleading.

## Handoff Criteria

- Exactly one audio session can be active and both consumers use the same lease/state contract.
- Voice rewrite can obtain a transcript without any editor commit.
- Success/error outcomes and reset timing are explicit and deterministic.
- The level reducer passes silence, speech, pause, reduced-motion, and bounded-history tests.
- Existing ordinary dictation routing and insertion tests pass.
