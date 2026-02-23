# Voxtral Keyboard — Feature scope (v0.2)

## Design direction (reference screenshot)
- Dark theme first, high contrast keys, rounded rectangles.
- Utility toolbar above keys with quick actions.
- Bottom row with language switch centered and visible.
- Minimal visual noise, fast interactions.

## P0 features (requested)

### 1) Clipboard (+ sync)
- Integrate with Android clipboard APIs as primary source.
- Keep local clipboard history in keyboard app (respecting Android privacy rules).
- Add explicit sync mode toggle:
  - Off (default, local only)
  - Relay sync (encrypted sync via backend)

**Note:** Android does not provide a generic cross-device clipboard sync API for third-party keyboards. Cross-device sync needs our own relay.

### 2) ASR with punctuation, streaming (NL + EN)
- Streaming dictation path via Voxtral Mini.
- Language mode:
  - Auto (NL/EN)
  - Force Dutch
  - Force English
- Punctuation mode:
  - Auto punctuation ON/OFF
- Low-latency partial transcript updates with final replacement.

### 3) Automatic spellcheck + dictionary
- Enable/keep keyboard spellcheck suggestions by default.
- Ensure Dutch + English dictionaries installed and selectable.
- Add settings for:
  - Auto-correct ON/OFF
  - Personal dictionary edits
  - Learned words reset

## Implementation order
1. Toolbar + action model updates (clipboard + mic prominence).
2. Real audio recorder + streaming transcription client.
3. NL/EN language + punctuation options.
4. Clipboard history and sync mode toggle scaffolding.
5. Spellcheck/dictionary defaults and QA.

## Done criteria for v0.2
- User can open clipboard quickly from toolbar.
- User can dictate in Dutch and English with punctuation.
- User sees spelling suggestions and can manage dictionary behavior.
- No API secrets in app binary.
