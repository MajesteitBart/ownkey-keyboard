---
timestamp: 2026-10-03T07:20:00Z
status: review
task: T-009
stream: WS-D
---

# Public Orukeet download

## Completed

- Release builds offer the Orukeet download on arm64 installs with a 64-bit process, by owner decision. See the 2026-10-03 entry in `decisions.md`.
- `supportsOrukeetRuntime` no longer takes the internal-build flag. Unit tests cover arm64 public and internal builds, debug-only x86_64, 32-bit processes and other ABIs.
- The settings card stays hidden on unsupported devices in public builds unless Orukeet is already selected.
- README, Play description, Play changelog 125 and CHANGELOG describe on-device and cloud routes separately.
- A release-variant build with x86_64 temporarily allowed (not committed) passed this emulator journey: the card appears, the 672 MB model downloads and verifies, activation loads `libsherpa-onnx-jni.so`, and live dictation typed the fixture sentence while recording. Stop committed the final sentence. No crashes were logged.

## Blockers

- No physical arm64 device was connected. Phone speed, memory, thermal behaviour, typing impact and accuracy remain unmeasured.

## Next Actions

- Collect T-009 phone evidence from public installs and owner devices, and revise the supported-device policy if it shows problems.
