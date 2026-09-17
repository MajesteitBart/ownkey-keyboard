# Progress

## What Changed
- 2026-09-17: Recovered the uncommitted Orukeet integration and combined it with saved-voice persistence recovery on `fix/saved-voices-orukeet`. [Validation and internal APK handoff](../projects/voice-prompt-rewrite/updates/2026-09-17-orukeet-reconciliation.md).
- 2026-09-15: Orukeet local dictation implemented for internal builds, including model downloads, explicit activation, session snapshots, local instruction transcription and separate inference process. [Delivery evidence](../projects/orukeet-offline-dictation/research/implementation-verification.md) records checks and remaining public-release gates.
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
- Default rewrite provider is OpenRouter with model setting `meta/muse-spark-1.1`.
- Existing installations preserve their dictation route. Internal builds can explicitly download and activate Orukeet; public local availability remains disabled pending phone qualification.
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
- Complete Orukeet physical arm64 accuracy/performance/accessibility and older-API transfer validation before public enablement.
- Consider renaming internal `voxtral` package/settings identifiers later if the user-facing AI naming sticks and the churn is worth it.

## Remaining Risks
- Play Store copy must not overclaim privacy or hosted AI functionality.
- Default model names can age quickly; verify provider docs before release-facing copy that mentions specific model names.
- AI settings and Orukeet activation were visually verified on API 36 emulators; physical layout/accessibility coverage remains pending.
