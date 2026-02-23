# Voxtral Keyboard Plan (FlorisBoard fork)

## Goal
Build an open-source Android keyboard with first-class voice dictation using **Mistral Voxtral Mini**,
with strong punctuation handling and a smooth in-keyboard UX.

## Why this base
This repo is forked from FlorisBoard (open-source, Android keyboard, Kotlin codebase), which already has:
- robust IME architecture
- quick-action voice key support
- multilingual keyboard/layout foundations

## Product direction
- Replace "open external voice IME" flow with built-in dictation flow.
- Tap microphone key -> record audio -> transcribe via Voxtral Mini -> insert text at cursor.
- Add punctuation and formatting post-processing options.

## Architecture (recommended)

### 1) Android app layer
- New dictation controller in the IME app.
- Audio recording pipeline (PCM/WAV, 16 kHz+).
- UI state in keyboard smartbar:
  - idle
  - listening
  - transcribing
  - inserting
  - error/retry

### 2) Networking layer
Two options:

A. Direct from app to Mistral API (fast to prototype, not secure for production)
- API key embedded/obfuscated in app, still extractable.

B. Backend relay (recommended for production)
- Keyboard app sends audio to your backend.
- Backend holds Mistral key, calls Voxtral Mini, returns transcript.
- Enables rate limiting, auth, usage quotas, logging, and key rotation.

## Implementation phases

### Phase 1 (MVP)
- Hook VOICE_INPUT key to internal dictation flow.
- Record short utterances (tap-to-start / tap-to-stop).
- Send audio to Voxtral endpoint.
- Insert transcript into active editor.

### Phase 2 (Quality)
- Auto punctuation mode and language-aware formatting.
- Better endpointing (silence detection).
- Retry behavior, clearer error feedback.
- Optional local post-processing rules (Dutch + English).

### Phase 3 (Production hardening)
- Move to backend relay.
- Telemetry (privacy-safe), rate limits, abuse protection.
- Config flags + staged rollout.

## Key risks / decisions
- **API key security**: mobile client key leakage risk is real.
- **Battery + latency**: streaming vs chunked upload tradeoff.
- **Privacy**: clear UX around cloud transcription.
- **Licensing**: keep FlorisBoard license/attribution intact in derivative distribution.

## MVP implemented

### What was implemented
- `KeyCode.VOICE_INPUT` now routes through an internal dictation hook (`VoxtralDictationManager`) instead of directly switching IME every time.
- Added minimal dictation module scaffolding with clear interfaces:
  - `AudioRecorder` abstraction
  - `TranscriptionClient` abstraction
  - `VoxtralDictationManager`
- Added safe mock dictation flow for local testing without API keys:
  - debug build behavior: tap mic once to start mock listening, tap again to insert mock transcript
  - non-debug behavior: fallback to legacy external voice IME switching
- Added concise future integration setup doc for backend relay + environment variables.

### Exact changed files
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/VoxtralDictationManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioRecorder.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionClient.kt`
- `.env.voxtral.example`
- `VOXTRAL_API_SETUP.md`
- `VOXTRAL_KEYBOARD_PLAN.md`
