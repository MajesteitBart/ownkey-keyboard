---
type: research_findings
project: voice-prompt-rewrite
slug: battery-observability
created: 2026-08-06T06:25:15Z
updated: 2026-08-06T07:03:58Z
---

# Findings: Battery Observability and Drain Audit

## Source References

- `app/src/main/AndroidManifest.xml`
- `settings.gradle.kts`
- `benchmark/build.gradle.kts`
- `benchmark/src/main/kotlin/dev/patrickgold/florisboard/benchmark/StartupBenchmark.kt`
- `benchmark/src/main/kotlin/dev/patrickgold/florisboard/benchmark/BaselineProfileGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioSessionCoordinator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/AudioLevelHistorySampler.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionClient.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteClient.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `wear/src/main/kotlin/nl/bartvandermeeren/ownkey/wear/WearTranscription.kt`
- `wear/src/main/kotlin/nl/bartvandermeeren/ownkey/wear/OwnkeyWearImeService.kt`
- Android Developers: Power Profiler, Macrobenchmark `PowerMetric`, custom trace events, and system tracing guidance.

## Observations

- The app is already shell-profileable, so release-like system tracing is permitted.
- The benchmark module is commented out in `settings.gradle.kts`, and its tests still target the upstream `dev.patrickgold.florisboard` package instead of Ownkey's benchmark application ID. The existing automated trace path therefore cannot target the installed Ownkey benchmark build.
- `ClipboardManager` launches a process-lifetime loop that wakes every 60 seconds even when old-item and sensitive-item auto-clean are both disabled by default.
- Dictation and voice rewrite share one recorder and invalidate it on field switch, keyboard hide, input restart, secure transition, and IME teardown. The 20 Hz amplitude sampler is scoped to active recording and stops for pause/processing/teardown.
- Provider clients use blocking `HttpURLConnection`. Preset rewrite and transcription callers currently move most calls to IO, but the new voice-rewrite caller can reach `LlmRewriteClient` from a main-immediate scope. Client-internal IO confinement and disconnect-on-cancellation are required to satisfy NFR-001/NFR-004 for every caller.
- Android power rails and Macrobenchmark `PowerMetric` are system-wide measurements. They must be correlated with fixed Ownkey trace spans and run on supported physical hardware; they are not exact per-app energy accounting.
- The Wear companion is a separate device/UID. Its IME previously published and read a recording that it immediately discarded on field exit, while a blocking transcription could continue for the full 90-second read timeout. Lifecycle cancellation, disconnect-on-cancel, shell profiling, and Wear-specific fixed spans now cover this path.

## Options Considered

| Option | Pros | Cons | Decision |
| --- | --- | --- | --- |
| Add production battery analytics | Fleet visibility | Privacy risk, misleading attribution, unnecessary network work | Rejected |
| Add an in-app estimated battery dashboard | User-visible | Android does not expose exact per-app energy attribution to ordinary apps | Rejected for this pass |
| Repair Macrobenchmark and add content-free trace spans | Repeatable, local, current Android tooling | Requires supported physical hardware for power numbers | Selected |
| Keep the one-minute cleanup poll and only trace it | Minimal code change | Preserves a known idle wake-up | Rejected |
| Event-driven clipboard expiry scheduling | No wake when disabled/empty; wakes at the next real expiry | Requires deterministic scheduler tests | Selected |

## Fold-Forward Candidates

| Finding | Target Artifact | Proposed Change |
| --- | --- | --- |
| Automated battery profiling is disabled and misaddressed | `plan.md`, WS-E, T-021 | Restore benchmark module, correct Ownkey IDs, and add a `PowerMetric` scenario. |
| Battery traces need feature correlation without content | `spec.md`, `plan.md`, T-021 | Add fixed trace spans for recording, processing, transcription, and rewrite. |
| Clipboard cleanup wakes unconditionally | T-021 | Replace fixed polling with next-expiry scheduling and tests. |
| Blocking provider calls can outlive UI cancellation | T-021, T-019 | Move I/O into a cancellation-aware client boundary that disconnects active connections. |
| Wear recording/request work could outlive its surface | T-021, T-019 | Cancel rather than publish abandoned audio, cancel provider work on input/activity teardown, and profile the watch separately. |
| Physical power evidence remains unavailable locally | T-019 | Keep the final power-number gate blocked behind the existing physical-device work. |

## Open Questions

- Real energy and battery deltas remain to be captured on a supported physical device during T-018/T-019.
