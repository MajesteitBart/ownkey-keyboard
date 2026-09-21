---
timestamp: 2026-09-17T16:30:28Z
status: review
task:
stream: WS-E
---

# Progress Update

## Completed
- Located the complete Orukeet Android integration as uncommitted work based on `12fa074c`. The remote main branch and runtime/model release tags did not contain this application integration.
- Preserved both original working copies and reconciled the recovered integration, the existing rewrite defaults, and the saved-voice fix in an isolated `fix/saved-voices-orukeet` branch.
- Retained the separate inference-process startup guard, orphaned-recording cleanup, model-download network fix, and internal-only Orukeet availability. Startup and backup restore both use the saved-voice recovery reader.
- Combined validation passed: 359 app JVM tests and 13 offline-ASR JVM tests, with no failures, errors, or skips. Debug and release Kotlin compilation, both APK variants, and the Android test APK built successfully.
- Two Android instrumentation checks passed on API 36: download transport consent and model-version-scoped download authorization.
- Upgraded the existing debug app on an x86_64 emulator without uninstalling it. Inspected the AI screen and confirmed the Orukeet section and enabled Download model control.
- Verified that the arm64 debug APK includes the native Orukeet runtime, uses `nl.bartvandermeeren.ownkey.debug`, and matches the signing certificate of the prior Orukeet APK.
- Project validation passed with zero errors or warnings. Source changes pass whitespace checks; inherited whitespace in two upstream runtime notice files and the earlier rewrite-defaults update is preserved.

## In Progress
- Arm64 internal-build handoff from `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`. Install over the existing Orukeet debug app to retain its data.

## Blockers
- Physical-device voice recovery and Orukeet qualification remain unverified. Existing public-release gates remain unchanged.

## Next Actions
- Confirm both edited and added voices survive restarts on the user's device. The automated persistence tests cover repeated real file saves and fresh datastore instances.
- Keep the recovered source together with the persistence fix when preparing subsequent builds. The earlier main-only release APK is superseded for this handoff.
- Recovery requires the original voice data to remain in preferences or a backup; already-overwritten data cannot be reconstructed.
