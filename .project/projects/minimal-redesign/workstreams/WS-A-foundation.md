---
id: WS-A
name: WS-A Foundation
owner: ownkey-keyboard-team
status: done
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T09:15:44Z
---

# Workstream: WS-A Foundation

## Objective
Shared tokens, Inter, theme, scaffold, components and jetpref drop-ins; routes, preference defaults and migrations, AI status.

## Owned Files/Areas
lib/compose/**, app/ui/**, apptheme/**, FlorisScreen, PreviewKeyboardField, FlorisAppActivity, Routes.kt, AppPrefs.kt, AiStatus.kt, fonts and license config

## Dependencies
None

## Risks
Drop-in signature drift from jetpref 0.3.0; colour roles leaking M3 baseline colours.

## Handoff Criteria
Compile, full unit suite and emulator screenshots verified by an independent reviewer.
