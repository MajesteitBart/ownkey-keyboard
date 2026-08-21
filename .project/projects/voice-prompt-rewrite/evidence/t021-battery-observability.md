---
task: T-021
created: 2026-08-06T07:03:58Z
updated: 2026-08-06T07:03:58Z
status: complete
---

# T-021 Battery Observability Evidence

## Confirmed findings

- The phone app was already shell-profileable, but the benchmark module was disabled and referenced the upstream package/component.
- Clipboard cleanup woke every 60 seconds for the process lifetime even when both cleanup policies were disabled.
- Blocking provider requests lacked a client-owned IO/cancellation boundary; a canceled operation could therefore retain its socket until the configured timeout.
- Phone microphone sampling is active-session-only and recorder teardown already covers field switch, keyboard hide, input restart, secure transition, and IME teardown.
- The Wear IME discarded field-exit audio only after stopping, publishing, and reading it, and its provider request was not canceled by input teardown.
- No app or Wear wake lock, repeating alarm, job scheduler, WorkManager job, or foreground service was found.

## Delivered controls

- Enabled and corrected the release-like benchmark module, added a neutral host field and a high-precision `PowerMetric` idle-keyboard scenario.
- Added fixed, content-free System Trace spans for phone recording/processing/provider work and Wear recording/provider work.
- Replaced fixed clipboard polling with earliest-expiry scheduling and no scheduled wake while disabled or empty.
- Confined blocking provider work to IO and disconnect active phone/Watch sockets on coroutine cancellation.
- Cancel abandoned Wear recordings and requests on keyboard/activity teardown.
- Added `docs/development/battery-profiling.md` with packages, commands, labels, hardware requirements, and attribution limits.

## Automated evidence

- `./gradlew :app:testDebugUnitTest`: 280 passed, 0 failed, 0 errors, 0 skipped across 41 suites.
- `./gradlew :benchmark:assembleBenchmark :app:compileReleaseKotlin`: passed.
- `./gradlew :app:assembleBenchmark`: passed; release-like target APK produced.
- `./gradlew :wear:compileDebugKotlin :wear:compileReleaseKotlin`: passed.
- `./gradlew :wear:processDebugMainManifest :wear:processReleaseMainManifest`: passed; the release merge retains shell profiling.
- `delano validate`: passed with 0 errors and 0 warnings.
- APK manifest inspection confirms the target package is `nl.bartvandermeeren.ownkey.bench`, it contains the real IME service and shell profiling, and the benchmark test APK contains its exported neutral host activity.

## Hardware-only carry-forward

No ADB device was attached. High-precision energy rails, the ten-second idle samples, and manual phone/Watch voice trace captures must be collected on supported physical hardware under T-019. `PowerMetric` is system-wide; the result must be treated as controlled A/B evidence correlated with Ownkey spans, not exact per-app energy accounting.
