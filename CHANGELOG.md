# Changelog

All notable changes to this fork are documented in this file.

## Unreleased

### Added
- Split keyboard layout for large screens (unfolded foldables and tablets), with never/automatic/always modes and an adjustable middle gap. The space bar is duplicated so each half gets its own.
- On-device personalized next-word prediction: the keyboard learns your own word sequences (bigrams/trigrams) locally and uses them to predict the next word and to context-boost autocorrect candidates within sentences.
- Personal data suggestions: e-mail addresses you type are remembered locally and offered as suggestions in e-mail fields and when typing a matching prefix.
- "Personalized suggestions" toggle and "Clear learned data" action in Typing settings. Learning is skipped in incognito sessions and password fields; all data stays on the device.

### Changed
- Auto-space after punctuation is now deferred: instead of eagerly inserting a space after a period (which broke URLs, e-mail addresses, decimals and abbreviations and left stray trailing spaces), the space is inserted when the next word is started, with automatic sentence capitalization preserved.

## 0.6.0-alpha02-ownkey (2026-02-24)

### Added
- Ownkey brandbook added to repository (`docs/brandbook/ownkey-brand-book-2026-02-24-v2.html`).
- Voxtral settings fields for configurable endpoint and model.
- Encrypted API key storage (`VoxtralSecretsStore`) with migration from legacy plaintext preference.
- Backup sanitization to avoid exporting Voxtral API key.
- EN/NL frequency dictionaries for improved typing suggestions.
- Theme presets for Ownkey visual style with border and radius options.

### Changed
- README and technical docs rebranded to Ownkey content and roadmap.
- Voice input path focused on Mistral Voxtral ASR defaults.
- Autocorrect and suggestion ranking tuned for practical typing behavior.
- Quick action and settings UI refined for cleaner dark-mode interaction.

### Security
- Sensitive key handling moved to Keystore-backed encrypted storage.
- Export path now strips key material from backup output.
