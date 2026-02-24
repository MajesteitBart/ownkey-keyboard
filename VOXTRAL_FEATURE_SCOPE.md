# Ownkey Keyboard — feature scope (current)

This scope reflects the current Ownkey direction: Android keyboard + practical AI input + privacy-first controls.

## Product direction

- Open-source Android keyboard
- BYOK (users add their own API keys)
- Mistral Voxtral ASR as the default voice-input path
- Stronger autocorrect/suggestions for daily typing
- Cleaner dark visual system and streamlined settings UX

---

## P0 focus (implemented / in progress)

## 1) ASR input with Voxtral

**Implemented**
- Internal dictation flow integrated with voice key
- Configurable endpoint + model
- Default configured for Mistral Voxtral Mini
- Settings UX for connection values

**Next refinements**
- More robust streaming/partial transcript behavior
- Better endpointing/silence handling
- Additional UX states and retry affordances

## 2) Autocorrect that works in real typing

**Implemented**
- Suggestion ranking tuned
- Caching added for faster responses
- EN/NL frequency dictionaries integrated
- Safer typo correction behavior and fallback suggestions

**Next refinements**
- More typo patterns and contextual ranking
- Expanded language quality checks
- Additional regression tests for false corrections

## 3) Ownkey design improvements

**Implemented**
- Dark-first visual pass
- Samsung-inspired quick-action layout updates
- Theme controls simplified (appearance, borders, corner radius)

**Next refinements**
- Consistent visual polish across all settings screens
- Optional premium style packs (without license conflicts)
- Better animation/motion consistency

## 4) Privacy + key management

**Implemented**
- Encrypted API key storage (Keystore-backed)
- Backup sanitization for sensitive fields
- Open-source transparency baseline

**Next refinements**
- Clearer in-app privacy copy per network feature
- Optional relay mode presets for managed deployments

---

## Done criteria for this cycle

- User can configure Voxtral endpoint/model/key and dictate reliably.
- User gets noticeably better autocorrect/suggestions than baseline Floris behavior.
- UI reflects Ownkey style direction consistently in key flows.
- Sensitive key storage is encrypted and backup-safe.
