---
name: WS-C Keyboard Surface Polish
owner: ownkey-keyboard-team
status: active
created: 2026-05-31T16:45:00Z
updated: 2026-06-03T09:49:26Z
---

# Workstream: WS-C Keyboard Surface Polish

## Objective
Apply the Ownkey brand to the IME itself without adding friction to typing.

## Owned Files/Areas
- Smartbar and quick actions
- Secondary action bar sizing, hit targets, and reserved IME placement
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
- It is easy to mix up the comparison: the screenshot/current secondary action bar is the smaller cramped one, while the design/reference bar is the taller usable target.

## Handoff Criteria
- Keyboard styling is stable across typing, dictation, and toast states.
- No visible layout jump when dictation starts/stops or smartbar actions expand/collapse.
- Secondary action bar matches the larger design/reference treatment while staying inside reserved keyboard space.
