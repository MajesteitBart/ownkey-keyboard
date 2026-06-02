# Progress

## What Changed
- Context pack refreshed on 2026-06-02 to match current Ownkey direction instead of the older typing-speed-only scope.
- Brand refresh shipped in commit `7ca917ec` (`Complete Ownkey brand refresh`).
- Default keyboard theme accent updated to `#f56c1e` in commit `eb241853` (`Set Ownkey keyboard theme orange`).
- AI settings refresh shipped in commit `df6657e0` (`Refresh Ownkey AI settings`).
- Play Store USP working list added in `play-store-usps.md`.

## Current Product State
- Settings home now shows `AI` with summary `Configure dictation and rewrite`.
- AI settings page is styled like onboarding and groups dictation plus rewrite.
- Rewrite provider presets exist for OpenAI Responses, OpenAI Chat Completions, Anthropic, Mistral, OpenRouter, and Other / Custom.
- Rewrite client handles OpenAI Responses, Anthropic Messages, and chat-completions-shaped providers separately.
- Default rewrite provider is OpenAI Responses with current model setting `gpt-5.5`.
- Dictation remains Mistral Voxtral ASR by default.
- Store icon and feature graphic were refreshed during the brand pass.

## Latest Validation
- `:app:compileDebugKotlin`
- `:app:compileReleaseKotlin`
- `:app:assembleRelease`
- `git diff --check`
- stale visible Voxtral umbrella label scan
- APK badging
- `bash .agents/scripts/pm/validate.sh` / `delano validate`

## What Is Next
- Review and choose the final Google Play USP angle from `play-store-usps.md`.
- Update Fastlane Play Store metadata only after Bart approves the copy direction.
- Capture live emulator/device screenshots for the refreshed AI settings UI.
- Consider renaming internal `voxtral` package/settings identifiers later if the user-facing AI naming sticks and the churn is worth it.

## Remaining Risks
- Play Store copy must not overclaim privacy or hosted AI functionality.
- Default model names can age quickly; verify provider docs before release-facing copy that mentions specific model names.
- AI settings were build-validated but not visually verified on a live device after the latest refresh.
