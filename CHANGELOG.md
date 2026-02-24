# Changelog

All notable changes to this fork are documented in this file.

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
