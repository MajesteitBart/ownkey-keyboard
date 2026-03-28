---
title: T-009 first execution slice - direct numeric-advanced shortcut
created: 2026-03-28T13:11:21Z
updated: 2026-03-28T13:11:21Z
task_id: T-009
---

# Update Summary
Implemented the first dependency-safe execution slice for T-009 by extending the shared `?123` key on the base characters modifier row with a direct popup shortcut to `VIEW_NUMERIC_ADVANCED`.

## Why this slice first
- `./.agents/scripts/pm/next.sh --all` identified T-009 as the next dependency-safe implementation task.
- This change stays inside the existing layout system rather than introducing new keyboard-mode state.
- It removes one interaction for common number and symbol bursts while preserving current prediction and correction behavior.
- It is safe to ship independently even if later T-009 follow-up work adds more ambitious momentary-symbol or auto-return behavior.

## Files Changed
- `app/src/main/assets/ime/keyboard/org.florisboard.layouts/layouts/charactersMod/default.json`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/CharactersModifierLayoutTest.kt`
- `.project/projects/typing-speed-core/tasks/T-009-symbol-number-flow-with-fewer-mode-switches.md`

## Validation
- `bash .claude/scripts/pm/validate.sh` — PASS
- `python3` JSON structural assertion against `charactersMod/default.json` — PASS
- Gradle compile/test/build attempt with local Temurin 17 — BLOCKED by sandbox runtime
  - Observed failure: `java.net.SocketException: Operation not permitted (Socket creation failed)`
  - Failing call path: Gradle file-lock listener startup -> `NetworkInterface.getNetworkInterfaces()`
  - Interpretation: environment blocker, not a code-level compile failure discovered in this pass

## Follow-up
- Run the Gradle validation and APK build in CI or a shell without the sandbox network-interface restriction.
- If user testing still shows too much layer friction after this shortcut, continue T-009 with a second slice focused on momentary symbol/number return behavior.
