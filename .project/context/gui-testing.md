# GUI Testing Policy

## Enforcement Mode
- Advisory by default.
- Required for changes to onboarding, AI settings, keyboard chrome, store screenshots, icon, or feature graphic before a release candidate.

## Smoke Routes
- First-run setup/onboarding.
- Settings home section list, especially the `AI` entry.
- Settings -> AI dictation configuration.
- Settings -> AI rewrite provider presets and custom endpoint/model fields.
- Keyboard surface with default Ownkey orange theme.
- Dictation recording state and toast/snackbar routing.
- Rewrite quick action and rewrite options panel.

## Console Filtering
- Android/emulator logs may include platform noise.
- Block merge/release when there are app crashes, Compose runtime errors, missing resources, or navigation failures in the smoke routes.

## Evidence Requirements
- Capture screenshots for onboarding/settings/store-facing UI when practical.
- Include build/test commands and results in the closeout.
- If a live screenshot is skipped, state that caveat explicitly.

## Design Validation Threshold
- Settings and onboarding should share the Ownkey dark-first visual language: dark base, restrained panels, clear hierarchy, brand orange for AI/active moments, trust blue for primary setup actions.
- Text must fit on common phone widths.
- Provider presets should read as selectable options and should not look like unrelated cards.
- Store assets should show the actual product/brand, not generic abstract art.
