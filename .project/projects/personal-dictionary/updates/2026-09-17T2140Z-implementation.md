---
timestamp: 2026-09-17T21:40:00Z
status: review
task: T-001
stream: WS-A
---

# Progress Update

## Completed
- T-001 storage and cleanup: Windows rules ported, file-backed repository with atomic writes, versioned export and validated restore.
- T-002 settings: Personal dictionary page under AI, typing-dictionary link, backup and restore section, English and Dutch copy.
- T-003 integration: session snapshots, off-thread cleanup before commit, neutral filler-only result, Mistral `context_bias`, explicit `prompt` mode, Orukeet hotwords over IPC with a 64 KiB budget and a beam profile.
- T-004 keyboard flow: "Fix a word" chip, chooser, retype-in-editor row, save correction plus optional word hint, manual fallback into settings.
- JVM evidence: `:app:testDebugUnitTest` 415 tests, 0 failures; `HotwordTransportTest` 5 tests green. The two `ModelLifecycleTest` failures in `lib/offline-asr` also fail on the base branch on this Windows machine (symlink and atomic write semantics), so they are not caused by this change.

- Emulator smoke run (API 35, x86_64 debug build): Personal dictionary page renders; word and correction added through the form; filler and hint cards; backup checkbox present; mock dictation → "Fix a word" chip → chooser with phrase selection → word selected in the host editor → live preview while typing → saved; the next dictation applied the new correction. Two defects found and fixed on the way: a shared string formatter loops forever when a value contains its own placeholder (user text now goes through a single-pass helper), and Android's ICU regex rejects `UNICODE_CHARACTER_CLASS` (explicit Unicode classes now).

## In Progress
- None.

## Blockers
- T-005 physical arm64 qualification: no phone connected in this session.

## Next Actions
- Open the feature PR against `fix/saved-voices-orukeet` (draft PR #13).
- Run the device probe from T-005 and confirm the on-device hint default.
