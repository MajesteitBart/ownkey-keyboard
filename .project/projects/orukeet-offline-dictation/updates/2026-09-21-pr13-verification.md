---
timestamp: 2026-09-21T21:12:00Z
status: review
task: T-003
stream: WS-B
---

# PR 13 verification

## Completed

- Reconciled the branch with the personal dictionary merged from PR 14, preserving the internal-only Orukeet gate and disabled-by-default local recognition hints.
- Full offline-ASR tests reproduced two filesystem failures on Windows. Model pointer replacement now uses an explicit replacing move, atomic where supported; model payload verification explicitly rejects symbolic links. Both existing regression tests pass without weakening their assertions.
- The combined source passes 463 app tests and all 19 offline-ASR tests, debug APK assembly and release Kotlin compilation.
- Probe evidence tests, ABI packaging tests and rolling CI publication-policy tests pass.

## In Progress

- Full PR review and CI verification before merging into main.

## Blockers

- No connected Android device for a fresh UI or physical Orukeet run. Existing phone accuracy, memory, latency, accessibility and network qualification gates remain required before public enablement. This merge is not public-release qualification.

## Next Actions

- Address confirmed review findings and verify the final reviewed commit before merge.

## Review repairs

- Orukeet compatibility now requires a 64-bit app process, as well as the supported device ABI and internal-build gates. A 32-bit APK on a dual-ABI device can no longer offer a model download without the packaged native runtime. Three regression cases cover dual-ABI 32-bit installs, supported internal builds and unsupported/public configurations.
- Runtime dependency preparation replaces a corrupt cached AAR with a verified download using a replacing move. Exercised the actual Gradle task in an isolated fixture cache seeded with corrupt bytes; the recovered artifact matches the pinned SHA-256 and extraction succeeds.
- All 466 app tests and 19 offline-ASR tests pass, alongside debug APK assembly and release Kotlin compilation.

References: [process ABI](https://github.com/MajesteitBart/ownkey-keyboard/pull/13#discussion_r4066550851), [runtime cache replacement](https://github.com/MajesteitBart/ownkey-keyboard/pull/13#discussion_r4066550856).
