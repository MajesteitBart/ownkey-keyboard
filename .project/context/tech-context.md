# Tech Context

## Stack
- Android app modules (`app`, `wear`) with Kotlin + Gradle.
- FlorisBoard-derived IME architecture.
- Jetpack Compose settings/setup surfaces in the app module.
- Local dictionaries, suggestion ranking, and autocorrect pipeline in the keyboard engine.
- AndroidX Security / Android Keystore-backed encrypted preferences for AI API keys.

## AI Integration Points
- Dictation: `VoxtralDictationManager` and related settings currently retain Voxtral naming internally, while user-facing settings now use `AI`.
- Rewrite: `LlmRewriteClient`, `LlmRewriteManager`, `LlmRewriteProvider`, and `RewriteOptionsPanel`.
- Rewrite request families:
  - OpenAI Responses uses Responses API-shaped request/response handling.
  - Anthropic uses Messages API-shaped headers and request body.
  - OpenAI Chat Completions, Mistral, OpenRouter, and custom providers use chat-completions-shaped requests.
- Provider defaults live in code, while endpoint/model values are exposed through settings.

## Runtime Constraints
- Keep keyboard open, typing, and suggestion interactions responsive.
- Suggestion pipeline performance remains a key quality bar, especially for top-3 prediction updates.
- AI network work must not block normal keyboard typing.
- Privacy-first behavior: avoid private-content telemetry and keep networked AI features opt-in/configured.

## Release Validation Baseline
- For settings/AI changes, run at least `:app:compileDebugKotlin`, `:app:compileReleaseKotlin`, and `:app:assembleRelease`.
- Run `git diff --check`.
- Scan for stale user-facing `Voxtral` umbrella labels after settings copy changes.
- Check APK badging after release builds when metadata, icon, label, or store assets change.
- Use `delano validate` when `.project` planning/context files change.
