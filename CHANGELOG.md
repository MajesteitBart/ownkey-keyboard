# Changelog

This file lists notable changes to Ownkey Keyboard. Version numbers match the [GitHub releases](https://github.com/MajesteitBart/ownkey-keyboard/releases). Ownkey is a fork of FlorisBoard 0.6.0-alpha02. For changes before the fork, see [FlorisBoard's releases](https://github.com/florisboard/florisboard/releases).

## 0.8.0 (2026-09-25)

Version code 122.

### Added
- Rebuilt autocorrect for English, Dutch and mixed Dutch and English typing ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)). Typos are fixed on space using key distances, where each tap landed and the previous word. Typos with two wrong letters, missing apostrophes (`dont` becomes "don't") and words typed without a space (`ofthe`) are fixed as well.
- Next-word predictions and context-aware suggestions from English and Dutch word-pair data ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)).
- One Autocorrect strength setting with Gentle, Normal and Strong. It replaces the tuning sliders ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)).
- After a correction, the typed word is the first suggestion and one tap restores it. Words you keep typing stop being corrected ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)).
- Personal dictionary for dictation ([#14](https://github.com/MajesteitBart/ownkey-keyboard/pull/14)). Saved words steer recognition, saved corrections rewrite transcripts, and filler words can be removed. "Fix a word" on the keyboard turns a misheard word into a saved correction.
- Floating split keyboard for tablets ([#16](https://github.com/MajesteitBart/ownkey-keyboard/pull/16)). It has two floating panels, actions above the keys and a background opacity setting.
- Voice-only mode ([#16](https://github.com/MajesteitBart/ownkey-keyboard/pull/16)). A draggable dictation bar replaces the keyboard. Password, incognito and number fields keep the full keyboard.
- Ownkey Signal themes: Graphite, Black and Bone ([#16](https://github.com/MajesteitBart/ownkey-keyboard/pull/16)).

### Changed
- Signal Graphite and Heroicons Mini are the default theme and icon style, unless you picked your own ([#16](https://github.com/MajesteitBart/ownkey-keyboard/pull/16)).
- "Block possibly offensive words" now also keeps words on the reviewed block lists out of autocorrect and next-word predictions ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)).
- Releases contain one APK per processor type instead of a universal APK.

### Fixed
- The autocorrect quick action and settings switch now turn autocorrect on and off ([#17](https://github.com/MajesteitBart/ownkey-keyboard/pull/17)).
- Edited and added rewrite voices no longer reset when settings reload or a backup is restored ([#13](https://github.com/MajesteitBart/ownkey-keyboard/pull/13)).

### Internal builds only
- Orukeet on-device dictation is included in debug and beta builds ([#13](https://github.com/MajesteitBart/ownkey-keyboard/pull/13)). Public releases don't include it yet.

## 0.7.0 (2026-09-09)

Version code 121. The 0.6.1-alpha01 build from the same day was never published.

### Added
- Voice rewrite ([#9](https://github.com/MajesteitBart/ownkey-keyboard/pull/9)). Hold the microphone key, say what to change, and review the result before it replaces your text.
- The mic key gives press and hold haptics and shows a hold ring with a "Hold to rewrite" hint ([#11](https://github.com/MajesteitBart/ownkey-keyboard/pull/11)).

### Changed
- The AI rewrite panel shows one step at a time: choose, record, process, then review and replace ([#10](https://github.com/MajesteitBart/ownkey-keyboard/pull/10)).
- The dictation waveform is a fixed nine-bar mark that shows speech at any microphone volume ([#11](https://github.com/MajesteitBart/ownkey-keyboard/pull/11)).
- Dictation and rewrite requests stop when the keyboard closes. The keyboard no longer samples audio, polls or cleans the clipboard while idle, which saves battery ([#9](https://github.com/MajesteitBart/ownkey-keyboard/pull/9)).

### Fixed
- Dictated text after punctuation gets a space, so it reads "keyboard. When I" instead of "keyboard.When I" ([#10](https://github.com/MajesteitBart/ownkey-keyboard/pull/10)).
- Dictation shows one processing indicator instead of two ([#10](https://github.com/MajesteitBart/ownkey-keyboard/pull/10)).

## 0.6.0 (2026-07-24)

Version name 0.6.0-alpha02, version code 119. This was the first published Ownkey release, and it covers everything since the fork.

### Added
- Voice dictation with Mistral Voxtral as the default, plus a configurable endpoint and model.
- AI rewrite for selected text, with presets for OpenAI, Anthropic, Mistral, OpenRouter and custom OpenAI-compatible endpoints. Rewrite voices include Improve writing, Fix grammar, Make shorter, Rewrite in Dutch and Plainspoken, and you can add your own.
- **Settings → AI** configures dictation and rewrite in one place.
- English and Dutch frequency dictionaries for suggestions.
- Autocorrect recovery. One tap undoes the last correction, backspace restores the typed word, and a correction you reverted isn't repeated. Autocorrect also adjusts per app.
- On-device next-word learning from your own typing, with a toggle and a "Clear learned data" action. Email addresses you type are offered again in email fields. Learning is skipped in incognito mode and password fields.
- Split keyboard for unfolded foldables and tablets, with never, automatic and always modes and an adjustable gap.
- Ownkey Liquid Glass theme and Glass presets, four icon packages and a live accent color.
- Direct numeric keypad shortcut from the ?123 key.
- Wear OS keyboard with dictation.

### Changed
- Renamed to Ownkey Keyboard with package name `nl.bartvandermeeren.ownkey`.
- Auto-space after punctuation waits until the next word starts. This no longer breaks URLs, email addresses and decimals.

### Fixed
- An out-of-memory crash at startup while building the typo index.

### Security
- API keys are stored in Android Keystore-backed encrypted storage. The dictation key is removed from settings backups.
