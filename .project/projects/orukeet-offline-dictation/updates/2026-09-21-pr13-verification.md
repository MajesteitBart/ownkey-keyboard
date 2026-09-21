---
timestamp: 2026-09-21T21:12:00Z
status: review
task: T-003
stream: WS-B
---

# PR 13 verification

## Initial verification ? ecc5d71

- Reconciled the branch with the personal dictionary merged from PR 14, preserving the internal-only Orukeet gate and disabled-by-default local recognition hints.
- Full offline-ASR tests reproduced two filesystem failures on Windows. Model pointer replacement now uses an explicit replacing move, atomic where supported; model payload verification explicitly rejects symbolic links. Both existing regression tests pass without weakening their assertions.
- At this initial commit, the combined source passed 463 app tests and all 19 offline-ASR tests, debug APK assembly and release Kotlin compilation.
- Probe evidence tests, ABI packaging tests and rolling CI publication-policy tests pass.

## In Progress

- Full PR review and CI verification before merging into main.

## Blockers

- No connected Android device for a fresh UI or physical Orukeet run. Existing phone accuracy, memory, latency, accessibility and network qualification gates remain required before public enablement. This merge is not public-release qualification.

## Next Actions

- Address confirmed review findings and verify the final reviewed commit before merge.

## First review repair batch ? 8ca026f

- Orukeet compatibility now requires a 64-bit app process, as well as the supported device ABI and internal-build gates. A 32-bit APK on a dual-ABI device can no longer offer a model download without the packaged native runtime. Three regression cases cover dual-ABI 32-bit installs, supported internal builds and unsupported/public configurations.
- Runtime dependency preparation replaces a corrupt cached AAR with a verified download using a replacing move. Exercised the actual Gradle task in an isolated fixture cache seeded with corrupt bytes; the recovered artifact matches the pinned SHA-256 and extraction succeeds.
- At this commit, all 466 app tests and 19 offline-ASR tests passed, alongside debug APK assembly and release Kotlin compilation.

References: [process ABI](https://github.com/MajesteitBart/ownkey-keyboard/pull/13#discussion_r4066550851), [runtime cache replacement](https://github.com/MajesteitBart/ownkey-keyboard/pull/13#discussion_r4066550856).

## Second review repair batch ? d0d872f

- Dictionary backup rejects unreadable/newer fallback state; unreadable-main recovery absorbs readable quarantines and preserves newer-file warnings. Quarantine decoding checks the actual schema. Keyboard correction checks the host/field, rejects relocation away from an end-of-field replacement, and only hides offers after recording or a successful external-IME switch. Automatic cloud hints require HTTPS.
- Damaged selected models can be repaired, directory symlinks are rejected, transfer suffixes are reserved, cancellation remains unloaded, and atomic replacement failures use a replacing fallback. An injected provider rejection exercised the fallback and recovered the pinned runtime checksum.
- Public builds hide the local feature card except when an existing local selection needs deactivation. Paused recordings retain their announcement, search filters remain clearable, and disclosure acknowledgment is immediately visible while preference persistence completes. The upstream MIT notice is packaged and included in the notices dialog.
- Probe integrity checks survive Python optimization. Package checks verify ELF architecture; input downloads are bounded; decoder format and process continuity are checked; stale probe callbacks and orphan processes are stopped. Latency comparison requires matching schema/metrics and interpolates quantiles, and scoring includes normalized character error counts. Benchmark cleanup covers startup failures and openings wait for confirmed IME dismissal. These changes build, but new physical measurements remain pending.
- The packaging regression now runs in CI, and release checksums use downloadable asset basenames. Device task notes include the September 18 phone activation stall and successful emulator attempt.
- Verification at this commit: 471 app tests and 21 offline-ASR tests pass with zero failures/errors/skips; debug assembly, release Kotlin compilation, benchmark APK and standalone probe APK build. Five evidence-tool tests pass under `python -O`. No new physical device run is claimed.
- The restart-readiness finding is refuted: every inference command loads the requested model in `InferenceService` before decoding when the engine is absent or its model ID differs. Readiness intentionally represents the verified model selection, not permanent native residency.

## Third review repair batch

- Dictionary saves fall back on any atomic replacement IO failure, retaining the durable previous copy and original failure when replacement fails too. A filesystem-provider regression rejects atomic overwrite and verifies two edits survive reopening. Correction offers/saves require current availability and field identity; entering voice rewrite interrupts the fix flow. Custom rewrite configuration fails before network access when its endpoint or model is missing.
- Dictionary restore validates with sanitized errors and completes speech persistence before changing other selected restore targets. Stored whitespace cannot bypass duplicate checks. Failed undo retains feedback, failed filler saves can be retried, and consecutive sentence fillers retain capitalization. Selection/checkbox semantics expose their state to accessibility services.
- Dictation cancellation is owner-scoped and coordinator disposal runs outside its monitor. Narrow local recording rows reserve room for the status by omitting the waveform. Older-API network failures retry within a bounded worker policy; model redirects are followed manually, limited to HTTPS before opening each target. Tests cover resume across HTTPS redirects and rejection of an HTTP target.
- Probe conversion uses unique temporary files, frees the codec on muxer-construction failure, and serializes cancellation/result delivery. Scoring reads UTF-8, mirror output must be empty, and the standalone build centralizes dependency repositories. Runtime cache downloads use unique temporary paths. Benchmark dismissal waits until insets are known, and WAV fixture parsing checks its header length.
- Verification: 476 app tests and 23 offline-ASR tests pass with zero failures/errors/skips. App debug assembly, release Kotlin compilation, benchmark APK, Android instrumentation APK and standalone probe APK build. Five evidence-tool tests pass under Python optimization. Instrumentation/device execution and public qualification remain pending.
