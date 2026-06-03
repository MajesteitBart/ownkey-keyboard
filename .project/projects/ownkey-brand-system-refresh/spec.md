---
name: Ownkey Brand System Refresh
slug: ownkey-brand-system-refresh
owner: ownkey-keyboard-team
status: active
created: 2026-05-31T16:45:00Z
updated: 2026-06-03T10:01:46Z
outcome: Translate the new Ownkey brand-book reference and monkey waveform assets into app, keyboard, onboarding, and store-facing styling without weakening keyboard usability.
uncertainty: low
probe_required: false
probe_status: skipped
---

# Spec: Ownkey Brand System Refresh

## Executive Summary
Apply the downloaded Ownkey brand-book direction to the Android keyboard experience as a product styling pass, not just a marketing skin. The brand should show up through consistent assets, restrained dark surfaces, warm signal accents, compact typography, and more intentional motion around voice/transcription.

Reference artifacts:
- `docs/brandbook/ownkey-delano-brand-reference-2026-05-31.html`
- `assets/branding/ownkey-app-icon-keycap.svg`
- `assets/branding/ownkey-monkey-waveform-mark.svg`

## Problem and Users
Ownkey now has a stronger visual identity, but the app and keyboard styling still carry mixed FlorisBoard defaults and ad hoc Voxtral/Ownkey additions. Users should recognize the product from setup through daily typing, while the keyboard remains quiet, fast, legible, and practical inside other apps.

## Scope
### In Scope
- Brand asset storage and naming for the keycap app icon and standalone monkey waveform mark.
- Shared color, typography, shape, and motion tokens derived from the brand book.
- Settings and onboarding visual refresh using the Ownkey mark and clearer product hierarchy.
- Keyboard surface styling for smartbar, dictation, toast/snackbar, key states, and action affordances.
- Store/metadata asset alignment where current Fastlane assets are stale or inconsistent.
- Manual QA checklist for phone sizes, dark/light theme behavior, and IME-window behavior.

### Out of Scope
- Rewriting keyboard layout logic.
- Changing transcription provider behavior.
- Adding new onboarding steps beyond visual and wording polish.
- Rebranding package IDs or app name strings outside visible presentation.

## Functional Requirements
1. The app uses the keycap SVG where an app-icon/tile treatment is needed and the standalone monkey waveform mark where a compact brand mark is needed.
2. Ownkey styling tokens are centralized enough that keyboard, settings, onboarding, and toast surfaces do not drift.
3. Keyboard UI remains usable at compact heights and in host apps with different editor backgrounds.
4. Dictation start/end states use short, non-blocking motion that makes state changes visible without delaying typing.
5. Toast/snackbar surfaces render inside the correct window context: IME messages in the keyboard, settings messages in the app.
6. The brand palette preserves contrast for primary text, labels, key legends, destructive states, and disabled states.
7. Store-facing screenshots/feature graphics can be regenerated from the same visual direction.

## Non-Functional Requirements
- Keep typing-critical UI dense and stable; brand treatment must not introduce layout shift.
- Avoid one-note orange/dark styling by using orange as signal, bone for text, blue for primary action, and green only for Android/platform cues.
- Prefer Compose theme tokens and existing Snygg theme hooks over duplicated literal colors.
- Preserve accessible touch targets and readable text at Android font scale changes.

## Success Metrics
- Settings, onboarding, keyboard smartbar, dictation, and toast surfaces share the same asset and token vocabulary.
- No regression in release compile/build.
- Manual screenshot review passes on at least one phone-sized emulator/device for keyboard and settings flows.
- Brand assets are committed in repo-native locations and referenced from the implementation plan.

## Risks and Assumptions
- The HTML brand-book reference uses web fonts that may not map directly to Android bundled fonts; Android implementation should choose available/system-safe equivalents unless font licensing is deliberately handled.
- IME surfaces have tighter constraints than the brand-book page; dense keyboard usability wins over decorative fidelity.
- Visual QA still needs a device or emulator; current local context recently had no attached ADB device.

## Dependencies
- Current Ownkey/FlorisBoard Compose UI structure.
- Existing theme JSON/Snygg asset pipeline.
- Fastlane metadata/image folders for store assets.
- Delano `.project` contracts for planning, task tracking, and evidence.
