---
timestamp: 2026-09-15T12:55:16Z
status: review
task:
stream:
---

# Progress update: Android emulator setup

Historical setup snapshot. See the later [runtime probe and environment repair update](2026-09-15-emulator-runtime.md) for current findings and guest state.

## Completed

- Installed user-local Java 21, Android SDK/platform/build tools, emulator, and API 36 ordinary/16 KB x86_64 system images.
- Created both virtual devices and a reusable local launcher/environment snippet.
- Booted the standard emulator using KVM. Verified API 36, x86_64, 4 KB pages, boot completion, ADB control, and the rendered Settings UI.
- Verified the user-launched 16 KB emulator: boot complete, API 36, x86_64, 16,384-byte pages, successful Settings launch, UI hierarchy and rendered screenshot. Both guests are online through ADB; emulator setup is complete.
- Added Stage A0 to the plan: emulator-only functional experiments can proceed before phones are available. [Evidence and local commands](../research/emulator-setup.md).

## In Progress

- Both emulators remain available for functional work. Orukeet harness/model experiments have not started.

## Blockers

- No remaining emulator setup blocker. The user granted persistent KVM membership and launched the second guest with the new group. Future launches from an old session still require a fresh login or an authenticated group launch because `sg`/`newgrp` are absent. Existing guests are usable through ADB.
- Orukeet graph/audio/service tests and physical-device performance gates have not run. This setup result does not approve production delivery.

## Validation

- KVM check, both Android boot/ADB/page-size/UI checks, and launcher shell syntax passed.
- Delano validation passed via `bash .agents/scripts/pm/validate.sh`; direct project Markdown checks and `git diff --check` passed. No app build was part of emulator installation.

## Next Actions

- Build and run the standalone probe harness for functional runtime experiments on the available guests. Record those results separately from the later arm64 phone performance gates.
