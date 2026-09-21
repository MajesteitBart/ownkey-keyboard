# Orukeet implementation verification

Implementation is authorized. Public availability is still gated by the physical
arm64 matrix; this document does not treat emulator evidence as phone qualification.

## Delivered behavior

- An explicit transcription backend and per-recording snapshot hold provider settings
  and a model-version lease. Local failures never trigger a cloud fallback.
- Seven pinned files download individually through a Wi-Fi/Ethernet-by-default Android
  transfer job (API 34+) or foreground WorkManager worker (older supported APIs).
  Range validation, bounded retries, hash/size checks and free-space reserve protect
  installation. Download, selection and runtime residency are separate states.
- Activate verifies and loads before switching the selected route. Deactivate restores
  the previous route; Delete cancels use and removes model data. Updates preserve the
  old version until the candidate loads and no session holds it.
- Local microphone output uses an owned no-backup file and descriptor, decoded in the
  conventional `:offline_asr` process. App startup in that process skips preferences,
  keyboard and cache initialization. Native cancellation terminates that process.
- Voice rewrite can transcribe instructions locally and explicitly discloses that the
  instruction transcript and target text still go to the configured cloud rewrite
  provider. Changing the audio path while disclosure is visible requires confirmation
  of the new disclosure before recording. Existing preview/Replace checks remain.
- English/Dutch settings cover consent, progress, activation, cancellation, deletion,
  unsupported builds, route choice and notices. A local recording label states the
  30-second cap. Residency uses a provisional two-minute deadline and memory trimming.

## Runtime and distribution

