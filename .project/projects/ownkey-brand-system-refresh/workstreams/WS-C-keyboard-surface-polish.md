---
name: WS-C Keyboard Surface Polish
owner: ownkey-keyboard-team
status: done
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T17:05:00Z
---

# Workstream: WS-C Keyboard Surface Polish

## Objective
Apply the Ownkey brand to the IME itself without adding friction to typing.

## Owned Files/Areas
- Smartbar and quick actions
- Dictation recording/transcription UI
- IME toast/snackbar overlay
- Key state and theme JSON styling
- Keyboard spacing and density behavior

## Dependencies
- WS-A token and motion decisions
- Current smartbar alignment fixes

## Risks
- IME window constraints can make otherwise good brand treatments feel cramped.
- Toast overlays can attach to the wrong host if app/IME routing is not explicit.

## Handoff Criteria
- Keyboard styling is stable across typing, dictation, and toast states.
- No visible layout jump when dictation starts/stops or smartbar actions expand/collapse.
