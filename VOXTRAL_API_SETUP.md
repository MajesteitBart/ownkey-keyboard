# Ownkey Voxtral ASR setup

This document describes how ASR is configured in Ownkey Keyboard.

## Current behavior

Ownkey supports two modes:

1. **Direct mode (default)**
   - Android keyboard calls the configured transcription endpoint directly.
   - Default endpoint/model are Mistral Voxtral:
     - `https://api.mistral.ai/v1/audio/transcriptions`
     - `voxtral-mini-latest`

2. **Relay mode (optional)**
   - Keyboard calls your backend relay.
   - Relay forwards to Mistral (or compatible provider).

Both modes use the same in-app settings fields for endpoint and model.

---

## In-app settings

Open Ownkey settings → Voxtral:

- **API key**
  - Stored encrypted (Android Keystore-backed)
  - Not exported in backup files

- **Endpoint URL**
  - Default: `https://api.mistral.ai/v1/audio/transcriptions`

- **Model**
  - Default: `voxtral-mini-latest`

---

## Recommended production architecture

For personal/self-managed usage, direct mode is fine.

For broader production distribution, relay mode is recommended to centralize:
- abuse protection and rate limiting
- policy enforcement
- usage metering/billing controls
- provider switching without app updates

---

## Example relay environment

```bash
# Provider settings
MISTRAL_API_KEY=...
MISTRAL_API_BASE_URL=https://api.mistral.ai
MISTRAL_MODEL=voxtral-mini-latest

# Relay controls
VOXTRAL_RELAY_AUTH_TOKEN=change-me
VOXTRAL_REQUEST_TIMEOUT_MS=45000
```

---

## Security notes

- Do not commit API keys to this repository.
- Ownkey stores user-provided keys encrypted on-device.
- Provider-side retention/policy is still determined by the endpoint you configure.

---

## Troubleshooting checklist

1. Endpoint returns 401/403
   - check API key validity and scope
2. Endpoint returns 404/400
   - check endpoint URL path and model name
3. Slow or failed transcriptions
   - verify network quality
   - test with short utterances first
   - check relay/provider timeout settings
