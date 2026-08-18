---
id: T-012
name: Add exclusive tap hold and accessibility entry
status: done
workstream: WS-D
created: 2026-08-04T13:27:38Z
updated: 2026-08-05T12:21:50Z
linear_issue_id: 
github_issue: 
github_pr: 
depends_on: [T-009]
conflicts_with: []
parallel: true
priority: high
estimate: M
story_id: US-001
acceptance_criteria_ids: [AC-001, AC-002, AC-006]
---

# Task: Add exclusive tap hold and accessibility entry

## Description

Refactor the sticky voice quick action so a normal tap preserves ordinary dictation while Android-configured long-press exclusively starts voice rewrite, with one haptic, visible/semantic mode feedback, an explicit accessibility action, and a one-time coach mark.

## Acceptance Criteria

- [x] Tap dispatches ordinary voice input exactly once with no added fixed three-second delay or voice-rewrite side effect.
- [x] Long-press uses the platform/accessibility touch-and-hold timeout, consumes release, dispatches voice rewrite exactly once, and cannot also dispatch tap.
- [x] Slide/cancel/disposal clears the press interaction and starts neither action after cancellation.
- [x] Recognition fires one system-respecting haptic and exposes visible/semantic `Speak your instruction` feedback before recording continues after finger release.
- [x] The dictation key exposes `Voice rewrite` as an explicit accessibility/custom action and resource-backed long-click label.
- [x] A dismissible one-time `Tap to dictate · hold to rewrite` coach mark does not block typing and does not recur after dismissal.
- [x] Deterministic gesture tests cover tap, hold, release, cancellation, double-dispatch prevention, configured delay, and non-voice quick-action regression.

## Traceability

- Story: US-001 and US-008.
- Acceptance criteria: AC-001, AC-002, AC-006.

## Technical Notes

The current quick-action pointer modifier calls generic key down/up. Isolate voice-specific classification without changing other quick-action semantics. TalkBack operation must not require discovering or performing a gesture.

## Definition of Done

- [x] Gesture arbitration implemented.
- [x] Haptic, semantics, and coach mark implemented.
- [x] Non-voice action regression covered.
- [x] Gesture tests pass.

## Evidence Log

- 2026-08-05T12:21:50Z: Added the pure VoiceActionGestureArbiter and a voice-only pointer pipeline in QuickActionButton that withholds the key down until the gesture resolves: a released tap dispatches ordinary dictation once, a hold at the platform touch-and-hold timeout dispatches voice rewrite once with one keyLongPress haptic and swallows the release, and cancellation before recognition dispatches neither. Added explicit Voice rewrite long-click and custom accessibility actions, a resource-backed Speak your instruction smartbar status with a polite live region, and a one-time provider-gated coach mark. Wired VoiceRewriteSessionManager/VoiceRewriteUiController into FlorisApplication and routed every IME lifecycle invalidation to both voice owners. Evidence: 211 app debug unit tests passed (0 failures), including 11 new gesture and non-voice regression cases; :app:compileDebugKotlin passed.

- 2026-08-05T11:59:06Z: Implementing exclusive tap/hold arbitration and accessibility entry

- 2026-08-05T11:59:05Z: Dependency T-009 is done; WS-D entry work starts

- 2026-08-04: Task created during delivery decomposition.
