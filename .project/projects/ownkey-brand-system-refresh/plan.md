---
name: Ownkey Brand System Refresh
status: planned
lead: ownkey-keyboard-team
created: 2026-05-31T16:45:00Z
updated: 2026-05-31T16:45:00Z
linear_project_id:
---

# Delivery Plan: Ownkey Brand System Refresh

## Architecture Decisions
- Treat the downloaded HTML as a brand reference, not app source. Android implementation should extract tokens and behavior, not embed the page.
- Store brand source assets under `assets/branding/` and reference them from generated Android drawables/store graphics as needed.
- Keep app/settings Compose styling and IME styling separate at the window-hosting layer, but feed both from the same Ownkey token vocabulary.
- Use the standalone monkey waveform mark for compact placement in setup/settings headers and the keycap icon for launch/store/app-tile contexts.
- Use motion only to clarify state: dictation entering, recording active, transcription finishing, and toast appearance/dismissal.

## Workstream Design
- **WS-A Brand Assets and Tokens**: stabilize source assets, color tokens, type choices, shape scale, elevation, and motion timings.
- **WS-B App and Onboarding Shell**: apply Ownkey identity to setup, settings, about, and transcription configuration screens.
- **WS-C Keyboard Surface Polish**: style smartbar, dictation panel, toast/snackbar overlay, key states, and brand action affordances inside the IME.
- **WS-D Validation and Store Readiness**: screenshot review, release validation, Fastlane asset alignment, and rollout notes.

## Milestone Strategy
1. M1: Commit references and define implementation tokens from the brand book.
2. M2: Refresh app/onboarding surfaces and verify navigation/readability.
3. M3: Refresh IME surfaces, including dictation motion and toast placement.
4. M4: Regenerate store-facing assets and complete visual QA on device/emulator.

## Rollout Strategy
- Land assets and tokens first so implementation changes stay small and reviewable.
- Ship app/settings styling before deeper keyboard styling, because it has lower typing-risk.
- Keep keyboard changes behind existing theme/default paths where possible.
- Validate with a release APK before replacing the MEGA build.

## Test Strategy
- `./gradlew :app:compileReleaseKotlin`
- `./gradlew :app:assembleRelease`
- `git diff --check`
- SVG/XML/resource parsing checks after drawable generation.
- Manual screenshots: onboarding, settings home, Voxtral settings, keyboard in chat, keyboard in browser/search field, dictation start/end, toast in IME, toast in settings.

## Rollback Strategy
- Brand assets and Delano plan are inert and can remain even if implementation rolls back.
- Token changes should be isolated so reverting styling does not affect keyboard logic.
- If IME styling regresses usability, keep settings/onboarding styling and revert only keyboard surface changes.
- If generated store assets are weak, keep source SVGs and defer metadata update.

## Prioritized Build Backlog
1. Define Ownkey token map from the brand-book CSS: key, graphite, slate, line, bone, ash, orange, amber, green, and blue.
2. Convert SVG brand assets into Android/vector and store-ready raster outputs where needed.
3. Apply icon/mark usage to setup and settings entry points.
4. Refresh settings/onboarding color, heading, and surface hierarchy.
5. Refresh smartbar/action button styling while preserving existing spacing fixes.
6. Polish dictation start/stop motion and active recording treatment.
7. Lock toast/snackbar behavior per window host and align visual styling with the brand.
8. Generate Fastlane icon/feature/screenshot candidates.
9. Run build checks and device screenshot review before release handoff.
