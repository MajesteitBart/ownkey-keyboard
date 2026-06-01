# Project Overview

## Mission
Build Ownkey into a privacy-first Android keyboard for practical AI input: fast daily typing, BYOK voice dictation, selected-text rewrite, clear provider control, and a polished dark-first product feel.

## Product Position
- Open-source Android keyboard derived from FlorisBoard and kept under Apache-2.0.
- BYOK model: users bring their own provider API keys instead of being forced into one hosted account.
- AI features are explicit settings, not hidden monitoring of private content.
- Current user-facing AI umbrella covers dictation and LLM rewrite, not just Voxtral voice input.

## Active Delivery Scopes
- `ownkey-brand-system-refresh`: app icon, setup flow, keyboard surface, store assets, and Ownkey brand tokens.
- AI settings refresh: Settings home now says `AI`; the AI page groups dictation and rewrite, mirrors onboarding styling, and includes provider presets.
- `typing-speed-core` / `predictive-typing-quality-trust`: typing quality, suggestion latency, autocorrect trust, and recovery behavior remain important but are not the only current product story.

## Current Health
- `main` is aligned with `origin/main` at commit `df6657e0` (`Refresh Ownkey AI settings`) as of 2026-06-02 context refresh.
- Latest APK was refreshed after the AI settings work in the usual Ownkey MEGA release folder.
- Latest release validation passed: debug/release Kotlin compile, release assemble, diff check, stale visible Voxtral umbrella scan, APK badging, and `delano validate`.
- Remaining caveat from latest work: no live emulator/device screenshot was captured for the refreshed AI settings UI.
