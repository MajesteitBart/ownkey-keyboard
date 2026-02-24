# Ownkey Keyboard Plan (FlorisBoard-derived)

## Goal

Build a high-quality, open-source Android keyboard where users can:
- bring their own AI API keys,
- use Mistral Voxtral ASR for practical dictation,
- get reliable autocorrect/suggestions,
- keep privacy and control by default.

---

## Product pillars

1. **Control**
   - User chooses provider/endpoint/model.
   - No forced vendor lock-in.

2. **Privacy**
   - No in-app monitoring of private content.
   - Keys stored encrypted on-device.

3. **Typing quality**
   - Suggestions/autocorrect tuned for real daily use.

4. **UX clarity**
   - Clean dark design, low visual noise, fast interactions.

---

## Architecture

## 1) IME app layer
- Internal dictation orchestration in keyboard flow
- Audio capture + dictation state handling
- Suggestion/autocorrect pipeline improvements
- Theme and quick-action UX layer

## 2) ASR integration layer
- Default direct path to Mistral Voxtral endpoint
- Configurable endpoint/model for compatible backends
- Optional relay mode for production hardening

## 3) Security layer
- Keystore-backed encrypted API key storage
- Legacy plaintext migration + cleanup
- Backup export sanitization for key fields

---

## What is already shipped

- Voice key integrated with internal Voxtral dictation manager.
- Mistral defaults configured (`/v1/audio/transcriptions`, `voxtral-mini-latest`).
- Endpoint + model configurable in settings.
- Encrypted API key storage via AndroidX Security.
- Autocorrect/suggestion tuning with EN/NL frequency dictionaries and ranking improvements.
- Design pass for quick actions, theme options, and cleaner dark look.
- Ownkey brandbook included in repo (`docs/brandbook/...`).

---

## Next roadmap (practical)

## Phase A — dictation quality
- More robust streaming/partial transcript handling
- Better stop detection and retry UX
- Better error taxonomy and UI feedback

## Phase B — typing quality
- More language-aware correction tuning
- Better contextual next-word quality
- Regression test set for autocorrect edge cases

## Phase C — product hardening
- Optional managed relay deployment templates
- Release automation + changelog discipline
- Privacy docs + in-app disclosure polish

---

## Licensing and attribution

- Base project: FlorisBoard (Apache-2.0)
- Ownkey continues under Apache-2.0
- Keep attribution clear in source and release notes for derivative work
