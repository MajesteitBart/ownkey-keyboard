# Project Brief

## Problem
Most Android keyboards make users choose between privacy, typing quality, and modern AI features. AI add-ons often feel bolted on, provider-locked, or unclear about where text and audio go.

Ownkey should make practical AI input feel native to the keyboard while keeping control visible: the user chooses the API provider, supplies the key, and can understand what each networked feature sends.

## Target Outcome
Ownkey should be Play-Store-ready as an open-source, privacy-first AI keyboard with:

- reliable daily typing, suggestions, and autocorrect
- built-in voice dictation via configurable ASR
- selected-text rewrite through configurable LLM providers
- encrypted on-device key storage
- clean Ownkey-branded onboarding, settings, keyboard, and store assets

## Current Scope Boundaries
In scope:
- AI settings naming, defaults, provider presets, and UX polish
- Dictation and rewrite provider configuration
- Secure local storage and backup sanitization for API keys
- Store-facing messaging and metadata
- Ownkey brand alignment across setup, settings, keyboard surface, icon, and feature graphics
- Typing quality work when it improves everyday keyboard trust

Out of scope unless explicitly requested:
- Backend account system or hosted subscription layer
- Undisclosed telemetry or private-content monitoring
- Major upstream FlorisBoard sync work unrelated to Ownkey release readiness
- Publishing Play Store metadata without Bart's review
