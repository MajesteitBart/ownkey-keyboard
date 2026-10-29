---
name: Ownkey Minimal Redesign
slug: minimal-redesign
owner: ownkey-keyboard-team
status: active
created: 2026-10-29T03:00:52Z
updated: 2026-10-29T03:00:52Z
outcome: Every settings screen and keyboard surface follows one minimal dark design system, Home drops from 11 rows to an AI card plus 7 rows, and the redesign ships as one mergeable PR with unit, e2e and bot-user test evidence.
uncertainty: low
probe_required: false
probe_status: skipped
---

# Spec: Ownkey Minimal Redesign

## Executive Summary
Ownkey's settings app still looks like FlorisBoard: a light Material list of 11 rows, with the AI screen and setup in a separate dark style that mixes blue buttons, orange selection and 1dp borders. The keyboard shows two orange blocks (enter and mic) and an always-expanded seven-icon toolbar. This project gives both surfaces one minimal dark design system (black canvas, grey grouped cards, white pill actions, one accent), restructures settings around what users actually change, and hides settings that are dead, misleading or developer-only.

## Problem and Users
- Everyday users can't find AI, the main feature: it is row 9 of 11 on Home.
- Several visible settings do nothing (gesture swipe sounds, smartbar auto-expand, show app icon on Android 10+) or break AI entry points (smartbar layouts without the mic).
- Four accent colours compete on the AI and setup screens; the settings app follows the system light theme while AI and setup are forced dark.
- The keyboard spends a full row on toolbar icons by default and uses orange for both enter and the mic.

## Outcome and Success Metrics
- Home: AI card plus Languages, Appearance, Keyboard, Typing, Clipboard & emoji, Privacy & backup and About.
- One accent colour across settings and keyboard; no borders on cards, rows, fields, popups or panels.
- Every feature that had one entry point keeps one (theme editor, custom themes, language packs, backup and restore, dictionary import and export, Orukeet delete, toolbar reset, developer tools).
- Unit tests, Maestro e2e flows and a bot-user session pass on the API 35 emulator; `:app:assembleDebug` and the unit suite pass.

## User Stories
- US-001: As an Ownkey user, I want AI dictation and rewrite status on Home, so that I can see at a glance what's set up.
- US-002: As a user, I want fewer, clearer settings, so that I can change what matters without reading jargon.
- US-003: As a user, I want a calm keyboard, so that typing feels fast and the mic is the one obvious AI entry point.
- US-004: As a privacy-conscious user, I want incognito, learning and backup controls in one place, with honest copy about what goes to cloud providers.

## Acceptance Scenarios
- AC-001: Given a fresh install, when the user opens settings, then Home shows the AI card and seven rows on a black background with grouped cards.
- AC-002: Given any settings screen, when it renders, then rows sit in rounded cards with 2dp gaps, primary actions are white pills, and only the accent colour marks checked or selected state.
- AC-003: Given the keyboard opens in a text field, then it shows one smartbar row (toggle, three suggestions, mic) and the mic is the only accent element.
- AC-004: Given a user who changed a setting that is now hidden, when they open its screen, then the row is still shown until it is back at its default.
- AC-005: Given the AI screen, when no key is set, then each unavailable dictation route stays visible with a one-line reason, and tapping it opens the fix.

## Scope
### In Scope
- Settings design system (tokens, Inter typography, scaffold, shared components, jetpref drop-ins).
- Settings information architecture and copy, including setup and About.
- AI, rewrite styles and personal dictionary screens.
- Signal keyboard stylesheets, smartbar defaults, toolbar, mic, panels, toasts.
- Preference migrations that lock hidden hazardous values.
- Unit tests, Maestro e2e suite and runner, bot-user test.

### Out of Scope
- New AI providers or changes to dictation/rewrite request behaviour.
- Renaming internal `voxtral` identifiers or preference keys.
- Translations (the app ships English only).
- Store screenshots and Play metadata.

## Functional Requirements
See the build spec summarised in `plan.md`. Hidden settings keep their preference declarations; values that would strand users are locked by idempotent migrate rules.

## Non-Functional Requirements
- No extra work on key press, suggestion or keyboard-open paths; no fonts in the IME.
- WCAG contrast (4.5:1 text, 3:1 non-text), 48dp touch targets, TalkBack semantics, 200% font scale, RTL mirroring.

## Assumptions
- A dark-only settings app is acceptable; the keyboard keeps a light theme.

## Needs Clarification
- None blocking.

## Hypotheses and Unknowns
- Bot-user testing may surface IA naming issues that need copy changes.

## Touchpoints to Exercise
- Setup, Home, every settings screen, AI dialogs and sheets, keyboard themes (Graphite, Black, Bone), smartbar, voice-only bar, rewrite panel.

## Probe Findings
- Skipped: the solution direction is clear; risk is in execution and was covered by three independent spec critiques.

## Footguns Discovered
- jetpref 0.3.0 runs `migrate()` on every load and restore, so reset rules are permanent locks.
- Changing `OwnkeyDefaultTopBarActions` reorders every user's stored toolbar through the existing arrangement rule.
- The old Appearance screen rewrote theme ids every time it opened, overriding user picks.
- agent-device's `open` switches the default input method to its own helper during testing.

## Remaining Unknowns
- Physical-device feel of the new keyboard palette.

## Dependencies
- Inter 4.1 font (SIL Open Font License 1.1), bundled for the settings app only.

## Approval Notes
- Requested by Bart on 2026-10-29: redesign keyboard and settings, move and remove settings freely, ship a tested mergeable PR.
