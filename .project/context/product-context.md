# Product Context

## Users
- Android users who want AI help while typing without switching apps.
- Privacy-conscious users who prefer open-source software and BYOK provider control.
- Multilingual users, especially current EN/NL usage, who need better suggestions and autocorrect.
- Power users who want configurable endpoints, models, and provider presets.

## Core Flows
- Enable Ownkey in Android input settings and complete the branded onboarding flow.
- Type normally with improved suggestions, autocorrect, and quick actions.
- Tap the voice/dictation flow, send audio to the configured ASR endpoint, and insert the transcript.
- Select or prepare text, tap Rewrite, choose a rewrite voice, and send the request to the configured LLM endpoint.
- Open Settings -> AI to configure dictation and rewrite provider details from one place.
- Store API keys locally with Android Keystore-backed encryption and avoid exporting secrets in backups.

## User-Facing AI Defaults
- Dictation defaults to Mistral Voxtral ASR: `https://api.mistral.ai/v1/audio/transcriptions`, `voxtral-mini-latest`.
- Rewrite defaults to OpenAI Responses, currently with model setting `gpt-5.5`.
- Rewrite provider presets include OpenAI Responses, OpenAI Chat Completions, Anthropic, Mistral, OpenRouter, and Other / Custom.
- Custom provider selection clears endpoint/model fields so the user can enter their own values.

## Constraints
- Do not imply that Ownkey can provide cloud AI without a provider API key.
- Be explicit that cloud ASR/rewrite sends request data to the configured endpoint.
- Keep privacy claims to app behavior: no added in-app private-content monitoring, encrypted local keys, and open-source transparency.
- Keep settings language broad enough for AI dictation and LLM rewrite; avoid using `Voxtral` as the umbrella section name.
