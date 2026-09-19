---
id: WS-A
name: WS-A Personal dictionary delivery
owner: ownkey-keyboard-team
status: active
created: 2026-09-17T21:40:00Z
updated: 2026-09-17T21:40:00Z
---

# Workstream: WS-A Personal dictionary delivery

## Objective

Ship vocabulary hints, spelling corrections, filler removal and the keyboard-side fix flow on the Android app, on top of the Orukeet branch (draft PR #13).

## Owned Files/Areas

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/dictionary/`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voxtral/SpeechDictionaryScreen.kt`
- Dictation integration files: `TranscriptionClient.kt`, `TranscriptionBackend.kt`, `VoxtralDictationManager.kt`, `offline/OfflineDictationController.kt`
- Runtime: `lib/offline-asr` (hotword transport, decoder profile, IPC field)
- Backup/restore screens, routes, smartbar and text input layout hooks, keyboard manager panel resets
- English strings in `values/strings.xml`, Dutch in `values-nl/ownkey.xml`

## Dependencies

- Orukeet branch `fix/saved-voices-orukeet` (draft PR #13) for the local runtime and session safety.
- Windows reference `ba94c11b` for cleanup semantics and hotword transport values.

## Risks

- Beam-search cost on phones is unmeasured; the local hint toggle exists so it can be switched off.
- Unknown cloud endpoints may reject a `prompt` field; automatic mode only sends to documented hosts.

## Handoff Criteria

- JVM tests green for cleanup parity, persistence, fix flow and cloud field selection.
- Debug build installs; settings, backup and the keyboard flow verified on an emulator.
- Physical arm64 accuracy, latency and memory results recorded before public enablement.
