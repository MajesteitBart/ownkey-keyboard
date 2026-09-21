---
timestamp: 2026-09-17T11:57:55Z
status: review
task:
stream: WS-E
---

# Progress Update

## Completed
- Reproduced loss of edited and added voices after preferences reload. JetPref 0.3.0 decodes escaped line breaks in JSON incorrectly; the voice parser then falls back to defaults. A separate ID-only migration also discards edits to older built-in voices.
- Encoded voice JSON with equivalent Unicode escapes for line breaks and literal backslashes, retaining compatibility with existing JSON readers and the existing preference key.
- Added a reader for startup and backup restore that recovers original voice JSON before JetPref decoding. It adapts only the voice entry and does not write the source file.
- Removed the ID-only migration so saved voice names, instructions, IDs, and order remain user-controlled.
- Added real file persistence tests covering repeated saves and fresh datastore instances, legacy recovery, backup import/export, escape combinations, and unchanged unrelated preference entries.
- Validation passed: 352 JVM tests across 48 suites, zero failures/errors/skips; `:app:compileDebugKotlin`, `:app:compileReleaseKotlin`, `:app:assembleRelease`, and `git diff --check`.
- Reviewed startup storage location parity, restore wiring, compatibility with older JSON readers, and preservation of pre-existing local changes. The recovery reader is idempotent and leaves its source file unchanged.
- Windows test fixtures account for `File.renameTo` not replacing existing destinations there, while keeping the real JetPref reader and writer under test. Android/Linux keep their normal overwrite behavior.
- Project validation passed with zero errors or warnings.

## In Progress
- The first release APK was built from the main-branch source, which lacked the uncommitted Orukeet integration. It is superseded by the internal build described in [Orukeet reconciliation](2026-09-17-orukeet-reconciliation.md).

## Blockers
- No physical Android device is connected. The combined build has subsequently passed an emulator upgrade and AI-screen smoke check; see the reconciliation update.

## Next Actions
- Install the combined internal build over the existing Orukeet debug app for physical-device confirmation. No public release was published.
- Recovery applies only while the original voice data remains in preferences or a backup; voices already overwritten by saving/resetting defaults cannot be reconstructed.
