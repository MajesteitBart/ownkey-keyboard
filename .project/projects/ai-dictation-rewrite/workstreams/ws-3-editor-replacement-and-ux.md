---
name: WS-3 Editor Replacement & UX
owner: ownkey-keyboard-team
status: planned
created: 2026-05-01T11:07:28Z
updated: 2026-05-01T11:07:28Z
linear_milestone_id:
linear_label_id:
---

# Workstream: WS-3 Editor Replacement & UX

## Objective
Make the rewrite action feel safe, obvious, and reversible inside the keyboard.

## Owned Files/Areas
- Rewrite action placement
- Target text selection policy
- Preview/apply/cancel UI
- Transactional replacement behavior

## Dependencies
- Existing keyboard action and smartbar system.
- Editor replacement probe.
- Provider fake for UI testing.

## Conflict Risk Zones
- `KeyboardManager.kt`, `EditorInstance.kt`, and smartbar/action layout files may be hot spots for other keyboard work.

## Handoff Criteria
- Rewrites can target selected text and recent dictation safely.
- Failed rewrites leave original text untouched.
- User can cancel or recover without manual retyping.
