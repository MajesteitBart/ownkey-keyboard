<img align="left" width="80" height="80" src=".github/repo_icon.png" alt="Ownkey app icon">

# Ownkey Keyboard (Android)

**Ownkey Keyboard** is a privacy-first, open-source Android keyboard focused on practical AI input.

Core idea:
- bring your own API key (BYOK)
- use **Mistral Voxtral ASR** for voice input
- keep user control over settings, provider choice, and local behavior
- no in-app monitoring of private user content

This project is a derivative fork of FlorisBoard and remains Apache-2.0 licensed.

---

## What is already implemented

## 1) Mistral Voxtral ASR input (working)
- Voice input routes to internal dictation flow (not only external IME switching).
- Default endpoint/model:
  - `https://api.mistral.ai/v1/audio/transcriptions`
  - `voxtral-mini-latest`
- Configurable endpoint + model in settings (for compatible/self-host flows).
- Mistral signup shortcut in settings.

## 2) BYOK with secure local key storage
- API keys are stored using Android Keystore-backed encrypted preferences.
- Legacy plaintext key migration is included and old plaintext values are removed.
- Backup export sanitizes sensitive key values.

## 3) Autocorrect and suggestions that actually help
- Suggestion pipeline tuned for real typing flow.
- EN/NL frequency dictionaries integrated.
- Ranking improvements, caching, and safer correction behavior on short tokens.
- Suggestion defaults enabled.

## 4) Design and UX improvements
- Ownkey dark visual direction (high contrast, cleaner quick actions).
- Samsung-inspired quick-actions overflow layout refinements.
- Theme controls simplified:
  - Light / Dark / Follow system
  - Key borders on/off
  - Corner radius: None / Small / Medium / Large

---

## Privacy model

Ownkey does not add private-content monitoring inside the keyboard app itself.

Important nuance:
- when ASR is enabled, audio is sent to the configured ASR endpoint (Mistral by default, or your custom endpoint)
- data handling at that endpoint is governed by that provider's policy

So, app-side telemetry/monitoring is not the model, but networked ASR naturally means request data goes to the configured ASR service.

---

## Quick start (dev)

### Requirements
- Android Studio (current stable)
- Java 17
- Android SDK + emulator/device

### Build
```bash
./gradlew :app:assembleDebug
```

### Run
- Install debug APK
- Enable Ownkey Keyboard in Android input settings
- Open Ownkey settings to configure Voxtral endpoint/model and API key

---

## Documentation

- Product/technical plan: [VOXTRAL_KEYBOARD_PLAN.md](VOXTRAL_KEYBOARD_PLAN.md)
- Feature scope and status: [VOXTRAL_FEATURE_SCOPE.md](VOXTRAL_FEATURE_SCOPE.md)
- API setup (direct + relay options): [VOXTRAL_API_SETUP.md](VOXTRAL_API_SETUP.md)
- Brandbook (HTML): [`docs/brandbook/ownkey-brand-book-2026-02-24-v2.html`](docs/brandbook/ownkey-brand-book-2026-02-24-v2.html)
- Changelog: [CHANGELOG.md](CHANGELOG.md)

---

## Contributing

PRs are welcome.

When contributing, please keep these priorities:
1. Privacy and explicit user control
2. Reliability of typing/autocorrect
3. Low-friction Voxtral dictation UX
4. Clear attribution for forked/original FlorisBoard components

---

## License and attribution

This project is distributed under the Apache License 2.0.

It is based on FlorisBoard, with Ownkey-specific modifications for dictation, privacy controls, autocorrect tuning, and UX/theme improvements.

- License text: [LICENSE](LICENSE)
- Original project: https://github.com/florisboard/florisboard
