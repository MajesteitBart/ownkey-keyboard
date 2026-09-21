---
timestamp: 2026-09-15T13:40:48Z
status: review
task:
stream:
---

# Progress update: Emulator runtime and SDK discovery

## Completed

- Fixed user-terminal and T3 Android command discovery using the installed user-local SDK/JDK, shell environment and command wrappers. Pinned the existing AVD directory so `avdmanager list avd` finds both guests. No apt SDK installation was needed. The 16 KB guest is running and open in the Device panel; the ordinary guest is stopped.
- Created a standalone probe with pinned input preparation, separate conventional/isolated services, Android M4A conversion, aggregate memory/timing evidence and repeatable failure/retry cases.
- Ran nine cases on API 36 x86_64 with 16 KB pages. Conventional WAV/M4A transcription and retry succeeded with matching normalized hashes. Kill/cancel cases preserved the main process; cancellation reached service death in 136 ms.
- Measured activation RSS high-water of about 848-855 MiB and cumulative RSS high-water through inference of 912-921 MiB across three successful runs. These are emulator observations with filesystem caches uncontrolled.
- Recorded isolation failures: descriptor reopening caused native permission-denied abort; M4A decoding raised `IOException`. Updated the spec/plan/decisions to continue with the conventional service for physical feasibility.
- Verified artifact hashes, all 16 native ELF libraries' 16 KB alignment, and the probe APK's 16 KB ZIP alignment.

See [findings](../research/emulator-runtime.md), [aggregate evidence](../research/emulator-runtime-results.json) and the [reproduction guide](../../../../tools/orukeet-probe/README.md).

## Remaining work

- Physical arm64 measurements, WER corpus/scoring, actual typing and keyboard-open benchmarks, cold/warm and thread comparisons, OS OOM observation, 15-minute soak, and retention/trim behavior.
- Per-file downloader/update/cloud-concurrency and integrated editor/audio-lifecycle acceptance cases.
- The existing T3/agent process still has its old supplementary groups. A fresh desktop login is needed for direct KVM launches from that session; this does not affect the running 16 KB guest. `sg`/`newgrp` are absent, so no group-refresh workaround is claimed.

## Validation

- Standalone `assembleDebug` passed. Verified inputs, native alignment, APK alignment and the nine-case result collection completed; isolation failures are recorded rather than reported as passes.
- Delano validation passed with zero summary errors/warnings; twelve existing local-only remote-sync observations in other projects remain. Direct checks passed for 30 files and 60 local Markdown links, JSON/Python syntax, aggregate-result consistency and whitespace. Shell syntax and command discovery passed; `avdmanager` lists both guests.
- Production app files and task activation state remain unchanged. The overall probe is pending; no production approval is claimed.

## Next action

Run the next bounded physical-device probe using the conventional service and extend the measurement harness for the remaining gates. Isolation needs a separately scoped adapter experiment if required.