The initial stock AAR was rejected for application distribution after inspection found
unused GPL eSpeak/TTS code. The final dependency is the
[ASR-only runtime](https://github.com/MajesteitBart/ownkey-keyboard/releases/tag/orukeet-runtime-sherpa-1.13.4-asr1),
19,696,942 bytes, SHA-256 `db5e489fb948e98a3c5cba14b1a5fe4e32aff68e238e971c4f27cb2db968677e`.
It uses the same sherpa-onnx 1.13.4 source, original Kotlin bindings and ONNX Runtime
1.27.0, with TTS disabled and Eigen MPL2-only. See [build provenance](asr-runtime-build.json)
and `tools/orukeet-runtime/` for pinned inputs and reproduction. Repackaging the same
local build twice produced the same digest; cross-machine reproducibility is not claimed.
All eleven runtime-release asset sizes/digests were checked before publication.
An anonymous download of the published AAR also matched the pinned byte count/hash.

The [model mirror](https://github.com/MajesteitBart/ownkey-keyboard/releases/tag/orukeet-v0.1.0-int8)
contains the seven unchanged upstream payloads plus provenance, upstream manifest and
checksums. All ten uploaded asset sizes/digests match the prepared files. Anonymous
encoder and notice range responses returned the exact expected slices; see
[range results](mirror-range-results.json). No app APK or public feature rollout was published.

Standalone APKs have one ABI each; CI preserves all four and makes arm64 the stable
rolling debug phone asset. The AAB is built separately with `-Pownkey.apkSplits=false`
because AGP 9 resource shrinking rejects a simultaneous multiple-APK/AAB build.
[APK evidence](release-package-results.json) and [bundle evidence](release-bundle-results.json)
record exact sizes, hashes, native
inventory, 16 KB ELF/ZIP alignment and absence of model weights. ASR native code is
included only for arm64 and x86_64; 32-bit keyboard/cloud APKs remain available.

Model/audio/partial/consent/pointer files use `noBackupFilesDir`. Existing platform XML
backup rules have explicit preference/IME/dictionary allowlists (including the API 31
resource variant); the manual backup builds its workspace from named preference,
keyboard, theme and optional clipboard sources. It never walks no-backup storage.
Restored selection without a verified pointer/files fails closed. Runtime and weight
licences/notices are shipped separately from the model download and remain readable.

## Executed checks

| Check | Result |
| --- | --- |
| Full app JVM suite | 342 tests pass, including new route/disclosure/concurrent-cancellation cases. |
| Offline-ASR JVM suite | 13 tests pass: integrity, resume, malformed ranges, 416/429, storage, leases, updates, tampering, PCM conversion and bounds. |
| Integrated lifecycle, standard API 36/x86_64 guest | ASR-only runtime passes WAV/M4A transcription, activation, active-lease rejection, inference process death, cancellation, retry, deactivation and deletion. Main process survives; selected route remains local after failure. |
| Integrated lifecycle, API 36/x86_64 16 KB guest | Same ASR-only lifecycle passes; actual guest page size is 16,384 bytes. |
| Real scheduler download | Standard guest passed in 34.54 seconds; final 16 KB guest passed in 52.147 seconds: visible activity → Wi-Fi UIDT job → all files verified. Cloud selection unchanged, no local activation/runtime load. No configured cloud endpoint was called during this test. |
| Version-bound download consent | Instrumentation passes: another model version or token cannot reuse approval. |
| Offline operation | Final runtime lifecycle passed with airplane mode enabled and Wi-Fi disabled; normal networking restored afterward. |
| Settings rendering | Downloaded/inactive controls and successful Activate → active state inspected. Model remains installed and selected on the running 16 KB guest. Physical large-font/TalkBack/tablet matrix remains pending. |
| Typing benchmark harness | Schema 2: one round with ten measured real letter taps and ten measured warm openings passed after three fresh-process starts (17.992 / 16.367 / 16.994 seconds); numeric [smoke output](typing-harness-smoke.json). This is a harness check, not a 5% regression result. |
| Distribution scripts | Four-ABI packager test and nine mocked rolling-publication scenarios pass. |
| Evidence tools | WER/silence aggregation and paired-bootstrap comparator tests pass. |
| Sustained inference | Final ASR-only 15-minute run passed on API 36/x86_64/4 KB: 79 WAV-to-AAC fixture cycles, no transcript mismatch or process failure; complete lifecycle test 931.981 seconds. Numeric [soak readings](asr-runtime-soak.json). |
| Release builds | Four release APKs and AAB build; 64-bit native and ZIP alignment pass; no model weights bundled. |

The typing tool records touch injection to host pre-draw after a text update and
keyboard show request to visible IME insets/pre-draw. It excludes ten warm-up taps per
round. The comparator requires 20 matched rounds, ≥100 taps and ≥10 warm openings per round.
It compares pooled typing p50/p95 and opening p95, bootstrapping paired whole rounds
for a 95% interval; upper bound ≤5% passes, overlap is inconclusive. Cold IME starts
remain a separate traced phone check. The README specifies paired ordering,
baseline-vs-baseline noise checks, fixed device/layout settings and each model lifecycle
condition. Corpus tooling aggregates WER by language/subset and silence false positives
without committing raw phrases or recordings.

## Startup repair found by the benchmark

The first repeated-opening run immediately after APK replacement failed to type:
layout metadata was indexed before the asynchronously combined extension list was
published. Looking up the extension through that lagging list returned missing and
left an empty keyboard. `ExtensionManager.getExtensionById` now consults the current
source indexes directly. Three subsequent fresh-process starts each passed repeated
opening and real-key typing. This verifies recovery from the observed race; it is
not a cold-start latency qualification. Warmup requirements remain explicit.

## Memory observations

The final-runtime [16 KB memory record](asr-runtime-memory.json) separates activation
from the cumulative first-inference high-water mark. Inference-process activation
VmHWM was 954,460 KiB (932.1 MiB), rising to 1,023,728 KiB (999.7 MiB) through the
first inference. These high-water readings capture RSS maxima, not sampled PSS peaks.
The debug main process includes instrumentation overhead; none of these values
qualifies a physical phone tier.

Across the 79-cycle 4 KB soak, inference PSS ranged from 907,374 to 982,250 KiB;
first/last readings were 907,374/981,913 KiB (8.2% increase). Main-process PSS stayed
between 199,067 and 201,028 KiB. The fixture and host are fixed; these results do not
establish Dutch quality, sustained mobile speed, battery drain or thermal behavior.

## Remaining release gates

- Physical arm64 devices across the supported API/RAM tiers, including a physical
  16 KB arm64 configuration and a validated unsupported-device policy.
- ≥50 Dutch and ≥50 English consented/public phrases with separate names, numbers,
  accents, noise, punctuation, mixed-language and silence results.
- Activation peak, steady residency, inference peak, cold/warm latency, thermal/soak,
  cancellation and foreground typing impact on phones. Compare two/five-minute
  residency policies under memory trim and app switching.
- Paired ≥20-round typing/keyboard-open results in every lifecycle condition, and
  physical accessibility/layout checks. The current emulator smoke is insufficient.
- Runtime-test the pre-API-34 WorkManager path, notification/network interruptions,
  real cloud requests during transfer, and capture attempted egress with networking
  enabled. Airplane-mode success alone is not an egress audit.
- Public capability enablement and route-accurate Play copy only after those gates.

`ORUKEET_INTERNAL` enables internal debug/beta/benchmark testing. Public release builds
keep local inference unavailable until these gates pass. T-009 owns the remaining
phone/corpus work. It is blocked on device/corpus access, not represented as completed.

## Reproduce the implemented checks

With Java 21 and the Android SDK environment configured:

```bash
./gradlew :app:testDebugUnitTest :lib:offline-asr:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :benchmark:assembleBenchmark
./gradlew :app:bundleRelease -Pownkey.apkSplits=false
python3 tools/orukeet-probe/integration.py --serial <dedicated-test-serial> --soak-seconds 900
python3 -m unittest discover -s tools/orukeet-probe -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 .github/scripts/test-package-phone.py
bash .github/scripts/test-publish-ci-debug.sh
bash .agents/scripts/pm/validate.sh
git diff --check
```

Prepare the pinned public inputs first and close the Device panel/helper before
instrumentation. The integration command replaces test model/audio state; use a
dedicated guest. See `tools/orukeet-probe/README.md` for the network opt-in test,
benchmark coordinates/rounds, fixture preparation and package alignment command.
All listed checks passed; Delano validation reports zero errors and warnings.

## Download follow-up

The reported network-wait bug was corrected after the initial verification.
[Follow-up evidence](network-wait-fix.md) covers a real metered-Wi-Fi download,
cellular consent/recovery UI, and the replacement internal APK. Earlier release
APK/AAB hashes above identify the pre-fix build.
