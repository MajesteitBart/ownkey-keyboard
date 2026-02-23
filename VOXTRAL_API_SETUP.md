# Voxtral API setup (future real integration)

> MVP currently runs in safe mock mode for debug builds and falls back to external voice IME in non-debug builds.

## Recommended architecture
Use a **backend relay** between the keyboard app and Mistral API.

Why:
- keeps `MISTRAL_API_KEY` out of the APK
- enables auth/rate limits/abuse protection
- allows key rotation and usage logging

## Suggested environment variables

### Backend relay
```bash
# Required
MISTRAL_API_KEY=...
MISTRAL_API_BASE_URL=https://api.mistral.ai
MISTRAL_MODEL=voxtral-mini-latest

# Optional
VOXTRAL_RELAY_AUTH_TOKEN=change-me
VOXTRAL_REQUEST_TIMEOUT_MS=45000
```

### Android app (non-secret)
```bash
# Endpoint exposed by your relay
VOXTRAL_RELAY_BASE_URL=https://your-relay.example.com

# Optional bearer token minted by your backend auth flow
VOXTRAL_RELAY_CLIENT_TOKEN=
```

## Minimal integration checklist
1. Implement a real `AudioRecorder` (PCM/WAV capture, runtime mic permission UX).
2. Implement `VoxtralRelayTranscriptionClient` to upload audio to relay.
3. Add request/response schema versioning and timeout/retry handling.
4. Keep secrets only on backend; never commit keys to this repository.
