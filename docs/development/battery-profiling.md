# Ownkey battery profiling

Ownkey supports local, content-free battery diagnosis through Android system traces, a release-like Macrobenchmark target, and Batterystats. No production analytics or keyboard-content telemetry is involved.

## What is measurable

- Macrobenchmark `PowerMetric` reports system-wide power/energy over a controlled scenario and writes a Perfetto trace for each iteration.
- Android Studio System Trace/Power Profiler correlates device power activity with Ownkey threads and fixed `Ownkey.*` spans.
- Batterystats records longer-running per-UID process, network, service, wake-lock, and estimated-power activity.

Power rails are device-wide measurements. They are useful for clean-device A/B comparisons, but they are not exact per-app energy accounting.

## Packages and component

| Build | Application ID |
| --- | --- |
| Release | `nl.bartvandermeeren.ownkey` |
| Beta | `nl.bartvandermeeren.ownkey.beta` |
| Debug | `nl.bartvandermeeren.ownkey.debug` |
| Benchmark target | `nl.bartvandermeeren.ownkey.bench` |

The benchmark IME component is:

```text
nl.bartvandermeeren.ownkey.bench/dev.patrickgold.florisboard.FlorisImeService
```

## Repeatable idle-keyboard power benchmark

Requirements:

- a physical device running API 29 or newer;
- Pixel 6 or newer hardware for high-precision `PowerMetric` energy rails;
- USB debugging enabled and a sufficiently charged, thermally stable device;
- `benchmark` selected for both the app and benchmark modules when running from Android Studio.

From PowerShell:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=dev.patrickgold.florisboard.benchmark.KeyboardIdlePowerBenchmark
```

The benchmark opens a neutral host text field, selects the Ownkey benchmark IME, holds the keyboard visibly idle for ten seconds, and repeats three times. Unsupported power hardware is skipped explicitly instead of returning a misleading zero or low-precision number.

Connected-test results and `.perfetto-trace` files are written below `benchmark/build/outputs/connected_android_test_additional_output/`.

## Manual System Trace for voice and provider work

1. Build/install the `benchmark` app variant.
2. In Android Studio Profiler, select `nl.bartvandermeeren.ownkey.bench` and record a System Trace with power data on a physical device.
3. Exercise one bounded scenario: idle keyboard, dictation recording, voice rewrite, or preset rewrite.
4. Search the trace for these fixed spans:

```text
Ownkey.voice.dictation.recording
Ownkey.voice.rewrite.recording
Ownkey.voice.dictation.processing
Ownkey.voice.rewrite.processing
Ownkey.ai.transcription.request
Ownkey.ai.rewrite.request
```

The labels identify operation state only. They never include typed/selected text, recognized speech, generated output, endpoint URLs, provider bodies, API keys, audio metadata, or content-derived lengths/fingerprints.

## Wear OS companion

The Wear companion runs on a different device and UID, so phone traces and the phone Macrobenchmark do not include its energy use. Select the watch as the ADB/Profiler target and search its System Trace for:

```text
Ownkey.wear.voice.recording
Ownkey.wear.ai.transcription.request
```

The Wear release manifest is shell-profileable. Leaving the Wear activity or keyboard now cancels an active recording/request, and request cancellation disconnects the blocking HTTP connection rather than waiting for the 90-second read timeout.

## Longer Batterystats capture

For a longer real-world session, follow Android's current Batterystats workflow:

```powershell
adb shell dumpsys batterystats --reset
# Disconnect USB, exercise Ownkey, then reconnect.
adb shell dumpsys batterystats > artifacts/batterystats.txt
adb bugreport artifacts/ownkey-battery-bugreport.zip
```

Use the App Stats view to select the tested Ownkey application ID. Keep display brightness, radios, thermal state, installed background apps, and the scenario duration consistent between A/B runs.

Android now recommends System Trace, Macrobenchmark power metrics, or Power Profiler for primary investigation; Battery Historian is useful for longer correlation but is no longer actively maintained.

## Sources

- [Power Profiler](https://developer.android.com/studio/profile/power-profiler)
- [Macrobenchmark PowerMetric](https://developer.android.com/reference/androidx/benchmark/macro/PowerMetric)
- [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [Custom trace events](https://developer.android.com/topic/performance/tracing/custom-events)
- [Batterystats setup](https://developer.android.com/topic/performance/power/setup-battery-historian)
