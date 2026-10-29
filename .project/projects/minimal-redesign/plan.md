---
name: Ownkey Minimal Redesign
status: active
lead: ownkey-keyboard-team
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T03:00:52Z
linear_project_id:
risk_level: medium
spec_status_at_plan_time: active
---

# Delivery Plan: Ownkey Minimal Redesign

## What Changed After Probe
No probe. Three independent critiques of the build spec (product and IA, Android engineering risk, visual taste and accessibility) changed the plan before implementation: tokens moved into `lib/compose`, sliders write on release, the subtype "Add language" flow uses presets, copy about cloud data was corrected, and the keyboard palette was raised for contrast.

## Technical Context
- Settings: Jetpack Compose with jetpref 0.3.0 and material3 1.4.0. Same-name drop-ins for `PreferenceGroup`, `Preference`, `SwitchPreference`, `ListPreference` and `DialogSliderPreference` restyle every screen through import swaps.
- Keyboard: Snygg JSON stylesheets in `ai.bvdm.ownkey.themes.signal`, plus hardcoded Compose surfaces in `ime/**`.

## Architecture Decisions
See `decisions.md`.

## Policy and Contract Checks
- [x] `.project` remains the execution source of truth
- [x] Probe decision is explicit
- [x] Evidence gates are defined before handoff
- [x] External sync writes require dry-run or operator approval

## Generated Artifact Map
- `spec.md`: written from the owner request and the repository inventory.
- `plan.md`: this file.
- `decisions.md`: accepted design and migration decisions.

## Complexity Exceptions
- None.

## Probe-Driven Architecture Changes
- None.

## Workstream Design
- A, Foundation: tokens, Inter, theme, scaffold, shared components, drop-ins, routes, preference defaults and migrations, AI status.
- B1, Core settings: Home, Appearance, Keyboard, Sound & vibration, Gestures, Setup.
- B2, More settings: Languages, Typing, Saved words, Clipboard & emoji, Privacy & backup, About, backup and restore, devtools, extension and theme-editor screens.
- C, AI: AI screen, rewrite styles, personal dictionary, provider copy.
- D, Keyboard: Signal stylesheets, accent injection, smartbar and toolbar, mic, panels, toasts.
- A ran first; B1, B2, C and D ran in parallel in separate worktrees with fixed file ownership and were integrated afterwards.

## Milestone Strategy
1. Foundation verified (compile, full unit suite, emulator screenshots).
2. Streams verified individually, then integrated.
3. E2E suite and bot-user session green; local code review resolved.
4. PR opened with screenshots and evidence.

## Rollout Strategy
One PR to `main`. CI builds debug APKs for testing on phones before merge.

## Test Strategy
- Unit: stylesheet parsing and contrast, accent injection, migrate decisions, merged-control mappings, AI status.
- E2E: Maestro flows in `tools/e2e/maestro/` run by `tools/e2e/run.sh` on the API 35 emulator.
- Bot user: an independent agent completes first-time-user tasks on the emulator and reports friction.

## Rollback Strategy
Revert the PR. Preference declarations are unchanged, so stored values load again on the old build; only migrate-locked values stay at their defaults.

## Remaining Delivery Risks
- Physical-device feel of the keyboard palette and the large title on small phones.
